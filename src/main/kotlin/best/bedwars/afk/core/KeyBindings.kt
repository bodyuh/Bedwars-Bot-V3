package best.bedwars.afk.core

import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

object KeyBindings {
    
    lateinit var toggleBotKeyBinding: KeyBinding
    
    fun register() {
        toggleBotKeyBinding = KeyBinding(
            "Toggle Keystrokes",
            Keyboard.KEY_R,
            "Keystrokes Mod"
        )
        
        ClientRegistry.registerKeyBinding(toggleBotKeyBinding)
    }
}


