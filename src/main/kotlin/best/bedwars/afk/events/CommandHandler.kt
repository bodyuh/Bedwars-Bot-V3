package best.bedwars.afk.events

import best.bedwars.afk.bot.commands.PathfindingCommands
import net.minecraftforge.client.ClientCommandHandler

/**
 * Handles client-side commands that don't send to chat
 * 
 * Commands are registered with ClientCommandHandler and work like vanilla commands
 * but execute client-side only without sending to the server.
 * 
 * Usage: Type in chat (won't be sent to server):
 * /goto 100 64 200
 * /pos
 * /help
 * /bridge start
 * /bridge stop
 * /test
 * 
 * NOTE: Use / (slash) not # (hash) - Minecraft's command system uses /
 */
object CommandHandler {
    
    fun registerCommands() {
        // Register all client-side commands
        ClientCommandHandler.instance.registerCommand(PathfindingCommands)
    }
}

