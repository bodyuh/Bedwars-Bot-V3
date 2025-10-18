package best.bedwars.afk.events

import best.bedwars.afk.bot.detector.GeneratorDetector
import best.bedwars.afk.bot.pathfinding.BridgingPathfinder
import best.bedwars.afk.core.KeyBindings
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.init.Blocks
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent

/**
 * Handles custom keybind events
 */
object KeybindHandler {
    
    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        // Test bridging keybind (B key)
        if (KeyBindings.testBridgingKeyBinding.isPressed) {
            handleTestBridging()
        }
        
        // Generator detector keybind (H key)
        if (KeyBindings.generatorDetectorKeyBinding.isPressed) {
            ChatUtils.info("§e[DEBUG] H key pressed, toggling generator detector...")
            GeneratorDetector.toggle()
        }
    }
    
    /**
     * Handle test bridging command
     */
    private fun handleTestBridging() {
        if (BridgingPathfinder.isActive()) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Stopping bridging test...")
            BridgingPathfinder.stop()
        } else {
            ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
            ChatUtils.info("${EnumChatFormatting.GOLD}⛏ BRIDGING TEST MODE ⛏")
            ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
            ChatUtils.info("${EnumChatFormatting.YELLOW}Searching for nearest Diamond Block...")
            ChatUtils.info("${EnumChatFormatting.GRAY}Press [B] again to cancel")
            
            // Find and bridge to nearest diamond block
            BridgingPathfinder.findAndBridgeToBlock(Blocks.diamond_block) {
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Test complete!")
                ChatUtils.info("${EnumChatFormatting.GRAY}Press [B] to test again")
            }
        }
    }
}







