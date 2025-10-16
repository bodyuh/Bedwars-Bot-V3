package best.bedwars.afk.utils

import best.bedwars.afk.BedwarsAFK
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting

object ChatUtils {

    fun removeFormatting(text: String): String {
        var t = ""
        var skip = false
        for (i in text.indices) {
            if (!skip) {
                if (text[i] == '§') {
                    skip = true
                } else {
                    t += text[i]
                }
            } else {
                skip = false
            }
        }
        return t
    }

    fun sendAsPlayer(message: String) {
        if (BedwarsAFK.mc.thePlayer != null) {
            BedwarsAFK.mc.thePlayer.sendChatMessage(message)
        }
    }

    fun info(message: String) {
        sendChatMessage("${EnumChatFormatting.WHITE}[${EnumChatFormatting.RED}BW${EnumChatFormatting.WHITE}] $message")
    }

    fun error(message: String) {
        sendChatMessage("${EnumChatFormatting.WHITE}[${EnumChatFormatting.RED}BW${EnumChatFormatting.WHITE}] ${EnumChatFormatting.RED}$message")
    }

    private fun sendChatMessage(message: String) {
        if (BedwarsAFK.mc.thePlayer != null) {
            BedwarsAFK.mc.thePlayer.addChatMessage(ChatComponentText(message))
        }
    }
}


