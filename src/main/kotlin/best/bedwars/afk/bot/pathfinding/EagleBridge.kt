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
        lastPlaceTime = 0L
        lastClickTime = 0L
        lastBlockPos = null // Reset last block position when starting
        readyToPlaceStartTime = null // Reset placement delay timer
        initialBlockCount = getBlockCount() // Track initial block count
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
     * Check if it's safe to move (no void below next position)
     */
    fun canMoveSafely(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false

        // Get block under player
        val pos = BlockPos(player.posX, player.posY - 0.5, player.posZ)
        if (world.getBlockState(pos).block == Blocks.air) {
            return false // No block directly under player
        }

        // Get block at the edge based on player's direction
        val yaw = player.rotationYaw * Math.PI / 180.0
        val edgeX = player.posX - MathHelper.sin(yaw.toFloat()) * 0.3
        val edgeZ = player.posZ + MathHelper.cos(yaw.toFloat()) * 0.3
        
        // Check if we're at an edge
        val edgePos = BlockPos(edgeX, player.posY - 1.0, edgeZ)
        val hasBlockAtEdge = world.getBlockState(edgePos).block != Blocks.air

        return hasBlockAtEdge
    }

    /**
     * Update eagle bridging logic (call every tick)
     */
    private fun update() {
        if (!isBridging) return
        
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        // Track progress
        val currentPos = BlockPos(player.posX, player.posY - 1, player.posZ)
        if (lastProgressPos == null) {
            lastProgressPos = currentPos
        }

        // Check if we're stuck (no new blocks placed)
        if (currentPos == lastProgressPos) {
            stuckTicks++
            if (stuckTicks % 20 == 0) { // Log every second
                ChatUtils.info("§e[EagleBridge] No progress for ${stuckTicks/20} seconds...")
            }
            if (stuckTicks > 60) { // 3 seconds
                ChatUtils.info("§c[EagleBridge] Stuck for 3 seconds - stopping")
                // Try to find why we're stuck
                val holding = player.inventory.getCurrentItem()
                if (holding == null) {
                    ChatUtils.info("§c[EagleBridge] Stuck: No item in hand")
                } else if (holding.item !is ItemBlock) {
                    ChatUtils.info("§c[EagleBridge] Stuck: Not holding blocks (${holding.item.javaClass.simpleName})")
                } else {
                    ChatUtils.info("§c[EagleBridge] Stuck: Block placement failing")
                }
                stopBridging()
                return
            }
        } else {
            if (stuckTicks > 0) {
                ChatUtils.info("§a[EagleBridge] Progress resumed")
            }
            stuckTicks = 0
            lastProgressPos = currentPos
        }

        // Control movement based on edge detection
        if (!canMoveSafely()) {
            // Back up slowly when near edge
            player.motionX = MathHelper.sin(player.rotationYaw * 0.017453292F) * 0.1
            player.motionZ = -MathHelper.cos(player.rotationYaw * 0.017453292F) * 0.1
            
            // First ensure we're in position to place
            if (shouldPlaceBlock()) {
                // Track when conditions first became true (for human-like delay)
                if (readyToPlaceStartTime == null) {
                    readyToPlaceStartTime = currentTime
                }
                
                // Wait 50ms after conditions become true before starting to place (delay)
                val timeSinceReady = currentTime - readyToPlaceStartTime!!
                if (timeSinceReady >= 50) {
                    // Reduced placement rate: Try every 50ms (every tick)
                    if (currentTime - lastPlaceTime >= 50) {
                        // Don't start new rotation if we're already rotating
                        if (!isRotating) {
                            // Calculate placement position before calling function
                            val yaw = player.rotationYaw * Math.PI / 180.0
                            val forwardX = -MathHelper.sin(yaw.toFloat())
                            val forwardZ = MathHelper.cos(yaw.toFloat())
                            val placePos = BlockPos(
                                player.posX.toInt(),
                                (player.posY - 1.0).toInt(),
                                player.posZ.toInt()
                            )
                            
                            val placed = tryPlaceBlockBehind(player, world, placePos)
                            if (placed) {
                                lastPlaceTime = currentTime
                                // Block counting will be verified after placement
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
        
        // Check if holding blocks
        if (!isHoldingBlock()) return false
        
        // Must be on ground
        if (!player.onGround) return false
        
        // Always sneak when bridging
        if (isBridging) return true
        
        // Pitch check: looking down (>69 degrees)
        if (player.rotationPitch < 69.0f) return false
        
        return true
    }

    /**
     * Check if we should place a block
     */
    private fun shouldPlaceBlock(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
        // Must be holding blocks
        if (!isHoldingBlock()) {
            return false
        }
        
        // Must be sneaking (for eagle bridging)
        if (!player.isSneaking) {
            return false
        }
        
        // Must be looking down enough (relaxed check - allow placement even if not perfectly on ground)
        if (player.rotationPitch < 60.0f) {
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
            (player.posX + forwardX).toInt(),
            (player.posY - 1.0).toInt(), // Same Y level as the block we're placing
            (player.posZ + forwardZ).toInt()
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
            dx > 0 -> EnumFacing.WEST   // PlacePos is east of clickOnPos, so click on west face
            dx < 0 -> EnumFacing.EAST   // PlacePos is west of clickOnPos, so click on east face
            dz > 0 -> EnumFacing.NORTH  // PlacePos is south of clickOnPos, so click on north face
            dz < 0 -> EnumFacing.SOUTH  // PlacePos is north of clickOnPos, so click on south face
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
     * Tick event handler - updates eagle bridge logic every tick
     */
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START && isBridging) {
            update()
        }
    }
}