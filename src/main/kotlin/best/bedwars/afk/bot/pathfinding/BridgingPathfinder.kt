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
    
    private var isActive = false
    private var currentPath = mutableListOf<BlockPos>()
    private var currentWaypointIndex = 0
    private var targetBlock: BlockPos? = null
    private var startingPosition: BlockPos? = null
    private var savedGeneratorPos: BlockPos? = null
    
    // Placement spam control - increased cooldown to reduce packets
    private var lastPlacementPos: BlockPos? = null
    private var lastPlacementAtMs: Long = 0L
    
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
                TimeUtils.setTimeout({
                    // Now start bridging from here
                    startBridgingSequence(targetPos, onComplete)
                }, 1000)
            }
        } else {
            // No bed detected, start from current position
            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Bed not detected, bridging from current position...")
            startBridgingSequence(targetPos, onComplete)
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
    
    /**
     * Follow the bridging path
     */
    private fun followBridgingPath(onComplete: () -> Unit) {
        if (!isActive || currentWaypointIndex >= currentPath.size) {
            // Finished!
            isActive = false
            Movement.clearAll()
            Mouse.stopTracking()
            PathRenderer.clearPath()
            
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bridging complete!")
            onComplete()
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
        val distance = player.getDistance(waypoint.x + 0.5, waypoint.y.toDouble(), waypoint.z + 0.5)
        
        ChatUtils.info("${EnumChatFormatting.GRAY}[${currentWaypointIndex + 1}/${currentPath.size}] Distance: ${String.format("%.1f", distance)}")
        
        // Check if we need to bridge
        val playerPos = BlockPos(player.posX, player.posY, player.posZ)
        val needsBridge = needsBridging(playerPos, waypoint, world)
        
        if (needsBridge) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Gap detected! Bridging...")
            bridgeToWaypoint(waypoint) {
                // After bridging, move to next waypoint
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
    
    /**
     * Check if we need to bridge between two positions
     */
    private fun needsBridging(from: BlockPos, to: BlockPos, world: net.minecraft.world.World): Boolean {
        // Check if there's a gap (no solid blocks) between positions
        val dx = to.x - from.x
        val dz = to.z - from.z
        val steps = sqrt((dx * dx + dz * dz).toDouble()).toInt()
        
        if (steps == 0) return false
        
        // Check path for gaps
        for (i in 1..steps) {
            val progress = i.toDouble() / steps
            val checkX = (from.x + dx * progress).toInt()
            val checkZ = (from.z + dz * progress).toInt()
            val checkY = from.y
            
            val checkPos = BlockPos(checkX, checkY - 1, checkZ)
            val blockBelow = world.getBlockState(checkPos).block
            
            // If there's air below, we need to bridge
            if (blockBelow.material == Material.air) {
                return true
            }
        }
        
        return false
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
        
        // Calculate direction to target
        val dx = target.x + 0.5 - player.posX
        val dz = target.z + 0.5 - player.posZ
        val targetYaw = (atan2(dz, dx) * 180.0 / Math.PI - 90.0).toFloat()
        
        // Determine if this is a diagonal bridge
        val absDx = abs(dx)
        val absDz = abs(dz)
        val isDiagonal = absDx > 1.5 && absDz > 1.5 // Both X and Z movement significant
        
        // Set yaw and pitch based on bridge type
        if (isDiagonal) {
            // DIAGONAL BRIDGE: Use fixed angles based on direction
            // Since we move BACKWARDS (S key), we face OPPOSITE of movement direction
            val diagonalYaw = when {
                dx > 0 && dz > 0 -> -45f   // Moving SE, face NW (opposite)
                dx > 0 && dz < 0 -> 45f  // Moving NE, face SW (opposite)
                dx < 0 && dz > 0 -> -135f    // Moving SW, face NE (opposite)
                else -> 135f               // Moving NW, face SE (opposite)
            }
            player.rotationYaw = diagonalYaw
            player.rotationPitch = 77f
        } else {
            // STRAIGHT BRIDGE: Face opposite direction
            val bridgingYaw = targetYaw + 180f
            player.rotationYaw = bridgingYaw
            player.rotationPitch = 83f // Steeper for straight bridges
            ChatUtils.info("§6[Bridge] §7Straight bridge at yaw=${bridgingYaw.toInt()}°, pitch=83°")
        }
        
        // Equip wool
        if (!equipWool()) {
            ChatUtils.error("No wool available!")
            onComplete()
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.AQUA}Bridging with wool...")
        
        // Start bridging sequence - WALK BACKWARDS (hold S)
        Movement.startBackward() // This is the key for human-like bridging!
        Movement.startSneaking() // Sneak for safety initially
        
        var bridgeTimer: Timer? = null
        var checkCount = 0
        var lastPlacementTime = System.currentTimeMillis()
        var consecutiveFailures = 0
        val maxChecks = 150 // 7.5 seconds max
        
        // Safe speedbridge variables (only for straight bridges)
        var speedbridgeCycle = 0 // Counter for sneak/sprint alternation
        var lastSpeedbridgeToggle = System.currentTimeMillis()
        val useSpeedbridge = !isDiagonal // Only speedbridge on straight bridges
        
        bridgeTimer = TimeUtils.setInterval({
            checkCount++
            
            val p = BedwarsAFK.mc.thePlayer
            val w = BedwarsAFK.mc.theWorld
            if (p == null || w == null) {
                bridgeTimer?.cancel()
                Movement.stopBackward()
                Movement.stopSneaking()
                onComplete()
                return@setInterval
            }
            
            // SAFE SPEEDBRIDGE MODE: Alternate between sneak and unsneak
            // This is much safer than full speedbridging but faster than pure eagle
            // ONLY on straight bridges (diagonal uses pure sneak for safety)
            if (enableSpeedBridging && useSpeedbridge && p.onGround) {
                val timeSinceToggle = System.currentTimeMillis() - lastSpeedbridgeToggle
                
                // Cycle: Sneak for 400ms, unsneak for 150ms, repeat
                // This keeps the player mostly safe while gaining some speed
                when {
                    speedbridgeCycle == 0 && timeSinceToggle > 400 -> {
                        // Switch to unsneak phase (faster movement)
                        Movement.stopSneaking()
                        speedbridgeCycle = 1
                        lastSpeedbridgeToggle = System.currentTimeMillis()
                        ChatUtils.info("§6[Speedbridge] §eUnsneak (${timeSinceToggle}ms sneak)")
                    }
                    speedbridgeCycle == 1 && timeSinceToggle > 150 -> {
                        // Back to sneak phase (safe)
                        Movement.startSneaking()
                        speedbridgeCycle = 0
                        lastSpeedbridgeToggle = System.currentTimeMillis()
                        ChatUtils.info("§6[Speedbridge] §7Sneak (${timeSinceToggle}ms unsneak)")
                    }
                    speedbridgeCycle == 0 -> {
                        // Maintain sneak phase
                        Movement.startSneaking()
                    }
                    speedbridgeCycle == 1 -> {
                        // Maintain unsneak phase
                        Movement.stopSneaking()
                    }
                }
            } else if (!enableSpeedBridging || !useSpeedbridge) {
                // Always sneak if speedbridge is disabled
                Movement.startSneaking()
            }
            
            // Check if we're still on solid ground (safety check)
            val blockBelow = w.getBlockState(BlockPos(p.posX, p.posY - 1, p.posZ)).block
            if (blockBelow.material == Material.air) {
                // CRITICAL: We're in the air! Force sneak immediately and place block
                if (enableSpeedBridging && useSpeedbridge && !p.isSneaking) {
                    Movement.startSneaking()
                    speedbridgeCycle = 0 // Reset cycle
                }
                
                // We're in the air! Try to place block immediately
                val placed = placeBlockBehind(p, w)
                if (!placed) {
                    consecutiveFailures++
                    
                    // If we can't place blocks for 2 seconds (40 checks), we're stuck
                    // Increased threshold since we have better fallback logic now
                    if (consecutiveFailures > 40) {
                        ChatUtils.error("Can't place blocks after 40 attempts! Stopping to prevent void fall")
                        bridgeTimer?.cancel()
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
            
            // Try to place block with configurable cooldown (reduces packet spam)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPlacementTime >= placementCooldownMs) {
                val placed = placeBlockBehind(p, w)
                if (placed) {
                    lastPlacementTime = currentTime
                }
            }
            
            // Check if we reached the waypoint
            val dist = p.getDistance(target.x + 0.5, target.y.toDouble(), target.z + 0.5)
            
            // Tighter completion threshold to avoid stopping shy of the target
            if (dist < 1.0 || checkCount >= maxChecks) {
                bridgeTimer?.cancel()
                
                // CRITICAL: Stop ALL movement BEFORE unshifting to prevent void fall!
                Movement.stopBackward()
                Movement.stopSprinting() // Stop speedbridge sprint if active
                
                // Wait for movement to stop, THEN unshift
                TimeUtils.setTimeout({
                    Movement.stopSneaking()
                    Mouse.stopTracking()
                    
                    if (dist < 1.0) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bridge complete!")
                    } else {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Bridge timeout")
                    }
                    
                    // Final nudge to stand on the destination block center if close
                    if (dist < 1.0) {
                        AdvancedPathfinder.goTo(target.x, target.y, target.z) {
                            onComplete()
                        }
                    } else {
                        TimeUtils.setTimeout({ onComplete() }, 200)
                    }
                }, 300) // Wait 300ms for movement to fully stop
            }
        }, 0, 50)
    }
    
    /**
     * Place a block behind the player for bridging
     * Returns true if placement was successful or block already exists
     */
    private fun placeBlockBehind(player: net.minecraft.client.entity.EntityPlayerSP, world: net.minecraft.client.multiplayer.WorldClient): Boolean {
        // Calculate position directly under player (for sneaking backwards)
        // When walking backwards while looking down, we want to place right below us
        val placePos = BlockPos(player.posX, player.posY - 1.0, player.posZ)
        
        // Check if position already has a block
        if (world.getBlockState(placePos).block.material != Material.air) {
            return true // Block already exists, success
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
        
        // HUMAN-LIKE PLACEMENT: Just simulate a right-click!
        // The server will handle block placement based on:
        // - Where the player is looking
        // - What block they're targeting
        // - If they're in range
        
        // This is exactly how a real player places blocks - they just press right-click
        // The Minecraft client/server handles the rest
        
        // Swing hand first (visual feedback)
        player.swingItem()
        
        // Simulate right-click (50ms press)
        Mouse.rClick()
        lastPlacementPos = placePos
        lastPlacementAtMs = now
        
        // Small delay to let the server process
        // We return true immediately to keep bridging smooth
        // The server will place the block if valid
        return true
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
     * Get the saved diamond generator position
     */
    fun getSavedGeneratorPos(): BlockPos? = savedGeneratorPos
    
    /**
     * Stop bridging pathfinder
     */
    fun stop() {
        isActive = false
        currentPath.clear()
        currentWaypointIndex = 0
        targetBlock = null
        startingPosition = null
        Movement.clearAll()
        Mouse.stopTracking()
        PathRenderer.clearPath()
        ChatUtils.info("${EnumChatFormatting.YELLOW}Bridging stopped")
    }
    
    /**
     * Check if currently active
     */
    fun isActive(): Boolean = isActive
    
    /**
     * Render the bridging path
     */
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!isActive || currentPath.isEmpty()) return
        
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
        
        // Render each waypoint in the path
        for ((index, pos) in currentPath.withIndex()) {
            val x = pos.x - renderManager.viewerPosX
            val y = pos.y - renderManager.viewerPosY
            val z = pos.z - renderManager.viewerPosZ
            
            val box = AxisAlignedBB(x, y, z, x + 1.0, y + 0.2, z + 1.0)
            
            // Color coding:
            // Green = completed waypoints
            // Yellow = current waypoint
            // Cyan = upcoming waypoints
            // Red = target block
            val (r, g, b, a) = when {
                index < currentWaypointIndex -> floatArrayOf(0.0f, 1.0f, 0.0f, 0.3f) // Green (completed)
                index == currentWaypointIndex -> floatArrayOf(1.0f, 1.0f, 0.0f, 0.5f) // Yellow (current)
                index == currentPath.size - 1 -> floatArrayOf(1.0f, 0.0f, 0.0f, 0.6f) // Red (target)
                else -> floatArrayOf(0.0f, 1.0f, 1.0f, 0.3f) // Cyan (upcoming)
            }
            
            // Draw filled box
            worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
            
            // Top face only for cleaner look
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
            
            tessellator.draw()
            
            // Draw outline
            GL11.glLineWidth(2.0f)
            worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
            
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, 1.0f).endVertex()
            worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, 1.0f).endVertex()
            
            tessellator.draw()
        }
        
        // Draw line connecting waypoints
        if (currentPath.size > 1) {
            GL11.glLineWidth(3.0f)
            worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
            
            for ((index, pos) in currentPath.withIndex()) {
                val x = pos.x + 0.5 - renderManager.viewerPosX
                val y = pos.y + 0.1 - renderManager.viewerPosY
                val z = pos.z + 0.5 - renderManager.viewerPosZ
                
                val (r, g, b) = when {
                    index < currentWaypointIndex -> floatArrayOf(0.0f, 1.0f, 0.0f) // Green
                    index == currentWaypointIndex -> floatArrayOf(1.0f, 1.0f, 0.0f) // Yellow
                    else -> floatArrayOf(0.0f, 1.0f, 1.0f) // Cyan
                }
                
                worldRenderer.pos(x, y, z).color(r, g, b, 0.8f).endVertex()
            }
            
            tessellator.draw()
        }
        
        GlStateManager.enableDepth()
        GlStateManager.enableAlpha()
        GlStateManager.disableBlend()
        GlStateManager.enableTexture2D()
        GlStateManager.popMatrix()
    }
}

