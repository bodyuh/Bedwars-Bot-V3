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
        
        if (!needArmor && !needWool) {
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
        
        // Purchase items in order: Iron Armor first, then Wool
        var purchaseDelay = 0
        
        // Buy Iron Armor if needed
        if (needArmor) {
            TimeUtils.setTimeout({
                buyIronArmor(currentScreen)
            }, purchaseDelay)
            purchaseDelay += 500
        }
        
        // Buy Wool if needed
        if (needWool) {
            val purchases = ResourceTracker.getWoolPurchasesAvailable()
            for (i in 0 until purchases) {
                TimeUtils.setTimeout({
                    buyWool(currentScreen)
                }, purchaseDelay)
                purchaseDelay += 500
            }
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
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && stack.item == Items.iron_boots) {
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
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null) {
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
            
            // Debug: list all items in shop
            ChatUtils.error("Wool not found in shop! Items:")
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null) {
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
}

