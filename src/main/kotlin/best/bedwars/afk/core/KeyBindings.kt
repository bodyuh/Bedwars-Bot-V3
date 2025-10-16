package best.bedwars.afk.core

import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

object KeyBindings {
    
    lateinit var toggleBotKeyBinding: KeyBinding
    lateinit var testBridgingKeyBinding: KeyBinding
    
    fun register() {
        toggleBotKeyBinding = KeyBinding(
            "Toggle Keystrokes",
            Keyboard.KEY_R,
            "Keystrokes Mod"
        )
        
        testBridgingKeyBinding = KeyBinding(
            "Test Bridging to Diamond",
            Keyboard.KEY_B,
            "Bedwars Bot"
        )
        
        ClientRegistry.registerKeyBinding(toggleBotKeyBinding)
        ClientRegistry.registerKeyBinding(testBridgingKeyBinding)
    }
}


