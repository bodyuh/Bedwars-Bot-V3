package best.bedwars.afk.bot.commands

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.pathfinding.SimpleAStar
import best.bedwars.afk.bot.pathfinding.ParkourPathfinder
import best.bedwars.afk.bot.pathfinding.AdvancedPathfinder
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumChatFormatting

/**
 * Client-side commands for testing pathfinding
 * These commands don't send to chat or the server
 * 
 * Commands:
 * - /goto <x> <y> <z> - Navigate to coordinates
 * - /goto ~ ~ ~ - Navigate using relative coordinates
 * - /stop - Stop pathfinding
 * - /pos - Show current position
 * - /help - Show commands
 * - /bridge <start|stop> - Manual eagle bridging control
 * - /debug <on|off> - Toggle debug mode
 * - /test - Show test suggestions
 */
object PathfindingCommands : CommandBase() {
    
    override fun getCommandName(): String = "goto"
    
    override fun getCommandAliases(): List<String> = listOf("stop", "pos", "help", "bridge", "debug", "test", "parkour")
    
    override fun getCommandUsage(sender: ICommandSender): String = "/goto <x> <y> <z> | /stop | /pos | /help"
    
    override fun getRequiredPermissionLevel(): Int = 0
    
    override fun canCommandSenderUseCommand(sender: ICommandSender): Boolean = true
    
    override fun addTabCompletionOptions(sender: ICommandSender, args: Array<String>, pos: BlockPos): List<String> {
        return when {
            args.isEmpty() -> listOf("goto", "stop", "pos", "help", "bridge", "debug", "test", "parkour")
            args.size == 1 -> listOf("goto", "stop", "pos", "help", "bridge", "debug", "test", "parkour").filter { 
                it.startsWith(args[0].lowercase()) 
            }
            args[0].equals("bridge", ignoreCase = true) && args.size == 2 -> listOf("start", "stop")
            args[0].equals("debug", ignoreCase = true) && args.size == 2 -> listOf("on", "off")
            args[0].equals("goto", ignoreCase = true) || args[0].equals("parkour", ignoreCase = true) -> {
                when (args.size) {
                    2, 3, 4 -> listOf("~")
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
    
    override fun processCommand(sender: ICommandSender, args: Array<String>) {
        val command = getCommandName()
        
        // Handle based on command name or first argument
        when {
            command == "goto" && args.isEmpty() -> {
                ChatUtils.error("Usage: /goto <x> <y> <z>")
                return
            }
            command == "goto" -> handleGoto(args.toList())
            args.isNotEmpty() && args[0].equals("parkour", ignoreCase = true) -> handleParkour(args.drop(1))
            args.isNotEmpty() && args[0].equals("stop", ignoreCase = true) -> handleStop()
            args.isNotEmpty() && args[0].equals("pos", ignoreCase = true) -> handlePos()
            args.isNotEmpty() && args[0].equals("debug", ignoreCase = true) -> handleDebug(args.toList())
            args.isNotEmpty() && args[0].equals("test", ignoreCase = true) -> handleTest()
            args.isNotEmpty() && args[0].equals("bridge", ignoreCase = true) -> handleBridge(args.toList())
            args.isNotEmpty() && args[0].equals("help", ignoreCase = true) -> handleHelp()
            else -> handleHelp()
        }
    }
    
    private fun handleGoto(parts: List<String>) {
        if (parts.size < 3) {
            ChatUtils.error("Usage: /goto <x> <y> <z>")
            ChatUtils.info("Example: /goto 100 64 200")
            ChatUtils.info("Example: /goto ~ ~10 ~ (relative)")
            return
        }
        
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            ChatUtils.error("Player is null!")
            return
        }
        
        try {
            val x = if (parts[0] == "~") player.posX.toInt() 
                   else if (parts[0].startsWith("~")) player.posX.toInt() + parts[0].substring(1).toInt()
                   else parts[0].toInt()
                   
            val y = if (parts[1] == "~") player.posY.toInt()
                   else if (parts[1].startsWith("~")) player.posY.toInt() + parts[1].substring(1).toInt()
                   else parts[1].toInt()
                   
            val z = if (parts[2] == "~") player.posZ.toInt()
                   else if (parts[2].startsWith("~")) player.posZ.toInt() + parts[2].substring(1).toInt()
                   else parts[2].toInt()
            
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
            ChatUtils.info("Example: /goto 100 64 200")
            ChatUtils.info("Example: /goto ~10 ~ ~-5")
        }
    }
    
    private fun handleParkour(parts: List<String>) {
        if (parts.size < 3) {
            ChatUtils.error("Usage: /parkour <x> <y> <z>")
            ChatUtils.info("Navigate using parkour jumps")
            return
        }
        
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            ChatUtils.error("Player is null!")
            return
        }
        
        try {
            val x = if (parts[0] == "~") player.posX.toInt() 
                   else if (parts[0].startsWith("~")) player.posX.toInt() + parts[0].substring(1).toInt()
                   else parts[0].toInt()
                   
            val y = if (parts[1] == "~") player.posY.toInt()
                   else if (parts[1].startsWith("~")) player.posY.toInt() + parts[1].substring(1).toInt()
                   else parts[1].toInt()
                   
            val z = if (parts[2] == "~") player.posZ.toInt()
                   else if (parts[2].startsWith("~")) player.posZ.toInt() + parts[2].substring(1).toInt()
                   else parts[2].toInt()
            
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
    
    private fun handleDebug(parts: List<String>) {
        if (parts.size < 2) {
            ChatUtils.error("Usage: /debug <on|off>")
            return
        }
        
        val enabled = when (parts[1].lowercase()) {
            "on", "true", "1" -> true
            "off", "false", "0" -> false
            else -> {
                ChatUtils.error("Usage: /debug <on|off>")
                return
            }
        }
        
        AdvancedPathfinder.setDebugMode(enabled)
    }
    
    private fun handleTest() {
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            ChatUtils.error("Player is null!")
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━ Pathfinding Test Mode ━━━")
        ChatUtils.info("${EnumChatFormatting.GRAY}Test commands:")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}Walk around walls:")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}/goto ~10 ~ ~")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}Climb up:")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}/goto ~ ~5 ~")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}Navigate complex terrain:")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}/goto ~20 ~10 ~20")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GOLD}Eagle bridging test:")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}/bridge start ${EnumChatFormatting.GRAY}(Start manual bridging)")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}/bridge stop ${EnumChatFormatting.GRAY}(Stop bridging)")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GOLD}Tips:")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}• Build a wall and test walking around it")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}• Place blocks at different heights")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}• Use /debug on to see detailed pathfinding info")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}• Green line = calculated path")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}• Red particle = current waypoint")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    private fun handleBridge(parts: List<String>) {
        if (parts.size < 2) {
            ChatUtils.error("Usage: /bridge <start|stop>")
            return
        }
        
        when (parts[1].lowercase()) {
            "start" -> {
                best.bedwars.afk.bot.pathfinding.EagleBridge.startBridging()
            }
            "stop" -> {
                best.bedwars.afk.bot.pathfinding.EagleBridge.stopBridging()
            }
            else -> {
                ChatUtils.error("Usage: /bridge <start|stop>")
            }
        }
    }
    
    private fun handleHelp() {
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━━ Pathfinding Commands ━━━━")
        ChatUtils.info("${EnumChatFormatting.GRAY}(Client-side only, won't send to chat)")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}/goto <x> <y> <z>")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Navigate with obstacle avoidance")
        ChatUtils.info("  ${EnumChatFormatting.WHITE}Examples:")
        ChatUtils.info("    ${EnumChatFormatting.GRAY}/goto 100 64 200")
        ChatUtils.info("    ${EnumChatFormatting.GRAY}/goto ~ ~5 ~ ${EnumChatFormatting.DARK_GRAY}(jump up 5 blocks)")
        ChatUtils.info("    ${EnumChatFormatting.GRAY}/goto ~50 ~ ~-30 ${EnumChatFormatting.DARK_GRAY}(navigate around walls)")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GOLD}/parkour <x> <y> <z>")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Navigate using parkour jumps")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.AQUA}/bridge <start|stop>")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Manual eagle bridging control")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}/stop")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Stop current pathfinding")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}/pos")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Show current position & rotation")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}/debug <on|off>")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Toggle detailed pathfinding logs")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}/test")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Show pathfinding test suggestions")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.GREEN}/help")
        ChatUtils.info("  ${EnumChatFormatting.GRAY}Show this help message")
        ChatUtils.info("")
        ChatUtils.info("${EnumChatFormatting.YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}
