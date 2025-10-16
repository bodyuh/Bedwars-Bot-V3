package best.bedwars.afk.bot.player

import best.bedwars.afk.BedwarsAFK
import net.minecraft.init.Items

object Inventory {

    /**
     * Sets the players current item to the item passed
     */
    fun setInvItem(item: String): Boolean {
        val _item = item.lowercase()
        if (BedwarsAFK.mc.thePlayer != null && BedwarsAFK.mc.thePlayer.inventory != null) {
            for (i in 0..8) {
                val stack = BedwarsAFK.mc.thePlayer.inventory.getStackInSlot(i)
                if (stack != null && stack.unlocalizedName.lowercase().contains(_item)) {
                    BedwarsAFK.mc.thePlayer.inventory.currentItem = i
                    return true
                }
            }
        }
        return false
    }

    /**
     * Get iron ingot count in inventory
     */
    fun getIronCount(): Int {
        if (BedwarsAFK.mc.thePlayer != null) {
            var count = 0
            for (itemStack in BedwarsAFK.mc.thePlayer.inventory.mainInventory) {
                if (itemStack != null && itemStack.item == Items.iron_ingot) {
                    count += itemStack.stackSize
                }
            }
            return count
        }
        return 0
    }
    
    /**
     * Get gold ingot count in inventory
     */
    fun getGoldCount(): Int {
        if (BedwarsAFK.mc.thePlayer != null) {
            var count = 0
            for (itemStack in BedwarsAFK.mc.thePlayer.inventory.mainInventory) {
                if (itemStack != null && itemStack.item == Items.gold_ingot) {
                    count += itemStack.stackSize
                }
            }
            return count
        }
        return 0
    }

    /**
     * Get item count by name
     */
    fun getItemCount(itemName: String): Int {
        if (BedwarsAFK.mc.thePlayer != null) {
            var count = 0
            for (itemStack in BedwarsAFK.mc.thePlayer.inventory.mainInventory) {
                if (itemStack != null && itemStack.unlocalizedName.lowercase().contains(itemName.lowercase())) {
                    count += itemStack.stackSize
                }
            }
            return count
        }
        return 0
    }

    /**
     * Check if inventory slot has item
     */
    fun getSlotItemCount(slot: Int): Int {
        if (BedwarsAFK.mc.thePlayer != null && BedwarsAFK.mc.thePlayer.inventory != null) {
            val stack = BedwarsAFK.mc.thePlayer.inventory.getStackInSlot(slot)
            return stack?.stackSize ?: 0
        }
        return 0
    }

    /**
     * Set inventory slot
     */
    fun setInvSlot(slot: Int) {
        if (BedwarsAFK.mc.thePlayer != null && BedwarsAFK.mc.thePlayer.inventory != null) {
            BedwarsAFK.mc.thePlayer.inventory.currentItem = slot
        }
    }

    /**
     * Checks if the player has this item
     */
    fun hasItem(item: String): Boolean {
        val _item = item.lowercase()
        if (BedwarsAFK.mc.thePlayer != null) {
            for (itemStack in BedwarsAFK.mc.thePlayer.inventory.mainInventory) {
                if (itemStack != null && itemStack.unlocalizedName.lowercase().contains(_item)) {
                    return true
                }
            }
        }
        return false
    }
}


