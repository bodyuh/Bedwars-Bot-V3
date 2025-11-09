package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Inventory
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.init.Blocks
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Advanced pathfinding system for bridging to other islands
 */
object BridgingPathfinder {
    
    var isActive = false
    private var currentPath = mutableListOf<BlockPos>()
    private var currentWaypointIndex = 0
    private var targetBlock: BlockPos? = null
    private var startingPosition: BlockPos? = null
    private var savedGeneratorPos: BlockPos? = null
    
    // Placement spam control - increased cooldown to reduce packets
    private var lastPlacementPos: BlockPos? = null
    private var lastPlacementAtMs: Long = 0L
    
    // Preparation phase tracking
    private var preparationPhase = false
    private var preparationStartTime = 0L
    
    // Bridging configuration
    var enableDiagonalBridging = true // Allow diagonal (faster but less common)
    var enableSpeedBridging = true // Enable safe speedbridging (sneak + sprint alternate)
    var placementCooldownMs = 350 // Increased from 250ms to reduce packet spam
    
    // Diagnostic flag - printed once on first use
    private var diagnosticShown = false
    
    private fun showDiagnostic() {
        // Removed - too aggressive
    }
    
    /**
     * Find diamond generator and bridge to it
     */
    fun findAndBridgeToDiamondGenerator(onComplete: () -> Unit) {
        showDiagnostic() // Show diagnostic on first use
        
        val player = BedwarsAFK.mc.thePlayer
        val world = BedwarsAFK.mc.theWorld
        
        if (player == null || world == null) {
            ChatUtils.error("Player or world is null!")
            onComplete()
            return
        }
        
        // Check if we have wool in inventory
        val woolCount = Inventory.getItemCount("wool") + countColoredWool()
        if (woolCount == 0) {
            ChatUtils.error("No wool in inventory! Cannot bridge.")
            onComplete()
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}Searching for Diamond Generator...")
        
        // Find diamond generator by entity name tag
        val diamondGenPos = findDiamondGenerator()
        
        if (diamondGenPos == null) {
            ChatUtils.error("No Diamond Generator found!")
            onComplete()
            return
        }
        
        targetBlock = diamondGenPos
        startingPosition = BlockPos(player.posX, player.posY, player.posZ) // Save where we started
        val distance = player.getDistance(diamondGenPos.x.toDouble(), diamondGenPos.y.toDouble(), diamondGenPos.z.toDouble())
        
        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Found Diamond Generator at ${diamondGenPos.x}, ${diamondGenPos.y}, ${diamondGenPos.z}")
        ChatUtils.info("${EnumChatFormatting.GRAY}Distance: ${String.format("%.1f", distance)} blocks")
        
        // First, navigate to the bed area (outside position) before bridging
        navigateToOutsidePosition(diamondGenPos, onComplete)
    }
    
    /**
     * Navigate to outside position (near bed) before starting bridge
     */
    private fun navigateToOutsidePosition(targetPos: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        // Use LocationManager to get bed position
        val bedPos = best.bedwars.afk.bot.LocationManager.bedPos
        
        if (bedPos != null) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Navigating to outside position near bed...")
            
            // Go to bed area first
            best.bedwars.afk.bot.LocationManager.goToBed {
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Reached outside position!")
                // Wait 2 seconds to ensure we're properly positioned
                TimeUtils.setTimeout({
                    // Now start bridging from here
                    startBridgingSequence(targetPos, onComplete)
                }, 2000)
            }
        } else {
            // No bed detected, start from current position
            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Bed not detected, preparing to bridge...")
            TimeUtils.setTimeout({
                startBridgingSequence(targetPos, onComplete)
            }, 1500) // Wait 1.5s to prepare
        }
    }
    
    /**
     * Start the actual bridging sequence
     */
    private fun startBridgingSequence(targetPos: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        val playerPos = BlockPos(player.posX, player.posY, player.posZ)
        ChatUtils.info("${EnumChatFormatting.GRAY}Starting position: ${playerPos.x}, ${playerPos.y}, ${playerPos.z}")
        
        // Calculate path with bridging
        val path = calculateBridgingPath(playerPos, targetPos)
        
        if (path.isEmpty()) {
            ChatUtils.error("Could not calculate path!")
            onComplete()
            return
        }
        
        currentPath = path.toMutableList()
        currentWaypointIndex = 0
        
        // Update path renderer
        updatePathRenderer()
        
        ChatUtils.info("${EnumChatFormatting.AQUA}Path calculated: ${path.size} waypoints")
        ChatUtils.info("${EnumChatFormatting.YELLOW}Starting bridging sequence...")
        
        // Start following the path
        isActive = true
        followBridgingPath {
            // After reaching target, stand on it and save position
            standOnTargetAndSave(targetPos, onComplete)
        }
    }
    
    /**
     * Find the nearest specific block type and bridge to it (for testing/keybind use)
     */
    fun findAndBridgeToBlock(blockType: Block, onComplete: () -> Unit) {
        showDiagnostic() // Show diagnostic on first use
        
        val player = BedwarsAFK.mc.thePlayer
        val world = BedwarsAFK.mc.theWorld
        
        if (player == null || world == null) {
            ChatUtils.error("Player or world is null!")
            onComplete()
            return
        }
        
        // Check if we have wool in inventory
        val woolCount = Inventory.getItemCount("wool") + countColoredWool()
        if (woolCount == 0) {
            ChatUtils.error("No wool in inventory! Cannot bridge.")
            onComplete()
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}Searching for ${blockType.localizedName}...")
        
        // Find nearest block of the specified type
        val playerPos = BlockPos(player.posX, player.posY, player.posZ)
        val nearestBlock = findNearestBlock(blockType, playerPos, 200)
        
        if (nearestBlock == null) {
            ChatUtils.error("No ${blockType.localizedName} found within 200 blocks!")
            onComplete()
            return
        }
        
        targetBlock = nearestBlock
        startingPosition = playerPos // Save where we started
        val distance = player.getDistance(nearestBlock.x.toDouble(), nearestBlock.y.toDouble(), nearestBlock.z.toDouble())
        
        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Found ${blockType.localizedName} at ${nearestBlock.x}, ${nearestBlock.y}, ${nearestBlock.z}")
        ChatUtils.info("${EnumChatFormatting.GRAY}Distance: ${String.format("%.1f", distance)} blocks")
        
        // For manual keybind testing, skip bed navigation
        startBridgingSequence(nearestBlock, onComplete)
    }
    
    /**
     * Find diamond generator by entity name tag
     */
    private fun findDiamondGenerator(): BlockPos? {
        val world = BedwarsAFK.mc.theWorld ?: return null
        val player = BedwarsAFK.mc.thePlayer ?: return null
        
        var diamondGenPos: BlockPos? = null
        var closestDistance = Double.MAX_VALUE
        
        // Look for entities with "Diamond" in their name (armor stands, holograms)
        for (entity in world.loadedEntityList) {
            val entityName = entity.name
            
            // Check if entity name contains "Diamond" (case insensitive)
            if (entityName.contains("Diamond", ignoreCase = true)) {
                val distance = player.getDistanceToEntity(entity).toDouble()
                
                // Ignore entities too close (likely decorative)
                if (distance > 20.0 && distance < closestDistance) {
                    closestDistance = distance
                    // Use the block position below the entity (where the generator actually is)
                    diamondGenPos = BlockPos(entity.posX, entity.posY - 1, entity.posZ)
                    
                    ChatUtils.info("${EnumChatFormatting.GRAY}Found entity: '$entityName' at ${String.format("%.1f", distance)} blocks")
                }
            }
        }
        
        return diamondGenPos
    }
    
    /**
     * Equip wool from inventory
     */
    private fun equipWool(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
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
     * Count colored wool in inventory
     */
    private fun countColoredWool(): Int {
        val player = BedwarsAFK.mc.thePlayer ?: return 0
        var count = 0
        
        for (itemStack in player.inventory.mainInventory) {
            if (itemStack != null) {
                val itemName = itemStack.unlocalizedName.lowercase()
                val displayName = itemStack.displayName.lowercase()
                
                if (itemName.contains("cloth") || itemName.contains("wool") || displayName.contains("wool")) {
                    count += itemStack.stackSize
                }
            }
        }
        
        return count
    }
    
    /**
     * Find nearest block of specific type
     */
    private fun findNearestBlock(blockType: Block, startPos: BlockPos, radius: Int): BlockPos? {
        val world = BedwarsAFK.mc.theWorld ?: return null
        
        var nearestBlock: BlockPos? = null
        var nearestDistance = Double.MAX_VALUE
        
        // Search in expanding radius
        for (y in (startPos.y - radius)..(startPos.y + radius)) {
            for (x in (startPos.x - radius)..(startPos.x + radius)) {
                for (z in (startPos.z - radius)..(startPos.z + radius)) {
                    val pos = BlockPos(x, y, z)
                    val block = world.getBlockState(pos).block
                    
                    if (block == blockType) {
                        val distance = startPos.distanceSq(pos)
                        if (distance < nearestDistance) {
                            nearestDistance = distance.toDouble()
                            nearestBlock = pos
                        }
                    }
                }
            }
        }
        
        return nearestBlock
    }
    
    /**
     * Calculate path that may require bridging
     * Supports both straight and diagonal bridging
     */
    private fun calculateBridgingPath(start: BlockPos, end: BlockPos): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        
        // Calculate horizontal direction
        val dx = end.x - start.x
        val dz = end.z - start.z
        val horizontalDist = sqrt((dx * dx + dz * dz).toDouble())
        
        // Determine if this is a diagonal bridge
        val isDiagonal = abs(dx) > 2 && abs(dz) > 2
        
        if (!enableDiagonalBridging && isDiagonal) {
            // If diagonal disabled, use L-shaped path (cardinal directions only)
            ChatUtils.info("§6[Bridge] §7Using L-shaped path (diagonal disabled)")
            
            // First leg: move in X direction
            val midX = end.x
            val midY = start.y
            val midZ = start.z
            path.add(BlockPos(midX, midY, midZ))
            
            // Second leg: move in Z direction to target
            path.add(end)
        } else {
            // Use direct path (diagonal if needed)
            if (isDiagonal) {
                ChatUtils.info("§6[Bridge] §7Using diagonal bridging path")
            }
            
            // Create waypoints every ~3 blocks for smooth bridging
            val steps = (horizontalDist / 3.0).toInt().coerceAtLeast(10)
            
            for (i in 0..steps) {
                val progress = i.toDouble() / steps
                val x = (start.x + dx * progress).toInt()
                val z = (start.z + dz * progress).toInt()
                
                // Gradually adjust Y
                val y = (start.y + (end.y - start.y) * progress).toInt()
                
                path.add(BlockPos(x, y, z))
            }
            
            // Always end at the target
            path.add(end)
        }
        
        return path
    }
    
    private fun followBridgingPath(onComplete: () -> Unit) {
        if (!isActive || currentWaypointIndex >= currentPath.size) {
            // Finished!
            isActive = false
            Movement.clearAll()
            Mouse.stopTracking()
            PathRenderer.clearPath()
            
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bridging complete!")
            onComplete.invoke()
            return
        }
        
        val player = BedwarsAFK.mc.thePlayer
        val world = BedwarsAFK.mc.theWorld
        
        if (player == null || world == null) {
            stop()
            onComplete()
            return
        }
        
        val waypoint = currentPath[currentWaypointIndex]
        val playerBlock = BlockPos(player.posX, player.posY, player.posZ)
        val distance = player.getDistance(waypoint.x + 0.5, waypoint.y.toDouble(), waypoint.z + 0.5)
        
        // Only update progress when we're actually on solid ground
        val isOnSolidGround = world.getBlockState(playerBlock.down()).block.material.isSolid
        if (isOnSolidGround) {
            ChatUtils.info("${EnumChatFormatting.GRAY}[${currentWaypointIndex + 1}/${currentPath.size}] Distance: ${String.format("%.1f", distance)}")
            
            // Only check for next action when we're stable
            val (needsBridging, prepPos) = checkNeedsBridging(playerBlock, waypoint, world)
            
            if (needsBridging) {
                ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Gap detected! Moving to preparation position...")
                
                if (prepPos != null) {
                    // First move to preparation position
                    AdvancedPathfinder.goTo(prepPos.x, prepPos.y, prepPos.z) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Reached preparation position!")
                        TimeUtils.setTimeout({
                            bridgeToWaypoint(waypoint) {
                                // After bridging, move to next waypoint
                                currentWaypointIndex++
                                followBridgingPath(onComplete)
                            }
                        }, 500) // Short pause at prep position
                    }
                } else {
                    ChatUtils.error("No valid preparation position found!")
                    currentWaypointIndex++
                    followBridgingPath(onComplete)
                }
            } else {
                // Normal pathfinding
                AdvancedPathfinder.goTo(waypoint.x, waypoint.y, waypoint.z) {
                    currentWaypointIndex++
                    followBridgingPath(onComplete)
                }
            }
        }
    }
    
    /**
     * Check if we need to bridge between two positions, returns the last solid block position
     */
    private fun checkNeedsBridging(from: BlockPos, to: BlockPos, world: net.minecraft.world.World): Pair<Boolean, BlockPos?> {
        // First check if we're currently on solid ground
        val blockUnderStart = world.getBlockState(from.down()).block
        if (!blockUnderStart.material.isSolid) {
            return Pair(false, null) // Can't start bridging if we're not on solid ground
        }

        // Check if there's a gap (no solid blocks) between positions
        val dx = to.x - from.x
        val dz = to.z - from.z
        val steps = sqrt((dx * dx + dz * dz).toDouble()).toInt()
        
        if (steps == 0) return Pair(false, null)
        
        var lastSolidPos: BlockPos = from
        var gapFound = false
        
        // Check path for gaps
        for (i in 0..steps) {
            val progress = i.toDouble() / steps
            val checkX = (from.x + dx * progress).toInt()
            val checkZ = (from.z + dz * progress).toInt()
            val checkY = from.y
            
            val checkPos = BlockPos(checkX, checkY - 1, checkZ)
            val blockBelow = world.getBlockState(checkPos).block
            
            if (blockBelow.material.isSolid) {
                lastSolidPos = BlockPos(checkX, checkY, checkZ)
            } else {
                gapFound = true
                break // Found the gap, lastSolidPos is our preparation position
            }
        }
        
        return Pair(gapFound, if (gapFound) lastSolidPos else null)
    }
    
    /**
     * Bridge to a waypoint (human-like backward bridging)
     */
    private fun bridgeToWaypoint(target: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer
        val world = BedwarsAFK.mc.theWorld
        
        if (player == null || world == null) {
            onComplete()
            return
        }
        
        // Verify we're actually on a solid block before starting to bridge
        val playerBlock = BlockPos(player.posX, player.posY, player.posZ)
        if (!world.getBlockState(playerBlock.down()).block.material.isSolid) {
            ChatUtils.error("Not on solid ground! Cannot start bridging.")
            onComplete()
            return
        }
        
        // Calculate direction to target
        val dx = target.x + 0.5 - player.posX
        val dz = target.z + 0.5 - player.posZ
        val targetYaw = (atan2(dz, dx) * 180.0 / Math.PI - 90.0).toFloat()
        
        // Determine if this is a diagonal bridge
        val absDx = abs(dx)
        val absDz = abs(dz)
        val isDiagonal = absDx > 1.5 && absDz > 1.5 // Both X and Z movement significant
        
        // Calculate initial position for bridging preparation
        val bridgingYaw = if (isDiagonal) {
            when {
                dx > 0 && dz > 0 -> -45f   // Moving SE, face NW (opposite)
                dx > 0 && dz < 0 -> 45f    // Moving NE, face SW (opposite)
                dx < 0 && dz > 0 -> -135f  // Moving SW, face NE (opposite)
                else -> 135f               // Moving NW, face SE (opposite)
            }
        } else {
            targetYaw + 180f // Face opposite direction for straight bridging
        }

        // Set initial rotation for preparation
        player.rotationYaw = bridgingYaw
        player.rotationPitch = if (isDiagonal) 77f else 83f

        ChatUtils.info("§6[Bridge] §7${if (isDiagonal) "Diagonal" else "Straight"} bridge at yaw=${bridgingYaw.toInt()}°, pitch=${player.rotationPitch}°")
        
        // Equip wool
        if (!equipWool()) {
            ChatUtils.error("No wool available!")
            onComplete()
            return
        }

        // Start preparation phase
        ChatUtils.info("${EnumChatFormatting.AQUA}Starting bridge preparation...")
        
        // Calculate the block behind player based on yaw
        val blockBehindPlayer = when {
            bridgingYaw < -135 || bridgingYaw > 135 -> BlockPos(player.posX, player.posY, player.posZ + 1) // Facing north
            bridgingYaw < 135 -> BlockPos(player.posX + 1, player.posY, player.posZ) // Facing west
            bridgingYaw < 45 -> BlockPos(player.posX, player.posY, player.posZ - 1) // Facing south
            bridgingYaw < -45 -> BlockPos(player.posX - 1, player.posY, player.posZ) // Facing east
            else -> BlockPos(player.posX, player.posY, player.posZ + 1) // Facing north (wrap)
        }
        
        // Check if we have safe ground to start from
        val isSafeToStart = world.getBlockState(blockBehindPlayer.down()).block.material.isSolid

        // Start sneaking for preparation
        Movement.startSneaking()
        
        if (!isSafeToStart) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Moving to safer starting position...")
            Movement.startForward()
            TimeUtils.setTimeout({
                Movement.stopForward()
                prepareBridging(player, target, isDiagonal, onComplete)
            }, 250)
        } else {
            prepareBridging(player, target, isDiagonal, onComplete)
        }
    }


    
    /**
     * Place a block behind the player for bridging
     * Returns true if placement was successful or block already exists
     */
    private fun placeBlockBehind(player: net.minecraft.client.entity.EntityPlayerSP, world: net.minecraft.client.multiplayer.WorldClient): Boolean {
        // Calculate position directly under player (for sneaking backwards)
        // When walking backwards while looking down, we want to place right below us
        val blockX = player.posX.toInt()
        val blockZ = player.posZ.toInt()
        val fracX = player.posX - blockX
        val fracZ = player.posZ - blockZ
        
        // More lenient edge detection - allow placement earlier to ensure safety
        // We're sneaking so we can't fall, and placing early is better than late
        val yaw = (player.rotationYaw % 360 + 360) % 360 // Normalize to 0-360
        val isNearEdge = when {
            yaw < -135 || yaw <= 45 -> fracZ < 0.5  // Facing north (-Z)
            yaw < -45-> fracX < 0.5              // Facing east (+X)
            yaw < 45 -> fracZ > 0.5              // Facing south (+Z)
            yaw < -45 -> fracX > 0.5              // Facing west (-X)
            else -> fracZ < 0.5                     // Facing north (wrap)
        }
        
        if (!isNearEdge && player.onGround) {
            // Only enforce edge check when on ground (allow emergency placements in air)
            return false
        }
        
        val placePos = BlockPos(player.posX, player.posY - 1.0, player.posZ)
        
        // Check if position already has a block
        val blockAtPos = world.getBlockState(placePos).block
        if (blockAtPos.material != Material.air) {
            // Block exists, but only return success if it's actually solid
            return blockAtPos.material.isSolid
        }

        // De-duplication: avoid spamming the same air position within cooldown window
        val now = System.currentTimeMillis()
        if (lastPlacementPos != null && lastPlacementPos == placePos && (now - lastPlacementAtMs) < placementCooldownMs) {
            return false
        }
        
        // Equip wool first
        if (!equipWool()) return false
        
        val heldItem = player.heldItem
        if (heldItem == null) return false
        
        // Try to find a solid block adjacent to place on
        val placementInfo = findPlacementSurface(placePos, world)
        if (placementInfo == null) {
            // Nothing to place against; skip this tick
            return false
        }
        
        val (placeOnPos, face) = placementInfo
        
        // Calculate hit vector (center of the face we're placing against)
        // In Minecraft 1.8.9, EnumFacing doesn't have directionVec, so we calculate it manually
        val faceX = when (face) {
            EnumFacing.EAST -> 1.0
            EnumFacing.WEST -> -1.0
            else -> 0.0
        }
        val faceY = when (face) {
            EnumFacing.UP -> 1.0
            EnumFacing.DOWN -> -1.0
            else -> 0.0
        }
        val faceZ = when (face) {
            EnumFacing.SOUTH -> 1.0
            EnumFacing.NORTH -> -1.0
            else -> 0.0
        }
        
        val hitVec = Vec3(
            placeOnPos.x + 0.5 + faceX * 0.5,
            placeOnPos.y + 0.5 + faceY * 0.5,
            placeOnPos.z + 0.5 + faceZ * 0.5
        )
        
        // Rotate player to look at the placement surface for better accuracy
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            hitVec.xCoord,
            hitVec.yCoord,
            hitVec.zCoord
        )
        
        if (rotations != null) {
            player.rotationYaw = rotations[0]
            player.rotationPitch = rotations[1]
        }
        
        // Use the proper block placement API for Minecraft 1.8.9
        val result = BedwarsAFK.mc.playerController.onPlayerRightClick(
            player,
            world,
            heldItem,
            placeOnPos,
            face,
            hitVec
        )
        
        if (result) {
            // Success! Block placement was sent to server
            player.swingItem()
            lastPlacementPos = placePos
            lastPlacementAtMs = now
            return true
        }
        
        return false
    }
    
    /**
     * Find a surface to place a block on
     */
    private fun findPlacementSurface(target: BlockPos, world: net.minecraft.world.World): Pair<BlockPos, EnumFacing>? {
        val faces = listOf(EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.UP)
        
        for (face in faces) {
            val checkPos = target.offset(face.opposite)
            val block = world.getBlockState(checkPos).block
            
            if (block.material.isSolid) {
                return Pair(checkPos, face)
            }
        }
        
        return null
    }
    
    /**
     * Get face vector for placement
     */
    private fun getFaceVector(face: EnumFacing): Vec3 {
        return when (face) {
            EnumFacing.DOWN -> Vec3(0.5, 0.0, 0.5)
            EnumFacing.UP -> Vec3(0.5, 1.0, 0.5)
            EnumFacing.NORTH -> Vec3(0.5, 0.5, 0.0)
            EnumFacing.SOUTH -> Vec3(0.5, 0.5, 1.0)
            EnumFacing.WEST -> Vec3(0.0, 0.5, 0.5)
            EnumFacing.EAST -> Vec3(1.0, 0.5, 0.5)
        }
    }
    
    /**
     * Prepare bridging by looking down and waiting
     */
    private fun prepareBridging(player: net.minecraft.client.entity.EntityPlayerSP, target: BlockPos, isDiagonal: Boolean, onComplete: () -> Unit) {
        // Initialize preparation phase
        preparationPhase = true
        preparationStartTime = System.currentTimeMillis()

        // Set initial player orientation
        // Look down a bit more for safety
        player.rotationPitch = if (isDiagonal) 77f else 83f 
        
        ChatUtils.info("${EnumChatFormatting.AQUA}Preparing to bridge...")
        Movement.startSneaking() // Ensure sneaking for preparation

        // Start bridging after preparation phase
        TimeUtils.setTimeout({
            preparationPhase = false 
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bridge preparation complete")
            startBridging(player, target, isDiagonal, onComplete)
        }, 2500) // 1.5s preparation time
    }

    /**
     * Start the actual bridging sequence
     */
    private fun startBridging(player: net.minecraft.client.entity.EntityPlayerSP, target: BlockPos, isDiagonal: Boolean, onComplete: () -> Unit) {
        ChatUtils.info("${EnumChatFormatting.AQUA}Starting bridging sequence...")
        Movement.startBackward()

        var checkCount = 0
        var lastPlacementTime = System.currentTimeMillis()
        var consecutiveFailures = 0
        val maxChecks = 150 // 7.5 seconds max

        // Safe speedbridge variables (only for straight bridges)
        var speedbridgeCycle = 0 // Counter for sneak/sprint alternation
        var lastSpeedbridgeToggle = System.currentTimeMillis()
        val useSpeedbridge = !isDiagonal // Only speedbridge on straight bridges
        
        TimeUtils.setInterval({
            checkCount++
            
            val p = BedwarsAFK.mc.thePlayer
            val w = BedwarsAFK.mc.theWorld
            
            if (p == null || w == null) {
                this.cancel()
                Movement.stopBackward()
                Movement.stopSneaking()
                onComplete()
                return@setInterval
            }

            // Check if we're still on solid ground (safety check)
            val playerBlock = BlockPos(p.posX, p.posY, p.posZ)
            val blockBelow = w.getBlockState(playerBlock.down()).block
            val blockAtFeet = w.getBlockState(playerBlock).block
            
            // We need to be actually falling (not just standing at edge) to trigger placement
            if (blockBelow.material == Material.air && blockAtFeet.material == Material.air && !p.onGround) {
                // CRITICAL: We're in the air! Force sneak immediately and place block
                if (enableSpeedBridging && useSpeedbridge && !p.isSneaking) {
                    Movement.startSneaking()
                    speedbridgeCycle = 0 // Reset cycle
                }
                
                // We're in the air! Try to place block immediately
                val placed = placeBlockBehind(player, w)
                if (!placed) {
                    consecutiveFailures++
                    
                    if (consecutiveFailures > 30) {
                        ChatUtils.error("Can't place blocks after 40 attempts! Stopping to prevent void fall")
                        this.cancel()
                        Movement.stopBackward()
                        Movement.stopSprinting()
                        // Keep sneaking for safety!
                        TimeUtils.setTimeout({
                            Movement.stopSneaking()
                            onComplete()
                        }, 1000)
                        return@setInterval
                    }
                } else {
                    consecutiveFailures = 0
                }
            } else {
                consecutiveFailures = 0
            }
            
            // Try to place block with configurable cooldown
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPlacementTime >= placementCooldownMs) {
                val placed = placeBlockBehind(p, w)
                if (placed) {
                    lastPlacementTime = currentTime
                }
            }
            
            // Check if we reached the target
            val dist = player.getDistance(target.x + 0.5, target.y.toDouble(), target.z + 0.5)
            val isOnSolidGround = w.getBlockState(playerBlock.down()).block.material.isSolid
            
            if ((dist < 0.75 && isOnSolidGround) || checkCount >= maxChecks) {
                this.cancel()
                
                // CRITICAL: Stop ALL movement BEFORE unshifting!
                Movement.stopBackward()
                Movement.stopSprinting()
                
                // Wait for movement to stop, THEN unshift
                TimeUtils.setTimeout({
                    Movement.stopSneaking()
                    Mouse.stopTracking()
                    
                    if (dist < 1.0) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bridge complete!")
                    } else {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Bridge timeout")
                    }
                    
                    // Final nudge to center if needed
                    if (dist < 1.0) {
                        AdvancedPathfinder.goTo(target.x, target.y, target.z) {
                            onComplete()
                        }
                    } else {
                        TimeUtils.setTimeout({ onComplete() }, 200)
                    }
                }, 300)
            }
        }, 0, 50)
    }
    
    /**
     * Stand on top of the target block and save it as a generator
     */
    /**
     * Stand on top of the target block and save it as a generator
     */
    private fun standOnTargetAndSave(targetBlock: BlockPos, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            onComplete()
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}→ Moving on top of diamond block...")
        
        // Position on top of the block
        val targetOnTop = targetBlock.up()
        
        AdvancedPathfinder.goTo(targetOnTop.x, targetOnTop.y, targetOnTop.z) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Standing on diamond block!")
            
            // Save this position as the diamond generator
            savedGeneratorPos = BlockPos(player.posX, player.posY, player.posZ)
            ChatUtils.info("${EnumChatFormatting.GOLD}⭐ Diamond generator saved at ${savedGeneratorPos?.x}, ${savedGeneratorPos?.y}, ${savedGeneratorPos?.z}")
            
            // Wait a moment, then return home
            TimeUtils.setTimeout({
                returnToStart(onComplete)
            }, 1000)
        }
    }

    /**
     * Return to the starting position
     */
    private fun returnToStart(onComplete: () -> Unit) {
        val startPos = startingPosition
        if (startPos == null) {
            ChatUtils.error("Starting position not saved!")
            isActive = false
            onComplete()
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}← Returning to starting position...")
        ChatUtils.info("${EnumChatFormatting.GRAY}Target: ${startPos.x}, ${startPos.y}, ${startPos.z}")
        
        AdvancedPathfinder.goTo(startPos.x, startPos.y, startPos.z) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Returned to starting position!")
            ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
            ChatUtils.info("${EnumChatFormatting.GOLD}✓ BRIDGING TEST COMPLETE!")
            ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
            if (savedGeneratorPos != null) {
                ChatUtils.info("${EnumChatFormatting.YELLOW}Diamond Generator: ${savedGeneratorPos?.x}, ${savedGeneratorPos?.y}, ${savedGeneratorPos?.z}")
            }
            
            isActive = false
            onComplete()
        }
    }

    /**
     * Get saved generator position
     */
    fun getSavedGeneratorPos(): BlockPos? = savedGeneratorPos

    /**
     * Stop bridging pathfinder
     */
    @JvmStatic
    fun stop() {
        isActive = false
        currentPath.clear()
        currentWaypointIndex = 0
        targetBlock = null
        startingPosition = null
        Movement.clearAll()
        Mouse.stopTracking()
        updatePathRenderer() // Will clear path since isActive is false
        ChatUtils.info("${EnumChatFormatting.YELLOW}Bridging stopped")
    }
    
    init {
        // Update path renderer when waypoint changes
        PathRenderer.setPath(currentPath)
    }
    
    /**
     * Update path renderer when path changes
     */
    private fun updatePathRenderer() {
        if (isActive && currentPath.isNotEmpty()) {
            PathRenderer.setPath(currentPath)
            PathRenderer.setWaypointIndex(currentWaypointIndex)
        } else {
            PathRenderer.clearPath()
        }
    }
}
