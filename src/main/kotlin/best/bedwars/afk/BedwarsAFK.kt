package best.bedwars.afk

import best.bedwars.afk.bot.BedwarsBot
import best.bedwars.afk.bot.pathfinding.PathRenderer
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.bot.player.LobbyMovement
import best.bedwars.afk.core.KeyBindings
import best.bedwars.afk.events.ChatEventHandler
import best.bedwars.afk.events.CommandHandler
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.client.Minecraft
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

@Mod(
    modid = BedwarsAFK.MODID,
    name = BedwarsAFK.NAME,
    version = BedwarsAFK.VERSION,
    clientSideOnly = true
)
class BedwarsAFK {
    companion object {
        const val MODID = "keystrokes"
        const val NAME = "Keystrokes Mod"
        const val VERSION = "1.0.0"

        @JvmStatic
        val mc: Minecraft = Minecraft.getMinecraft()
        
        @JvmStatic
        var bot: BedwarsBot? = null
    }
    
    // Version message tracking
    private var versionMessageShown = false

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        println("========================================")
        println("[BedwarsAFK] DIAGNOSTIC VERSION LOADING")
        println("$NAME v$VERSION initializing...")
        println("========================================")
        
        // Initialize bot
        bot = BedwarsBot()
        
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(this) // Register self for version message
        MinecraftForge.EVENT_BUS.register(bot)
        MinecraftForge.EVENT_BUS.register(ChatEventHandler())
        MinecraftForge.EVENT_BUS.register(CommandHandler)
        MinecraftForge.EVENT_BUS.register(Mouse)
        MinecraftForge.EVENT_BUS.register(LobbyMovement)
        MinecraftForge.EVENT_BUS.register(PathRenderer)
        MinecraftForge.EVENT_BUS.register(best.bedwars.afk.bot.ResourceTracker)
        MinecraftForge.EVENT_BUS.register(best.bedwars.afk.bot.BedDefender)
        MinecraftForge.EVENT_BUS.register(best.bedwars.afk.events.KeybindHandler)
        MinecraftForge.EVENT_BUS.register(best.bedwars.afk.bot.pathfinding.BridgingPathfinder)
        MinecraftForge.EVENT_BUS.register(best.bedwars.afk.bot.pathfinding.EagleBridge)
        
        println("$NAME initialized successfully!")
    }
    
    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        // Register keybindings in post-init to ensure all systems are ready
        KeyBindings.register()
    }
    
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        // Version message removed per user request
    }
}


