package best.bedwars.afk.bot

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Inventory
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Tracks and displays player resources
 */
object ResourceTracker {
    
    // Current resources
    private var iron = 0
    private var gold = 0
    private var woolCount = 0
    private var hasIronArmor = false
    
    // Shopping goals
    private const val WOOL_BLOCKS_PER_PURCHASE = 16
    private const val IRON_COST_PER_WOOL = 4
    private const val MAX_WOOL_BLOCKS = 64
    
    private const val GOLD_COST_FOR_ARMOR = 12
    
    /**
     * Update resource counts from inventory
     */
    fun update() {
        iron = Inventory.getIronCount()
        gold = Inventory.getGoldCount()
        
        // Count all wool (including colored wool like "white", "red", "green", etc.)
        woolCount = countAllWool()
        
        // Check if player has iron armor (check for iron boots)
        hasIronArmor = Inventory.hasItem("iron_boots") || 
                       Inventory.hasItem("iron_helmet") ||
                       Inventory.hasItem("iron_chestplate") ||
                       Inventory.hasItem("iron_leggings")
    }
    
    /**
     * Count all wool blocks in inventory (including colored wool)
     */
    private fun countAllWool(): Int {
        val player = best.bedwars.afk.BedwarsAFK.mc.thePlayer ?: return 0
        var count = 0
        
        for (itemStack in player.inventory.mainInventory) {
            if (itemStack != null) {
                val itemName = itemStack.unlocalizedName.lowercase()
                val displayName = itemStack.displayName.lowercase()
                
                // Check if it's any type of wool
                if (itemName.contains("cloth") || // Minecraft 1.8.9 wool is "tile.cloth"
                    itemName.contains("wool") ||
                    displayName.contains("wool")) {
                    count += itemStack.stackSize
                }
            }
        }
        
        return count
    }
    
    /**
     * Get current iron count
     */
    fun getIron(): Int = iron
    
    /**
     * Get current gold count
     */
    fun getGold(): Int = gold
    
    /**
     * Get current wool count
     */
    fun getWool(): Int = woolCount
    
    /**
     * Check if player has iron armor
     */
    fun hasIronArmor(): Boolean = hasIronArmor
    
    /**
     * Check if we can afford wool (4 iron per 16 blocks)
     */
    fun canAffordWool(): Boolean {
        return iron >= IRON_COST_PER_WOOL && woolCount < MAX_WOOL_BLOCKS
    }
    
    /**
     * Check if we should buy wool
     */
    fun shouldBuyWool(): Boolean {
        // Don't buy wool if we already have 64 or more
        if (woolCount >= MAX_WOOL_BLOCKS) return false
        return canAffordWool() && woolCount < MAX_WOOL_BLOCKS
    }
    
    /**
     * Check if we can afford iron armor (12 gold)
     */
    fun canAffordIronArmor(): Boolean {
        return gold >= GOLD_COST_FOR_ARMOR && !hasIronArmor
    }
    
    /**
     * Check if we should buy iron armor
     */
    fun shouldBuyIronArmor(): Boolean {
        return canAffordIronArmor() && !hasIronArmor
    }
    
    /**
     * Calculate how many wool purchases we can make
     */
    fun getWoolPurchasesAvailable(): Int {
        val maxPurchases = (MAX_WOOL_BLOCKS - woolCount) / WOOL_BLOCKS_PER_PURCHASE
        val affordablePurchases = iron / IRON_COST_PER_WOOL
        return minOf(maxPurchases, affordablePurchases, 4) // Max 4 purchases (64 wool)
    }
    
    /**
     * Check if we need to go shopping
     */
    fun shouldGoShopping(): Boolean {
        // Check wool: must have enough iron AND not have max wool AND can afford at least 1 purchase
        val canBuyWool = shouldBuyWool() && getWoolPurchasesAvailable() > 0
        
        // Check armor: must have enough gold AND not have armor
        val canBuyArmor = shouldBuyIronArmor()
        
        return canBuyWool || canBuyArmor
    }
    
    /**
     * Get shopping list as string
     */
    fun getShoppingList(): List<String> {
        val list = mutableListOf<String>()
        
        if (shouldBuyWool()) {
            val purchases = getWoolPurchasesAvailable()
            if (purchases > 0) {
                list.add("Wool x${purchases * WOOL_BLOCKS_PER_PURCHASE} (${purchases * IRON_COST_PER_WOOL} iron)")
            }
        }
        
        if (shouldBuyIronArmor()) {
            list.add("Iron Armor ($GOLD_COST_FOR_ARMOR gold)")
        }
        
        return list
    }
    
    /**
     * Render resource HUD in top-left corner
     */
    @SubscribeEvent
    fun onRenderOverlay(event: RenderGameOverlayEvent.Text) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return
        
        val mc = BedwarsAFK.mc
        if (mc.thePlayer == null || mc.theWorld == null) return
        
        // Update resources
        update()
        
        val sr = ScaledResolution(mc)
        val fontRenderer = mc.fontRendererObj
        
        var y = 5
        val x = 5
        
        // Title
        fontRenderer.drawStringWithShadow(
            "${EnumChatFormatting.GOLD}${EnumChatFormatting.BOLD}Resources",
            x.toFloat(),
            y.toFloat(),
            0xFFFFFF
        )
        y += 12
        
        // Iron count
        val ironColor = if (canAffordWool()) EnumChatFormatting.GREEN else EnumChatFormatting.WHITE
        fontRenderer.drawStringWithShadow(
            "${ironColor}Iron: ${EnumChatFormatting.WHITE}$iron",
            x.toFloat(),
            y.toFloat(),
            0xFFFFFF
        )
        y += 10
        
        // Gold count
        val goldColor = if (canAffordIronArmor()) EnumChatFormatting.GREEN else EnumChatFormatting.WHITE
        fontRenderer.drawStringWithShadow(
            "${goldColor}Gold: ${EnumChatFormatting.WHITE}$gold",
            x.toFloat(),
            y.toFloat(),
            0xFFFFFF
        )
        y += 10
        
        // Wool count
        val woolColor = if (woolCount >= MAX_WOOL_BLOCKS) EnumChatFormatting.GREEN else EnumChatFormatting.YELLOW
        fontRenderer.drawStringWithShadow(
            "${woolColor}Wool: ${EnumChatFormatting.WHITE}$woolCount/$MAX_WOOL_BLOCKS",
            x.toFloat(),
            y.toFloat(),
            0xFFFFFF
        )
        y += 10
        
        // Iron armor status
        val armorStatus = if (hasIronArmor) "${EnumChatFormatting.GREEN}✓" else "${EnumChatFormatting.RED}✗"
        fontRenderer.drawStringWithShadow(
            "${EnumChatFormatting.GRAY}Iron Armor: $armorStatus",
            x.toFloat(),
            y.toFloat(),
            0xFFFFFF
        )
        y += 12
        
        // Shopping list
        if (shouldGoShopping()) {
            fontRenderer.drawStringWithShadow(
                "${EnumChatFormatting.YELLOW}${EnumChatFormatting.BOLD}Shopping:",
                x.toFloat(),
                y.toFloat(),
                0xFFFFFF
            )
            y += 10
            
            for (item in getShoppingList()) {
                fontRenderer.drawStringWithShadow(
                    "${EnumChatFormatting.WHITE}• $item",
                    (x + 5).toFloat(),
                    y.toFloat(),
                    0xFFFFFF
                )
                y += 10
            }
        }
    }
    
    /**
     * Reset all tracked resources
     */
    fun reset() {
        iron = 0
        gold = 0
        woolCount = 0
        hasIronArmor = false
    }
}

