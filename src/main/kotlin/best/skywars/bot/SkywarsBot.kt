package best.skywars.bot

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.settings.KeyBinding
import net.minecraft.util.BlockPos
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import java.util.regex.Pattern

enum class BotState {
    LOBBY,
    IN_CAGE,
    LOOTING_CHESTS,
    BRIDGING,
    COMBAT
}

@Mod(
    modid = SkywarsBot.MODID,
    name = SkywarsBot.NAME,
    version = SkywarsBot.VERSION,
    clientSideOnly = true
)
class SkywarsBot {

    companion object {
        const val MODID = "skywarsbot"
        const val NAME = "Skywars Autoplay Bot"
        const val VERSION = "1.0.0"

        val mc: Minecraft = Minecraft.getMinecraft()
        lateinit var toggleKey: KeyBinding

        var isEnabled = true
        var state = BotState.LOBBY
    }

    private var spawnPos: BlockPos? = null
    private var targetBridgePos: BlockPos? = null
    private var lastStateChange = 0L

    // Regex for kill tracking to detect Hypixel Bulldozer strength
    private val killPattern = Pattern.compile("(.+) (?:was killed by|died to|thrown into the void by|was knocked into the void by|was blasted into the void by) (.+)\\.")

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        MinecraftForge.EVENT_BUS.register(this)
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        toggleKey = KeyBinding("Toggle SkyWars Bot", Keyboard.KEY_R, "SkyWars Bot")
        ClientRegistry.registerKeyBinding(toggleKey)
    }

    private fun logChat(msg: String) {
        mc.thePlayer?.addChatMessage(ChatComponentText("${EnumChatFormatting.GOLD}[SW-Bot] ${EnumChatFormatting.RESET}$msg"))
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (toggleKey.isPressed) {
            isEnabled = !isEnabled
            if (isEnabled) {
                logChat("${EnumChatFormatting.GREEN}ВКЛЮЧЕН (Autoplay активен)")
            } else {
                logChat("${EnumChatFormatting.RED}ВЫКЛЮЧЕН")
                stopAllMovement()
                state = BotState.LOBBY
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        val raw = event.message.unformattedText

        // 1. Detect match start
        if (raw.contains("Cages open in", ignoreCase = true)) {
            state = BotState.IN_CAGE
            val player = mc.thePlayer
            if (player != null) {
                spawnPos = BlockPos(player.posX, player.posY, player.posZ)
            }
            ChestLooter.reset()
            CombatEngine.reset()
        }

        if (raw.contains("Cages opened!", ignoreCase = true) || raw.contains("The game has started!", ignoreCase = true)) {
            state = BotState.LOOTING_CHESTS
            val player = mc.thePlayer
            if (player != null) {
                spawnPos = BlockPos(player.posX, player.posY, player.posZ)
            }
            logChat("${EnumChatFormatting.AQUA}Клетки открылись! Старт лута сундуков.")
        }

        // 2. Track Kills for Strength (Bulldozer) perk detection
        val matcher = killPattern.matcher(raw)
        if (matcher.find()) {
            val killerRaw = matcher.group(2).trim()
            CombatEngine.onPlayerKill(killerRaw)
        }

        // 3. Match End -> Auto Requeue
        if (raw.contains("Winner:", ignoreCase = true) ||
            raw.contains("Reward Summary", ignoreCase = true) ||
            raw.contains("You died!", ignoreCase = true) ||
            raw.contains("Game ends in", ignoreCase = true)) {
            
            stopAllMovement()
            state = BotState.LOBBY
            logChat("${EnumChatFormatting.YELLOW}Катка завершена. Авто-реквей через 2.5 сек...")

            TimeHelper.setTimeout({
                if (isEnabled) {
                    mc.thePlayer?.sendChatMessage("/play skywars_solo_normal")
                }
            }, 2500)
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (!isEnabled) return

        // Auto Armor runs constantly in background
        AutoArmor.tick()

        when (state) {
            BotState.LOBBY -> {
                // Idle in lobby or waiting
            }

            BotState.IN_CAGE -> {
                // Wait for cage to drop
                stopAllMovement()
            }

            BotState.LOOTING_CHESTS -> {
                val origin = spawnPos ?: BlockPos(player.posX, player.posY, player.posZ)

                // Currently in a chest GUI
                if (mc.currentScreen != null) {
                    val finished = ChestLooter.tickLooting()
                    if (finished) {
                        // Check if we need more chests
                        val nextChest = ChestLooter.findNextChest(origin)
                        if (nextChest == null || ChestLooter.lootedChests.size >= 3) {
                            state = BotState.COMBAT
                            logChat("${EnumChatFormatting.GREEN}Лут завершен. Переход к поиску врагов!")
                        }
                    }
                    return
                }

                // Find next chest to loot
                var targetChest = ChestLooter.targetChestPos
                if (targetChest == null) {
                    targetChest = ChestLooter.findNextChest(origin)
                    ChestLooter.targetChestPos = targetChest
                }

                if (targetChest != null) {
                    val dist = player.getDistance(targetChest.x + 0.5, targetChest.y.toDouble(), targetChest.z + 0.5)
                    ChestLooter.lookAtBlock(targetChest)

                    if (dist > 3.0) {
                        // Walk to chest
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, true)
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true)
                    } else {
                        // In reach: stop and right click to open
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, false)
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, true)
                        TimeHelper.setTimeout({
                            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
                        }, 50)
                    }
                } else {
                    // No more chests found
                    state = BotState.COMBAT
                    logChat("${EnumChatFormatting.GREEN}Сундуки острова залутаны. Ищу соперников!")
                }
            }

            BotState.BRIDGING -> {
                val target = targetBridgePos
                if (target != null) {
                    val dist = player.getDistance(target.x + 0.5, target.y.toDouble(), target.z + 0.5)
                    if (dist > 2.5) {
                        SkyBridge.tickBridge(target)
                    } else {
                        SkyBridge.stopBridge()
                        state = BotState.COMBAT
                    }
                } else {
                    SkyBridge.stopBridge()
                    state = BotState.COMBAT
                }
            }

            BotState.COMBAT -> {
                val nearestEnemy = CombatEngine.findNearestEnemy()
                if (nearestEnemy != null) {
                    val dist = player.getDistanceToEntity(nearestEnemy)

                    // Check if enemy is across a void gap (> 12 blocks) and we need to bridge
                    val feetPos = BlockPos(player.posX, player.posY - 1, player.posZ)
                    val groundAhead = world.getBlockState(feetPos).block

                    if (dist > 14.0f && SkyBridge.findHotbarBlocks() != -1) {
                        // Need bridge to reach them
                        targetBridgePos = BlockPos(nearestEnemy.posX, nearestEnemy.posY, nearestEnemy.posZ)
                        state = BotState.BRIDGING
                        return
                    }

                    // Direct engagement via CombatEngine
                    CombatEngine.tickCombat()
                } else {
                    // No enemies nearby: stop moving
                    stopAllMovement()
                }
            }
        }
    }

    private fun stopAllMovement() {
        val gs = mc.gameSettings ?: return
        KeyBinding.setKeyBindState(gs.keyBindForward.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindBack.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindLeft.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindRight.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindJump.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindSneak.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindSprint.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindUseItem.keyCode, false)
        KeyBinding.setKeyBindState(gs.keyBindAttack.keyCode, false)
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return
        val font = mc.fontRendererObj ?: return

        val statusColor = if (isEnabled) EnumChatFormatting.GREEN else EnumChatFormatting.RED
        val statusText = "$statusColor[SW-BOT: ${if (isEnabled) "ON" else "OFF"}] ${EnumChatFormatting.WHITE}State: $state"
        font.drawStringWithShadow(statusText, 4f, 4f, 0xFFFFFF)

        val target = CombatEngine.currentTarget
        if (target != null) {
            val dist = mc.thePlayer?.getDistanceToEntity(target) ?: 0f
            val targetInfo = "${EnumChatFormatting.YELLOW}Target: ${target.name} (${String.format("%.1f", dist)}m)"
            font.drawStringWithShadow(targetInfo, 4f, 16f, 0xFFFFFF)

            if (CombatEngine.hasStrength(target)) {
                val rem = CombatEngine.getStrengthRemainingSec(target)
                val alert = "${EnumChatFormatting.RED}⚠ ВРАГ ПОД СИЛОЙ (${String.format("%.1f", rem)}s) - ДЕРЖУ ДИСТАНЦИЮ!"
                font.drawStringWithShadow(alert, 4f, 28f, 0xFF0000)
            }
        }
    }
}
