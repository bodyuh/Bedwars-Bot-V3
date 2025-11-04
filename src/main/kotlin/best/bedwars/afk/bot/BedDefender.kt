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
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.multiplayer.WorldClient
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
    
    // Stuck detection variables
    private var lastPlayerPos: Vec3? = null
    private var stuckStartTime: Long = 0
    private var stuckCheckTimer: Timer? = null
    private const val STUCK_TIMEOUT_MS = 5000L // 5 seconds
    
    // Periodic defense variables
    private var periodicDefenseTimer: Timer? = null
    private var isPeriodicDefenseActive = false
    
    /**
     * Start monitoring if the bot is stuck
     */
    private fun startStuckDetection(onStuck: () -> Unit) {
        stuckCheckTimer?.cancel()
        lastPlayerPos = null
        stuckStartTime = 0
        
        stuckCheckTimer = TimeUtils.setInterval({
            val player = BedwarsAFK.mc.thePlayer ?: return@setInterval
            val currentPos = Vec3(player.posX, player.posY, player.posZ)
            
            if (lastPlayerPos == null) {
                lastPlayerPos = currentPos
                stuckStartTime = System.currentTimeMillis()
                return@setInterval
            }
            
            // Check if player has moved significantly
            val distance = lastPlayerPos!!.distanceTo(currentPos)
            if (distance < 0.3) { // Less than 0.3 blocks moved
                val stuckDuration = System.currentTimeMillis() - stuckStartTime
                if (stuckDuration >= STUCK_TIMEOUT_MS) {
                    ChatUtils.info("${EnumChatFormatting.RED}⚠ Bot stuck for ${stuckDuration / 1000}s, returning to generator...")
                    stopStuckDetection()
                    stopDefense()
                    onStuck()
                }
            } else {
                // Player moved, reset timer
                lastPlayerPos = currentPos
                stuckStartTime = System.currentTimeMillis()
            }
        }, 0, 1000) // Check every second
    }
    
    /**
     * Stop monitoring stuck detection
     */
    private fun stopStuckDetection() {
        stuckCheckTimer?.cancel()
        stuckCheckTimer = null
        lastPlayerPos = null
        stuckStartTime = 0
    }
    
    /**
     * Stop the bed defense process
     */
    fun stopDefense() {
        isDefending = false
        stopStuckDetection()
        Movement.stopForward()
        Movement.stopBackward()
        Movement.stopJumping()
        Mouse.stopTracking()
        ChatUtils.info("${EnumChatFormatting.YELLOW}Bed defense stopped")
    }
    
    /**
     * Check if we have enough resources for bed defense
     */
    private fun hasEnoughResourcesForDefense(): Boolean {
        // Update resources first
        ResourceTracker.update()
        
        // Count wool manually
        val player = BedwarsAFK.mc.thePlayer ?: return false
        var woolCount = 0
        for (itemStack in player.inventory.mainInventory) {
            if (itemStack != null) {
                val itemName = itemStack.unlocalizedName.lowercase()
                val displayName = itemStack.displayName.lowercase()
                if (itemName.contains("cloth") || itemName.contains("wool") || displayName.contains("wool")) {
                    woolCount += itemStack.stackSize
                }
            }
        }
        
        val woodCount = Inventory.getItemCount("planks")
        val endStoneCount = Inventory.getItemCount("end_stone")
        
        return woolCount >= 16 && woodCount >= 16 && endStoneCount >= 12
    }
    
    /**
     * Start periodic bed defense - continuously monitors and repairs bed defense
     */
    fun startPeriodicDefense(bedPos: BlockPos, onNeedResources: () -> Unit) {
        if (isPeriodicDefenseActive) {
            ChatUtils.info("Periodic defense already active!")
            return
        }
        
        bedPosition = bedPos
        isPeriodicDefenseActive = true
        
        ChatUtils.info("${EnumChatFormatting.GOLD}⚡ Starting periodic bed defense system")
        
        // Check every 10 seconds if bed needs repairs
        periodicDefenseTimer = TimeUtils.setInterval({
            if (!isPeriodicDefenseActive) return@setInterval
            
            // Don't start new defense if already defending
            if (isDefending) return@setInterval
            
            val currentBedPos = bedPosition ?: return@setInterval
            
            // Check if we have enough resources
            if (!hasEnoughResourcesForDefense()) {
                ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Low on defense resources, going to shop...")
                onNeedResources()
                return@setInterval
            }
            
            // Find positions that need defense
            findEmptyPositionsAroundBed(currentBedPos)
            
            if (blocksToPlace.isNotEmpty()) {
                ChatUtils.info("${EnumChatFormatting.GOLD}🛡 Repairing bed defense (${blocksToPlace.size} blocks needed)")
                currentBlockIndex = 0
                isDefending = true
                
                // Navigate to bed first
                val player = BedwarsAFK.mc.thePlayer
                if (player != null) {
                    val distanceToBed = Math.sqrt(
                        Math.pow(player.posX - currentBedPos.x, 2.0) +
                        Math.pow(player.posZ - currentBedPos.z, 2.0)
                    )
                    
                    // If far from bed (>10 blocks), navigate to it first
                    if (distanceToBed > 10.0) {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}Navigating to bed for repairs...")
                        LocationManager.goToBed {
                            // Now at bed, start repairs
                            startStuckDetection {
                                // If stuck, get more resources
                                onNeedResources()
                            }
                            
                            placeNextBlock {
                                // Defense complete, continue monitoring
                                stopStuckDetection()
                                isDefending = false
                            }
                        }
                    } else {
                        // Already near bed, start repairs directly
                        startStuckDetection {
                            // If stuck, get more resources
                            onNeedResources()
                        }
                        
                        placeNextBlock {
                            // Defense complete, continue monitoring
                            stopStuckDetection()
                            isDefending = false
                        }
                    }
                } else {
                    isDefending = false
                }
            }
        }, 0, 10000) // Check every 10 seconds
    }
    
    /**
     * Stop periodic defense system
     */
    fun stopPeriodicDefense() {
        periodicDefenseTimer?.cancel()
        periodicDefenseTimer = null
        isPeriodicDefenseActive = false
        stopDefense()
        ChatUtils.info("${EnumChatFormatting.YELLOW}Periodic defense system stopped")
    }
    
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
        
        // Start stuck detection - if stuck, return to generator
        startStuckDetection {
            // On stuck, call onComplete to return control
            onComplete()
        }
        
        // Find all empty positions around the bed
        findEmptyPositionsAroundBed(bedPos)
        
        if (blocksToPlace.isEmpty()) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bed already fully protected!")
            stopStuckDetection()
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
     * Calculate maximum defense radius based on available resources
     */
    private fun calculateMaxDefenseRadius(): Int {
        val player = BedwarsAFK.mc.thePlayer ?: return 2
        
        // Count available defense materials
        var totalBlocks = 0
        for (itemStack in player.inventory.mainInventory) {
            if (itemStack != null) {
                val itemName = itemStack.unlocalizedName.lowercase()
                val displayName = itemStack.displayName.lowercase()
                
                // Count wool, wood planks, and end stone
                if (itemName.contains("cloth") || itemName.contains("wool") || displayName.contains("wool") ||
                    itemName.contains("planks") || itemName.contains("wood") ||
                    itemName.contains("end_stone") || displayName.contains("end stone")) {
                    totalBlocks += itemStack.stackSize
                }
            }
        }
        
        // Expand radius based on total blocks available
        return when {
            totalBlocks >= 120 -> 5  // Layer 5: 120+ blocks (nearly full inventory)
            totalBlocks >= 90 -> 4   // Layer 4: 90+ blocks
            totalBlocks >= 60 -> 3   // Layer 3: 60+ blocks
            totalBlocks >= 40 -> 2   // Layer 2: 40+ blocks
            else -> 1                // Layer 1: <40 blocks (inner protection)
        }
    }
    
    /**
     * Find all empty air positions around the bed that can be filled
     * Expands outward as more resources are available
     */
    private fun findEmptyPositionsAroundBed(bedPos: BlockPos) {
        val world = BedwarsAFK.mc.theWorld ?: return
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        synchronized(blocksToPlace) {
            blocksToPlace.clear()
        }
        
        // Calculate maximum defense radius based on resources
        val maxRadius = calculateMaxDefenseRadius()
        ChatUtils.info("${EnumChatFormatting.AQUA}Building defense layer $maxRadius (expanding defense)")
        
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
        
        // Now find air blocks around bed, expanding based on radius
        val checkedPositions = mutableSetOf<BlockPos>()
        
        for (bedBlock in bedBlocks) {
            // Expand search area based on max radius
            val searchRadius = maxRadius + 1
            
            for (x in -searchRadius..searchRadius) {
                for (y in 0..(maxRadius + 2)) { // Height scales with radius
                    for (z in -searchRadius..searchRadius) {
                        val checkPos = bedBlock.add(x, y, z)
                        
                        // Skip if already checked or is a bed block
                        if (checkPos in checkedPositions || checkPos in bedBlocks) continue
                        checkedPositions.add(checkPos)
                        
                        // Calculate distance from bed for layering
                        val distanceFromBed = Math.sqrt(
                            Math.pow((checkPos.x - bedBlock.x).toDouble(), 2.0) +
                            Math.pow((checkPos.z - bedBlock.z).toDouble(), 2.0)
                        )
                        
                        // Only place blocks within current radius
                        if (distanceFromBed <= maxRadius + 0.5) {
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
        }
        
        // Sort by: layer, Y level, then GROUP BY AREA for contiguous placement
        synchronized(blocksToPlace) {
            blocksToPlace.sortWith(compareBy(
                { pos -> // Priority 1: Inner layers first (distance from bed)
                    val bedBlock = bedBlocks.firstOrNull() ?: bedPos
                    Math.sqrt(
                        Math.pow((pos.x - bedBlock.x).toDouble(), 2.0) +
                        Math.pow((pos.z - bedBlock.z).toDouble(), 2.0)
                    )
                },
                { it.y }, // Priority 2: Lower Y first (ground level)
                { pos -> // Priority 3: Group by quadrant (work in one area at a time)
                    val bedBlock = bedBlocks.firstOrNull() ?: bedPos
                    val relX = pos.x - bedBlock.x
                    val relZ = pos.z - bedBlock.z
                    // Return quadrant: 0=NE, 1=SE, 2=SW, 3=NW
                    when {
                        relX >= 0 && relZ >= 0 -> 0 // North-East
                        relX >= 0 && relZ < 0 -> 1  // South-East
                        relX < 0 && relZ < 0 -> 2   // South-West
                        else -> 3                   // North-West
                    }
                },
                { it.x }, // Priority 4: Sort by X within quadrant
                { it.z }  // Priority 5: Sort by Z within quadrant
            ))
        }
        
        ChatUtils.info("${EnumChatFormatting.GRAY}Defense plan: ${blocksToPlace.size} blocks (Layer $maxRadius)")
    }
    
    /**
     * Try to break a block in front of the player (only bed defense blocks)
     * Returns true if a block was broken
     */
    private fun tryBreakBlockInFront(player: EntityPlayerSP, bedPos: BlockPos): Boolean {
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        // Get player's look direction
        val lookVec = player.lookVec
        
        // Check blocks in front of player (1-2 blocks away only - more conservative)
        for (distance in 1..2) {
            val checkX = player.posX + (lookVec.xCoord * distance)
            val checkY = player.posY + player.eyeHeight
            val checkZ = player.posZ + (lookVec.zCoord * distance)
            val checkPos = BlockPos(checkX, checkY, checkZ)
            
            // Only break blocks VERY close to the bed (within 6 blocks for safety)
            val distanceToBed = Math.sqrt(
                Math.pow((checkPos.x - bedPos.x).toDouble(), 2.0) +
                Math.pow((checkPos.z - bedPos.z).toDouble(), 2.0)
            )
            
            if (distanceToBed > 6.0) continue // More conservative distance
            
            val block = world.getBlockState(checkPos).block
            val blockName = block.unlocalizedName.lowercase()
            
            // Only break soft defense blocks (wool, wood, stained glass)
            // Avoid breaking end stone and regular glass (might be part of island)
            val isBreakableDefenseBlock = blockName.contains("cloth") || // wool
                                          blockName.contains("wool") ||
                                          blockName.contains("planks") || // wood planks
                                          (blockName.contains("stained_glass") && !blockName.contains("tile.glass")) // only stained glass, not regular
            
            if (isBreakableDefenseBlock) {
                // Check if block is above ground level (likely defense, not island structure)
                val groundLevel = bedPos.y
                if (checkPos.y >= groundLevel) {
                    // Break the block
                    BedwarsAFK.mc.playerController.onPlayerDamageBlock(checkPos, net.minecraft.util.EnumFacing.UP)
                    player.swingItem()
                    ChatUtils.info("${EnumChatFormatting.GRAY}Breaking ${block.localizedName} to clear path")
                    return true
                }
            }
        }
        
        return false
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
            stopStuckDetection()
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
            stopStuckDetection()
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
            val playerPos = BlockPos(player.posX, player.posY, player.posZ)
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
        val world = BedwarsAFK.mc.theWorld ?: return
        
        // Calculate direction to standing position
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            standPos.x + 0.5,
            standPos.y.toDouble(),
            standPos.z + 0.5
        )
        
        if (rotations == null) {
            // Can't calculate rotation, just try to place anyway
            attemptBlockPlacement(targetPos, onComplete)
            return
        }
        
        // Smoothly rotate towards the position with easing
        val currentYaw = player.rotationYaw
        val currentPitch = player.rotationPitch
        val targetYaw = rotations[0]
        val targetPitch = rotations[1]
        
        ChatUtils.info("${EnumChatFormatting.GRAY}Walking to position...")
        
        // Smooth rotation over 400ms with easing for natural movement
        var rotationTimer: Timer? = null
        var rotationSteps = 0
        val maxRotationSteps = 8 // More steps = smoother, especially for large rotations
        val yawDiff = ((targetYaw - currentYaw + 180) % 360 - 180) // Normalize to [-180, 180]
        val pitchDiff = targetPitch - currentPitch
        
        // For large rotations (>90 degrees), use more steps
        val actualMaxSteps = if (Math.abs(yawDiff) > 90) 12 else maxRotationSteps
        
        rotationTimer = TimeUtils.setInterval({
            rotationSteps++
            val p = BedwarsAFK.mc.thePlayer
            if (p != null && rotationSteps <= actualMaxSteps) {
                // Ease-in-out function for smooth acceleration and deceleration
                val progress = rotationSteps.toDouble() / actualMaxSteps
                val easeProgress = if (progress < 0.5) {
                    2 * progress * progress // Ease-in
                } else {
                    1 - Math.pow(-2 * progress + 2, 2.0) / 2 // Ease-out
                }
                
                p.rotationYaw = (currentYaw + (yawDiff * easeProgress)).toFloat()
                p.rotationPitch = (currentPitch + (pitchDiff * easeProgress)).toFloat()
            }
            
            if (rotationSteps >= actualMaxSteps) {
                rotationTimer?.cancel()
                // Start moving after rotation complete
                startMovementToPosition(standPos, targetPos, onComplete)
            }
        }, 0, 50) // Rotate over 400-600ms depending on rotation size
    }
    
    /**
     * Start moving to the position with jump support
     */
    private fun startMovementToPosition(standPos: BlockPos, targetPos: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return
        
        Movement.startForward()
        
        // Check distance periodically
        var moveTimer: Timer? = null
        var checkCount = 0
        var stuckCount = 0
        var lastX = player.posX
        var lastY = player.posY
        var lastZ = player.posZ
        
        moveTimer = TimeUtils.setInterval({
            checkCount++
            
            val currentPlayer = BedwarsAFK.mc.thePlayer
            if (currentPlayer == null) {
                moveTimer?.cancel()
                Movement.stopForward()
                Movement.stopJumping()
                attemptBlockPlacement(targetPos, onComplete)
                return@setInterval
            }
            
            // Check if stuck (not moving)
            val movedDistance = Math.sqrt(
                Math.pow(currentPlayer.posX - lastX, 2.0) + 
                Math.pow(currentPlayer.posZ - lastZ, 2.0)
            )
            
            if (movedDistance < 0.1) {
                stuckCount++
                
                // If stuck, try multiple recovery strategies
                if (stuckCount >= 2 && currentPlayer.onGround) {
                    Movement.jump() // Try jumping over obstacle
                } else if (stuckCount >= 4) {
                    // Try breaking blocks in front of player (bed defense blocks only)
                    val bedPos = bedPosition
                    if (bedPos != null && tryBreakBlockInFront(currentPlayer, bedPos)) {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}Breaking block to clear path...")
                        stuckCount = 0 // Reset stuck counter after breaking
                    } else if (stuckCount >= 6) {
                        // If can't break block, try strafing
                        if (stuckCount % 2 == 0) {
                            Movement.startLeft()
                            TimeUtils.setTimeout({
                                Movement.stopLeft()
                            }, 200)
                        } else {
                            Movement.startRight()
                            TimeUtils.setTimeout({
                                Movement.stopRight()
                            }, 200)
                        }
                    }
                }
            } else {
                stuckCount = 0
            }
            
            // Check if we need to jump up to the target position
            val heightDiff = standPos.y - currentPlayer.posY
            if (heightDiff > 0.5 && currentPlayer.onGround) {
                Movement.jump()
            }
            
            lastX = currentPlayer.posX
            lastY = currentPlayer.posY
            lastZ = currentPlayer.posZ
            
            // Calculate current distance to target (horizontal only)
            val distanceToStand = Math.sqrt(
                Math.pow(currentPlayer.posX - standPos.x - 0.5, 2.0) +
                Math.pow(currentPlayer.posZ - standPos.z - 0.5, 2.0)
            )
            
            // Stop if: close enough, timeout, or stuck for too long
            if (distanceToStand <= 2.0 || checkCount >= 40 || stuckCount >= 15) {
                moveTimer?.cancel()
                Movement.stopForward()
                Movement.stopJumping()
                
                if (distanceToStand > 3.5) {
                    // Too far, skip this block
                    ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Couldn't reach position, skipping...")
                    currentBlockIndex++
                    placeNextBlock(onComplete)
                } else {
                    ChatUtils.info("${EnumChatFormatting.GRAY}Arrived (dist: ${String.format("%.1f", distanceToStand)})")
                    
                    TimeUtils.setTimeout({
                        attemptBlockPlacement(targetPos, onComplete)
                    }, 300)
                }
            }
        }, 0, 200) // Check every 200ms
    }
    
    /**
     * Check if there's a block between player and target (collision detection)
     */
    private fun isBlockingLineOfSight(player: net.minecraft.entity.player.EntityPlayer, targetPos: BlockPos, world: net.minecraft.world.World): Boolean {
        val playerEye = player.getPositionEyes(1.0f)
        val targetCenter = Vec3(targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5)
        
        // Cast ray from player's eyes to target
        val rayTrace = world.rayTraceBlocks(playerEye, targetCenter, false, true, false)
        
        if (rayTrace != null && rayTrace.blockPos != null) {
            val hitPos = rayTrace.blockPos
            // If we hit a block that isn't the target, there's a collision
            if (hitPos != targetPos) {
                val hitBlock = world.getBlockState(hitPos).block
                // Check if it's a solid block (not air, not already placed wool)
                if (hitBlock.material.isSolid && hitBlock.material != Material.air) {
                    return true // Collision detected!
                }
            }
        }
        
        return false
    }
    
    /**
     * Find a position that doesn't have collision with blocks and avoids void
     */
    private fun findNonCollidingPosition(targetPos: BlockPos, player: net.minecraft.entity.player.EntityPlayer, world: net.minecraft.world.World): BlockPos? {
        // Try positions around the target
        val testOffsets = listOf(
            // Sides (prioritize)
            BlockPos(2, 0, 0), BlockPos(-2, 0, 0),
            BlockPos(0, 0, 2), BlockPos(0, 0, -2),
            BlockPos(2, 0, 1), BlockPos(-2, 0, 1),
            BlockPos(2, 0, -1), BlockPos(-2, 0, -1),
            BlockPos(1, 0, 2), BlockPos(-1, 0, 2),
            BlockPos(1, 0, -2), BlockPos(-1, 0, -2),
            // Diagonals
            BlockPos(2, 0, 2), BlockPos(-2, 0, 2),
            BlockPos(2, 0, -2), BlockPos(-2, 0, -2),
            // Elevated positions
            BlockPos(1, 1, 1), BlockPos(-1, 1, 1),
            BlockPos(1, 1, -1), BlockPos(-1, 1, -1)
        )
        
        for (offset in testOffsets) {
            val testPos = targetPos.add(offset)
            
            // Check if this position is safe (not void, has solid ground)
            val blockBelow = world.getBlockState(testPos.down()).block
            val blockAt = world.getBlockState(testPos).block
            val blockAbove = world.getBlockState(testPos.up()).block
            
            // Must have solid ground and air to stand in
            if (blockBelow.material.isSolid && 
                blockAt.material == Material.air && 
                blockAbove.material == Material.air) {
                
                // Check if we can reach the target from here (within 4.5 blocks)
                val distance = testPos.distanceSq(targetPos)
                if (distance <= 20.25) {
                    // Check if there's NO collision from this position
                    val testPlayer = BedwarsAFK.mc.thePlayer ?: return null
                    // Temporarily move perspective for ray trace
                    val testEye = Vec3(testPos.x + 0.5, testPos.y + testPlayer.getEyeHeight().toDouble(), testPos.z + 0.5)
                    val targetCenter = Vec3(targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5)
                    val rayTrace = world.rayTraceBlocks(testEye, targetCenter, false, true, false)
                    
                    if (rayTrace == null || rayTrace.blockPos == targetPos) {
                        // No collision! This is a good position
                        return testPos
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find a safe position to stand when placing a block (not on top of the target)
     */
    private fun findSafeStandingPosition(targetPos: BlockPos, player: net.minecraft.entity.player.EntityPlayer): BlockPos? {
        val world = BedwarsAFK.mc.theWorld ?: return null
        val bedPos = bedPosition ?: return null
        
        // Get all blocks we're planning to place (excluding already placed ones)
        val plannedBlocks = synchronized(blocksToPlace) { 
            blocksToPlace.drop(currentBlockIndex).toSet() 
        }
        
        // Try positions around the target (prioritize closer positions to avoid walking off)
        val offsets = listOf(
            // 1 block away (PRIORITIZED to stay close to bed)
            BlockPos(1, 0, 0), BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1), BlockPos(0, 0, -1),
            // Diagonal (okay)
            BlockPos(1, 0, 1), BlockPos(-1, 0, 1),
            BlockPos(1, 0, -1), BlockPos(-1, 0, -1),
            // 2 blocks away (acceptable)
            BlockPos(2, 0, 0), BlockPos(-2, 0, 0),
            BlockPos(0, 0, 2), BlockPos(0, 0, -2),
            BlockPos(2, 0, 1), BlockPos(-2, 0, 1),
            BlockPos(2, 0, -1), BlockPos(-2, 0, -1),
            BlockPos(1, 0, 2), BlockPos(-1, 0, 2),
            BlockPos(1, 0, -2), BlockPos(-1, 0, -2),
            // ON TOP of already placed blocks (1 block up from placed blocks)
            BlockPos(0, 1, 0)
        )
        
        for (offset in offsets) {
            val testPos = targetPos.add(offset)
            
            // CRITICAL: Check if position is within safe bounds of the bed (within 8 blocks)
            val distanceFromBed = Math.sqrt(
                Math.pow((testPos.x - bedPos.x).toDouble(), 2.0) +
                Math.pow((testPos.z - bedPos.z).toDouble(), 2.0)
            )
            
            if (distanceFromBed > 8.0) {
                // Too far from bed, might fall off island
                continue
            }
            
            // Allow standing on future placement positions if they're already complete
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
        
        // CRITICAL: Re-check if position is STILL AIR before attempting placement
        val currentBlock = world.getBlockState(targetPos).block
        if (currentBlock.material != Material.air) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Position already filled, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // Check if player is standing in the target position (or very close to it)
        val distanceToTarget = Math.sqrt(
            Math.pow((player.posX - targetPos.x - 0.5), 2.0) +
            Math.pow((player.posZ - targetPos.z - 0.5), 2.0)
        )
        
        // Check if we're too far away (out of reach)
        if (distanceToTarget > 4.5) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Too far from target (${String.format("%.1f", distanceToTarget)} blocks), skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        if (distanceToTarget < 0.8) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Too close, moving back...")
            // Calculate direction away from target
            val awayYaw = Math.toDegrees(Math.atan2(
                player.posZ - (targetPos.z + 0.5),
                player.posX - (targetPos.x + 0.5)
            )).toFloat() - 90f
            
            player.rotationYaw = awayYaw
            
            TimeUtils.setTimeout({
                Movement.startBackward()
                TimeUtils.setTimeout({
                    Movement.stopBackward()
                    TimeUtils.setTimeout({
                        attemptBlockPlacement(targetPos, onComplete)
                    }, 300)
                }, 400)
            }, 100)
            return
        }
        
        // Check if we're too far away
        val distanceToTargetSq = player.getDistanceSq(targetPos.x.toDouble(), targetPos.y.toDouble(), targetPos.z.toDouble())
        if (distanceToTargetSq > 25.0) { // More than 5 blocks
            ChatUtils.info("${EnumChatFormatting.YELLOW}Too far away, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // CHECK FOR COLLISION: Is there a block between player and target?
        if (isBlockingLineOfSight(player, targetPos, world)) {
            ChatUtils.info("${EnumChatFormatting.RED}Block collision detected! Moving to better position...")
            // Find a non-colliding position
            val safePos = findNonCollidingPosition(targetPos, player, world)
            val playerPos = BlockPos(player.posX, player.posY, player.posZ)
            if (safePos != null && safePos != playerPos) {
                ChatUtils.info("${EnumChatFormatting.GRAY}Moving to safe position...")
                moveToPositionDirectly(safePos, targetPos, onComplete)
                return
            } else {
                // Can't find safe position, skip this block
                ChatUtils.info("${EnumChatFormatting.YELLOW}Can't find safe position, skipping...")
                currentBlockIndex++
                placeNextBlock(onComplete)
                return
            }
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
        
        // Double-check the block we're placing on is still solid
        if (world.getBlockState(placeOnBlock).block.material != Material.air && 
            !world.getBlockState(placeOnBlock).block.material.isSolid) {
            ChatUtils.info("${EnumChatFormatting.RED}Placement block invalid, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
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
        
        if (rotations == null) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Can't calculate rotation, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.GRAY}Looking at ${placeOnFace.name} face...")
        
        // Start placement timeout timer (2 seconds)
        val placementStartTime = System.currentTimeMillis()
        
        // Smoothly rotate to look at the block face
        val startYaw = player.rotationYaw
        val startPitch = player.rotationPitch
        val targetYaw = rotations[0]
        val targetPitch = rotations[1]
        
        // Normalize yaw difference to [-180, 180]
        val yawDiff = ((targetYaw - startYaw + 180) % 360 - 180)
        val pitchDiff = targetPitch - startPitch
        
        // Smooth rotation over 400ms with easing (8 steps)
        var rotationTimer: Timer? = null
        var rotationSteps = 0
        val maxRotationSteps = 8
        
        // For large rotations (>90 degrees), use more steps to prevent snapping
        val actualMaxSteps = if (Math.abs(yawDiff) > 90) 12 else maxRotationSteps
        
        rotationTimer = TimeUtils.setInterval({
            rotationSteps++
            
            // Check for timeout (2 seconds)
            if (System.currentTimeMillis() - placementStartTime > 2000) {
                rotationTimer?.cancel()
                ChatUtils.info("${EnumChatFormatting.YELLOW}⏱ Placement timeout, skipping...")
                currentBlockIndex++
                placeNextBlock(onComplete)
                return@setInterval
            }
            
            val p = BedwarsAFK.mc.thePlayer
            if (p != null && rotationSteps <= actualMaxSteps) {
                // Ease-in-out function for smooth acceleration and deceleration
                val progress = rotationSteps.toDouble() / actualMaxSteps
                val easeProgress = if (progress < 0.5) {
                    2 * progress * progress // Ease-in
                } else {
                    1 - Math.pow(-2 * progress + 2, 2.0) / 2 // Ease-out
                }
                
                p.rotationYaw = (startYaw + (yawDiff * easeProgress)).toFloat()
                p.rotationPitch = (startPitch + (pitchDiff * easeProgress)).toFloat()
            }
            
            if (rotationSteps >= actualMaxSteps) {
                rotationTimer?.cancel()
                
                // Wait a bit longer for rotation to settle, then place
                TimeUtils.setTimeout({
                    // Final timeout check
                    if (System.currentTimeMillis() - placementStartTime > 2000) {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}⏱ Placement timeout, skipping...")
                        currentBlockIndex++
                        placeNextBlock(onComplete)
                        return@setTimeout
                    }
                    performBlockPlacement(targetPos, placeOnBlock, placeOnFace, lookAtPos, world, player, onComplete)
                }, 150) // Increased from 100ms to 150ms for better reliability
            }
        }, 0, 50) // 8-12 steps * 50ms = 400-600ms total
    }
    
    /**
     * Perform the actual block placement
     */
    private fun performBlockPlacement(
        targetPos: BlockPos,
        placeOnBlock: BlockPos,
        placeOnFace: EnumFacing,
        lookAtPos: Vec3,
        world: WorldClient,
        player: EntityPlayerSP,
        onComplete: () -> Unit
    ) {
        // FINAL CHECK: Verify position is STILL air before placing
        val currentBlockState = world.getBlockState(targetPos)
        if (currentBlockState.block.material != Material.air) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Position ${targetPos.x}, ${targetPos.y}, ${targetPos.z} filled (${currentBlockState.block.localizedName}), skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // FINAL CHECK: Verify the block we're clicking on is still solid
        val placeOnBlockState = world.getBlockState(placeOnBlock)
        if (!placeOnBlockState.block.material.isSolid) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Placement block no longer solid, skipping...")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // Make sure wool is still equipped
        if (!equipWool()) {
            ChatUtils.error("Lost wool!")
            currentBlockIndex++
            placeNextBlock(onComplete)
            return
        }
        
        // CURSOR-BASED PLACEMENT: More accurate and reliable
        val heldItem = player.heldItem
        if (heldItem != null) {
            // Swing hand (visual feedback)
            player.swingItem()
            
            // Wait for rotation to fully settle before placing
            TimeUtils.setTimeout({
                // Simulate right-click (press and release)
                Mouse.rClick()
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Placed at ${targetPos.x}, ${targetPos.y}, ${targetPos.z}")
            }, 150) // Small delay for accuracy
        }
        
        // Move to next block
        currentBlockIndex++
        
        TimeUtils.setTimeout({
            placeNextBlock(onComplete)
        }, 400)
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
