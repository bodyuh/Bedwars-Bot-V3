package best.skywars.bot

import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

object SkyBridge {

    private val mc = Minecraft.getMinecraft()

    var isBridging = false
    private var lastPlaceTime = 0L

    fun tickBridge(targetPos: BlockPos) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        // 1. Ensure building blocks in hand
        val blockSlot = findHotbarBlocks()
        if (blockSlot == -1) {
            stopBridge()
            return
        }
        if (player.inventory.currentItem != blockSlot) {
            player.inventory.currentItem = blockSlot
        }

        // 2. Aim pitch at 79.0° (the user's exact recorded angle)
        // Yaw points backwards (away from target direction, so moving S goes towards target)
        val dx = (targetPos.x + 0.5) - player.posX
        val dz = (targetPos.z + 0.5) - player.posZ
        val forwardYaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val backwardYaw = (forwardYaw + 180f) % 360f

        player.rotationPitch = 79.0f
        player.rotationYaw = backwardYaw

        // 3. Move backward (S)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, true)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)

        // 4. Edge Detection (Eagle Bridge)
        // Check block right below the player
        val feetPos = BlockPos(player.posX, player.posY - 0.5, player.posZ)
        val blockBelow = world.getBlockState(feetPos).block

        // If air is below the player's edge, sneak and place block
        if (blockBelow == Blocks.air) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.keyCode, true)

            // Right click to place block
            val now = System.currentTimeMillis()
            if (now - lastPlaceTime > 60) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, true)
                TimeHelper.setTimeout({
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
                }, 40)
                lastPlaceTime = now
            }
        } else {
            // Solid ground under feet, release sneak for speed
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.keyCode, false)
        }
    }

    fun stopBridge() {
        isBridging = false
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
    }

    fun findHotbarBlocks(): Int {
        val player = mc.thePlayer ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i)
            if (stack != null && stack.item is ItemBlock) {
                val block = (stack.item as ItemBlock).block
                if (block.isCollidable && block != Blocks.air) {
                    return i
                }
            }
        }
        return -1
    }
}
