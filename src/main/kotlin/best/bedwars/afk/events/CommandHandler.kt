package best.bedwars.afk.events

import best.bedwars.afk.bot.commands.PathfindingCommands
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Handles commands from chat messages
 * 
 * Since we can't intercept outgoing messages in 1.8.9 easily,
 * we'll look for our commands in the chat history after they're sent.
 * 
 * Usage: Just type in chat normally:
 * #goto 100 64 200
 * #pos
 * #help
 */
object CommandHandler {
    
    @SubscribeEvent
    fun onChatReceived(event: ClientChatReceivedEvent) {
        val message = event.message.unformattedText
        
        // Look for our own messages (they appear in chat with our username)
        // Format: "<Username> #command args"
        if (message.contains("#")) {
            // Extract just the command part
            val parts = message.split(" ", limit = 2)
            if (parts.size >= 2) {
                val possibleCommand = parts[1].trim()
                
                // If it starts with #, it's our command
                if (possibleCommand.startsWith("#")) {
                    // Handle the command
                    PathfindingCommands.handleCommand(possibleCommand)
                }
            }
        }
    }
}

