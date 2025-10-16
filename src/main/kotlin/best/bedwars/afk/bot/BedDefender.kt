package best.bedwars.afk.bot

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.pathfinding.AdvancedPathfinder
import best.bedwars.afk.bot.player.Inventory
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.block.BlockBed
import net.minecraft.block.material.Material
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import java.util.*

/**
 * Handles bed defense by placing blocks around the bed
 */
object BedDefender {
    
    private var isDefending = false
    private var bedPosition: BlockPos? = null
    private var blocksToPlace = mutableListOf<BlockPos>()
    private var currentBlockIndex = 0
    
    /**
     * Start defending the bed
     */
    fun startDefense(bedPos: BlockPos, onComplete: () -> Unit) {
        if (isDefending) {
            ChatUtils.info("Already defending bed!")
            onComplete()
            return
        }
        
        bedPosition = bedPos
        isDefending = true
        
        ChatUtils.info("${EnumChatFormatting.GOLD}Starting bed defense!")
        
        // Find all empty positions around the bed
        findEmptyPositionsAroundBed(bedPos)
        
        if (blocksToPlace.isEmpty()) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bed already fully protected!")
            isDefending = false
            onComplete()
            return
        }
        
        ChatUtils.info("Found ${blocksToPlace.size} positions to protect")
        currentBlockIndex = 0
        
        // Start placing blocks
        placeNextBlock(onComplete)
    }
    
    /**
     * Find all empty air positions around the bed that can be filled
     */
    private fun findEmptyPositionsAroundBed(bedPos: BlockPos) {
        val world = BedwarsAFK.mc.theWorld ?: return
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        synchronized(blocksToPlace) {
            blocksToPlace.clear()
        }
        
        // Find the actual bed blocks (beds are 2 blocks)
        val bedBlocks = mutableListOf<BlockPos>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val checkPos = bedPos.add(dx, dy, dz)
                    if (world.getBlockState(checkPos).block is BlockBed) {
                        bedBlocks.add(checkPos)
                    }
                }
            }
        }
        
        // Now find air blocks adjacent to bed blocks that we can fill
        val checkedPositions = mutableSetOf<BlockPos>()
        
        for (bedBlock in bedBlocks) {
            // Check all 6 sides + some diagonal positions
            for (x in -2..2) {
                for (y in 0..3) { // Only go up, not down
                    for (z in -2..2) {
                        val checkPos = bedBlock.add(x, y, z)
                        
                        // Skip if already checked or is a bed block
                        if (checkPos in checkedPositions || checkPos in bedBlocks) continue
                        checkedPositions.add(checkPos)
                        
                        // Check if this is a valid placement position
                        if (isValidPlacementPosition(world, checkPos)) {
                            synchronized(blocksToPlace) {
                                blocksToPlace.add(checkPos)
                            }
                        }
                    }
                }
            }
        }
        
        // Sort by: ground level first, then by distance from player
        synchronized(blocksToPlace) {
            blocksToPlace.sortWith(compareBy(
                { it.y }, // Lower Y first (ground level)
                { player.getDistanceSq(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) } // Then by distance
            ))
        }
        
        ChatUtils.info("${EnumChatFormatting.GRAY}Defense plan: ${blocksToPlace.size} blocks")
    }
    
    /**
     * Check if a position is valid for placing a block
     */
    private fun isValidPlacementPosition(world: net.minecraft.world.World, pos: BlockPos): Boolean {
        val blockAt = world.getBlockState(pos).block
        
        // Must be air
        if (blockAt.material != Material.air) return false
        
        // Must have a solid block below OR next to it to place on
        val blockBelow = world.getBlockState(pos.down()).block
        if (blockBelow.material.isSolid) {
            return true // Can place on top of solid block
        }
        
        // Check if there's a solid block on any side (for placing against walls)
        for (facing in EnumFacing.values()) {
            if (facing == EnumFacing.UP) continue // Skip up
            val neighbor = pos.offset(facing)
            val neighborBlock = world.getBlockState(neighbor).block
            if (neighborBlock.material.isSolid) {
                return true // Can place against this solid block
            }
        }
        
        return false
    }
    
    /**
     * Place the next block in the defense sequence
     */
    private fun placeNextBlock(onComplete: () -> Unit) {
        val blockListSize = synchronized(blocksToPlace) { blocksToPlace.size }
        
        if (currentBlockIndex >= blockListSize) {
            // All blocks placed!
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bed defense complete! Placed $blockListSize blocks")
            isDefending = false
            synchronized(blocksToPlace) {
                blocksToPlace.clear()
            }
            Movement.clearAll()
            Mouse.stopTracking()
            onComplete()
            return
        }
        
        val targetPos = synchronized(blocksToPlace) { 
            if (currentBlockIndex < blocksToPlace.size) blocksToPlace[currentBlockIndex] else null
        } ?: return
        val player = BedwarsAFK.mc.thePlayer
        val world = BedwarsAFK.mc.theWorld
        
        if (player == null || world == null) {
            isDefending = false
            onComplete()
            return
        }
        
        // Re-check if position is still valid (might have been filled by another placement)
        if (world.getBlockState(targetPos).block.material != Material.air) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Position already filled, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // Check if we still have wool
        ResourceTracker.update()
        val woolCount = ResourceTracker.getWool()
        if (woolCount == 0) {
            val totalBlocks = synchronized(blocksToPlace) { blocksToPlace.size }
            ChatUtils.error("Out of wool! Defense incomplete (${currentBlockIndex}/${totalBlocks})")
            isDefending = false
            Movement.clearAll()
            Mouse.stopTracking()
            synchronized(blocksToPlace) {
                blocksToPlace.clear()
            }
            onComplete()
            return
        }
        
        // Find and equip wool
        if (!equipWool()) {
            ChatUtils.error("Can't find wool in hotbar!")
            isDefending = false
            Movement.clearAll()
            Mouse.stopTracking()
            synchronized(blocksToPlace) {
                blocksToPlace.clear()
            }
            onComplete()
            return
        }
        
        val totalBlocks = synchronized(blocksToPlace) { blocksToPlace.size }
        ChatUtils.info("${EnumChatFormatting.AQUA}[${currentBlockIndex + 1}/${totalBlocks}] Moving near position...")
        
        // Calculate a safe position to stand (2 blocks away from target)
        val standPos = findSafeStandingPosition(targetPos, player)
        
        if (standPos != null) {
            val playerPos = player.position
            val distanceToStand = playerPos.distanceSq(standPos)
            
            if (distanceToStand > 4.0) { // More than 2 blocks away from standing position
                ChatUtils.info("${EnumChatFormatting.GRAY}Moving to safe position at ${standPos.x}, ${standPos.y}, ${standPos.z}")
                
                // Stop any existing pathfinder
                AdvancedPathfinder.stop()
                
                // Simple direct movement instead of pathfinder (to avoid spinning)
                moveToPositionDirectly(standPos, targetPos, onComplete)
            } else {
                // Already in good position, just place
                attemptBlockPlacement(targetPos, onComplete)
            }
        } else {
            // Can't find safe position, try to place anyway
            attemptBlockPlacement(targetPos, onComplete)
        }
    }
    
    /**
     * Move directly to a position without using pathfinder (simpler, no spinning)
     */
    private fun moveToPositionDirectly(standPos: BlockPos, targetPos: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        // Look towards the standing position
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            standPos.x + 0.5,
            standPos.y.toDouble(),
            standPos.z + 0.5
        )
        
        if (rotations != null) {
            Mouse.lookAt(rotations[0], 0f)
        }
        
        // Move forward for a bit
        TimeUtils.setTimeout({
            Movement.startForward()
            Movement.startSprinting()
            
            // Check distance periodically
            var moveTimer: Timer? = null
            var checkCount = 0
            moveTimer = TimeUtils.setInterval({
                checkCount++
                
                val currentPlayer = BedwarsAFK.mc.thePlayer
                if (currentPlayer == null) {
                    moveTimer?.cancel()
                    Movement.stopForward()
                    Movement.stopSprinting()
                    attemptBlockPlacement(targetPos, onComplete)
                    return@setInterval
                }
                
                val distanceToStand = currentPlayer.position.distanceSq(standPos)
                
                // If close enough or timeout, stop and place
                if (distanceToStand <= 2.0 || checkCount >= 20) { // 2 seconds max
                    moveTimer?.cancel()
                    Movement.stopForward()
                    Movement.stopSprinting()
                    Mouse.stopTracking()
                    
                    TimeUtils.setTimeout({
                        attemptBlockPlacement(targetPos, onComplete)
                    }, 200)
                }
            }, 0, 100)
        }, 300)
    }
    
    /**
     * Find a safe position to stand when placing a block (not on top of the target)
     */
    private fun findSafeStandingPosition(targetPos: BlockPos, player: net.minecraft.entity.player.EntityPlayer): BlockPos? {
        val world = BedwarsAFK.mc.theWorld ?: return null
        
        // Get all blocks we're planning to place
        val plannedBlocks = synchronized(blocksToPlace) { blocksToPlace.toSet() }
        
        // Try positions around the target (prioritize further away positions)
        val offsets = listOf(
            // 3 blocks away (best)
            BlockPos(3, 0, 0), BlockPos(-3, 0, 0),
            BlockPos(0, 0, 3), BlockPos(0, 0, -3),
            BlockPos(2, 0, 2), BlockPos(-2, 0, 2),
            BlockPos(2, 0, -2), BlockPos(-2, 0, -2),
            // 2 blocks away (good)
            BlockPos(2, 0, 0), BlockPos(-2, 0, 0),
            BlockPos(0, 0, 2), BlockPos(0, 0, -2),
            BlockPos(2, 0, 1), BlockPos(-2, 0, 1),
            BlockPos(2, 0, -1), BlockPos(-2, 0, -1),
            BlockPos(1, 0, 2), BlockPos(-1, 0, 2),
            BlockPos(1, 0, -2), BlockPos(-1, 0, -2),
            // Diagonal (okay)
            BlockPos(1, 0, 1), BlockPos(-1, 0, 1),
            BlockPos(1, 0, -1), BlockPos(-1, 0, -1),
            // 1 block away (last resort)
            BlockPos(1, 0, 0), BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1), BlockPos(0, 0, -1)
        )
        
        for (offset in offsets) {
            val testPos = targetPos.add(offset)
            
            // Make sure we're not standing in a position we need to place a block
            if (testPos in plannedBlocks) continue
            
            // Check if this is a valid standing position
            val blockBelow = world.getBlockState(testPos.down()).block
            val blockAt = world.getBlockState(testPos).block
            val blockAbove = world.getBlockState(testPos.up()).block
            
            // Check if player can fit (2 blocks tall)
            if (blockBelow.material.isSolid && 
                blockAt.material == Material.air && 
                blockAbove.material == Material.air) {
                
                // Make sure we can reach the target from this position (within 4.5 blocks)
                val distance = testPos.distanceSq(targetPos)
                if (distance <= 20.25) { // sqrt(20.25) = 4.5 blocks
                    return testPos
                }
            }
        }
        
        return null
    }
    
    /**
     * Attempt to place a block at the target position
     */
    private fun attemptBlockPlacement(targetPos: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer
        val world = BedwarsAFK.mc.theWorld
        
        if (player == null || world == null) {
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // Check if player is standing in the target position (or very close to it)
        val playerPos = player.position
        val distanceToTarget = Math.sqrt(
            Math.pow((player.posX - targetPos.x - 0.5), 2.0) +
            Math.pow((player.posZ - targetPos.z - 0.5), 2.0)
        )
        
        if (distanceToTarget < 0.8) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Too close to target position, moving away...")
            // Calculate direction away from target
            val awayYaw = Math.toDegrees(Math.atan2(
                player.posZ - (targetPos.z + 0.5),
                player.posX - (targetPos.x + 0.5)
            )).toFloat() - 90f
            
            Mouse.lookAt(awayYaw, 0f)
            
            TimeUtils.setTimeout({
                Movement.startForward()
                TimeUtils.setTimeout({
                    Movement.stopForward()
                    Mouse.stopTracking()
                    TimeUtils.setTimeout({
                        attemptBlockPlacement(targetPos, onComplete)
                    }, 200)
                }, 600)
            }, 200)
            return
        }
        
        // Re-check if position is still valid
        if (world.getBlockState(targetPos).block.material != Material.air) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Already filled!")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // Find which face to place on (the solid block we're clicking)
        val placeInfo = findBestPlacementInfo(world, targetPos, player)
        if (placeInfo == null) {
            ChatUtils.info("${EnumChatFormatting.RED}Can't find placement face, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        val (placeOnBlock, placeOnFace) = placeInfo
        
        // Calculate the exact point on the block face to look at
        val faceOffset = when (placeOnFace) {
            EnumFacing.DOWN -> Vec3(0.5, 0.0, 0.5)  // Bottom face
            EnumFacing.UP -> Vec3(0.5, 1.0, 0.5)    // Top face
            EnumFacing.NORTH -> Vec3(0.5, 0.5, 0.0) // North face
            EnumFacing.SOUTH -> Vec3(0.5, 0.5, 1.0) // South face
            EnumFacing.WEST -> Vec3(0.0, 0.5, 0.5)  // West face
            EnumFacing.EAST -> Vec3(1.0, 0.5, 0.5)  // East face
        }
        
        val lookAtPos = Vec3(
            placeOnBlock.x + faceOffset.xCoord,
            placeOnBlock.y + faceOffset.yCoord,
            placeOnBlock.z + faceOffset.zCoord
        )
        
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            lookAtPos.xCoord,
            lookAtPos.yCoord,
            lookAtPos.zCoord
        )
        
        if (rotations != null) {
            // Look at the exact face
            Mouse.lookAt(rotations[0], rotations[1])
            
            TimeUtils.setTimeout({
                // Make sure wool is still equipped
                if (!equipWool()) {
                    ChatUtils.error("Lost wool!")
                    currentBlockIndex++
                    placeNextBlock(onComplete)
                    return@setTimeout
                }
                
                // Right-click to place
                val heldItem = player.heldItem
                if (heldItem != null) {
                    val result = BedwarsAFK.mc.playerController.onPlayerRightClick(
                        player,
                        world,
                        heldItem,
                        placeOnBlock,
                        placeOnFace,
                        lookAtPos
                    )
                    
                    if (result) {
                        player.swingItem()
                        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Block placed at ${targetPos.x}, ${targetPos.y}, ${targetPos.z}")
                    } else {
                        ChatUtils.error("Placement failed!")
                    }
                } else {
                    ChatUtils.error("No item in hand!")
                }
                
                // Move to next block
                currentBlockIndex++
                
                TimeUtils.setTimeout({
                    Mouse.stopTracking()
                    placeNextBlock(onComplete)
                }, 300)
            }, 200)
        } else {
            // Skip this block
            ChatUtils.info("${EnumChatFormatting.YELLOW}Can't calculate rotation, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
        }
    }
    
    /**
     * Find the best block and face to place on
     */
    private fun findBestPlacementInfo(world: net.minecraft.world.World, targetPos: BlockPos, player: net.minecraft.entity.player.EntityPlayer): Pair<BlockPos, EnumFacing>? {
        // Priority order: below, then sides nearest to player, then above
        val facesToCheck = mutableListOf<EnumFacing>()
        
        // Always prefer placing on top of blocks
        facesToCheck.add(EnumFacing.DOWN)
        
        // Add horizontal faces sorted by distance to player
        val horizontalFaces = listOf(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST)
        val sortedHorizontal = horizontalFaces.sortedBy { facing ->
            val checkPos = targetPos.offset(facing.opposite)
            player.getDistanceSq(checkPos.x.toDouble(), checkPos.y.toDouble(), checkPos.z.toDouble())
        }
        facesToCheck.addAll(sortedHorizontal)
        
        // Last resort: above
        facesToCheck.add(EnumFacing.UP)
        
        // Find first valid face
        for (face in facesToCheck) {
            val blockToClickOn = targetPos.offset(face.opposite)
            val blockState = world.getBlockState(blockToClickOn)
            
            if (blockState.block.material.isSolid) {
                return Pair(blockToClickOn, face)
            }
        }
        
        return null
    }
    
    
    /**
     * Equip wool from inventory
     */
    private fun equipWool(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
        // Check hotbar for wool
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i)
            if (stack != null) {
                val itemName = stack.unlocalizedName.lowercase()
                val displayName = stack.displayName.lowercase()
                if (itemName.contains("cloth") || itemName.contains("wool") || displayName.contains("wool")) {
                    player.inventory.currentItem = i
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Stop defending
     */
    fun stop() {
        isDefending = false
        synchronized(blocksToPlace) {
            blocksToPlace.clear()
        }
        currentBlockIndex = 0
        Movement.clearAll()
        Mouse.stopTracking()
        AdvancedPathfinder.stop()
    }
    
    /**
     * Check if currently defending
     */
    fun isDefending(): Boolean = isDefending
    
    /**
     * Get blocks to place (for rendering)
     */
    fun getBlocksToPlace(): List<BlockPos> = blocksToPlace
    
    /**
     * Get current block index (for rendering)
     */
    fun getCurrentBlockIndex(): Int = currentBlockIndex
    
    /**
     * Render defense plan
     */
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!isDefending) return
        
        // Create a copy to avoid ConcurrentModificationException
        val blocksCopy = synchronized(blocksToPlace) {
            blocksToPlace.toList()
        }
        
        if (blocksCopy.isEmpty()) return
        
        val player = BedwarsAFK.mc.thePlayer ?: return
        val renderManager = BedwarsAFK.mc.renderManager
        
        GlStateManager.pushMatrix()
        GlStateManager.disableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.disableAlpha()
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)
        GlStateManager.disableDepth()
        
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        
        for ((index, pos) in blocksCopy.withIndex()) {
            val x = pos.x - renderManager.viewerPosX
            val y = pos.y - renderManager.viewerPosY
            val z = pos.z - renderManager.viewerPosZ
            
            val box = AxisAlignedBB(x, y, z, x + 1.0, y + 1.0, z + 1.0)
            
            // Color: green for completed, yellow for current, white for pending
            val (r, g, b, a) = when {
                index < currentBlockIndex -> floatArrayOf(0.0f, 1.0f, 0.0f, 0.3f) // Green (completed)
                index == currentBlockIndex -> floatArrayOf(1.0f, 1.0f, 0.0f, 0.5f) // Yellow (current)
                else -> floatArrayOf(1.0f, 1.0f, 1.0f, 0.2f) // White (pending)
            }
            
            // Draw filled box
            worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
            
            // Bottom
            worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
            
            // Top
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            
            // Sides
            worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
            
            worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
            
            worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
            
            worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
            
            tessellator.draw()
            
            // Draw outline
            GL11.glLineWidth(2.0f)
            worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
            
            // Bottom loop
            worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, 1.0f).endVertex()
            
            tessellator.draw()
            
            // Top loop
            worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            
            tessellator.draw()
            
            // Vertical lines
            worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
            worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            
            worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            
            worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            
            worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            
            tessellator.draw()
        }
        
        GlStateManager.enableDepth()
        GlStateManager.enableAlpha()
        GlStateManager.disableBlend()
        GlStateManager.enableTexture2D()
        GlStateManager.popMatrix()
    }
}
