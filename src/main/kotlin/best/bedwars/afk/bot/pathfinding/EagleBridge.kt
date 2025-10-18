package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Inventory
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Eagle bridging implementation for safe bridging across the void
 * Based on the Myau client's eagle bridging logic
 */
object EagleBridge {
    
    private var isBridging = false
    private var lastPlaceTime = 0L
    private var blocksPlaced = 0
    
    /**
     * Check if the player is currently bridging
     */
    fun isBridging(): Boolean = isBridging
    
    
    /**
     * Start bridging mode
     */
    fun startBridging() {
        isBridging = true
        blocksPlaced = 0
        ChatUtils.info("§6[EagleBridge] §7Started eagle bridging")
    }
    
    /**
     * Stop bridging mode
     */
    fun stopBridging() {
        isBridging = false
        blocksPlaced = 0
        Movement.stopSneak()
        ChatUtils.info("§6[EagleBridge] §7Stopped eagle bridging")
    }
    
    /**
     * Check if we can move safely (there's a block below our next position)
     * Based on Myau's canMoveSafely logic
     */
    fun canMoveSafely(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        // Predict next movement position
        val offset = predictMovement()
        val nextX = player.posX + player.motionX + offset[0]
        val nextZ = player.posZ + player.motionZ + offset[1]
        
        // Check if there's a block below next position
        val checkPos = BlockPos(nextX, player.posY - 1, nextZ)
        val block = world.getBlockState(checkPos).block
        
        // Safe if there's a solid block below
        return block.material.isSolid
    }
    
    /**
     * Predict player movement based on current input
     * Based on Myau's predictMovement logic
     */
    private fun predictMovement(): DoubleArray {
        val player = BedwarsAFK.mc.thePlayer ?: return doubleArrayOf(0.0, 0.0)
        val world = BedwarsAFK.mc.theWorld ?: return doubleArrayOf(0.0, 0.0)
        
        val strafeInput = player.movementInput.moveStrafe * 0.98f
        val forwardInput = player.movementInput.moveForward * 0.98f
        
        var inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput
        
        if (inputMagnitude >= 1.0E-4f) {
            inputMagnitude = MathHelper.sqrt_float(inputMagnitude)
            if (inputMagnitude < 1.0f) {
                inputMagnitude = 1.0f
            }
            
            // Get allowed horizontal distance
            val groundPos = BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.entityBoundingBox.minY) - 1,
                MathHelper.floor_double(player.posZ)
            )
            val slipperiness = world.getBlockState(groundPos).block.slipperiness * 0.91f
            val allowedDistance = player.aiMoveSpeed * (0.16277136f / (slipperiness * slipperiness * slipperiness))
            
            inputMagnitude = allowedDistance / inputMagnitude
            
            val sinYaw = MathHelper.sin(player.rotationYaw * Math.PI.toFloat() / 180.0f)
            val cosYaw = MathHelper.cos(player.rotationYaw * Math.PI.toFloat() / 180.0f)
            
            val strafe = strafeInput * inputMagnitude
            val forward = forwardInput * inputMagnitude
            
            return doubleArrayOf(
                (strafe * cosYaw - forward * sinYaw).toDouble(),
                (forward * cosYaw + strafe * sinYaw).toDouble()
            )
        }
        
        return doubleArrayOf(0.0, 0.0)
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
        
        // Pitch check: looking down (>69 degrees)
        if (player.rotationPitch < 69.0f) return false
        
        return true
    }
    
    /**
     * Update eagle bridging logic (call every tick)
     * Based on BridgingPathfinder's working implementation
     */
    fun update() {
        if (!isBridging) return
        
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return
        val currentTime = System.currentTimeMillis()
        
        // NOTE: Sneaking is now handled by AdvancedPathfinder's speedbridge logic
        // We don't call Movement.startSneak() here to allow speedbridge to work
        // The pathfinder will handle sneak/unsneak cycling for straight bridges
        
        // Try to place block every ~300ms (slightly slower for more stability)
        if (currentTime - lastPlaceTime >= 300) {
            val placed = tryPlaceBlockBehind(player, world)
            if (placed) {
                lastPlaceTime = currentTime
                blocksPlaced++
                
                if (blocksPlaced % 5 == 0) {
                    ChatUtils.info("§6[Eagle] §7Placed ${blocksPlaced} blocks")
                }
            }
        }
    }
    
    /**
     * Try to place a block behind the player (BridgingPathfinder method)
     * Returns true if placement was successful or block already exists
     */
    private fun tryPlaceBlockBehind(player: net.minecraft.client.entity.EntityPlayerSP, world: net.minecraft.client.multiplayer.WorldClient): Boolean {
        // Calculate position directly under player (for sneaking backwards)
        // When walking backwards while looking down, we want to place right below us
        val placePos = BlockPos(player.posX, player.posY - 1.0, player.posZ)
        
        // Check if position already has a block
        if (world.getBlockState(placePos).block != Blocks.air) {
            return true // Block already exists, success
        }
        
        // Equip wool/blocks first
        if (!switchToBlocks()) return false
        
        val heldItem = player.heldItem
        if (heldItem == null) return false
        
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
        
        // Small delay to let the server process
        // We return true immediately to keep bridging smooth
        // The server will place the block if valid
        return true
    }
    
    
    /**
     * Check if we should place a block
     */
    private fun shouldPlaceBlock(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
        // Must be on ground
        if (!player.onGround) return false
        
        // Must be holding blocks
        if (!isHoldingBlock()) return false
        
        // Check if there's no block below where we're about to step
        return !canMoveSafely()
    }
    
    /**
     * Check if player is holding a block
     */
    private fun isHoldingBlock(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val heldItem = player.heldItem ?: return false
        
        return heldItem.item is ItemBlock
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
     */
    fun switchToBlocks(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        
        // Check if already holding blocks
        if (isHoldingBlock()) return true
        
        // Find blocks in hotbar
        for (i in 0..8) {
            val stack = player.inventory.mainInventory[i] ?: continue
            if (stack.item is ItemBlock) {
                player.inventory.currentItem = i
                return true
            }
        }
        
        return false
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

