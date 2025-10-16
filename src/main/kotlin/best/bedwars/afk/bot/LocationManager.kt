package best.bedwars.afk.bot

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.pathfinding.AdvancedPathfinder
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraft.block.BlockBed
import net.minecraft.block.BlockChest
import net.minecraft.block.BlockEnderChest
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.entity.Entity
import net.minecraft.init.Items
import net.minecraft.inventory.IInventory
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumChatFormatting
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manages saved locations and navigation to them
 */
object LocationManager {
    
    // Saved locations
    private var generatorPos: BlockPos? = null
    private var shopkeeperPos: BlockPos? = null
    private var shopkeeperEntity: Entity? = null
    var bedPos: BlockPos? = null  // Public for bed defense check
        private set
    private var chestPos: BlockPos? = null
    private var enderChestPos: BlockPos? = null
    
    // Navigation state
    private var isNavigating = false
    private var currentDestination: String = ""
    
    /**
     * Save the generator location
     */
    fun saveGenerator(x: Double, y: Double, z: Double) {
        generatorPos = BlockPos(x.toInt(), y.toInt(), z.toInt())
        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Generator saved at: ${x.toInt()}, ${y.toInt()}, ${z.toInt()}")
    }
    
    /**
     * Save the shopkeeper location
     */
    fun saveShopkeeper(entity: Entity) {
        shopkeeperEntity = entity
        shopkeeperPos = BlockPos(entity.posX.toInt(), entity.posY.toInt(), entity.posZ.toInt())
        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Shopkeeper saved at: ${entity.posX.toInt()}, ${entity.posY.toInt()}, ${entity.posZ.toInt()}")
    }
    
    /**
     * Save the bed location
     */
    fun saveBed(x: Int, y: Int, z: Int) {
        bedPos = BlockPos(x, y, z)
        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Bed saved at: $x, $y, $z")
    }
    
    /**
     * Get saved generator position
     */
    fun getGenerator(): BlockPos? = generatorPos
    
    /**
     * Get saved shopkeeper position
     */
    fun getShopkeeper(): BlockPos? = shopkeeperPos
    
    /**
     * Get saved shopkeeper entity
     */
    fun getShopkeeperEntity(): Entity? = shopkeeperEntity
    
    /**
     * Get saved bed position
     */
    fun getBed(): BlockPos? = bedPos
    
    /**
     * Get saved chest position
     */
    fun getChest(): BlockPos? = chestPos
    
    /**
     * Get saved ender chest position
     */
    fun getEnderChest(): BlockPos? = enderChestPos
    
    /**
     * Auto-detect and save nearest bed
     */
    fun autoDetectBed(searchRadius: Int = 20): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        val playerPos = player.position
        var nearestBed: BlockPos? = null
        var nearestDistance = Double.MAX_VALUE
        
        // Search in a cube around the player
        for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val checkPos = playerPos.add(x, y, z)
                    val block = world.getBlockState(checkPos).block
                    
                    if (block is BlockBed) {
                        val distance = player.getDistanceSq(checkPos.x.toDouble(), checkPos.y.toDouble(), checkPos.z.toDouble())
                        if (distance < nearestDistance) {
                            nearestDistance = distance
                            nearestBed = checkPos
                        }
                    }
                }
            }
        }
        
        return if (nearestBed != null) {
            saveBed(nearestBed.x, nearestBed.y, nearestBed.z)
            true
        } else {
            ChatUtils.error("No bed found within $searchRadius blocks")
            false
        }
    }
    
    /**
     * Auto-detect and save nearest chest
     */
    fun autoDetectChest(searchRadius: Int = 20): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        val playerPos = player.position
        var nearestChest: BlockPos? = null
        var nearestDistance = Double.MAX_VALUE
        
        // Search in a cube around the player
        for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val checkPos = playerPos.add(x, y, z)
                    val block = world.getBlockState(checkPos).block
                    
                    if (block is BlockChest) {
                        val distance = player.getDistanceSq(checkPos.x.toDouble(), checkPos.y.toDouble(), checkPos.z.toDouble())
                        if (distance < nearestDistance) {
                            nearestDistance = distance
                            nearestChest = checkPos
                        }
                    }
                }
            }
        }
        
        return if (nearestChest != null) {
            chestPos = nearestChest
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Chest saved at: ${nearestChest.x}, ${nearestChest.y}, ${nearestChest.z}")
            true
        } else {
            ChatUtils.error("No chest found within $searchRadius blocks")
            false
        }
    }
    
    /**
     * Auto-detect and save nearest ender chest
     */
    fun autoDetectEnderChest(searchRadius: Int = 20): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val world = BedwarsAFK.mc.theWorld ?: return false
        
        val playerPos = player.position
        var nearestEnderChest: BlockPos? = null
        var nearestDistance = Double.MAX_VALUE
        
        // Search in a cube around the player
        for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val checkPos = playerPos.add(x, y, z)
                    val block = world.getBlockState(checkPos).block
                    
                    if (block is BlockEnderChest) {
                        val distance = player.getDistanceSq(checkPos.x.toDouble(), checkPos.y.toDouble(), checkPos.z.toDouble())
                        if (distance < nearestDistance) {
                            nearestDistance = distance
                            nearestEnderChest = checkPos
                        }
                    }
                }
            }
        }
        
        return if (nearestEnderChest != null) {
            enderChestPos = nearestEnderChest
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Ender Chest saved at: ${nearestEnderChest.x}, ${nearestEnderChest.y}, ${nearestEnderChest.z}")
            true
        } else {
            ChatUtils.error("No ender chest found within $searchRadius blocks")
            false
        }
    }
    
    /**
     * Auto-detect all nearby objects (bed, chest, ender chest)
     */
    fun autoDetectAll(searchRadius: Int = 20) {
        ChatUtils.info("${EnumChatFormatting.YELLOW}Scanning for nearby objects...")
        var detected = 0
        
        if (autoDetectBed(searchRadius)) detected++
        if (autoDetectChest(searchRadius)) detected++
        if (autoDetectEnderChest(searchRadius)) detected++
        
        ChatUtils.info("${EnumChatFormatting.GREEN}✓ Detected $detected objects")
    }
    
    /**
     * Check if currently at generator (within 3 blocks)
     */
    fun isAtGenerator(): Boolean {
        val player = BedwarsAFK.mc.thePlayer ?: return false
        val gen = generatorPos ?: return false
        val distance = sqrt((player.posX - gen.x).pow(2.0) + (player.posZ - gen.z).pow(2.0))
        return distance < 3.0
    }
    
    /**
     * Navigate to generator and keep moving until resources are picked up
     */
    fun goToGenerator(onComplete: () -> Unit = {}, retryCount: Int = 0) {
        val gen = generatorPos
        if (gen == null) {
            ChatUtils.error("Generator location not saved!")
            onComplete()
            return
        }
        
        // Ensure no GUI is open before starting navigation (failsafe)
        ensureNoGUI {
            isNavigating = true
            currentDestination = "generator"
            
            if (retryCount == 0) {
                ChatUtils.info("${EnumChatFormatting.YELLOW}→ Going to generator...")
            } else {
                ChatUtils.info("${EnumChatFormatting.YELLOW}→ Retrying generator navigation (attempt ${retryCount + 1}/5)...")
            }
            
            // First, try to detect the exact generator position by looking for dropped items
            val detectedGen = detectGeneratorByDroppedItems()
            val targetGen = detectedGen ?: gen
            
            if (detectedGen != null) {
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Generator detected at ${detectedGen.x}, ${detectedGen.y}, ${detectedGen.z}")
                generatorPos = detectedGen // Update to exact position
            }
            
            AdvancedPathfinder.goTo(targetGen.x, targetGen.y, targetGen.z) {
                ChatUtils.info("Reached generator area, aligning to exact position...")
                
                // Align to exact generator coordinates
                alignToGenerator(targetGen, retryCount, onComplete)
            }
        }
    }
    
    /**
     * Detect generator by looking for dropped iron/gold items nearby
     */
    private fun detectGeneratorByDroppedItems(): BlockPos? {
        val world = BedwarsAFK.mc.theWorld ?: return null
        val player = BedwarsAFK.mc.thePlayer ?: return null
        
        // Look for dropped items (EntityItem) within 10 blocks
        val nearbyEntities = world.loadedEntityList
        var closestItemPos: BlockPos? = null
        var closestDistance = Double.MAX_VALUE
        
        for (entity in nearbyEntities) {
            if (entity is net.minecraft.entity.item.EntityItem) {
                val itemStack = entity.entityItem
                val itemName = itemStack?.unlocalizedName?.lowercase() ?: continue
                
                // Check if it's iron or gold
                if (itemName.contains("iron") || itemName.contains("gold")) {
                    val distance = player.getDistanceToEntity(entity).toDouble()
                    
                    if (distance < 10.0 && distance < closestDistance) {
                        closestDistance = distance
                        closestItemPos = BlockPos(entity.posX, entity.posY, entity.posZ)
                    }
                }
            }
        }
        
        return closestItemPos
    }
    
    /**
     * Align player to exact generator coordinates
     */
    private fun alignToGenerator(genPos: BlockPos, retryCount: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            onComplete()
            return
        }
        
        // Calculate distance to generator
        val currentDist = sqrt(
            (player.posX - genPos.x - 0.5).pow(2.0) +
            (player.posZ - genPos.z - 0.5).pow(2.0)
        )
        
        ChatUtils.info("${EnumChatFormatting.GRAY}Distance to gen: ${String.format("%.2f", currentDist)} blocks")
        
        // If we're already very close (within 0.5 blocks), just stand still
        if (currentDist < 0.5) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Perfectly aligned!")
            Movement.stopForward()
            Mouse.stopTracking()
            
            // Update generator position to current location
            generatorPos = player.position
            
            isNavigating = false
            currentDestination = ""
            onComplete()
            return
        }
        
        // If we're close but not perfect (0.5 - 1.5 blocks), fine-tune position
        if (currentDist < 1.5) {
            fineTuneGeneratorPosition(genPos, retryCount, onComplete)
        } else {
            // Still too far, move until we pick up resources
            moveUntilResourcePickup(genPos, retryCount, onComplete)
        }
    }
    
    /**
     * Fine-tune position to get exactly on the generator
     */
    private fun fineTuneGeneratorPosition(genPos: BlockPos, retryCount: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            onComplete()
            return
        }
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}⚙ Fine-tuning position...")
        
        // Record initial resource count
        val initialIron = best.bedwars.afk.bot.player.Inventory.getIronCount()
        val initialGold = best.bedwars.afk.bot.player.Inventory.getGoldCount()
        
        // Calculate precise rotation toward generator center
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            genPos.x + 0.5,
            genPos.y.toDouble(),
            genPos.z + 0.5
        )
        
        if (rotations != null) {
            player.rotationYaw = rotations[0]
        }
        
        // Move slowly forward
        TimeUtils.setTimeout({
            Movement.startForward()
            
            var checkCount = 0
            val maxChecks = 30 // 1.5 seconds
            
            var alignTimer: Timer? = null
            alignTimer = TimeUtils.setInterval({
                checkCount++
                
                val p = BedwarsAFK.mc.thePlayer
                if (p == null) {
                    alignTimer?.cancel()
                    Movement.stopForward()
                    onComplete()
                    return@setInterval
                }
                
                // Check if we picked up resources
                val currentIron = best.bedwars.afk.bot.player.Inventory.getIronCount()
                val currentGold = best.bedwars.afk.bot.player.Inventory.getGoldCount()
                
                if (currentIron > initialIron || currentGold > initialGold) {
                    // Perfect! We picked up resources
                    alignTimer?.cancel()
                    Movement.stopForward()
                    Mouse.stopTracking()
                    
                    // Save exact position
                    generatorPos = p.position
                    ChatUtils.info("${EnumChatFormatting.GREEN}✓ Aligned! Position saved: ${p.posX.toInt()}, ${p.posY.toInt()}, ${p.posZ.toInt()}")
                    
                    isNavigating = false
                    currentDestination = ""
                    onComplete()
                    return@setInterval
                }
                
                // Check current distance
                val dist = sqrt(
                    (p.posX - genPos.x - 0.5).pow(2.0) +
                    (p.posZ - genPos.z - 0.5).pow(2.0)
                )
                
                // If we're within 0.3 blocks, stop and wait for resources
                if (dist < 0.3) {
                    alignTimer?.cancel()
                    Movement.stopForward()
                    
                    // Wait a bit for resources to spawn
                    TimeUtils.setTimeout(fun() {
                        val finalIron = best.bedwars.afk.bot.player.Inventory.getIronCount()
                        val finalGold = best.bedwars.afk.bot.player.Inventory.getGoldCount()
                        
                        if (finalIron > initialIron || finalGold > initialGold) {
                            // Got resources!
                            generatorPos = p.position
                            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Position locked!")
                        } else if (retryCount < 4) {
                            // Retry
                            ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ No resources, retrying...")
                            TimeUtils.setTimeout({
                                goToGenerator(onComplete, retryCount + 1)
                            }, 500)
                            return
                        }
                        
                        Mouse.stopTracking()
                        isNavigating = false
                        currentDestination = ""
                        onComplete()
                    }, 1000)
                    return@setInterval
                }
                
                // Timeout
                if (checkCount >= maxChecks) {
                    alignTimer?.cancel()
                    Movement.stopForward()
                    
                    if (retryCount < 4) {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Alignment timeout, retrying...")
                        TimeUtils.setTimeout({
                            goToGenerator(onComplete, retryCount + 1)
                        }, 500)
                    } else {
                        Mouse.stopTracking()
                        isNavigating = false
                        currentDestination = ""
                        onComplete()
                    }
                }
            }, 0, 50)
        }, 200)
    }
    
    /**
     * Move toward generator position until resources are picked up
     */
    private fun moveUntilResourcePickup(genPos: BlockPos, retryCount: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer
        if (player == null) {
            onComplete()
            return
        }
        
        // Record initial resource count
        val initialIron = best.bedwars.afk.bot.player.Inventory.getIronCount()
        val initialGold = best.bedwars.afk.bot.player.Inventory.getGoldCount()
        
        // Calculate rotation toward generator
        val rotations = best.bedwars.afk.utils.EntityUtils.getRotationsToCoords(
            player,
            genPos.x + 0.5,
            genPos.y.toDouble(),
            genPos.z + 0.5
        )
        
        if (rotations != null) {
            Mouse.lookAt(rotations[0], 0f)
        }
        
        TimeUtils.setTimeout({
            Movement.startForward()
            
            var checkCount = 0
            val maxChecks = 60 // 3 seconds max
            
            var resourceCheckTimer: Timer? = null
            resourceCheckTimer = TimeUtils.setInterval({
                checkCount++
                
                // Check if we picked up new resources
                val currentIron = best.bedwars.afk.bot.player.Inventory.getIronCount()
                val currentGold = best.bedwars.afk.bot.player.Inventory.getGoldCount()
                
                if (currentIron > initialIron || currentGold > initialGold) {
                    // Picked up resources! We're at the generator
                    resourceCheckTimer?.cancel()
                    Movement.stopForward()
                    Mouse.stopTracking()
                    
                    // Update generator position to current location
                    val newGenPos = player.position
                    generatorPos = newGenPos
                    ChatUtils.info("${EnumChatFormatting.GREEN}✓ Generator position updated!")
                    
                    isNavigating = false
                    currentDestination = ""
                    onComplete()
                } else if (checkCount >= maxChecks) {
                    // Timeout, retry if we haven't exceeded max retries
                    resourceCheckTimer?.cancel()
                    Movement.stopForward()
                    Mouse.stopTracking()
                    
                    if (retryCount < 4) {
                        // Retry pathfinding to generator
                        ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ No resources detected, retrying...")
                        TimeUtils.setTimeout({
                            goToGenerator(onComplete, retryCount + 1)
                        }, 500)
                    } else {
                        // Max retries exceeded, give up
                        ChatUtils.error("Failed to reach generator after 5 attempts!")
                        isNavigating = false
                        currentDestination = ""
                        onComplete()
                    }
                }
            }, 0, 50) // Check every 50ms
        }, 300)
    }
    
    /**
     * Navigate to shopkeeper with guaranteed interaction
     */
    fun goToShopkeeper(onShopOpened: () -> Unit) {
        val shop = shopkeeperEntity
        val shopPos = shopkeeperPos
        
        if (shop == null || shopPos == null) {
            ChatUtils.error("Shopkeeper location not saved!")
            onShopOpened()
            return
        }
        
        // Ensure no GUI is open before starting navigation (failsafe)
        ensureNoGUI {
            isNavigating = true
            currentDestination = "shopkeeper"
            
            ChatUtils.info("${EnumChatFormatting.YELLOW}→ Going to shopkeeper...")
            
            AdvancedPathfinder.goTo(shopPos.x, shopPos.y, shopPos.z) {
                ChatUtils.info("Reached shop area, interacting...")
                
                // Now reliably interact with shopkeeper
                interactWithShopkeeper(shop, onShopOpened)
            }
        }
    }
    
    /**
     * Navigate to bed
     */
    fun goToBed(onComplete: () -> Unit = {}) {
        val bed = bedPos
        if (bed == null) {
            ChatUtils.error("Bed location not saved!")
            onComplete()
            return
        }
        
        isNavigating = true
        currentDestination = "bed"
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}→ Going to bed...")
        
        AdvancedPathfinder.goTo(bed.x, bed.y, bed.z) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Reached bed!")
            isNavigating = false
            currentDestination = ""
            onComplete()
        }
    }
    
    /**
     * Navigate to chest
     */
    fun goToChest(onComplete: () -> Unit = {}) {
        val chest = chestPos
        if (chest == null) {
            ChatUtils.error("Chest location not saved! Try auto-detecting first.")
            onComplete()
            return
        }
        
        isNavigating = true
        currentDestination = "chest"
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}→ Going to chest...")
        
        AdvancedPathfinder.goTo(chest.x, chest.y, chest.z) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Reached chest!")
            isNavigating = false
            currentDestination = ""
            onComplete()
        }
    }
    
    /**
     * Navigate to ender chest
     */
    fun goToEnderChest(onComplete: () -> Unit = {}) {
        val enderChest = enderChestPos
        if (enderChest == null) {
            ChatUtils.error("Ender Chest location not saved! Try auto-detecting first.")
            onComplete()
            return
        }
        
        isNavigating = true
        currentDestination = "enderchest"
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}→ Going to ender chest...")
        
        AdvancedPathfinder.goTo(enderChest.x, enderChest.y, enderChest.z) {
            ChatUtils.info("${EnumChatFormatting.GREEN}✓ Reached ender chest!")
            isNavigating = false
            currentDestination = ""
            onComplete()
        }
    }
    
    /**
     * Stop any ongoing navigation
     */
    fun stopNavigation() {
        if (isNavigating) {
            ChatUtils.info("${EnumChatFormatting.RED}✗ Navigation stopped")
            isNavigating = false
            currentDestination = ""
            AdvancedPathfinder.stop()
            Movement.clearAll()
            Mouse.stopTracking()
        }
    }
    
    /**
     * Check if currently navigating
     */
    fun isNavigating(): Boolean = isNavigating
    
    /**
     * Get current destination
     */
    fun getCurrentDestination(): String = currentDestination
    
    /**
     * Check if the current GUI screen is the shopkeeper interface (chest with nether star)
     */
    private fun isShopkeeperInterface(screen: GuiScreen): Boolean {
        if (screen !is GuiChest) return false
        
        try {
            // Access the shop inventory through the container
            val container = screen.inventorySlots
            val inventory = container.inventorySlots
            
            // Check if the chest contains a nether star (item shop indicator)
            for (slot in inventory) {
                val stack = slot.stack
                if (stack != null && stack.item == Items.nether_star) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Failed, fallback to any chest
            return true
        }
        
        return false
    }
    
    /**
     * Force close any open GUI (MUST be called on main thread)
     */
    private fun forceCloseGUI() {
        val currentScreen = BedwarsAFK.mc.currentScreen
        if (currentScreen != null) {
            // Must run on main thread
            BedwarsAFK.mc.addScheduledTask {
                BedwarsAFK.mc.thePlayer?.closeScreen()
                BedwarsAFK.mc.displayGuiScreen(null)
            }
        }
    }
    
    /**
     * Ensure no GUI is open before proceeding (failsafe)
     */
    private fun ensureNoGUI(onComplete: () -> Unit) {
        var attempts = 0
        val maxAttempts = 10
        
        var closeTimer: Timer? = null
        closeTimer = TimeUtils.setInterval({
            attempts++
            
            val currentScreen = BedwarsAFK.mc.currentScreen
            if (currentScreen == null) {
                // GUI closed successfully
                closeTimer?.cancel()
                onComplete()
            } else if (attempts >= maxAttempts) {
                // Force close after max attempts
                ChatUtils.error("GUI still open after $maxAttempts attempts, force closing")
                forceCloseGUI()
                closeTimer?.cancel()
                TimeUtils.setTimeout({
                    onComplete()
                }, 500) // Increased delay to ensure GUI closes
            } else {
                // Try to close again
                forceCloseGUI()
            }
        }, 0, 150) // Increased interval to give more time between close attempts
    }
    
    /**
     * Reliably interact with shopkeeper - keeps trying until shop menu opens
     */
    private fun interactWithShopkeeper(shop: Entity, onShopOpened: () -> Unit) {
        Movement.clearAll()
        Mouse.stopTracking()
        
        // Look directly at the shopkeeper entity
        Mouse.startTracking(shop)
        
        TimeUtils.setTimeout({
            Movement.startForward()
            
            var attemptCount = 0
            var interactionTimer: Timer? = null
            
            interactionTimer = TimeUtils.setInterval({
                val player = BedwarsAFK.mc.thePlayer
                if (player == null) {
                    interactionTimer?.cancel()
                    Movement.stopForward()
                    Mouse.stopTracking()
                    isNavigating = false
                    currentDestination = ""
                    onShopOpened()
                    return@setInterval
                }
                
                // Keep looking at shopkeeper
                Mouse.startTracking(shop)
                
                // Right-click to interact
                Mouse.rClick(50)
                attemptCount++
                
                val distance = player.getDistanceToEntity(shop)
                
                // Check if we opened the SHOPKEEPER GUI (chest with nether star)
                val currentScreen = BedwarsAFK.mc.currentScreen
                if (currentScreen != null && isShopkeeperInterface(currentScreen)) {
                    ChatUtils.info("${EnumChatFormatting.GREEN}✓ Shopkeeper menu opened!")
                    interactionTimer?.cancel()
                    Movement.stopForward()
                    Mouse.stopTracking()
                    isNavigating = false
                    currentDestination = ""
                    
                    // Call the callback immediately (for shop purchase)
                    onShopOpened()
                    return@setInterval
                }
                
                // If too close, back up slightly
                if (distance < 1.5) {
                    Movement.stopForward()
                    Movement.startBackward()
                    TimeUtils.setTimeout({
                        Movement.stopBackward()
                        Movement.startForward()
                    }, 200)
                }
                
                // If too far after 10 attempts, move forward more
                if (attemptCount > 10 && distance > 3.0) {
                    ChatUtils.info("Too far, moving closer...")
                    Movement.startForward()
                }
                
                // Safety timeout after 50 attempts (5 seconds)
                if (attemptCount > 50) {
                    ChatUtils.error("Could not open shopkeeper menu, timeout reached")
                    interactionTimer?.cancel()
                    Movement.stopForward()
                    Mouse.stopTracking()
                    isNavigating = false
                    currentDestination = ""
                    onShopOpened()
                }
            }, 0, 100)
            
        }, 300)
    }
    
    /**
     * Manually approach a location (for fine-tuning after pathfinding)
     */
    private fun approachLocation(x: Double, y: Double, z: Double, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        // Calculate rotation to target
        val dx = x - player.posX
        val dz = z - player.posZ
        val yaw = Math.toDegrees(Math.atan2(dz, dx)).toFloat() - 90f
        
        Mouse.lookAt(yaw, player.rotationPitch)
        
        TimeUtils.setTimeout({
            Movement.startForward()
            
            TimeUtils.setTimeout({
                Movement.stopForward()
                Mouse.stopTracking()
                onComplete()
            }, 1000)
        }, 300)
    }
    
    /**
     * Clear all saved locations (for new game)
     */
    fun clearAll() {
        generatorPos = null
        shopkeeperPos = null
        shopkeeperEntity = null
        bedPos = null
        chestPos = null
        enderChestPos = null
        isNavigating = false
        currentDestination = ""
        ChatUtils.info("${EnumChatFormatting.GRAY}Locations cleared")
    }
}

