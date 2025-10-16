package best.bedwars.afk.bot

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.LocationManager
import best.bedwars.afk.bot.pathfinding.AdvancedPathfinder
import best.bedwars.afk.bot.pathfinding.BridgingPathfinder
import best.bedwars.afk.bot.player.Inventory
import best.bedwars.afk.bot.player.LobbyMovement
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.core.KeyBindings
import best.bedwars.afk.utils.*
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

class BedwarsBot {

    private var toggled = false
    fun toggled() = toggled

    private var state = BotState.LOBBY
    
    // Saved generator coordinates
    private var genX: Double = 0.0
    private var genY: Double = 0.0
    private var genZ: Double = 0.0
    
    // Progress tracking
    private var hasDefendedBed = false
    private var hasCollectedDiamonds = false
    private var diamondBridgeComplete = false
    
    // Teammate tracking
    private var teammate1: String = ""
    private var teammate2: String = ""
    private var teammate3: String = ""
    
    // Health monitoring
    private var prevHealth: Float = 20f
    
    // Timers
    private var afkTimer: Timer? = null
    private var purchaseTimer: Timer? = null
    private var inventoryCheckTimer: Timer? = null
    private var teammateCheckTimer: Timer? = null
    private var enemyCheckTimer: Timer? = null
    
    // State flags
    private var isDefending = false
    private var isPurchasing = false
    private var itemShop: Entity? = null
    private var returningToGen = false
    
    // Auto-detect player IGN
    private val yourIGN: String
        get() = BedwarsAFK.mc.thePlayer?.displayNameString ?: "Unknown"

    fun toggle() {
        toggled = !toggled
        if (toggled) {
            ChatUtils.info("Started Bot")
            TimeUtils.setTimeout({
                ChatUtils.info("${EnumChatFormatting.GRAY}Open Source - ${EnumChatFormatting.WHITE}Programmed by Rice")
            }, 20)
            TimeUtils.setTimeout({
                joinGame()
            }, 2000)
        } else {
            ChatUtils.info("Stopped Bot")
            stopAll()
        }
    }

    private fun joinGame() {
        if (toggled && state == BotState.LOBBY) {
            ChatUtils.sendAsPlayer("/play bedwars_four_four")
        }
    }

    fun handleGameStart() {
        if (!toggled) return
        
        // Clear saved locations from previous game
        LocationManager.clearAll()
        
        // Reset progress flags
        hasDefendedBed = false
        hasCollectedDiamonds = false
        diamondBridgeComplete = false
        
        ChatUtils.info("Walking Back Into Generator")
        state = BotState.STARTING
        
        // Stop any movement
        Movement.clearAll()
        Mouse.stopTracking()
        
        // Wait briefly then walk backward
        TimeUtils.setTimeout({
            Movement.startBackward()
            
            // Monitor for resource pickup to detect generator
            detectGeneratorByResourcePickup()
        }, 1000)
    }
    
    private fun detectGeneratorByResourcePickup() {
        var checkCount = 0
        val maxChecks = 100 // 5 seconds max
        
        var resourceCheckTimer: Timer? = null
        resourceCheckTimer = TimeUtils.setInterval({
            checkCount++
            
            // Check if we picked up iron or gold
            val ironCount = Inventory.getIronCount()
            val goldCount = Inventory.getItemCount("gold_ingot")
            
            if (ironCount > 0 || goldCount > 0) {
                // Found resources! This is the generator location
                resourceCheckTimer?.cancel()
                Movement.stopBackward()
                
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Generator detected by resource pickup!")
                saveGenCoords()
                detectTeammates()
                
                TimeUtils.setTimeout({
                    startIdleAtGen()
                }, 2000)
            } else if (checkCount >= maxChecks) {
                // Timeout, fallback to time-based detection
                resourceCheckTimer?.cancel()
                Movement.stopBackward()
                
                ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ Generator detection timeout, using position")
                saveGenCoords()
                detectTeammates()
                
                TimeUtils.setTimeout({
                    startIdleAtGen()
                }, 2000)
            }
        }, 0, 50) // Check every 50ms
    }

    private fun saveGenCoords() {
        if (BedwarsAFK.mc.thePlayer != null) {
            val player = BedwarsAFK.mc.thePlayer
            genX = player.posX
            genY = player.posY + 1
            genZ = player.posZ
            
            // Save to LocationManager for easy access
            LocationManager.saveGenerator(genX, genY, genZ)
            
            ChatUtils.info("Saved Generator Coordinates")
            ChatUtils.info("${EnumChatFormatting.GRAY}> Gen XPOS: ${player.posX.toInt()}")
            ChatUtils.info("${EnumChatFormatting.GRAY}> Gen YPOS: ${player.posY.toInt()}")
            ChatUtils.info("${EnumChatFormatting.GRAY}> Gen ZPOS: ${player.posZ.toInt()}")
        }
    }

    private fun detectTeammates() {
        if (BedwarsAFK.mc.thePlayer == null) return
        
        val myIGN = yourIGN
        val teammates = EntityUtils.getTeammates(20f) // Increased radius from 10f to 20f
        var count = 0
        
        for (teammate in teammates) {
            if (teammate.displayNameString != myIGN) {
                when (count) {
                    0 -> teammate1 = teammate.displayNameString
                    1 -> teammate2 = teammate.displayNameString
                    2 -> teammate3 = teammate.displayNameString
                }
                count++
                if (count >= 3) break
            }
        }
    }

    private fun startIdleAtGen() {
        state = BotState.IDLE_AT_GEN
        
        // Start AFK movement
        startAFKMovement()
        
        // Start inventory checking
        startInventoryCheck()
        
        // Start teammate resource sharing
        startTeammateCheck()
        
        // Start enemy detection
        startEnemyCheck()
    }

    private fun startAFKMovement() {
        afkTimer?.cancel()
        afkTimer = TimeUtils.setInterval({
            if (state == BotState.IDLE_AT_GEN && !isDefending) {
                // Alternate left/right
                if (Movement.left()) {
                    Movement.stopLeft()
                    TimeUtils.setTimeout({
                        if (state == BotState.IDLE_AT_GEN) {
                            Movement.startRight()
                            TimeUtils.setTimeout({
                                Movement.stopRight()
                            }, 130)
                        }
                    }, 5000)
                } else {
                    Movement.startLeft()
                    TimeUtils.setTimeout({
                        Movement.stopLeft()
                    }, 130)
                }
            }
        }, 0, 5130)
    }

    private fun startInventoryCheck() {
        inventoryCheckTimer?.cancel()
        inventoryCheckTimer = TimeUtils.setInterval({
            if (state == BotState.IDLE_AT_GEN && !isPurchasing && !BedDefender.isDefending()) {
                // Update resource tracker
                ResourceTracker.update()
                
                val woolCount = ResourceTracker.getWool()
                
                // Priority 1: Defend bed with first 16 wool (check even if bed pos not known yet)
                if (woolCount >= 16 && !hasDefendedBed) {
                    // Auto-detect bed location if not known
                    if (LocationManager.bedPos == null) {
                        LocationManager.autoDetectBed(30)
                    }
                    
                    if (LocationManager.bedPos != null) {
                        ChatUtils.info("${EnumChatFormatting.GOLD}✓ First 16 wool! Defending bed...")
                        startBedDefense()
                        hasDefendedBed = true // Only defend once
                        return@setInterval
                    } else {
                        ChatUtils.info("${EnumChatFormatting.YELLOW}⚠ 16 wool ready, but bed not detected yet")
                    }
                }
                
                // Priority 2: DISABLED - Bridging to diamond generator
                // (Focusing on bed defense only)
                
                // Priority 3: Regular shopping (only if not ready for major actions)
                // Don't shop if we're at/near the wool thresholds for bed defense
                val nearBedDefenseThreshold = woolCount >= 12 && !hasDefendedBed // Close to 16
                
                if (!nearBedDefenseThreshold && ResourceTracker.shouldGoShopping()) {
                    ChatUtils.info("Shopping triggered")
                    startPurchase()
                }
            }
        }, 100, 1000)
    }

    private fun startTeammateCheck() {
        teammateCheckTimer?.cancel()
        teammateCheckTimer = TimeUtils.setInterval({
            if (state == BotState.IDLE_AT_GEN && !isDefending) {
                val myIGN = yourIGN
                val teammates = EntityUtils.getTeammates(7f)
                for (teammate in teammates) {
                    if (teammate.displayNameString != myIGN) {
                        val distance = EntityUtils.getDistanceNoY(BedwarsAFK.mc.thePlayer, teammate)
                        if (distance < 4f) {
                            // Drop resources to this teammate
                            ChatUtils.info("Dropping Resources to Nearest Teammate. ${EnumChatFormatting.GRAY}(${teammate.displayNameString} | ${distance.toInt()} blocks away)")
                            dropResourcesToTeammate(teammate)
                            break
                        }
                    }
                }
            }
        }, 0, 500)
    }

    private fun startEnemyCheck() {
        enemyCheckTimer?.cancel()
        enemyCheckTimer = TimeUtils.setInterval({
            if (state == BotState.IDLE_AT_GEN && !isDefending) {
                checkForEnemies()
            }
        }, 0, 100)
    }

    private fun checkForEnemies() {
        if (BedwarsAFK.mc.thePlayer == null) return
        
        val currentHealth = BedwarsAFK.mc.thePlayer.health
        if (currentHealth < prevHealth) {
            // Took damage - enemy nearby
            onEnemyDetected()
        }
        prevHealth = currentHealth
    }

    private fun onEnemyDetected() {
        if (isDefending) return
        
        isDefending = true
        ChatUtils.info("Detected Enemy Near")
        
        // Stop all timers
        stopTimers()
        
        // Look backward
        if (BedwarsAFK.mc.thePlayer != null) {
            val reverseYaw = BedwarsAFK.mc.thePlayer.rotationYaw + 180
            Mouse.lookAt(reverseYaw, 80f)
        }
        
        TimeUtils.setTimeout({
            // Use fireballs (spawn egg)
            if (Inventory.setInvItem("spawn_egg")) {
                Mouse.startRightClick()
                TimeUtils.setTimeout({
                    Mouse.stopRightClick()
                }, 1000)
            }
            
            TimeUtils.setTimeout({
                // Eat golden apples
                if (Inventory.setInvItem("golden_apple")) {
                    var appleCount = 0
                    val appleTimer = TimeUtils.setInterval({
                        if (appleCount < 35) {
                            Mouse.rClick(50)
                            appleCount++
                        }
                    }, 0, 50)
                    
                    TimeUtils.setTimeout({
                        appleTimer?.cancel()
                        Mouse.stopTracking()
                        isDefending = false
                        startIdleAtGen()
                    }, 2000)
                }
            }, 1500)
        }, 500)
    }

    private fun dropResourcesToTeammate(teammate: EntityPlayer) {
        // Look at the teammate
        Mouse.startTracking(teammate)
        
        TimeUtils.setTimeout({
            // Drop iron ingots
            val ironCount = Inventory.getIronCount()
            if (ironCount > 0) {
                if (Inventory.setInvItem("iron_ingot")) {
                    ChatUtils.info("Dropping $ironCount iron ingots")
                    dropCurrentItem()
                }
            }
            
            // Wait a bit then drop gold ingots
            TimeUtils.setTimeout({
                val goldCount = Inventory.getItemCount("gold_ingot")
                if (goldCount > 0) {
                    if (Inventory.setInvItem("gold_ingot")) {
                        ChatUtils.info("Dropping $goldCount gold ingots")
                        dropCurrentItem()
                    }
                }
                
                // Stop looking at teammate
                TimeUtils.setTimeout({
                    Mouse.stopTracking()
                    Inventory.setInvSlot(0)
                }, 300)
            }, 200)
        }, 300)
    }
    
    private fun dropCurrentItem() {
        // Press Q to drop entire stack (hold ctrl + Q)
        net.minecraft.client.settings.KeyBinding.setKeyBindState(
            BedwarsAFK.mc.gameSettings.keyBindDrop.keyCode, true
        )
        TimeUtils.setTimeout({
            net.minecraft.client.settings.KeyBinding.setKeyBindState(
                BedwarsAFK.mc.gameSettings.keyBindDrop.keyCode, false
            )
        }, 100)
    }

    private fun startBedDefense() {
        val bedPos = LocationManager.bedPos
        if (bedPos == null) {
            ChatUtils.error("Bed not found! Can't defend")
            returnToGenerator()
            return
        }
        
        // Stop AFK movement
        stopTimers()
        Movement.clearAll()
        
        state = BotState.DEFENDING_BED
        
        // Set timeout for navigation (30 seconds)
        val navigationTimeout = TimeUtils.setTimeout({
            ChatUtils.info("${EnumChatFormatting.RED}⚠ Navigation to bed timed out, returning to generator...")
            BedDefender.stopDefense()
            returnToGenerator()
        }, 30000)
        
        // Navigate to bed first
        LocationManager.goToBed {
            navigationTimeout?.cancel()
            ChatUtils.info("${EnumChatFormatting.GREEN}Reached bed! Building initial defense...")
            
            // Build initial defense first
            BedDefender.startDefense(bedPos) {
                ChatUtils.info("${EnumChatFormatting.GREEN}Initial defense complete! Starting periodic monitoring...")
                
                // Now start periodic defense system to maintain it
                BedDefender.startPeriodicDefense(bedPos) {
                    // Need more resources - go shopping
                    ChatUtils.info("${EnumChatFormatting.YELLOW}Need defense resources, going shopping...")
                    BedDefender.stopPeriodicDefense()
                    startPurchase()
                }
            }
        }
    }
    
    private fun startPurchase() {
        if (isPurchasing) return
        
        // Update resources and check if we need to shop
        ResourceTracker.update()
        
        if (!ResourceTracker.shouldGoShopping()) {
            ChatUtils.info("No shopping needed right now")
            returnToGenerator()
            return
        }
        
        isPurchasing = true
        state = BotState.PURCHASING
        
        ChatUtils.info("${EnumChatFormatting.YELLOW}Shopping needed:")
        for (item in ResourceTracker.getShoppingList()) {
            ChatUtils.info("${EnumChatFormatting.WHITE}• $item")
        }
        
        // Stop AFK movement
        stopTimers()
        Movement.clearAll()
        
        // Set timeout for entire shopping process (60 seconds)
        val shoppingTimeout = TimeUtils.setTimeout({
            ChatUtils.info("${EnumChatFormatting.RED}⚠ Shopping timed out, returning to generator...")
            isPurchasing = false
            closeMenu()
            returnToGenerator()
        }, 60000)
        
        TimeUtils.setTimeout({
            // Find item shop
            itemShop = EntityUtils.findItemShop()
            
            if (itemShop != null) {
                // Save shopkeeper location for future use
                LocationManager.saveShopkeeper(itemShop!!)
                
                // Start moving toward shop
                navigateToShop(shoppingTimeout)
            } else {
                // Shop not found, return to gen
                shoppingTimeout?.cancel()
                isPurchasing = false
                returnToGenerator()
            }
        }, 500)
    }

    private fun navigateToShop(shoppingTimeout: Timer?) {
        state = BotState.PURCHASING
        
        // Set timeout for navigation to shop (30 seconds)
        val navigationTimeout = TimeUtils.setTimeout({
            ChatUtils.info("${EnumChatFormatting.RED}⚠ Navigation to shop timed out...")
            shoppingTimeout?.cancel()
            isPurchasing = false
            returnToGenerator()
        }, 30000)
        
        // Use LocationManager for reliable navigation and interaction
        LocationManager.goToShopkeeper {
            navigationTimeout?.cancel()
            // Shop menu opened successfully
            startShopPurchase(shoppingTimeout)
        }
    }

    private fun closeMenu() {
        // Press ESC to close any open menu
        KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindInventory.keyCode, true)
        TimeUtils.setTimeout({
            KeyBinding.setKeyBindState(BedwarsAFK.mc.gameSettings.keyBindInventory.keyCode, false)
        }, 50)
        
        // Also try ESC key
        KeyBinding.setKeyBindState(1, true) // ESC key code
        TimeUtils.setTimeout({
            KeyBinding.setKeyBindState(1, false)
        }, 50)
    }

    private fun startShopPurchase(shoppingTimeout: Timer?) {
        state = BotState.AT_SHOP
        
        // Update resources before shopping
        ResourceTracker.update()
        
        // Set timeout for shop purchase (20 seconds)
        val purchaseTimeout = TimeUtils.setTimeout({
            ChatUtils.info("${EnumChatFormatting.RED}⚠ Purchase process timed out...")
            shoppingTimeout?.cancel()
            isPurchasing = false
            closeMenu()
            returnToGenerator()
        }, 20000)
        
        // Execute shopping using ShopHandler
        ShopHandler.executeShopping {
            purchaseTimeout?.cancel()
            shoppingTimeout?.cancel()
            // Shopping complete, return to generator
            Mouse.stopTracking()
            returnToGenerator()
        }
    }

    private fun returnToGenerator() {
        state = BotState.RETURNING_TO_GEN
        returningToGen = true
        
        Movement.clearAll()
        Mouse.stopTracking()
        BedDefender.stopPeriodicDefense()
        
        // Set timeout for returning to generator (45 seconds)
        val returnTimeout = TimeUtils.setTimeout({
            ChatUtils.info("${EnumChatFormatting.RED}⚠ Return to generator timed out, restarting idle...")
            isPurchasing = false
            returningToGen = false
            Movement.clearAll()
            Mouse.stopTracking()
            startIdleAtGen()
        }, 45000)
        
        // Use LocationManager for reliable navigation to generator
        LocationManager.goToGenerator(
            onComplete = {
                returnTimeout?.cancel()
                // Successfully returned to generator
                isPurchasing = false
                returningToGen = false
                startIdleAtGen()
            }
        )
    }

    fun handleRespawn() {
        if (!toggled) return
        
        ChatUtils.info("Respawned, returning to generator")
        state = BotState.RESPAWNING
        
        stopTimers()
        Movement.clearAll()
        Mouse.stopTracking()
        
        TimeUtils.setTimeout({
            returnToGenerator()
        }, 1000)
    }

    fun handleGameEnd() {
        if (!toggled) return
        
        ChatUtils.info("Game Ended. Requeueing.")
        
        // Stop everything completely
        stopAll()
        
        // Stop bed defender
        BedDefender.stopPeriodicDefense()
        BedDefender.stopDefense()
        
        // Stop all pathfinding
        BridgingPathfinder.stop()
        LocationManager.clearAll()
        
        state = BotState.LOBBY
        isDefending = false
        isPurchasing = false
        returningToGen = false
        hasDefendedBed = false
        hasCollectedDiamonds = false
        
        // Reset teammate tracking
        teammate1 = ""
        teammate2 = ""
        teammate3 = ""
        
        // Reset resource tracker
        ResourceTracker.reset()
        
        TimeUtils.setTimeout({
            joinGame()
        }, RandomUtils.randomIntInRange(3000, 5000))
    }

    private fun stopTimers() {
        afkTimer?.cancel()
        purchaseTimer?.cancel()
        inventoryCheckTimer?.cancel()
        teammateCheckTimer?.cancel()
        enemyCheckTimer?.cancel()
    }

    private fun stopAll() {
        stopTimers()
        Movement.clearAll()
        Mouse.stopTracking()
        LobbyMovement.stop()
        BridgingPathfinder.stop()
        state = BotState.LOBBY
        isDefending = false
        isPurchasing = false
        returningToGen = false
        hasDefendedBed = false
        hasCollectedDiamonds = false
        diamondBridgeComplete = false
    }
    
    /**
     * Test bridge building (small bridge then return)
     */
    private fun startBridgeTest() {
        state = BotState.BRIDGING_TO_DIAMOND
        ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
        ChatUtils.info("${EnumChatFormatting.GOLD}⚙ BRIDGE TEST MODE ⚙")
        ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
        
        hasCollectedDiamonds = true // Mark as complete to prevent re-triggering
        
        // Find and start bridging toward diamond generator
        BridgingPathfinder.findAndBridgeToDiamondGenerator {
            // After bridging attempt, return to base
            ChatUtils.info("${EnumChatFormatting.YELLOW}Bridge test complete! Returning to base...")
            
            // Stop bridging and return to generator
            TimeUtils.setTimeout({
                BridgingPathfinder.stop()
                returnToGenerator()
            }, 2000)
        }
    }
    
    /**
     * Start diamond collection sequence (DISABLED FOR NOW)
     */
    private fun startDiamondCollection() {
        state = BotState.BRIDGING_TO_DIAMOND
        ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
        ChatUtils.info("${EnumChatFormatting.GOLD}⛏ DIAMOND COLLECTION MODE ⛏")
        ChatUtils.info("${EnumChatFormatting.AQUA}═══════════════════════════════")
        
        // Find and bridge to diamond generator (by entity name tag)
        BridgingPathfinder.findAndBridgeToDiamondGenerator {
            // Bridging complete or failed
            val diamondGen = BridgingPathfinder.getSavedGeneratorPos()
            if (diamondGen != null) {
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Diamond generator located!")
                startDiamondPickup(diamondGen)
            } else {
                ChatUtils.error("Failed to locate diamond generator!")
                state = BotState.IDLE_AT_GEN
                hasCollectedDiamonds = true // Don't retry
            }
        }
    }
    
    /**
     * Pick up diamonds from the generator
     */
    private fun startDiamondPickup(diamondGenPos: BlockPos) {
        state = BotState.COLLECTING_DIAMONDS
        ChatUtils.info("${EnumChatFormatting.YELLOW}💎 Collecting diamonds...")
        
        // Stand at the diamond generator for a bit to collect
        TimeUtils.setTimeout({
            val initialDiamonds = Inventory.getItemCount("diamond")
            ChatUtils.info("${EnumChatFormatting.GRAY}Waiting for diamonds to spawn...")
            
            // Wait 10 seconds at the generator
            TimeUtils.setTimeout({
                val finalDiamonds = Inventory.getItemCount("diamond")
                val collected = finalDiamonds - initialDiamonds
                
                if (collected > 0) {
                    ChatUtils.info("${EnumChatFormatting.GREEN}✓ Collected $collected diamonds!")
                } else {
                    ChatUtils.info("${EnumChatFormatting.YELLOW}No diamonds collected (may need to wait longer)")
                }
                
                // Return to base
                hasCollectedDiamonds = true
                state = BotState.RETURNING_FROM_DIAMOND
                
                // The BridgingPathfinder already returns us to start position
                // Just wait for it to complete
                ChatUtils.info("${EnumChatFormatting.GREEN}✓ Diamond collection complete!")
                state = BotState.IDLE_AT_GEN
            }, 10000)
        }, 1000)
    }

    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (KeyBindings.toggleBotKeyBinding.isPressed) {
            toggle()
            ChatUtils.info("Bedwars Bot has been toggled ${if (toggled()) "${EnumChatFormatting.GREEN}on" else "${EnumChatFormatting.RED}off"}")
        }
    }

    @SubscribeEvent
    fun onJoinWorld(ev: EntityJoinWorldEvent) {
        if (BedwarsAFK.mc.thePlayer != null && ev.entity == BedwarsAFK.mc.thePlayer) {
            if (toggled()) {
                stopTimers()
                Movement.clearAll()
                Mouse.stopTracking()
                state = BotState.LOBBY
                isDefending = false
                isPurchasing = false
            }
        }
    }
}

