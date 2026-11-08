package best.skywars.bot

import net.minecraft.init.Items
import net.minecraft.item.*

object ItemFilter {

    fun isValuable(stack: ItemStack?): Boolean {
        if (stack == null) return false
        val item = stack.item

        // Swords
        if (item is ItemSword) return true

        // Armor (skip basic leather if not needed, but keep iron/diamond/gold/chain)
        if (item is ItemArmor) {
            val mat = item.armorMaterial
            return mat == ItemArmor.ArmorMaterial.DIAMOND ||
                   mat == ItemArmor.ArmorMaterial.IRON ||
                   mat == ItemArmor.ArmorMaterial.CHAIN ||
                   mat == ItemArmor.ArmorMaterial.GOLD ||
                   (mat == ItemArmor.ArmorMaterial.LEATHER && item.armorType == 1) // chestplate
        }

        // Projectiles & Rod
        if (item is ItemFishingRod) return true
        if (item is ItemBow) return true
        if (item == Items.arrow) return true
        if (item == Items.snowball || item == Items.egg) return true

        // Food & Health
        if (item is ItemAppleGold) return true
        if (item is ItemPotion) return true

        // Buckets
        if (item == Items.water_bucket || item == Items.lava_bucket) return true

        // Solid Building Blocks
        if (item is ItemBlock) {
            val name = item.block.registryName?.lowercase() ?: ""
            if (name.contains("planks") || name.contains("stone") || name.contains("wood") || name.contains("cobblestone") || name.contains("tnt")) {
                return true
            }
        }

        // Tools
        if (item is ItemPickaxe || item is ItemAxe) return true

        return false
    }

    fun getSwordScore(stack: ItemStack?): Float {
        if (stack == null || stack.item !is ItemSword) return 0f
        val sword = stack.item as ItemSword
        var damage = sword.damageVsEntity
        // Check sharpness if present
        val enchantList = stack.enchantmentTagList
        if (enchantList != null) {
            for (i in 0 until enchantList.tagCount()) {
                val tag = enchantList.getCompoundTagAt(i)
                if (tag.getShort("id").toInt() == 16) { // Sharpness
                    val lvl = tag.getShort("lvl").toInt()
                    damage += lvl * 1.25f
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
}
