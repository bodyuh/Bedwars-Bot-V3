package best.bedwars.afk.bot.player

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.EntityUtils
import best.bedwars.afk.utils.RandomUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.abs

object Mouse {

    private var tracking = false
    private var targetEntity: Entity? = null
    private var targetYaw: Float? = null
    private var targetPitch: Float? = null

    var rClickDown = false

    fun leftClick() {
        if (BedwarsAFK.mc.thePlayer != null) {
            BedwarsAFK.mc.thePlayer.swingItem()
            KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindAttack.keyCode, true)
            TimeUtils.setTimeout({
                KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindAttack.keyCode, false)
            }, 50)
        }
    }

    fun rClick(duration: Int) {
        if (!rClickDown) {
            rClickDown()
            TimeUtils.setTimeout(this::rClickUp, duration)
        }
    }

    private fun rClickDown() {
        rClickDown = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindUseItem.keyCode, true)
    }

    fun rClickUp() {
        rClickDown = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindUseItem.keyCode, false)
    }

    fun startRightClick() {
        rClickDown = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindUseItem.keyCode, true)
    }

    fun stopRightClick() {
        rClickDown = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindUseItem.keyCode, false)
    }

    // Start tracking an entity
    fun startTracking(entity: Entity?) {
        tracking = true
        targetEntity = entity
    }

    // Start looking at specific angles
    fun lookAt(yaw: Float, pitch: Float) {
        tracking = true
        targetEntity = null
        targetYaw = yaw
        targetPitch = pitch
    }

    fun stopTracking() {
        tracking = false
        targetEntity = null
        targetYaw = null
        targetPitch = null
    }

    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (BedwarsAFK.mc.thePlayer != null && tracking) {
            val player = BedwarsAFK.mc.thePlayer
            
            var rotations: FloatArray? = null
            
            // Entity tracking
            if (targetEntity != null) {
                rotations = EntityUtils.getRotations(player, targetEntity!!, false)
            }
            // Fixed angle tracking
            else if (targetYaw != null && targetPitch != null) {
                rotations = floatArrayOf(targetYaw!!, targetPitch!!)
            }

            if (rotations != null) {
                // Calculate difference
                var dyaw = rotations[0] - player.rotationYaw
                var dpitch = rotations[1] - player.rotationPitch
                
                // Normalize yaw difference to [-180, 180]
                while (dyaw > 180f) dyaw -= 360f
                while (dyaw < -180f) dyaw += 360f

                // Only add tiny randomness when very close to target (reduces shake)
                val distanceToTarget = kotlin.math.sqrt(dyaw * dyaw + dpitch * dpitch)
                if (distanceToTarget < 2f) {
                    val lookRand = 0.05  // Much smaller randomness
                    dyaw += RandomUtils.randomDoubleInRange(-lookRand, lookRand).toFloat()
                    dpitch += RandomUtils.randomDoubleInRange(-lookRand, lookRand).toFloat()
                }

                // Smooth easing - slow down as we approach target
                val smoothingFactor = 0.3f  // Lower = smoother but slower
                dyaw *= smoothingFactor
                dpitch *= smoothingFactor

                // Speed limiting for maximum rotation speed
                val maxRotH = 20f
                val maxRotV = 15f

                if (abs(dyaw) > maxRotH) {
                    dyaw = if (dyaw > 0) maxRotH else -maxRotH
                }

                if (abs(dpitch) > maxRotV) {
                    dpitch = if (dpitch > 0) maxRotV else -maxRotV
                }

                // Apply rotation
                player.rotationYaw += dyaw
                player.rotationPitch += dpitch
            }
        }
    }
}


