package best.bedwars.afk.events

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.RandomUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class ChatEventHandler {

    @SubscribeEvent
    fun onChat(ev: ClientChatReceivedEvent) {
        val unformatted = ev.message.unformattedText
        val bot = BedwarsAFK.bot ?: return
        
        if (!bot.toggled()) return

        // Game start detection
        if (unformatted.contains("Protect your bed", ignoreCase = true)) {
            handleGameStart()
        }
        
        // Alternative game mode detections
        if (unformatted.contains("Bed Wars Lucky Blocks", ignoreCase = true) ||
            unformatted.contains("Bed Wars Swappage", ignoreCase = true) ||
            unformatted.contains("Found an in-progress", ignoreCase = true) ||
            unformatted.contains("Select an ultimate in the store!", ignoreCase = true) ||
            unformatted.contains("All generators are maxed!", ignoreCase = true)) {
            handleGameStart()
        }

        // Death/elimination detection
        if (unformatted.contains("You have been eliminated!", ignoreCase = true)) {
            ChatUtils.info("You took a final death. Requeueing.")
            TimeUtils.setTimeout({
                bot.handleGameEnd()
            }, 600)
        }
        
        // Bed broken but still alive
        if (unformatted.contains("Your bed was destroyed so you are a spectator!", ignoreCase = true)) {
            ChatUtils.info("You rejoined but your bed was broken. Requeueing.")
            TimeUtils.setTimeout({
                bot.handleGameEnd()
            }, 600)
        }

        // Game end detection
        if (unformatted.contains("Reward Summary", ignoreCase = true)) {
            ChatUtils.sendAsPlayer("gg")
            TimeUtils.setTimeout({
                ChatUtils.info("Game Ended. Requeueing.")
                bot.handleGameEnd()
            }, 600)
        }

        // Respawn detection
        if (unformatted.contains("You have respawned!", ignoreCase = true)) {
            bot.handleRespawn()
        }

        // AFK warning
        if (unformatted.contains("You will be afk", ignoreCase = true)) {
            // Just move slightly
            best.bedwars.afk.bot.player.Movement.startForward()
            TimeUtils.setTimeout({
                best.bedwars.afk.bot.player.Movement.stopForward()
            }, 500)
        }

        // Staff detection - leave immediately
        if (unformatted.contains("[YOUTUBE]", ignoreCase = false) ||
            unformatted.contains("[HELPER]", ignoreCase = false) ||
            unformatted.contains("[MOD]", ignoreCase = false) ||
            unformatted.contains("[ADMIN]", ignoreCase = false)) {
            ChatUtils.error("Staff detected! Leaving game...")
            best.bedwars.afk.bot.player.Movement.clearAll()
            best.bedwars.afk.bot.player.Mouse.stopTracking()
            TimeUtils.setTimeout({
                ChatUtils.sendAsPlayer("/l")
                TimeUtils.setTimeout({
                    bot.handleGameEnd()
                }, 2000)
            }, 100)
        }

        // Error handling
        if (unformatted.contains("Something went wrong", ignoreCase = true) ||
            unformatted.contains("Couldn't connect you", ignoreCase = true) ||
            unformatted.contains("Spam the command", ignoreCase = true)) {
            ChatUtils.info("Error detected, requeueing...")
            best.bedwars.afk.bot.player.Movement.clearAll()
            best.bedwars.afk.bot.player.Mouse.stopTracking()
            TimeUtils.setTimeout({
                bot.handleGameEnd()
            }, RandomUtils.randomIntInRange(6000, 8000))
        }

        // Limbo detection
        if (unformatted.contains("You are AFK. Move around to return from AFK.", ignoreCase = true) ||
            unformatted.contains("You were spawned in Limbo.", ignoreCase = true)) {
            ChatUtils.info("Detected in LIMBO. Restarting.")
            TimeUtils.setTimeout({
                ChatUtils.sendAsPlayer("/lobby")
                TimeUtils.setTimeout({
                    bot.handleGameEnd()
                }, 1000)
            }, 100)
        }

        // Disconnect detection
        if (unformatted.contains("A disconnect occured in your connection", ignoreCase = true)) {
            ChatUtils.info("Disconnect detected, requeueing...")
            TimeUtils.setTimeout({
                bot.handleGameEnd()
            }, 1000)
        }

        // Inventory full handling
        if (unformatted.contains("You didn't pick up any more", ignoreCase = true)) {
            ChatUtils.info("Inventory Full. Clearing extra items.")
            // Would implement inventory clearing here
        }
        
        // Detect when bot tries to break protected blocks
        if (unformatted.contains("You can't break blocks here", ignoreCase = true)) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Can't break blocks in this area")
            // Bot will naturally timeout and try alternative strategies
        }
        
        // Purchase tracking (filter out player messages with ":")
        if (!unformatted.contains(":")) {
            // Match both "You purchased" and "You purchase" for different server versions
            val isPurchaseMessage = unformatted.contains("You purchased", ignoreCase = true) || 
                                   unformatted.contains("You purchase", ignoreCase = true)
            
            // Wool purchase detected
            if (isPurchaseMessage && unformatted.contains("Wool", ignoreCase = true)) {
                ChatUtils.info("Wool purchase detected!")
                // Delay update to allow items to appear in inventory
                TimeUtils.setTimeout({
                    best.bedwars.afk.bot.ResourceTracker.update()
                }, 200)
            }
            
            // Wood planks purchase detected
            if (isPurchaseMessage && unformatted.contains("Wood", ignoreCase = true)) {
                ChatUtils.info("Wood planks purchase detected!")
                // Delay update to allow items to appear in inventory
                TimeUtils.setTimeout({
                    best.bedwars.afk.bot.ResourceTracker.update()
                }, 200)
            }
            
            // End Stone purchase detected
            if (isPurchaseMessage && unformatted.contains("End Stone", ignoreCase = true)) {
                ChatUtils.info("End Stone purchase detected!")
                // Delay update to allow items to appear in inventory
                TimeUtils.setTimeout({
                    best.bedwars.afk.bot.ResourceTracker.update()
                }, 200)
            }
            
            // Iron Armor purchase detected
            if (isPurchaseMessage && 
                (unformatted.contains("Iron Armor", ignoreCase = true) || 
                 unformatted.contains("Permanent Iron Armor", ignoreCase = true))) {
                ChatUtils.info("Iron Armor purchase detected!")
                // Delay update to allow armor to appear in inventory/equipment
                TimeUtils.setTimeout({
                    best.bedwars.afk.bot.ResourceTracker.update()
                }, 200)
            }
        }

        // Easter egg from original - you can customize this!
        // if (unformatted.contains("YourFavoritePlayer has joined", ignoreCase = true)) {
        //     TimeUtils.setTimeout({
        //         ChatUtils.sendAsPlayer("OMG YourFavoritePlayer IM A FAN!")
        //     }, 2000)
        // }
    }

    private fun handleGameStart() {
        ChatUtils.info("Game starting...")
        BedwarsAFK.bot?.handleGameStart()
    }
}

