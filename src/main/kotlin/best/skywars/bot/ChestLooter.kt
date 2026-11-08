package best.skywars.bot

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.inventory.ContainerChest
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object ChestLooter {

    private val mc = Minecraft.getMinecraft()

    val lootedChests = HashSet<BlockPos>()
    var targetChestPos: BlockPos? = null

    private var openTicks = 0
    private var lastLootTick = 0L

    fun reset() {
        lootedChests.clear()
        targetChestPos = null
        openTicks = 0
    }

    fun findNextChest(spawnPos: BlockPos): BlockPos? {
        val world = mc.theWorld ?: return null
        val player = mc.thePlayer ?: return null

        val islandChests = mutableListOf<TileEntityChest>()
        for (tile in world.loadedTileEntityList) {
            if (tile is TileEntityChest) {
                val pos = tile.pos
                if (lootedChests.contains(pos)) continue
                val distFromSpawn = spawnPos.distanceSq(pos)
                if (distFromSpawn <= 18.0 * 18.0) { // On spawn island
                    islandChests.add(tile)
                }
            }
        }

        if (islandChests.isEmpty()) return null

        // Sort by minimum |deltaY| first (same platform), then by distance to player
        islandChests.sortWith(Comparator { c1, c2 ->
            val dy1 = abs(c1.pos.y - player.posY)
            val dy2 = abs(c2.pos.y - player.posY)
            if (abs(dy1 - dy2) > 1.5) {
                dy1.compareTo(dy2)
            } else {
                val d1 = player.getDistanceSq(c1.pos)
                val d2 = player.getDistanceSq(c2.pos)
                d1.compareTo(d2)
            }
        })

        return islandChests.firstOrNull()?.pos
    }

    fun tickLooting(): Boolean {
        val player = mc.thePlayer ?: return false
        val screen = mc.currentScreen

        if (screen is GuiChest) {
            openTicks++
            val container = screen.inventorySlots
            if (container is ContainerChest) {
                val chestInv = container.lowerChestInventory
                val now = System.currentTimeMillis()

                // Fast shift-click loot delay ~50-80ms like the recorded player
                if (now - lastLootTick >= 65) {
                    var foundValuable = false
                    for (i in 0 until chestInv.sizeInventory) {
                        val stack = chestInv.getStackInSlot(i)
                        if (stack != null && ItemFilter.isValuable(stack)) {
                            mc.playerController.windowClick(
                                container.windowId,
                                i,
                                0,
                                1, // Shift-click
                                player
                            )
                            lastLootTick = now
                            foundValuable = true
                            break
                        }
                    }

                    // If no valuable items remain or timeout exceeded (25 ticks = 1.25s)
                    if (!foundValuable || openTicks > 25) {
                        if (targetChestPos != null) {
                            lootedChests.add(targetChestPos!!)
                            targetChestPos = null
                        }
                        player.closeScreen()
                        openTicks = 0
                        return true // Finished this chest
                    }
                }
            }
            return false
        }

        return false
    }

    fun lookAtBlock(pos: BlockPos) {
        val player = mc.thePlayer ?: return
        val dx = (pos.x + 0.5) - player.posX
        val dy = (pos.y + 0.5) - (player.posY + player.getEyeHeight())
        val dz = (pos.z + 0.5) - player.posZ
        val dist = sqrt(dx * dx + dz * dz)

        val targetYaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val targetPitch = (-Math.toDegrees(atan2(dy, dist))).toFloat()

        // Smooth rotation interpolation
        player.rotationYaw = interpolateAngle(player.rotationYaw, targetYaw, 0.35f)
        player.rotationPitch = interpolateAngle(player.rotationPitch, targetPitch, 0.35f)
    }

    private fun interpolateAngle(from: Float, to: Float, factor: Float): Float {
        var diff = (to - from) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return from + diff * factor
    }
}
