package best.bedwars.afk.bot

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Inventory
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
    private var woodPlanksCount = 0
    private var endStoneCount = 0
    private var hasIronArmor = false
    private var hasShears = false
    
    // Current tools (tier tracking)
    private var currentPickaxeTier = 0 // 0=none, 1=wood, 2=stone, 3=iron, 4=gold, 5=diamond
    private var currentAxeTier = 0     // 0=none, 1=wood, 2=stone, 3=iron, 4=gold, 5=diamond
    
    // Shopping goals - Basic resources
    private const val WOOL_BLOCKS_PER_PURCHASE = 16
    private const val IRON_COST_PER_WOOL = 4
    private const val MAX_WOOL_BLOCKS = 128  // Increased from 64 to 128 for expanding defense
    private const val BED_DEFENSE_WOOL_THRESHOLD = 16  // Keep buying when below 16
    private const val DIAMOND_COLLECTION_WOOL_THRESHOLD = 64  // Need 64 to go for diamonds (DISABLED)
    
    private const val GOLD_COST_FOR_ARMOR = 12
    
    // Shopping goals - Building blocks (keep collecting to expand defense)
    private const val WOOD_PLANKS_PER_PURCHASE = 16
    private const val GOLD_COST_PER_WOOD = 4
    private const val BED_DEFENSE_WOOD_THRESHOLD = 12  // Keep buying when below 12
    private const val MAX_WOOD_PLANKS = 64  // Max wood planks to carry
    
    private const val ENDSTONE_PER_PURCHASE = 12
    private const val IRON_COST_PER_ENDSTONE = 24
    private const val BED_DEFENSE_ENDSTONE_THRESHOLD = 12  // Keep buying when below 12
    private const val MAX_ENDSTONE = 64  // Max end stone to carry
    
    // Shopping goals - Tools
    private const val SHEARS_COST_IRON = 20
    
    // Tool costs (iron)
    private val PICKAXE_COSTS_IRON = mapOf(1 to 10, 2 to 10) // Wood=10, Stone=10
    private val AXE_COSTS_IRON = mapOf(1 to 10, 2 to 10)     // Wood=10, Stone=10
    
    // Tool costs (gold)
    private val PICKAXE_COSTS_GOLD = mapOf(3 to 3, 4 to 6)   // Iron=3, Gold=6 (actually Diamond costs 6)
    private val AXE_COSTS_GOLD = mapOf(3 to 3, 4 to 6)       // Iron=3, Gold=6 (actually Diamond costs 6)
    
    /**
     * Update resource counts from inventory
     */
    fun update() {
        iron = Inventory.getIronCount()
        gold = Inventory.getGoldCount()
        
        // Count all wool (including colored wool like "white", "red", "green", etc.)
        woolCount = countAllWool()
        
        // Count wood planks and end stone
        woodPlanksCount = Inventory.getItemCount("planks")
        endStoneCount = Inventory.getItemCount("end_stone")
        
        // Check for shears
        hasShears = Inventory.hasItem("shears")
        
        // Detect tool tiers
        currentPickaxeTier = detectToolTier("pickaxe")
        currentAxeTier = detectToolTier("axe")
        
        // Check if player has iron armor (check armor slots AND inventory)
        val player = BedwarsAFK.mc.thePlayer
        hasIronArmor = if (player != null) {
            // Check worn armor
            val hasIronBoots = player.inventory.armorInventory[0]?.item?.unlocalizedName?.contains("iron", ignoreCase = true) == true
            val hasIronLeggings = player.inventory.armorInventory[1]?.item?.unlocalizedName?.contains("iron", ignoreCase = true) == true
            val hasIronChestplate = player.inventory.armorInventory[2]?.item?.unlocalizedName?.contains("iron", ignoreCase = true) == true
            val hasIronHelmet = player.inventory.armorInventory[3]?.item?.unlocalizedName?.contains("iron", ignoreCase = true) == true
            
            // Also check inventory for iron armor pieces
            val hasInInventory = Inventory.hasItem("iron_boots") || 
                                 Inventory.hasItem("iron_helmet") ||
                                 Inventory.hasItem("iron_chestplate") ||
                                 Inventory.hasItem("iron_leggings")
            
            hasIronBoots || hasIronLeggings || hasIronChestplate || hasIronHelmet || hasInInventory
        } else {
            false
        }
    }
    
    /**
     * Detect tool tier from inventory
     */
    private fun detectToolTier(toolType: String): Int {
        val player = BedwarsAFK.mc.thePlayer ?: return 0
        
        var highestTier = 0
        
        for (itemStack in player.inventory.mainInventory) {
            if (itemStack != null) {
                val itemName = itemStack.unlocalizedName.lowercase()
                
                if (itemName.contains(toolType)) {
                    val tier = when {
                        itemName.contains("diamond") -> 5
                        itemName.contains("gold") -> 4
                        itemName.contains("iron") -> 3
                        itemName.contains("stone") -> 2
                        itemName.contains("wood") -> 1
                        else -> 0
                    }
                    if (tier > highestTier) {
                        highestTier = tier
                    }
                }
            }
        }
        
        return highestTier
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
     * Check if we have enough wool for bed defense (16+)
     */
    fun canDefendBed(): Boolean = woolCount >= BED_DEFENSE_WOOL_THRESHOLD
    
    /**
     * Check if we have enough wool to go for diamonds (64)
     */
    fun canGoForDiamonds(): Boolean = woolCount >= DIAMOND_COLLECTION_WOOL_THRESHOLD
    
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
     * Get wood planks count
     */
    fun getWoodPlanks(): Int = woodPlanksCount
    
    /**
     * Get end stone count
     */
    fun getEndStone(): Int = endStoneCount
    
    /**
     * Get current pickaxe tier
     */
    fun getPickaxeTier(): Int = currentPickaxeTier
    
    /**
     * Get current axe tier
     */
    fun getAxeTier(): Int = currentAxeTier
    
    /**
     * Check if we should buy wood planks
     */
    fun shouldBuyWoodPlanks(): Boolean {
        return gold >= GOLD_COST_PER_WOOD && woodPlanksCount < MAX_WOOD_PLANKS
    }
    
    /**
     * Check if we should buy end stone
     */
    fun shouldBuyEndStone(): Boolean {
        return iron >= IRON_COST_PER_ENDSTONE && endStoneCount < MAX_ENDSTONE
    }
    
    /**
     * Check if we should buy shears
     */
    fun shouldBuyShears(): Boolean {
        return !hasShears && iron >= SHEARS_COST_IRON
    }
    
    /**
     * Get next pickaxe tier to buy (0 if can't afford)
     */
    fun getNextPickaxeTier(): Int {
        val nextTier = currentPickaxeTier + 1
        if (nextTier > 5) return 0 // Max tier reached
        
        // Check if we can afford the next tier
        return when (nextTier) {
            1, 2 -> if (iron >= PICKAXE_COSTS_IRON[nextTier] ?: Int.MAX_VALUE) nextTier else 0
            3, 4 -> if (gold >= PICKAXE_COSTS_GOLD[nextTier] ?: Int.MAX_VALUE) nextTier else 0
            else -> 0
        }
    }
    
    /**
     * Get next axe tier to buy (0 if can't afford)
     */
    fun getNextAxeTier(): Int {
        val nextTier = currentAxeTier + 1
        if (nextTier > 5) return 0 // Max tier reached
        
        // Check if we can afford the next tier
        return when (nextTier) {
            1, 2 -> if (iron >= AXE_COSTS_IRON[nextTier] ?: Int.MAX_VALUE) nextTier else 0
            3, 4 -> if (gold >= AXE_COSTS_GOLD[nextTier] ?: Int.MAX_VALUE) nextTier else 0
            else -> 0
        }
    }
    
    /**
     * Check if we need to go shopping
     */
    fun shouldGoShopping(): Boolean {
        // PRIORITY: Check if defense materials are below minimum threshold
        val needsDefenseMaterials = woolCount < BED_DEFENSE_WOOL_THRESHOLD || 
                                   woodPlanksCount < BED_DEFENSE_WOOD_THRESHOLD || 
                                   endStoneCount < BED_DEFENSE_ENDSTONE_THRESHOLD
        
        // Check wool: must have enough iron AND not have max wool AND can afford at least 1 purchase
        val canBuyWool = shouldBuyWool() && getWoolPurchasesAvailable() > 0
        
        // Check armor: must have enough gold AND not have armor
        val canBuyArmor = shouldBuyIronArmor()
        
        // Check building blocks (wood/end stone)
        val canBuyWood = shouldBuyWoodPlanks()
        val canBuyEndStone = shouldBuyEndStone()
        
        // REMOVED: Tool purchasing (pickaxe, axe, shears)
        
        return needsDefenseMaterials || canBuyWool || canBuyArmor || canBuyWood || canBuyEndStone
    }
    
    /**
     * Get shopping list as string
     */
    fun getShoppingList(): List<String> {
        val list = mutableListOf<String>()
        
        // Priority 1: Building blocks for bed defense
        if (shouldBuyWoodPlanks()) {
            list.add("Wood Planks x$WOOD_PLANKS_PER_PURCHASE ($GOLD_COST_PER_WOOD gold)")
        }
        
        if (shouldBuyEndStone()) {
            list.add("End Stone x$ENDSTONE_PER_PURCHASE ($IRON_COST_PER_ENDSTONE iron)")
        }
        
        // Priority 2: Wool
        if (shouldBuyWool()) {
            val purchases = getWoolPurchasesAvailable()
            if (purchases > 0) {
                list.add("Wool x${purchases * WOOL_BLOCKS_PER_PURCHASE} (${purchases * IRON_COST_PER_WOOL} iron)")
            }
        }
        
        // Priority 3: Armor
        if (shouldBuyIronArmor()) {
            list.add("Iron Armor ($GOLD_COST_FOR_ARMOR gold)")
        }
        
        // REMOVED: Tool purchasing (pickaxe, axe, shears)
        
        return list
    }
    
    /**
     * Get tier name for display
     */
    private fun getTierName(tier: Int): String {
        return when (tier) {
            1 -> "Wooden"
            2 -> "Stone"
            3 -> "Iron"
            4 -> "Diamond"  // Gold pickaxe tier is actually Diamond
            5 -> "Diamond"
            else -> "Unknown"
        }
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
        
        // Wood planks count
        val woodColor = if (woodPlanksCount >= MAX_WOOD_PLANKS) EnumChatFormatting.GREEN else EnumChatFormatting.YELLOW
        fontRenderer.drawStringWithShadow(
            "${woodColor}Wood: ${EnumChatFormatting.WHITE}$woodPlanksCount/$MAX_WOOD_PLANKS",
            x.toFloat(),
            y.toFloat(),
            0xFFFFFF
        )
        y += 10
        
        // End stone count
        val endStoneColor = if (endStoneCount >= MAX_ENDSTONE) EnumChatFormatting.GREEN else EnumChatFormatting.YELLOW
        fontRenderer.drawStringWithShadow(
            "${endStoneColor}End Stone: ${EnumChatFormatting.WHITE}$endStoneCount/$MAX_ENDSTONE",
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
        woodPlanksCount = 0
        endStoneCount = 0
        hasIronArmor = false
        hasShears = false
        currentPickaxeTier = 0
        currentAxeTier = 0
    }
}

