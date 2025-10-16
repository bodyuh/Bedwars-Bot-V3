package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Inventory
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.init.Blocks
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraft.world.World
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Advanced pathfinder with vertical climbing and block placement
 * Can tower up, bridge across gaps, and climb mountains
 */
object AdvancedPathfinder {

    data class Node(
        val pos: BlockPos,
        val parent: Node?,
        var g: Double,
        val h: Double,
        val needsBlockPlace: Boolean = false, // Requires placing a block
        val actionType: ActionType = ActionType.WALK
    ) : Comparable<Node> {
        val f: Double get() = g + h
        override fun compareTo(other: Node): Int = f.compareTo(other.f)
    }

    enum class ActionType {
        WALK,       // Normal walking
        JUMP,       // Jumping up
        TOWER,      // Tower up with blocks
        BRIDGE,     // Bridge across gap
        CLIMB       // Climb mountain
    }

    private var currentPath: List<Node>? = null
    private var currentPathIndex = 0
    private var isFollowingPath = false
    private var pathfindingTimer: Timer? = null
    private var lastBlockPlace = 0L
    private var lastRotationUpdate = 0L
    private var stuckCounter = 0
    private var lastPlayerPos: BlockPos? = null

    fun goTo(x: Int, y: Int, z: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        val start = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
        val end = BlockPos(x, y, z)

        stop()

        Thread {
            ChatUtils.info("Finding advanced path...")
            val path = findAdvancedPath(world, start, end)
            if (path != null && path.size > 1) {
                ChatUtils.info("Path found: ${path.size} steps (${path.count { it.needsBlockPlace }} blocks needed)")
                currentPath = path
                
                // Skip first waypoint if it's where we're standing
                val playerBlockPos = BlockPos(player.posX, player.posY - 1, player.posZ)
                if (path.isNotEmpty() && path[0].pos.distanceSq(playerBlockPos) < 4.0) {
                    ChatUtils.info("Skipping starting position")
                    currentPathIndex = 1
                } else {
                    currentPathIndex = 0
                }
                
                PathRenderer.setPath(path.map { it.pos })
                PathRenderer.setWaypointIndex(currentPathIndex)
                followAdvancedPath(onComplete)
            } else {
                ChatUtils.error("No path found")
                onComplete()
            }
        }.start()
    }

    fun stop() {
        isFollowingPath = false
        pathfindingTimer?.cancel()
        pathfindingTimer = null
        Movement.clearAll()
        Mouse.stopTracking()
        PathRenderer.clearPath()
        currentPath = null
    }

    private fun findAdvancedPath(world: World, start: BlockPos, end: BlockPos): List<Node>? {
        val openList = PriorityQueue<Node>()
        val closedSet = HashSet<BlockPos>()
        val openSet = HashMap<BlockPos, Node>()

        val startNode = Node(start, null, 0.0, heuristic(start, end))
        openList.add(startNode)
        openSet[start] = startNode

        var iterations = 0
        val maxIterations = 5000

        while (openList.isNotEmpty() && iterations < maxIterations) {
            iterations++
            
            if (iterations % 1000 == 0) {
                ChatUtils.info("Searching... $iterations nodes")
            }

            val current = openList.poll()
            openSet.remove(current.pos)

            // Found destination (more lenient - within 3 blocks)
            if (current.pos.distanceSq(end) < 9.0) {
                ChatUtils.info("Path found after $iterations iterations")
                return reconstructPath(current)
            }

            closedSet.add(current.pos)

            // Get neighbors including vertical movement
            for (neighbor in getAdvancedNeighbors(world, current)) {
                if (closedSet.contains(neighbor.pos)) continue

                val distance = current.pos.distanceSq(neighbor.pos)
                val blockPlacePenalty = if (neighbor.needsBlockPlace) 3.0 else 1.0 // Reduced penalty
                val tentativeG = current.g + sqrt(distance.toDouble()) * blockPlacePenalty

                val existingNode = openSet[neighbor.pos]
                if (existingNode != null && tentativeG >= existingNode.g) {
                    continue
                }

                val newNeighbor = Node(
                    neighbor.pos,
                    current,
                    tentativeG,
                    heuristic(neighbor.pos, end),
                    neighbor.needsBlockPlace,
                    neighbor.actionType
                )
                
                openSet[neighbor.pos] = newNeighbor
                openList.add(newNeighbor)
            }
        }

        ChatUtils.error("No path after $iterations iterations")
        return null
    }

    private fun getAdvancedNeighbors(world: World, current: Node): List<Node> {
        val neighbors = mutableListOf<Node>()
        val pos = current.pos

        // Check horizontal and diagonal movements (not pure vertical)
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    // Skip pure vertical - we'll handle that specially
                    if (dx == 0 && dz == 0 && dy > 0) continue

                    val neighborPos = pos.add(dx, dy, dz)
                    
                    if (!isWalkable(world, neighborPos)) {
                        // If not walkable and going up, try to tower
                        if (dy > 0) {
                            val canTower = canTowerTo(world, pos, neighborPos)
                            if (canTower) {
                                neighbors.add(Node(neighborPos, current, 0.0, 0.0, true, ActionType.TOWER))
                            }
                        }
                        continue
                    }

                    // Determine action type based on movement
                    val actionType = when {
                        dy > 0 -> ActionType.JUMP  // Going up diagonally
                        dy < 0 -> ActionType.WALK  // Going down
                        else -> ActionType.WALK    // Same level
                    }

                    neighbors.add(Node(neighborPos, current, 0.0, 0.0, false, actionType))
                }
            }
        }

        // Add vertical movement options (jumping for 1 block, towering for 2+ blocks)
        for (height in 1..10) {
            val upPos = pos.up(height)
            
            // Check if there's space
            var hasSpace = true
            for (i in 1..height) {
                val checkPos = pos.up(i)
                val blockAt = world.getBlockState(checkPos).block
                val blockAbove1 = world.getBlockState(checkPos.up()).block
                val blockAbove2 = world.getBlockState(checkPos.up(2)).block
                
                // Need air/space and headroom
                if (blockAt.material.isSolid || blockAbove1.material.isSolid || blockAbove2.material.isSolid) {
                    hasSpace = false
                    break
                }
            }
            
            if (hasSpace) {
                if (height == 1) {
                    // 1 block up = JUMP (no blocks needed)
                    neighbors.add(Node(upPos, current, 0.0, 0.0, false, ActionType.JUMP))
                } else {
                    // 2+ blocks up = TOWER (blocks needed)
                    neighbors.add(Node(upPos, current, 0.0, 0.0, true, ActionType.TOWER))
                }
            }
        }

        return neighbors
    }

    private fun isWalkable(world: World, pos: BlockPos): Boolean {
        val block = world.getBlockState(pos).block
        val blockAbove1 = world.getBlockState(pos.up()).block
        val blockAbove2 = world.getBlockState(pos.up(2)).block

        // Standing block should be solid OR air (for falling/jumping)
        val isSolidOrAir = block.material.isSolid || block.material == Material.air

        // Need headroom (2 blocks above should be passable)
        val hasHeadroom = !blockAbove1.material.isSolid && !blockAbove2.material.isSolid

        // Avoid lava and fire
        val isSafe = block.material != Material.lava && 
                     block.material != Material.fire &&
                     blockAbove1.material != Material.lava &&
                     blockAbove1.material != Material.fire

        return isSolidOrAir && hasHeadroom && isSafe
    }

    private fun canTowerTo(world: World, from: BlockPos, to: BlockPos): Boolean {
        // Check if we can tower from 'from' to 'to'
        val height = to.y - from.y
        if (height <= 0) return false

        // Check if path upward is clear
        for (i in 1..height) {
            val checkPos = from.up(i)
            val blockAbove1 = world.getBlockState(checkPos.up()).block
            val blockAbove2 = world.getBlockState(checkPos.up(2)).block
            
            // Need space for player
            if (blockAbove1.material.isSolid || blockAbove2.material.isSolid) {
                return false
            }
        }

        return true
    }

    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(from.x - to.x)
        val dy = abs(from.y - to.y)
        val dz = abs(from.z - to.z)
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    private fun reconstructPath(node: Node): List<Node> {
        val path = mutableListOf<Node>()
        var current: Node? = node
        while (current != null) {
            path.add(0, current)
            current = current.parent
        }
        return path
    }

    private fun followAdvancedPath(onComplete: () -> Unit) {
        isFollowingPath = true
        val path = currentPath ?: return

        ChatUtils.info("Following advanced path")

        var tickCount = 0
        var lastJumpTime = 0L
        var toweringInProgress = false
        stuckCounter = 0
        lastPlayerPos = null
        
        pathfindingTimer = Timer()
        val task = object : TimerTask() {
            override fun run() {
                try {
                    if (!isFollowingPath || currentPath == null) {
                        cancel()
                        return
                    }

                    val player = BedwarsAFK.mc.thePlayer
                    val world = BedwarsAFK.mc.theWorld
                    if (player == null || world == null) {
                        stop()
                        cancel()
                        onComplete()
                        return
                    }

                    if (currentPathIndex >= path.size) {
                        ChatUtils.info("Destination reached!")
                        stop()
                        cancel()
                        onComplete()
                        return
                    }

                    // Check if stuck (not moving)
                    val currentPlayerPos = BlockPos(player.posX, player.posY, player.posZ)
                    if (lastPlayerPos != null && lastPlayerPos == currentPlayerPos) {
                        stuckCounter++
                        if (stuckCounter > 40) { // Stuck for 2 seconds
                            ChatUtils.error("Stuck! Skipping waypoint")
                            currentPathIndex++
                            stuckCounter = 0
                            if (currentPathIndex >= path.size) {
                                stop()
                                cancel()
                                onComplete()
                            }
                            return
                        }
                    } else {
                        stuckCounter = 0
                    }
                    lastPlayerPos = currentPlayerPos

                    val currentNode = path[currentPathIndex]
                    val target = currentNode.pos
                    val diffX = target.x + 0.5 - player.posX
                    val diffZ = target.z + 0.5 - player.posZ
                    val diffY = target.y - player.posY
                    val distance = sqrt(diffX * diffX + diffZ * diffZ)

                    tickCount++
                    if (tickCount % 20 == 0) {
                        ChatUtils.info("Step ${currentPathIndex + 1}/${path.size} [${currentNode.actionType}] Dist=${"%.1f".format(distance)} DiffY=${"%.1f".format(diffY)}")
                        ChatUtils.info("Pos: ${player.posX.toInt()}, ${player.posY.toInt()}, ${player.posZ.toInt()} -> Target: ${target.x}, ${target.y}, ${target.z}")
                    }

                    // Check if reached waypoint (more lenient)
                    val reachedWaypoint = if (currentNode.actionType == ActionType.TOWER) {
                        // For towering, just check if we reached the Y level
                        abs(diffY) < 1.5 && distance < 1.5
                    } else {
                        // For other actions, check distance (don't require onGround for better flow)
                        distance < 1.2 && abs(diffY) < 1.5
                    }
                    
                    if (reachedWaypoint) {
                        ChatUtils.info("Reached waypoint ${currentPathIndex + 1}")
                        currentPathIndex++
                        PathRenderer.setWaypointIndex(currentPathIndex)
                        toweringInProgress = false
                        
                        if (currentPathIndex >= path.size) {
                            ChatUtils.info("Destination reached!")
                            stop()
                            cancel()
                            onComplete()
                            return
                        }
                        return
                    }

                    val currentTime = System.currentTimeMillis()

                    // Only update rotation every 150ms to prevent spinning
                    val shouldUpdateRotation = currentTime - lastRotationUpdate > 150

                    // Handle different action types
                    when (currentNode.actionType) {
                        ActionType.TOWER -> {
                            if (!toweringInProgress) {
                                toweringInProgress = true
                                ChatUtils.info("Towering up!")
                            }
                            
                            handleTowering(player, target, currentTime)
                        }
                        
                        ActionType.JUMP -> {
                            if (shouldUpdateRotation) {
                                handleJumping(player, target, currentTime, lastJumpTime)
                                lastRotationUpdate = currentTime
                            } else {
                                // Keep moving without updating rotation
                                Movement.startForward()
                                Movement.startSprinting()
                            }
                        }
                        
                        ActionType.WALK, ActionType.CLIMB -> {
                            if (shouldUpdateRotation) {
                                handleWalking(player, target, currentTime, lastJumpTime)
                                lastRotationUpdate = currentTime
                            } else {
                                // Keep moving without updating rotation
                                Movement.startForward()
                                Movement.startSprinting()
                            }
                        }
                        
                        ActionType.BRIDGE -> {
                            handleBridging(player, target, currentTime)
                        }
                    }

                } catch (e: Exception) {
                    ChatUtils.error("Error: ${e.message}")
                    e.printStackTrace()
                    stop()
                    cancel()
                    onComplete()
                }
            }
        }
        pathfindingTimer!!.scheduleAtFixedRate(task, 0, 50)
    }

    private fun handleTowering(player: net.minecraft.entity.player.EntityPlayer, target: BlockPos, currentTime: Long) {
        val diffY = target.y - player.posY
        
        ChatUtils.info("Towering: Target Y=${target.y}, Player Y=${player.posY.toInt()}, Diff=${"%.1f".format(diffY)}")
        
        // Look straight down to place blocks
        Mouse.lookAt(player.rotationYaw, 89f) // Almost straight down
        
        // Switch to blocks
        if (!switchToBlocks()) {
            ChatUtils.error("No blocks in hotbar to tower!")
            stop()
            return
        }
        
        // Tower up by placing blocks below
        if (player.onGround && currentTime - lastBlockPlace > 200) {
            // Start jumping
            Movement.startJumping()
            
            // Wait a moment to start rising
            Thread {
                Thread.sleep(50)
                
                // Place block below while in air
                Mouse.startRightClick()
                Thread.sleep(100)
                Mouse.stopRightClick()
                
                Thread.sleep(50)
                Movement.stopJumping()
            }.start()
            
            lastBlockPlace = currentTime
            ChatUtils.info("Placed block!")
        }
    }

    private fun handleJumping(player: net.minecraft.entity.player.EntityPlayer, target: BlockPos, currentTime: Long, lastJumpTime: Long) {
        // Look ahead to next waypoint for smoother movement
        val path = currentPath ?: return
        val lookAheadIndex = kotlin.math.min(currentPathIndex + 1, path.size - 1)
        val lookAheadTarget = path[lookAheadIndex].pos
        
        val diffX = lookAheadTarget.x + 0.5 - player.posX
        val diffZ = lookAheadTarget.z + 0.5 - player.posZ
        val diffY = target.y - player.posY
        val yaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f
        
        // Human-like pitch: slightly look up when jumping, down when falling
        val humanPitch = when {
            diffY > 0.5 -> best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(-5.0, 5.0).toFloat() // Going up, look slightly up
            diffY < -0.5 -> best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(5.0, 15.0).toFloat() // Going down, look slightly down
            else -> best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(-2.0, 2.0).toFloat() // Flat terrain, slight variation
        }
        
        Mouse.lookAt(yaw, humanPitch)
        
        // Move forward and sprint
        Movement.startForward()
        Movement.startSprinting()
        
        // Jump if needed
        val targetDiffY = target.y - player.posY
        if (player.onGround && (targetDiffY > 0.5 || player.isCollidedHorizontally)) {
            Movement.startJumping()
            Thread {
                Thread.sleep(100)
                Movement.stopJumping()
            }.start()
        }
    }

    private fun handleWalking(player: net.minecraft.entity.player.EntityPlayer, target: BlockPos, currentTime: Long, lastJumpTime: Long) {
        // Look ahead for smoother movement
        val path = currentPath ?: return
        val lookAheadIndex = kotlin.math.min(currentPathIndex + 1, path.size - 1)
        val lookAheadTarget = path[lookAheadIndex].pos
        
        val diffX = lookAheadTarget.x + 0.5 - player.posX
        val diffZ = lookAheadTarget.z + 0.5 - player.posZ
        val diffY = target.y - player.posY
        val yaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f
        
        // Human-like pitch: slight random variation
        val humanPitch = when {
            diffY > 0.3 -> best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(-5.0, 5.0).toFloat() // Going up slightly
            diffY < -0.3 -> best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(3.0, 10.0).toFloat() // Going down slightly
            else -> best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(-2.0, 3.0).toFloat() // Mostly flat
        }
        
        Mouse.lookAt(yaw, humanPitch)
        
        // Move
        Movement.startForward()
        Movement.startSprinting()
        
        // Jump over obstacles
        if ((diffY > 0.5 || player.isCollidedHorizontally) && player.onGround) {
            Movement.startJumping()
            Thread {
                Thread.sleep(100)
                Movement.stopJumping()
            }.start()
        }
    }

    private fun handleBridging(player: net.minecraft.entity.player.EntityPlayer, target: BlockPos, currentTime: Long) {
        // Look slightly down to place blocks
        val diffX = target.x + 0.5 - player.posX
        val diffZ = target.z + 0.5 - player.posZ
        val yaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f
        
        // Look down for bridging with slight variation
        val bridgePitch = best.bedwars.afk.utils.RandomUtils.randomDoubleInRange(55.0, 65.0).toFloat()
        Mouse.lookAt(yaw, bridgePitch)
        
        // Switch to blocks
        if (!switchToBlocks()) {
            ChatUtils.error("No blocks to bridge!")
            return
        }
        
        // Move forward slowly
        Movement.startForward()
        Movement.startSneaking() // Sneak to avoid falling
        
        // Place blocks
        if (currentTime - lastBlockPlace > 300) {
            Mouse.rClick(50)
            lastBlockPlace = currentTime
        }
    }

    private fun switchToBlocks(): Boolean {
        // Try to find blocks in hotbar
        val blockNames = listOf("wool", "clay", "terracotta", "concrete", "plank", "wood", "stone", "sandstone", "brick")
        
        for (blockName in blockNames) {
            if (Inventory.setInvItem(blockName)) {
                return true
            }
        }
        
        // Try any block item
        val player = BedwarsAFK.mc.thePlayer ?: return false
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i)
            if (stack != null) {
                val item = stack.item
                val block = Block.getBlockFromItem(item)
                if (block != null && block != Blocks.air) {
                    player.inventory.currentItem = i
                    return true
                }
            }
        }
        
        return false
    }
}

