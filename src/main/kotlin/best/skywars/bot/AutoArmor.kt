package best.skywars.bot

import net.minecraft.client.Minecraft
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemStack

object AutoArmor {

    private val mc = Minecraft.getMinecraft()
    private var lastEquipTime = 0L

    fun tick() {
        val player = mc.thePlayer ?: return
        if (mc.currentScreen != null) return // don't mess up when chest is open

        val now = System.currentTimeMillis()
        if (now - lastEquipTime < 150) return // human-like delay between swaps

        // Armor types: 0 = helmet, 1 = chestplate, 2 = leggings, 3 = boots
        for (armorType in 0..3) {
            val currentArmorStack = player.inventory.armorInventory[3 - armorType]
            val currentScore = ItemFilter.getArmorScore(currentArmorStack)

            var bestSlot = -1
            var bestScore = currentScore

            // Scan player inventory (slots 9 to 44)
            for (slot in 9..44) {
                val stack = player.inventoryContainer.getSlot(slot).stack
                if (stack != null && stack.item is ItemArmor) {
                    val armor = stack.item as ItemArmor
                    if (armor.armorType == armorType) {
                        val score = ItemFilter.getArmorScore(stack)
                        if (score > bestScore) {
                            bestScore = score
                            bestSlot = slot
                        }
                    }
                }
            }

            if (bestSlot != -1) {
                // Equip new armor piece: shift-click directly equips it
                mc.playerController.windowClick(
                    player.inventoryContainer.windowId,
                    bestSlot,
                    0,
                    1, // Shift-click
                    player
                )
                lastEquipTime = now
                return
            }
        }
    }
}
