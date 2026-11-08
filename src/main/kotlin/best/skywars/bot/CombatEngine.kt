package best.skywars.bot

import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemFishingRod
import net.minecraft.item.ItemSword
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

object CombatEngine {

    private val mc = Minecraft.getMinecraft()

    // Tracks players with active Strength perk: PlayerName -> Expiry timestamp (ms)
    val strengthMap = ConcurrentHashMap<String, Long>()

    var currentTarget: EntityPlayer? = null
    var isAvoidingStrength = false

    private var lastAttackTime = 0L
    private var lastRodTime = 0L
    private var rodStage = 0 // 0 = idle, 1 = casted, 2 = return to sword
    private var prevHotbarSlot = 0

    fun reset() {
        strengthMap.clear()
        currentTarget = null
        isAvoidingStrength = false
        rodStage = 0
    }

    /**
     * Call when a chat kill event is detected
     */
    fun onPlayerKill(killerName: String) {
        val cleanName = killerName.trim().split(" ").last() // strips rank like [VIP+]
        strengthMap[cleanName] = System.currentTimeMillis() + 5000L
    }

    fun hasStrength(player: EntityPlayer): Boolean {
        val exp = strengthMap[player.name] ?: return false
        return System.currentTimeMillis() < exp
    }

    fun getStrengthRemainingSec(player: EntityPlayer): Float {
        val exp = strengthMap[player.name] ?: return 0f
        val rem = exp - System.currentTimeMillis()
        return if (rem > 0) rem / 1000f else 0f
    }

    /**
     * Find nearest alive enemy player
     */
    fun findNearestEnemy(maxDistance: Double = 40.0): EntityPlayer? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        var nearest: EntityPlayer? = null
        var bestDist = maxDistance

        for (other in world.playerEntities) {
            if (other == player || other !is EntityPlayer) continue
            if (other.isDead || other.health <= 0f) continue
            if (other.isInvisibleToPlayer(player)) continue

            val dist = player.getDistanceToEntity(other).toDouble()
            if (dist < bestDist) {
                bestDist = dist
                nearest = other
            }
        }
        return nearest
    }

    fun tickCombat() {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        currentTarget = findNearestEnemy()
        val target = currentTarget

        if (target == null) {
            isAvoidingStrength = false
            return
        }

        val dist = player.getDistanceToEntity(target)

        // 1. Anti-Strength Logic: Do NOT engage if enemy has Strength (Bulldozer 5s)
        if (hasStrength(target)) {
            isAvoidingStrength = true
            val remSec = getStrengthRemainingSec(target)

            // Look at enemy to track them and rod them away if they rush
            lookAtEntity(target)

            if (dist < 12.0f) {
                // Backpedal and keep distance
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, true)
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true)

                // If enemy gets too close (< 7m), rod them to knock them back!
                if (dist < 7.0f && System.currentTimeMillis() - lastRodTime > 700) {
                    executeRodKnockback()
                }
            } else {
                // Safe distance, hold position
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, false)
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
            }
            return
        }

        isAvoidingStrength = false

        // 2. Normal Combat: Enemy has NO Strength

        // Look smoothly at target hitbox using GCD-aligned angles
        lookAtEntity(target)

        // Fishing Rod knockback trick at 4.5m - 8.0m distance
        val rodSlot = findHotbarRod()
        val now = System.currentTimeMillis()

        if (rodSlot != -1 && dist in 4.5f..8.0f && (now - lastRodTime > 1200)) {
            executeRodKnockback()
            return
        }

        // Equip best sword
        val swordSlot = findBestSwordSlot()
        if (swordSlot != -1 && player.inventory.currentItem != swordSlot && rodStage == 0) {
            player.inventory.currentItem = swordSlot
        }

        // Approach & W-Tap combo movement
        if (dist > 2.2f) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, true)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, false)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true)
        } else if (dist < 1.7f) {
            // Keep sweet spot combo distance ~1.9m - 2.1m
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, true)
        } else {
            // In optimal combo lock range
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, false)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, true)
        }

        // Critical hit timing: jump when approaching inside 3.0m
        if (dist <= 3.0f && player.onGround && Math.random() < 0.25) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, true)
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, false)
        }

        // 3. Attack trigger: NO custom reach packets!
        // We simulate native LMB clicks with human-like jittered delay (95-135ms ~ 8-11 CPS).
        // Vape v4 hooks getMouseOver / raytrace and handles the 3.15-3.2 Reach completely legitimately.
        val isAimedAtTarget = mc.objectMouseOver?.entityHit == target
        val jitteredDelay = (95 + Math.random() * 40).toLong()

        if ((isAimedAtTarget || dist <= 3.2f) && (now - lastAttackTime >= jitteredDelay)) {
            // Press native attack key so Minecraft/Vape processes raycast and reach
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, true)
            lastAttackTime = now

            TimeHelper.setTimeout({
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
            }, (30 + Math.random() * 20).toLong())

            // W-Tap: momentary sprint reset on hit for knockback combo
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, true)
        }
    }

    private fun executeRodKnockback() {
        val player = mc.thePlayer ?: return
        val rodSlot = findHotbarRod()
        if (rodSlot == -1) return

        prevHotbarSlot = player.inventory.currentItem
        player.inventory.currentItem = rodSlot

        // Cast rod (right click)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, true)
        lastRodTime = System.currentTimeMillis()

        // Switch back to sword after 100ms
        best.skywars.bot.TimeHelper.setTimeout({
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
            val swordSlot = findBestSwordSlot()
            player.inventory.currentItem = if (swordSlot != -1) swordSlot else prevHotbarSlot
        }, 100)
    }

    private fun findHotbarRod(): Int {
        val player = mc.thePlayer ?: return -1
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i)
            if (stack != null && stack.item is ItemFishingRod) {
                return i
            }
        }
        return -1
    }

    private fun findBestSwordSlot(): Int {
        val player = mc.thePlayer ?: return -1
        var bestSlot = -1
        var bestScore = 0f

        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i)
            if (stack != null && stack.item is ItemSword) {
                val score = ItemFilter.getSwordScore(stack)
                if (score > bestScore) {
                    bestScore = score
                    bestSlot = i
                }
            }
        }
        return bestSlot
    }

    fun lookAtEntity(target: Entity) {
        val player = mc.thePlayer ?: return
        val dx = target.posX - player.posX
        // Aim at torso/head (height * 0.75)
        val dy = (target.posY + target.height * 0.75) - (player.posY + player.getEyeHeight())
        val dz = target.posZ - player.posZ
        val dist = sqrt(dx * dx + dz * dz)

        val targetYaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val targetPitch = (-Math.toDegrees(atan2(dy, dist))).toFloat()

        // Interpolate angle with GCD parity for mouse sensitivity
        val smoothYaw = interpolateAngle(player.rotationYaw, targetYaw, 0.40f)
        val smoothPitch = interpolateAngle(player.rotationPitch, targetPitch, 0.40f)

        player.rotationYaw = applyGCD(smoothYaw, player.rotationYaw)
        player.rotationPitch = applyGCD(smoothPitch, player.rotationPitch)
    }

    private fun applyGCD(targetAngle: Float, currentAngle: Float): Float {
        val sens = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f
        val gcd = sens * sens * sens * 1.2f
        if (gcd <= 0f) return targetAngle
        val delta = targetAngle - currentAngle
        val fixedDelta = Math.round(delta / gcd) * gcd
        return currentAngle + fixedDelta
    }

    private fun interpolateAngle(from: Float, to: Float, factor: Float): Float {
        var diff = (to - from) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return from + diff * factor
    }
}
