package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Inventory
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * Eagle bridging implementation for safe bridging across the void
 * Based on the Myau client's eagle bridging logic
 * 
 * CRITICAL: This is a PASSIVE bridging system. It should ONLY be active when
 * AdvancedPathfinder explicitly calls startBridging(). All state management
 * and bridge mode activation/deactivation must be coordinated by AdvancedPathfinder
 * to prevent state desynchronization.
 */
object EagleBridge {
    private var isBridging = false
    private var registeredEvents = false
    private var lastBlockPos: BlockPos? = null
    
    private fun ensureEventHandlersRegistered() {
        if (!registeredEvents) {
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this)
                net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this)
                registeredEvents = true
            } catch (e: Exception) {
                ChatUtils.info("§c[EagleBridge] Failed to register events: ${e.message}")
            }
        }
    }
    private var lastPlaceTime = 0L
    private var blocksPlaced = 0
    private var lastClickTime = 0L
    private var lastProgressPos: BlockPos? = null
    private var stuckTicks = 0
    private var isRotating = false // Track if we're currently rotating for placement
    private var readyToPlaceStartTime: Long? = null // Track when placement conditions first became true
    private var initialBlockCount: Int = 0 // Track initial block count for verification
    private var strafeStartTime: Long? = null // Track when strafing started
    private var strafeTarget: Pair<Double, Double>? = null // Target position for strafing (x, z)
    private val currentTime: Long
        get() = System.currentTimeMillis()

    /**
     * Check if the player is currently bridging
     */
    fun isBridging(): Boolean = isBridging

    /**
     * Start bridging mode
     */
    fun startBridging() {
        // Ensure events are registered
        ensureEventHandlersRegistered()
        
        // Make sure we have blocks before starting
        if (!switchToBlocks()) {
            ChatUtils.info("§c[EagleBridge] Cannot start bridging - no blocks found")
            return
        }

        isBridging = true
        blocksPlaced = 0
        lastPlaceTime = System.currentTimeMillis() // Initialize to current time so cooldown works
        lastClickTime = 0L
        lastBlockPos = null // Reset last block position when starting
        readyToPlaceStartTime = null // Reset placement delay timer
        initialBlockCount = getBlockCount() // Track initial block count
        
        // Initialize strafing to center with very tight targeting to prevent drift
        val player = BedwarsAFK.mc.thePlayer
        if (player != null) {
            val blockX = Math.floor(player.posX).toInt()
            val blockZ = Math.floor(player.posZ).toInt()
            // Use very small random buffer (±0.1) to prevent left/right shifting
            val randomBufferX = (Math.random() - 0.5) * 0.2  // Range: -0.1 to 0.1
            val randomBufferZ = (Math.random() - 0.5) * 0.2  // Range: -0.1 to 0.1
            strafeTarget = Pair(blockX + 0.5 + randomBufferX, blockZ + 0.5 + randomBufferZ)
            strafeStartTime = currentTime
        }
        
        ChatUtils.info("§6[EagleBridge] §7Started eagle bridging")
    }

    /**
     * Stop bridging mode
     */
    fun stopBridging() {
        isBridging = false
        blocksPlaced = 0
        Movement.stopSneak()
        Mouse.stopTracking() // Stop any rotation tracking
        isRotating = false
        readyToPlaceStartTime = null
        strafeTarget = null
        strafeStartTime = null
        lastProgressPos = null
        stuckTicks = 0
        ChatUtils.info("§6[EagleBridge] §7Stopped eagle bridging")
    }

    /**
     * Get the number of blocks in inventory
     */
    fun getBlockCount(): Int {
        val player = BedwarsAFK.mc.thePlayer ?: return 0
        var count = 0
        
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i] ?: continue
            if (stack.item is ItemBlock) {
                count += stack.stackSize
            }
        }
        
        return count
    }

    /**
     * Switch to blocks in hotbar
     * Returns true if successful
     */
    fun switchToBlocks(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
        // Already holding blocks
        if (isHoldingBlock()) {
            return true
        }
        
        // Find blocks in hotbar
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i) ?: continue
            if (stack.item is ItemBlock) {
                // Switch to this slot
                player.inventory.currentItem = i
                return true
            }
        }
        
        ChatUtils.info("§c[EagleBridge] No blocks found in hotbar")
        return false
    }

    /**
     * Check if player is holding a block
     */
    fun isHoldingBlock(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val heldItem = player.inventory.getCurrentItem() ?: return false
        return heldItem.item is ItemBlock
    }

    /**
     * Check if it's safe to move - verifies that bridge blocks behind the player are solid
     * and that the player is standing on a solid block. This ensures bridge is complete.
     */
    fun canMoveSafely(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false

        // FIRST: Check block directly below player (must be solid)
        val playerBelowPos = BlockPos(Math.floor(player.posX).toInt(), Math.floor(player.posY - 1).toInt(), Math.floor(player.posZ).toInt())
        val playerBelowBlock = world.getBlockState(playerBelowPos).block
        if (playerBelowBlock == Blocks.air) {
            // Player is over void, bridge incomplete
            return false
        }

        // SECOND: Check blocks behind player (in the direction they're moving backwards)
        // For eagle bridging, we're moving backwards, so we place blocks behind us
        val yaw = player.rotationYaw * Math.PI / 180.0
        val backwardX = MathHelper.sin(yaw.toFloat())  // Backward direction in X
        val backwardZ = -MathHelper.cos(yaw.toFloat()) // Backward direction in Z
        
        // Check 2 blocks behind the player to ensure bridge is solid
        for (distance in 1..2) {
            val checkX = Math.floor(player.posX + backwardX * distance).toInt()
            val checkY = Math.floor(player.posY - 1.0).toInt()  // Check 1 block below player level
            val checkZ = Math.floor(player.posZ + backwardZ * distance).toInt()
            
            val pos = BlockPos(checkX, checkY, checkZ)
            val block = world.getBlockState(pos).block
            
            // If any block behind the player is air (void), bridge isn't complete
            if (block == Blocks.air) {
                return false
            }
        }
        
        // All blocks checked are solid - bridge is complete
        return true
    }

    /**
     * Update eagle bridging logic (call every tick)
     */
    private fun update() {
        if (!isBridging) return
        
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        // CRITICAL: Keep player sneaking while bridging (required for eagle bridging)
        if (shouldSneak() && !player.isSneaking) {
            Movement.startSneaking()
        } else if (!shouldSneak() && player.isSneaking) {
            Movement.stopSneaking()
        }

        // Handle strafing to center during first part of bridge
        if (strafeTarget != null && strafeStartTime != null) {
            val strafeDuration = 150L // Shorter 150ms strafe to reduce jitter
            val timeSinceStart = currentTime - strafeStartTime!!
            
            if (timeSinceStart < strafeDuration) {
                // Still strafing - use motion vectors to drift toward center
                val progress = timeSinceStart.toFloat() / strafeDuration
                val easeProgress = progress * progress * (3f - 2f * progress) // Smooth hermite easing
                
                val currentX = player.posX
                val currentZ = player.posZ
                val targetX = strafeTarget!!.first
                val targetZ = strafeTarget!!.second
                
                // Calculate drift needed
                val driftX = (targetX - currentX) * easeProgress
                val driftZ = (targetZ - currentZ) * easeProgress
                
                // Apply drift through motion vectors to respect physics
                // Each tick applies a fraction of remaining drift
                val remainingDriftX = driftX - (targetX - currentX) * (progress * progress * (3f - 2f * progress))
                val remainingDriftZ = driftZ - (targetZ - currentZ) * (progress * progress * (3f - 2f * progress))
                
                // Add drift as motion adjustment (max 0.02 per tick to stay extremely tight and prevent block shifting)
                val maxDrift = 0.02
                player.motionX = (player.motionX * 0.9) + remainingDriftX.coerceIn(-maxDrift, maxDrift) * 0.1
                player.motionZ = (player.motionZ * 0.9) + remainingDriftZ.coerceIn(-maxDrift, maxDrift) * 0.1
            } else {
                // Strafing complete
                strafeTarget = null
                strafeStartTime = null
            }
        }

        // Track progress
        val currentPos = BlockPos(player.posX, player.posY - 1, player.posZ)
        if (lastProgressPos == null) {
            lastProgressPos = currentPos
        }

        // Check if we're stuck (no new blocks placed)
        if (currentPos == lastProgressPos) {
            stuckTicks++
            if (stuckTicks > 60) { // 3 seconds
                stopBridging()
                return
            }
        } else {
            stuckTicks = 0
            lastProgressPos = currentPos
        }

        // Control movement based on edge detection
        if (!canMoveSafely()) {
            // Back up slowly when near edge - use vanilla-like sneaking speed (0.03 instead of 0.1)
            player.motionX = MathHelper.sin(player.rotationYaw * 0.017453292F) * 0.03
            player.motionZ = -MathHelper.cos(player.rotationYaw * 0.017453292F) * 0.03
            
            // First ensure we're in position to place
            if (shouldPlaceBlock()) {
                // Track when conditions first became true (for human-like delay)
                if (readyToPlaceStartTime == null) {
                    readyToPlaceStartTime = currentTime
                }
                
                // Wait 50ms after conditions become true before starting to place (delay)
                val timeSinceReady = currentTime - readyToPlaceStartTime!!
                if (timeSinceReady >= 50) {
                    // Placement rate: Try every 50ms (every tick)
                    if (currentTime - lastPlaceTime >= 50) {
                        // Don't start new rotation if we're already rotating
                        if (!isRotating) {
                            // Calculate placement position before calling function
                            val yaw = player.rotationYaw * Math.PI / 180.0
                            val forwardX = -MathHelper.sin(yaw.toFloat())
                            val forwardZ = MathHelper.cos(yaw.toFloat())
                            val placePos = BlockPos(
                                Math.floor(player.posX).toInt(),
                                Math.floor(player.posY - 1.0).toInt(),
                                Math.floor(player.posZ).toInt()
                            )
                            
                            val placed = tryPlaceBlockBehind(player, world, placePos)
                            if (placed) {
                                lastPlaceTime = currentTime
                            }
                        }
                    }
                }
            } else {
                // Reset ready timer if conditions aren't met
                readyToPlaceStartTime = null
                // Stop rotation if conditions aren't met
                if (isRotating) {
                    Mouse.stopTracking()
                    isRotating = false
                }
            }
        } else {
            // Reset ready timer when moving to edge
            readyToPlaceStartTime = null
            // Stop rotation tracking when not actively placing
            if (isRotating) {
                Mouse.stopTracking()
                isRotating = false
            }
        }
    }
    /**
     * Check if we should sneak based on Myau's eagle bridging logic
     */
    fun shouldSneak(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
        // CRITICAL: Always sneak while actively bridging (even if momentarily airborne)
        // This is essential for eagle bridging to work across the void
        if (isBridging) return true
        
        // Check if holding blocks
        if (!isHoldingBlock()) return false
        
        // Must be on ground
        if (!player.onGround) return false
        
        // Pitch check: looking down (>69 degrees)
        if (player.rotationPitch < 69.0f) return false
        
        return true
    }

    /**
     * Check if we should place a block
     * Returns true when at edge and ready to place, false otherwise
     */
    private fun shouldPlaceBlock(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        // Must be holding blocks
        if (!isHoldingBlock()) {
            return false
        }
        
        // Must be sneaking
        if (!player.isSneaking) {
            return false
        }
        
        // Must be looking down enough (relaxed check - allow placement even if not perfectly on ground)
        if (player.rotationPitch < 60.0f) {
            return false
        }
        
        // CRITICAL: Only return true if we're actually at the edge (canMoveSafely = false)
        // This ensures blocks are only placed when needed
        if (canMoveSafely()) {
            return false
        }
        
        return true
    }

    /**
     * Try to place a block directly under the player (eagle bridging style)
     * The player is on the edge looking at the block next to where it should place
     * For eagle bridging:
     * - Click on the face of the block that is one block forward and 1 Y level below the player
     * - But the actual block that gets placed should be exactly 1 Y level below the player (directly under them)
     */
    private fun tryPlaceBlockBehind(player: net.minecraft.client.entity.EntityPlayerSP, world: net.minecraft.client.multiplayer.WorldClient, placePos: BlockPos): Boolean {
        // Calculate position one block forward in the direction player is facing
        val yaw = player.rotationYaw * Math.PI / 180.0
        val forwardX = -MathHelper.sin(yaw.toFloat()) // Forward direction in X
        val forwardZ = MathHelper.cos(yaw.toFloat())  // Forward direction in Z
        
        // The block we CLICK ON is one block forward and 1 Y level below (the face we place against)
        val clickOnPos = BlockPos(
            Math.floor(player.posX + forwardX).toInt(),
            Math.floor(player.posY - 1.0).toInt(), // Same Y level as the block we're placing
            Math.floor(player.posZ + forwardZ).toInt()
        )
        
        // Check if position already has a block
        if (world.getBlockState(placePos).block != Blocks.air) {
            // Update lastBlockPos to track progress
            if (lastBlockPos == null || placePos != lastBlockPos) {
                lastBlockPos = placePos
            }
            return true
        }
        
        // Check if we have blocks
        val stack = player.inventory.getCurrentItem()
        if (stack == null || stack.item !is ItemBlock) {
            return false
        }
        
        // Check if the block we need to click on exists (needed for placement)
        val clickOnBlock = world.getBlockState(clickOnPos).block
        if (clickOnBlock == Blocks.air) {
            return false
        }
        
        // Calculate where to look - we look at the face of the block we're clicking on that faces towards the placement position
        // Determine which face of clickOnPos faces towards placePos
        val dx = placePos.x - clickOnPos.x
        val dz = placePos.z - clickOnPos.z
        
        // Determine the facing direction (which face of clickOnPos to click on)
        val facing = when {
            dx < 0 -> EnumFacing.WEST   // PlacePos is west of clickOnPos, so click on west face
            dx > 0 -> EnumFacing.EAST   // PlacePos is east of clickOnPos, so click on east face
            dz < 0 -> EnumFacing.NORTH  // PlacePos is north of clickOnPos, so click on north face
            dz > 0 -> EnumFacing.SOUTH  // PlacePos is south of clickOnPos, so click on south face
            else -> EnumFacing.UP       // Fallback
        }
        
        // Calculate the exact center of the edge of the face we're clicking on
        // For eagle bridging, we want to look at the edge (where the face meets the placement position)
        // Adjust slightly toward the placement position to center on the edge
        val baseOffset = when (facing) {
            EnumFacing.WEST -> Vec3(0.0, 0.5, 0.5)  // West face - center vertically and horizontally
            EnumFacing.EAST -> Vec3(1.0, 0.5, 0.5)  // East face
            EnumFacing.NORTH -> Vec3(0.5, 0.5, 0.0) // North face
            EnumFacing.SOUTH -> Vec3(0.5, 0.5, 1.0) // South face
            EnumFacing.UP -> Vec3(0.5, 1.0, 0.5)    // Top face
            EnumFacing.DOWN -> Vec3(0.5, 0.0, 0.5)  // Bottom face
        }
        
        // Adjust the look-at point slightly toward the placement position to center on the edge
        // This ensures we're looking at the exact edge where the face meets the placement space
        val edgeBias = 0.3 // Bias toward placement position (0.0 = face center, 0.5 = edge)
        val adjustedX = baseOffset.xCoord + (placePos.x - clickOnPos.x) * edgeBias
        val adjustedZ = baseOffset.zCoord + (placePos.z - clickOnPos.z) * edgeBias
        
        val lookAtX = clickOnPos.x + adjustedX
        val lookAtY = clickOnPos.y + baseOffset.yCoord
        val lookAtZ = clickOnPos.z + adjustedZ
        
        // Use Mouse.lookAt() for smooth rotation instead of directly setting rotation
        // This prevents jittery camera movement
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            lookAtX,
            lookAtY,
            lookAtZ
        )
        
        if (rotations == null) {
            return false
        }
        
        // Get block count before placement for verification
        val blockCountBefore = getBlockCount()
        
        // Use Mouse.lookAt() for smooth rotation tracking
        Mouse.lookAt(rotations[0], rotations[1])
        isRotating = true // Mark that we're rotating
        
        // Wait a bit longer for smooth rotation to complete, then click
        // Use setTimeout to ensure rotation is applied before clicking
        TimeUtils.setTimeout({
            // Use Mouse.rClick() to simulate a real right-click instead of API call
            // This is more reliable for block placement
            Mouse.rClick()
            player.swingItem()
            
            // Stop rotation tracking after clicking
            Mouse.stopTracking()
            isRotating = false
            
            // Update lastBlockPos when we attempt placement
            lastBlockPos = placePos
            
            // Verify block was actually placed after a short delay
            // This is more accurate than just counting attempts
            TimeUtils.setTimeout({
                val worldAfter = BedwarsAFK.mc.theWorld
                if (worldAfter != null) {
                    // Check if the block was actually placed
                    val blockAfter = worldAfter.getBlockState(placePos).block
                    if (blockAfter != Blocks.air) {
                        // Block was successfully placed - verify by checking block count decreased
                        val blockCountAfter = getBlockCount()
                        if (blockCountAfter < blockCountBefore) {
                            blocksPlaced++
                            if (blocksPlaced % 5 == 0) {
                                ChatUtils.info("§a[EagleBridge] Progress: Placed ${blocksPlaced} blocks")
                            }
                        }
                    }
                }
            }, 50) // Wait 50ms for server to process placement
        }, 50) // Longer delay to allow smooth rotation to complete
        
        // Return true since we attempted placement (success will be verified later)
        return true
    }

    /**
     * Check if bridge can be activated at the next waypoint
     * Used by AdvancedPathfinder to determine bridge necessity
     */
    fun canActivateBridge(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        // Check if there's void below within 5 blocks
        for (i in 0..5) {
            val below = BlockPos(player.posX.toInt(), (player.posY - i).toInt() - 1, player.posZ.toInt())
            val block = world.getBlockState(below).block
            if (block != Blocks.air) {
                // Found solid ground, don't need bridge
                return false
            }
        }
        // Void detected, bridge needed
        return true
    }
    
    /**
     * Activate bridge mode
     * Called when AdvancedPathfinder detects void and needs bridging
     * Note: AdvancedPathfinder maintains the primary bridge mode state
     */
    fun activateBridge() {
        startBridging()
    }

    /**
     * Tick event handler - updates eagle bridge logic every tick
     */
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START && isBridging) {
            update()
        }
    }
}