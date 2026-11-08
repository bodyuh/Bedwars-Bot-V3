package best.skywars.bot

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.item.*

object ItemFilter {

    /**
     * Smart loot check: compares chest item against current inventory
     * Never picks up junk or items that are worse than what we already have!
     */
    fun shouldLoot(chestStack: ItemStack?, player: EntityPlayer?): Boolean {
        if (chestStack == null) return false
        val item = chestStack.item

        if (player == null) return isValuableGeneric(chestStack)

        // 1. Swords: only loot if strictly better than the best sword in our inventory!
        if (item is ItemSword) {
            val chestSwordScore = getSwordScore(chestStack)
            val currentBestScore = getBestSwordScoreInInventory(player)
            // Knight kit Golden Sword with Sharp II has score 6.5
            // Stone Sword with Sharp I has score 6.25 -> won't be picked up!
            return chestSwordScore > currentBestScore
        }

        // 2. Armor: only loot if better than current worn/inventory armor of this type
        if (item is ItemArmor) {
            val chestArmorScore = getArmorScore(chestStack)
            val currentBestArmorScore = getBestArmorScoreForType(player, item.armorType)
            return chestArmorScore > currentBestArmorScore
        }

        // 3. Blocks: stop picking up blocks if we already have 64 or more
        if (item is ItemBlock) {
            val name = item.block.registryName?.lowercase() ?: ""
            val isSolid = name.contains("planks") || name.contains("stone") || name.contains("wood") || name.contains("cobblestone") || name.contains("tnt")
            if (!isSolid) return false
            if (name.contains("tnt")) return true // TNT is always fun/useful

            val totalBlocks = countBlocksInInventory(player)
            return totalBlocks < 64
        }

        // 4. Utility: Fishing Rod (only need 1)
        if (item is ItemFishingRod) {
            return !hasItemType(player, ItemFishingRod::class.java)
        }

        // 5. Tools (Pickaxe / Axe: only need 1 good one)
        if (item is ItemPickaxe) {
            return !hasItemType(player, ItemPickaxe::class.java)
        }
        if (item is ItemAxe) {
            return !hasItemType(player, ItemAxe::class.java)
        }

        // 6. Buckets (only need 1 water and 1 lava)
        if (item == Items.water_bucket) {
            return !hasExactItem(player, Items.water_bucket)
        }
        if (item == Items.lava_bucket) {
            return !hasExactItem(player, Items.lava_bucket)
        }

        // 7. Projectiles (Snowballs / Eggs: max 32)
        if (item == Items.snowball || item == Items.egg) {
            return countProjectiles(player) < 32
        }

        // 8. Bow & Arrows
        if (item is ItemBow) {
            return !hasItemType(player, ItemBow::class.java)
        }
        if (item == Items.arrow) {
            return true
        }

        // 9. Food & Potions (Golden apples, heads, speed/heal potions: ALWAYS valuable)
        if (item is ItemAppleGold || item is ItemPotion) {
            return true
        }

        // JUNK: sticks, seeds, saplings, feathers, string, dirt, raw meat, leather etc. -> IGNORE
        return false
    }

    private fun isValuableGeneric(stack: ItemStack): Boolean {
        val item = stack.item
        return item is ItemSword || item is ItemArmor || item is ItemFishingRod || item is ItemAppleGold || item is ItemPotion
    }

    fun getSwordScore(stack: ItemStack?): Float {
        if (stack == null || stack.item !is ItemSword) return 0f
        val sword = stack.item as ItemSword
        var damage = sword.damageVsEntity

        // Check sharpness enchantment
        val enchantList = stack.enchantmentTagList
        if (enchantList != null) {
            for (i in 0 until enchantList.tagCount()) {
                val tag = enchantList.getCompoundTagAt(i)
                if (tag.getShort("id").toInt() == 16) { // Sharpness
                    val lvl = tag.getShort("lvl").toInt()
                    // 1.8.9 Sharpness adds 1.25 damage per level
                    damage += lvl * 1.25f
                }
                if (tag.getShort("id").toInt() == 20) { // Fire Aspect
                    damage += 0.75f
                }
            }
        }
        return damage
    }

    fun getArmorScore(stack: ItemStack?): Float {
        if (stack == null || stack.item !is ItemArmor) return 0f
        val armor = stack.item as ItemArmor
        var score = armor.damageReduceAmount.toFloat()

        val enchantList = stack.enchantmentTagList
        if (enchantList != null) {
            for (i in 0 until enchantList.tagCount()) {
                val tag = enchantList.getCompoundTagAt(i)
                if (tag.getShort("id").toInt() == 0) { // Protection
                    score += tag.getShort("lvl").toInt() * 1.0f
                }
            }
        }
        return score
    }

    private fun getBestSwordScoreInInventory(player: EntityPlayer): Float {
        var best = 0f
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i]
            if (stack != null && stack.item is ItemSword) {
                val score = getSwordScore(stack)
                if (score > best) best = score
            }
        }
        return best
    }

    private fun getBestArmorScoreForType(player: EntityPlayer, armorType: Int): Float {
        var best = 0f
        // Check currently worn armor piece (3 - armorType in armorInventory: 0=helm, 1=chest, 2=legs, 3=boots)
        val worn = player.inventory.armorInventory[3 - armorType]
        if (worn != null && worn.item is ItemArmor) {
            best = getArmorScore(worn)
        }
        // Also check if better armor is sitting in main inventory
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i]
            if (stack != null && stack.item is ItemArmor) {
                val a = stack.item as ItemArmor
                if (a.armorType == armorType) {
                    val score = getArmorScore(stack)
                    if (score > best) best = score
                }
            }
        }
        return best
    }

    private fun countBlocksInInventory(player: EntityPlayer): Int {
        var count = 0
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i]
            if (stack != null && stack.item is ItemBlock) {
                count += stack.stackSize
            }
        }
        return count
    }

    private fun countProjectiles(player: EntityPlayer): Int {
        var count = 0
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i]
            if (stack != null && (stack.item == Items.snowball || stack.item == Items.egg)) {
                count += stack.stackSize
            }
        }
        return count
    }

    private fun hasItemType(player: EntityPlayer, itemClass: Class<*>): Boolean {
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i]
            if (stack != null && itemClass.isInstance(stack.item)) {
                return true
            }
        }
        return false
    }

    private fun hasExactItem(player: EntityPlayer, item: Item): Boolean {
        for (i in 0 until player.inventory.mainInventory.size) {
            val stack = player.inventory.mainInventory[i]
            if (stack != null && stack.item == item) {
                return true
            }
        }
        return false
    }
}
