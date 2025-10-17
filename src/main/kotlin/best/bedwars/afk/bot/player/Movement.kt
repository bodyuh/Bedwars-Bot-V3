package best.bedwars.afk.bot.player

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.client.settings.KeyBinding

object Movement {
    private var forward = false
    private var backward = false
    private var left = false
    private var right = false
    private var jumping = false
    private var sprinting = false
    private var sneaking = false

    fun startForward() {
        forward = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindForward.keyCode, true)
    }

    fun stopForward() {
        forward = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindForward.keyCode, false)
    }

    fun startBackward() {
        backward = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindBack.keyCode, true)
    }

    fun stopBackward() {
        backward = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindBack.keyCode, false)
    }

    fun startLeft() {
        left = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindLeft.keyCode, true)
    }

    fun stopLeft() {
        left = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindLeft.keyCode, false)
    }

    fun startRight() {
        right = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindRight.keyCode, true)
    }

    fun stopRight() {
        right = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindRight.keyCode, false)
    }

    fun startJumping() {
        jumping = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindJump.keyCode, true)
    }

    fun stopJumping() {
        jumping = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindJump.keyCode, false)
    }

    fun startSprinting() {
        sprinting = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindSprint.keyCode, true)
    }

    fun stopSprinting() {
        sprinting = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindSprint.keyCode, false)
    }

    fun startSneaking() {
        sneaking = true
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindSneak.keyCode, true)
    }

    fun stopSneaking() {
        sneaking = false
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindSneak.keyCode, false)
    }
    
    // Aliases for compatibility
    fun startSneak() = startSneaking()
    fun stopSneak() = stopSneaking()

    fun clearAll() {
        stopForward()
        stopBackward()
        stopLeft()
        stopRight()
        stopJumping()
        stopSprinting()
        stopSneaking()
    }

    fun clearLeftRight() {
        stopLeft()
        stopRight()
    }

    fun swapLeftRight() {
        if (left) {
            stopLeft()
            startRight()
        } else if (right) {
            stopRight()
            startLeft()
        }
    }
    
    fun jump() {
        startJumping()
        TimeUtils.setTimeout({
            stopJumping()
        }, 100)
    }

    fun forward() = forward
    fun backward() = backward
    fun left() = left
    fun right() = right
    fun jumping() = jumping
    fun sprinting() = sprinting
    fun sneaking() = sneaking
}


