package best.skywars.recorder

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.inventory.ContainerChest
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.BlockPos
import net.minecraft.util.ChatComponentText
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.sqrt

@Mod(
    modid = SkywarsRecorder.MODID,
    name = SkywarsRecorder.NAME,
    version = SkywarsRecorder.VERSION,
    clientSideOnly = true
)
class SkywarsRecorder {

    companion object {
        const val MODID = "skywarsrecorder"
        const val NAME = "Skywars Telemetry Recorder"
        const val VERSION = "1.0.0"

        val mc: Minecraft = Minecraft.getMinecraft()
        val gson: Gson = GsonBuilder().create()

        lateinit var toggleKey: KeyBinding
    }

    private var isRecording = false
    private var currentMatchId: String = ""
    private var currentWriter: BufferedWriter? = null
    private var recordingFile: File? = null
    private var tickCount = 0

    private val outputDir = File(mc.mcDataDir, "skywars_recordings")

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        MinecraftForge.EVENT_BUS.register(this)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        toggleKey = KeyBinding("Toggle Recording", Keyboard.KEY_P, "Skywars Recorder")
        ClientRegistry.registerKeyBinding(toggleKey)
    }

    private fun logChat(message: String) {
        mc.thePlayer?.addChatMessage(ChatComponentText("${EnumChatFormatting.AQUA}[SkywarsRecorder] ${EnumChatFormatting.RESET}$message"))
    }

    fun startRecording(trigger: String) {
        if (isRecording) return

        tickCount = 0
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        currentMatchId = "match_$timestamp"
        recordingFile = File(outputDir, "$currentMatchId.jsonl")

        try {
            currentWriter = BufferedWriter(FileWriter(recordingFile!!, true))
            isRecording = true

            val metaObj = JsonObject()
            metaObj.addProperty("event_type", "SESSION_START")
            metaObj.addProperty("match_id", currentMatchId)
            metaObj.addProperty("trigger", trigger)
            metaObj.addProperty("timestamp", System.currentTimeMillis())
            metaObj.addProperty("player_name", mc.thePlayer?.name ?: "Unknown")
            
            currentWriter?.write(gson.toJson(metaObj) + "\n")
            currentWriter?.flush()

            logChat("${EnumChatFormatting.GREEN}● Запись начата: ${EnumChatFormatting.YELLOW}$currentMatchId.jsonl ${EnumChatFormatting.GRAY}(Триггер: $trigger)")
        } catch (e: Exception) {
            logChat("${EnumChatFormatting.RED}Ошибка создания файла записи: ${e.message}")
            e.printStackTrace()
            isRecording = false
        }
    }

    fun stopRecording(reason: String) {
        if (!isRecording) return

        try {
            val endObj = JsonObject()
            endObj.addProperty("event_type", "SESSION_END")
            endObj.addProperty("match_id", currentMatchId)
            endObj.addProperty("reason", reason)
            endObj.addProperty("total_ticks", tickCount)
            endObj.addProperty("timestamp", System.currentTimeMillis())

            currentWriter?.write(gson.toJson(endObj) + "\n")
            currentWriter?.flush()
            currentWriter?.close()

            logChat("${EnumChatFormatting.RED}■ Запись завершена ${EnumChatFormatting.GRAY}($reason). ${EnumChatFormatting.GREEN}Всего тиков: $tickCount. Сохранено в skywars_recordings/$currentMatchId.jsonl")
        } catch (e: Exception) {
            logChat("${EnumChatFormatting.RED}Ошибка при закрытии файла записи: ${e.message}")
        } finally {
            currentWriter = null
            isRecording = false
            currentMatchId = ""
            tickCount = 0
        }
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (toggleKey.isPressed) {
            if (isRecording) {
                stopRecording("MANUAL_KEY_P")
            } else {
                startRecording("MANUAL_KEY_P")
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        val rawText = event.message.unformattedText
        val cleanText = event.message.formattedText

        // Log chat message if recording
        if (isRecording) {
            val chatObj = JsonObject()
            chatObj.addProperty("event_type", "CHAT_MESSAGE")
            chatObj.addProperty("tick", tickCount)
            chatObj.addProperty("timestamp", System.currentTimeMillis())
            chatObj.addProperty("text", rawText)
            
            writeEntry(chatObj)
        }

        // Auto-detection triggers
        if (rawText.contains("Cages open in", ignoreCase = true) || 
            rawText.contains("The game has started!", ignoreCase = true) ||
            rawText.contains("Cages opened!", ignoreCase = true)) {
            if (!isRecording) {
                startRecording("AUTO_MATCH_START")
            }
        }

        if (rawText.contains("Winner:", ignoreCase = true) ||
            rawText.contains("1st Killer -", ignoreCase = true) ||
            rawText.contains("Reward Summary", ignoreCase = true) ||
            rawText.contains("You died!", ignoreCase = true) ||
            rawText.contains("Game ends in", ignoreCase = true)) {
            if (isRecording) {
                stopRecording("AUTO_MATCH_END")
            }
        }
    }

    @SubscribeEvent
    fun onAttack(event: AttackEntityEvent) {
        if (!isRecording) return
        val target = event.target
        val attackObj = JsonObject()
        attackObj.addProperty("event_type", "ATTACK_ENTITY")
        attackObj.addProperty("tick", tickCount)
        attackObj.addProperty("timestamp", System.currentTimeMillis())
        attackObj.addProperty("target_id", target.entityId)
        attackObj.addProperty("target_name", target.name)
        attackObj.addProperty("distance", mc.thePlayer.getDistanceToEntity(target))
        writeEntry(attackObj)
    }

    @SubscribeEvent
    fun onGuiOpen(event: GuiOpenEvent) {
        if (!isRecording) return
        val gui = event.gui
        val guiObj = JsonObject()
        guiObj.addProperty("event_type", "GUI_STATE_CHANGE")
        guiObj.addProperty("tick", tickCount)
        guiObj.addProperty("timestamp", System.currentTimeMillis())
        guiObj.addProperty("gui_class", gui?.javaClass?.simpleName ?: "None")
        guiObj.addProperty("is_chest", gui is GuiChest)
        writeEntry(guiObj)
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        if (isRecording) {
            stopRecording("WORLD_UNLOAD")
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (!isRecording) return

        tickCount++

        val tickObj = JsonObject()
        tickObj.addProperty("event_type", "TICK")
        tickObj.addProperty("tick", tickCount)
        tickObj.addProperty("timestamp", System.currentTimeMillis())

        // Player Telemetry
        val pObj = JsonObject()
        pObj.addProperty("x", player.posX)
        pObj.addProperty("y", player.posY)
        pObj.addProperty("z", player.posZ)
        pObj.addProperty("motion_x", player.motionX)
        pObj.addProperty("motion_y", player.motionY)
        pObj.addProperty("motion_z", player.motionZ)
        pObj.addProperty("yaw", player.rotationYaw)
        pObj.addProperty("pitch", player.rotationPitch)
        pObj.addProperty("on_ground", player.onGround)
        pObj.addProperty("health", player.health)
        pObj.addProperty("hunger", player.foodStats.foodLevel)
        pObj.addProperty("sprinting", player.isSprinting)
        pObj.addProperty("sneaking", player.isSneaking)
        pObj.addProperty("held_item", player.heldItem?.displayName ?: "Empty")
        pObj.addProperty("held_item_id", player.heldItem?.item?.registryName ?: "minecraft:air")
        pObj.addProperty("active_slot", player.inventory.currentItem)
        tickObj.add("player", pObj)

        // Raw Key Inputs
        val kObj = JsonObject()
        kObj.addProperty("w", mc.gameSettings.keyBindForward.isKeyDown)
        kObj.addProperty("s", mc.gameSettings.keyBindBack.isKeyDown)
        kObj.addProperty("a", mc.gameSettings.keyBindLeft.isKeyDown)
        kObj.addProperty("d", mc.gameSettings.keyBindRight.isKeyDown)
        kObj.addProperty("jump", mc.gameSettings.keyBindJump.isKeyDown)
        kObj.addProperty("sneak", mc.gameSettings.keyBindSneak.isKeyDown)
        kObj.addProperty("sprint_key", mc.gameSettings.keyBindSprint.isKeyDown)
        kObj.addProperty("lmb_attack", mc.gameSettings.keyBindAttack.isKeyDown)
        kObj.addProperty("rmb_use", mc.gameSettings.keyBindUseItem.isKeyDown)
        tickObj.add("keys", kObj)

        // Chest Awareness (Scans every 5 ticks to optimize performance)
        if (tickCount % 5 == 0) {
            val chestArray = JsonArray()
            val tileEntities = world.loadedTileEntityList

            for (tile in tileEntities) {
                if (tile is TileEntityChest) {
                    val pos = tile.pos
                    val dist = player.getDistance(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)

                    // Scans chests within 30 blocks
                    if (dist <= 30.0) {
                        val cObj = JsonObject()
                        cObj.addProperty("x", pos.x)
                        cObj.addProperty("y", pos.y)
                        cObj.addProperty("z", pos.z)
                        cObj.addProperty("distance", dist)

                        // Check void / solid ground under the chest
                        var hasSolidGroundBelow = false
                        var groundBlockY = -1
                        var groundBlockName = "void"

                        for (y in (pos.y - 1) downTo 0) {
                            val checkPos = BlockPos(pos.x, y, pos.z)
                            val blockState = world.getBlockState(checkPos)
                            val block = blockState.block
                            if (block != Blocks.air && block.isCollidable) {
                                hasSolidGroundBelow = true
                                groundBlockY = y
                                groundBlockName = block.registryName ?: "unknown"
                                break
                            }
                        }

                        cObj.addProperty("has_solid_ground", hasSolidGroundBelow)
                        cObj.addProperty("ground_y", groundBlockY)
                        cObj.addProperty("ground_block", groundBlockName)

                        // Check obstacle/accessibility (surrounding blocks north, south, east, west)
                        val accessibleSides = JsonArray()
                        val sides = listOf(
                            Pair("North", pos.north()),
                            Pair("South", pos.south()),
                            Pair("East", pos.east()),
                            Pair("West", pos.west())
                        )
                        for ((name, sidePos) in sides) {
                            val b1 = world.getBlockState(sidePos).block
                            val b2 = world.getBlockState(sidePos.up()).block
                            val groundSide = world.getBlockState(sidePos.down()).block
                            // Walkable if 2 air blocks above and solid block below
                            val isWalkable = (b1 == Blocks.air) && (b2 == Blocks.air) && (groundSide != Blocks.air && groundSide.isCollidable)
                            if (isWalkable) {
                                accessibleSides.add(JsonPrimitive(name))
                            }
                        }
                        cObj.add("accessible_approach_sides", accessibleSides)

                        chestArray.add(cObj)
                    }
                }
            }
            tickObj.add("chests", chestArray)
        }

        // Enemy Awareness (players within 35 blocks)
        val enemyArray = JsonArray()
        for (other in world.playerEntities) {
            if (other != player && other is EntityPlayer) {
                val dist = player.getDistanceToEntity(other)
                if (dist <= 35.0) {
                    val eObj = JsonObject()
                    eObj.addProperty("name", other.name)
                    eObj.addProperty("x", other.posX)
                    eObj.addProperty("y", other.posY)
                    eObj.addProperty("z", other.posZ)
                    eObj.addProperty("distance", dist)
                    eObj.addProperty("health", other.health)
                    eObj.addProperty("held_item", other.heldItem?.displayName ?: "Empty")
                    enemyArray.add(eObj)
                }
            }
        }
        tickObj.add("nearby_enemies", enemyArray)

        // Chest container contents if a chest is currently open
        val screen = mc.currentScreen
        if (screen is GuiChest) {
            val container = screen.inventorySlots
            if (container is ContainerChest) {
                val chestInv = container.lowerChestInventory
                val slotArray = JsonArray()
                for (i in 0 until chestInv.sizeInventory) {
                    val stack = chestInv.getStackInSlot(i)
                    if (stack != null) {
                        val slotObj = JsonObject()
                        slotObj.addProperty("slot", i)
                        slotObj.addProperty("item", stack.item.registryName ?: "unknown")
                        slotObj.addProperty("name", stack.displayName)
                        slotObj.addProperty("count", stack.stackSize)
                        slotArray.add(slotObj)
                    }
                }
                tickObj.add("open_chest_contents", slotArray)
            }
        }

        writeEntry(tickObj)
    }

    private fun writeEntry(json: JsonObject) {
        try {
            currentWriter?.write(gson.toJson(json) + "\n")
            if (tickCount % 40 == 0) {
                currentWriter?.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
