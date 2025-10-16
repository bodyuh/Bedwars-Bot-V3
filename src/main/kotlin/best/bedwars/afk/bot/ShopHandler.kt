package best.bedwars.afk.bot

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumChatFormatting
import java.util.*

/**
 * Handles shopping in the Bedwars shop
 */
object ShopHandler {
    
    /**
     * Execute shopping list when shop is open
     */
    fun executeShopping(onComplete: () -> Unit) {
        val currentScreen = BedwarsAFK.mc.currentScreen
        
        if (currentScreen !is GuiChest) {
            ChatUtils.error("Shop GUI not open!")
            onComplete()
            return
        }
        
        // Update resources before shopping
        ResourceTracker.update()
        
        // Check if we actually need to buy anything
        val needArmor = ResourceTracker.shouldBuyIronArmor()
        val needWool = ResourceTracker.shouldBuyWool()
        val needWood = ResourceTracker.shouldBuyWoodPlanks()
        val needEndStone = ResourceTracker.shouldBuyEndStone()
        
        if (!needArmor && !needWool && !needWood && !needEndStone) {
            ChatUtils.info("${EnumChatFormatting.YELLOW}Nothing to buy, closing shop...")
            
            // Close GUI and return
            BedwarsAFK.mc.addScheduledTask {
                BedwarsAFK.mc.thePlayer?.closeScreen()
                BedwarsAFK.mc.displayGuiScreen(null)
            }
            
            TimeUtils.setTimeout({
                onComplete()
            }, 500)
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}Starting shopping...")
        
        // Purchase items in priority order
        var purchaseDelay = 0
        
        // Priority 1: Building blocks for bed defense
        if (needWood) {
            TimeUtils.setTimeout({
                buyWoodPlanks(currentScreen)
            }, purchaseDelay)
            purchaseDelay += 500
        }
        
        if (needEndStone) {
            TimeUtils.setTimeout({
                buyEndStone(currentScreen)
            }, purchaseDelay)
            purchaseDelay += 500
        }
        
        // Priority 2: Wool
        if (needWool) {
            val purchases = ResourceTracker.getWoolPurchasesAvailable()
            for (i in 0 until purchases) {
                TimeUtils.setTimeout({
                    buyWool(currentScreen)
                }, purchaseDelay)
                purchaseDelay += 500
            }
        }
        
        // Priority 3: Iron Armor
        if (needArmor) {
            TimeUtils.setTimeout({
                buyIronArmor(currentScreen)
            }, purchaseDelay)
            purchaseDelay += 500
        }
        
        // Close shop and complete (must run on main thread)
        TimeUtils.setTimeout({
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Shopping complete!")
            
            // Schedule GUI close on main thread
            BedwarsAFK.mc.addScheduledTask {
                BedwarsAFK.mc.thePlayer?.closeScreen()
                BedwarsAFK.mc.displayGuiScreen(null)
            }
            
            TimeUtils.setTimeout({
                onComplete()
            }, 500) // Increased delay to ensure GUI closes properly
        }, purchaseDelay + 300)
    }
    
    /**
     * Buy iron armor (iron boots) from shop
     */
    private fun buyIronArmor(gui: GuiChest) {
        try {
            // Access the shop inventory through the container
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            // Find iron boots in the shop inventory (upper chest slots)
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9 && stack.item == Items.iron_boots) {
                    ChatUtils.info("${EnumChatFormatting.GREEN}Buying Iron Armor...")
                    
                    // Click the iron boots slot
                    BedwarsAFK.mc.playerController.windowClick(
                        container.windowId,
                        slot.slotNumber,
                        0, // Left click
                        0, // Normal click mode
                        BedwarsAFK.mc.thePlayer
                    )
                    return
                }
            }
            
            ChatUtils.error("Iron Armor not found in shop!")
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy iron armor: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Buy wool from shop
     */
    private fun buyWool(gui: GuiChest) {
        try {
            // Access the shop inventory through the container
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            // Find wool in the shop inventory (upper chest slots)
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9) {
                    // Debug: print item info
                    val itemName = stack.item.unlocalizedName
                    val displayName = stack.displayName
                    
                    // Check if it's wool (check display name, unlocalized name, and item class)
                    if (isWoolItem(stack)) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}Buying Wool (16 blocks)...")
                        ChatUtils.info("${EnumChatFormatting.GRAY}Found: $displayName")
                        
                        // Click the wool slot
                        BedwarsAFK.mc.playerController.windowClick(
                            container.windowId,
                            slot.slotNumber,
                            0, // Left click
                            0, // Normal click mode
                            BedwarsAFK.mc.thePlayer
                        )
                        return
                    }
                }
            }
            
            // Debug: list all items in shop (excluding top row)
            ChatUtils.error("Wool not found in shop! Items:")
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9) {
                    ChatUtils.info("${EnumChatFormatting.GRAY}Slot ${slot.slotNumber}: ${stack.displayName} (${stack.item.unlocalizedName})")
                }
            }
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy wool: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Check if an item stack is wool
     */
    private fun isWoolItem(stack: ItemStack): Boolean {
        val itemName = stack.item.unlocalizedName.lowercase()
        val displayName = stack.displayName.lowercase()
        
        // Check unlocalized name, display name, and item type
        return itemName.contains("wool") || 
               displayName.contains("wool") ||
               stack.item.registryName?.contains("wool") == true
    }
    
    /**
     * Buy pickaxe from shop
     */
    private fun buyPickaxe(gui: GuiChest, tier: Int) {
        try {
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            val tierName = when (tier) {
                1 -> "wood"
                2 -> "stone"
                3 -> "iron"
                4 -> "diamond" // Gold tier
                else -> return
            }
            
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9) {
                    val itemName = stack.item.unlocalizedName.lowercase()
                    val displayName = stack.displayName.lowercase()
                    
                    if (itemName.contains("pickaxe") && (itemName.contains(tierName) || displayName.contains(tierName))) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}Buying ${displayName}...")
                        
                        BedwarsAFK.mc.playerController.windowClick(
                            container.windowId,
                            slot.slotNumber,
                            0,
                            0,
                            BedwarsAFK.mc.thePlayer
                        )
                        return
                    }
                }
            }
            
            ChatUtils.error("Pickaxe (tier $tier) not found in shop!")
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy pickaxe: ${e.message}")
        }
    }
    
    /**
     * Buy axe from shop
     */
    private fun buyAxe(gui: GuiChest, tier: Int) {
        try {
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            val tierName = when (tier) {
                1 -> "wood"
                2 -> "stone"
                3 -> "iron"
                4 -> "diamond" // Gold tier
                else -> return
            }
            
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9) {
                    val itemName = stack.item.unlocalizedName.lowercase()
                    val displayName = stack.displayName.lowercase()
                    
                    if (itemName.contains("axe") && (itemName.contains(tierName) || displayName.contains(tierName))) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}Buying ${displayName}...")
                        
                        BedwarsAFK.mc.playerController.windowClick(
                            container.windowId,
                            slot.slotNumber,
                            0,
                            0,
                            BedwarsAFK.mc.thePlayer
                        )
                        return
                    }
                }
            }
            
            ChatUtils.error("Axe (tier $tier) not found in shop!")
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy axe: ${e.message}")
        }
    }
    
    /**
     * Buy shears from shop
     */
    private fun buyShears(gui: GuiChest) {
        try {
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9 && stack.item == Items.shears) {
                    ChatUtils.info("${EnumChatFormatting.GREEN}Buying Shears...")
                    
                    BedwarsAFK.mc.playerController.windowClick(
                        container.windowId,
                        slot.slotNumber,
                        0,
                        0,
                        BedwarsAFK.mc.thePlayer
                    )
                    return
                }
            }
            
            ChatUtils.error("Shears not found in shop!")
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy shears: ${e.message}")
        }
    }
    
    /**
     * Buy wood planks from shop
     */
    private fun buyWoodPlanks(gui: GuiChest) {
        try {
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9) {
                    val itemName = stack.item.unlocalizedName.lowercase()
                    val displayName = stack.displayName.lowercase()
                    
                    if (itemName.contains("planks") || displayName.contains("planks") || displayName.contains("wood")) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}Buying Wood Planks (16 blocks)...")
                        
                        BedwarsAFK.mc.playerController.windowClick(
                            container.windowId,
                            slot.slotNumber,
                            0,
                            0,
                            BedwarsAFK.mc.thePlayer
                        )
                        return
                    }
                }
            }
            
            ChatUtils.error("Wood Planks not found in shop!")
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy wood planks: ${e.message}")
        }
    }
    
    /**
     * Buy end stone from shop
     */
    private fun buyEndStone(gui: GuiChest) {
        try {
            val container = gui.inventorySlots
            val inventory = container.inventorySlots
            
            // Skip top row (slots 0-8) - these are category switchers
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && slot.slotNumber >= 9) {
                    val itemName = stack.item.unlocalizedName.lowercase()
                    val displayName = stack.displayName.lowercase()
                    
                    if (itemName.contains("end_stone") || displayName.contains("end stone")) {
                        ChatUtils.info("${EnumChatFormatting.GREEN}Buying End Stone (12 blocks)...")
                        
                        BedwarsAFK.mc.playerController.windowClick(
                            container.windowId,
                            slot.slotNumber,
                            0,
                            0,
                            BedwarsAFK.mc.thePlayer
                        )
                        return
                    }
                }
            }
            
            ChatUtils.error("End Stone not found in shop!")
        } catch (e: Exception) {
            ChatUtils.error("Failed to buy end stone: ${e.message}")
        }
    }
}

