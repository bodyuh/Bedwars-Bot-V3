package best.bedwars.afk.bot.commands

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.pathfinding.SimpleAStar
import best.bedwars.afk.bot.pathfinding.ParkourPathfinder
import best.bedwars.afk.bot.pathfinding.AdvancedPathfinder
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.util.EnumChatFormatting

/**
 * Chat commands for testing pathfinding
 * Commands:
 * - #goto <x> <y> <z> - Navigate to coordinates
 * - #goto ~ ~ ~ - Navigate using relative coordinates
 * - #stop - Stop pathfinding
 * - #pos - Show current position
 * - #help - Show commands
 */
object PathfindingCommands {
    
    fun handleCommand(message: String): Boolean {
        if (!message.startsWith("#")) return false
        
        val parts = message.substring(1).split(" ")
        val command = parts[0].lowercase()
        
        when (command) {
            "goto" -> handleGoto(parts)
            "parkour" -> handleParkour(parts)
            "stop" -> handleStop()
            "pos" -> handlePos()
            "help", "commands" -> handleHelp()
            else -> {
                ChatUtils.error("Unknown command: #$command")
                ChatUtils.info("Type ${EnumChatFormatting.YELLOW}#help${EnumChatFormatting.GRAY} for commands")
                return false
            }
        }
        
        return true
    }
    
    private fun handleGoto(parts: List<String>) {
        if (parts.size < 4) {
            ChatUtils.error("Usage: #goto <x> <y> <z>")
            ChatUtils.info("Example: #goto 100 64 200")
            ChatUtils.info("Example: #goto ~ ~10 ~ (relative)")
            return
        }
        
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            ChatUtils.error("Player is null!")
            return
        }
        
        try {
            val x = if (parts[1] == "~") player.posX.toInt() 
                   else if (parts[1].startsWith("~")) player.posX.toInt() + parts[1].substring(1).toInt()
                   else parts[1].toInt()
                   
            val y = if (parts[2] == "~") player.posY.toInt()
                   else if (parts[2].startsWith("~")) player.posY.toInt() + parts[2].substring(1).toInt()
                   else parts[2].toInt()
                   
            val z = if (parts[3] == "~") player.posZ.toInt()
                   else if (parts[3].startsWith("~")) player.posZ.toInt() + parts[3].substring(1).toInt()
                   else parts[3].toInt()
            
            ChatUtils.info("${EnumChatFormatting.GREEN}→ Pathfinding to: ${EnumChatFormatting.WHITE}$x, $y, $z")
            ChatUtils.info("${EnumChatFormatting.GRAY}From: ${player.posX.toInt()}, ${player.posY.toInt()}, ${player.posZ.toInt()}")
            
            AdvancedPathfinder.goTo(x, y, z) {
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Reached destination!")
                val finalPos = BedwarsAFK.mc.thePlayer
                if (finalPos != null) {
                    ChatUtils.info("${EnumChatFormatting.GRAY}Final: ${finalPos.posX.toInt()}, ${finalPos.posY.toInt()}, ${finalPos.posZ.toInt()}")
                }
            }
        } catch (e: NumberFormatException) {
            ChatUtils.error("Invalid coordinates! Use numbers or ~")
            ChatUtils.info("Example: #goto 100 64 200")
            ChatUtils.info("Example: #goto ~10 ~ ~-5")
        }
    }
    
    private fun handleParkour(parts: List<String>) {
        if (parts.size < 4) {
            ChatUtils.error("Usage: #parkour <x> <y> <z>")
            ChatUtils.info("Navigate using parkour jumps")
            return
        }
        
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            ChatUtils.error("Player is null!")
            return
        }
        
        try {
            val x = if (parts[1] == "~") player.posX.toInt() 
                   else if (parts[1].startsWith("~")) player.posX.toInt() + parts[1].substring(1).toInt()
                   else parts[1].toInt()
                   
            val y = if (parts[2] == "~") player.posY.toInt()
                   else if (parts[2].startsWith("~")) player.posY.toInt() + parts[2].substring(1).toInt()
                   else parts[2].toInt()
                   
            val z = if (parts[3] == "~") player.posZ.toInt()
                   else if (parts[3].startsWith("~")) player.posZ.toInt() + parts[3].substring(1).toInt()
                   else parts[3].toInt()
            
            ChatUtils.info("${EnumChatFormatting.GOLD}⚡ Parkour mode to: ${EnumChatFormatting.WHITE}$x, $y, $z")
            
            ParkourPathfinder.goTo(x, y, z) {
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Parkour completed!")
            }
        } catch (e: NumberFormatException) {
            ChatUtils.error("Invalid coordinates!")
        }
    }
    
    private fun handleStop() {
        SimpleAStar.stop()
        ParkourPathfinder.stop()
        AdvancedPathfinder.stop()
        ChatUtils.info("${EnumChatFormatting.RED}✗ Pathfinding stopped")
    }
    
    private fun handlePos() {
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            ChatUtils.error("Player is null!")
            return
        }
        
        val x = player.posX.toInt()
        val y = player.posY.toInt()
        val z = player.posZ.toInt()
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━ Current Position ━━━")
        ChatUtils.info("${EnumChatFormatting.WHITE}X: ${EnumChatFormatting.GREEN}$x ${EnumChatFormatting.WHITE}Y: ${EnumChatFormatting.GREEN}$y ${EnumChatFormatting.WHITE}Z: ${EnumChatFormatting.GREEN}$z")
        ChatUtils.info("${EnumChatFormatting.GRAY}Yaw: ${player.rotationYaw.toInt()}° Pitch: ${player.rotationPitch.toInt()}°")
    }
    
    private fun handleHelp() {
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━━ Pathfinding Commands ━━━━")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}#goto <x> <y> <z>")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Advanced navigation (climbs, towers, bridges)")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}Examples:")
        ChatUtils.info("    ${EnumChatFormatting.GRAY}#goto 100 64 200")
        ChatUtils.info("    ${EnumChatFormatting.GRAY}#goto ~ ~10 ~ ${EnumChatFormatting.DARK_GRAY}(tower up 10 blocks)")
        ChatUtils.info("    ${EnumChatFormatting.GRAY}#goto ~50 ~ ~-30 ${EnumChatFormatting.DARK_GRAY}(climb mountains)")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GOLD}#parkour <x> <y> <z>")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Navigate using parkour jumps")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Handles gaps and precision jumps")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}#stop")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Stop current pathfinding")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}#pos")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Show current position & status")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}#help")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Show this help message")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

