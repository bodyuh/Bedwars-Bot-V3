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
 * Advanced 3D A* pathfinder that can navigate around walls and obstacles
 * Features:
 * - True 3D pathfinding with obstacle avoidance
 * - Can walk around walls instead of walking into them
 * - Proper ground detection
 * - Jump detection (1-2 blocks)
 * - Fall detection (safe falling down)
 * - Debug visualization with PathRenderer
 */
object AdvancedPathfinder {

    data class Node(
        val pos: BlockPos,
        val parent: Node?,
        var g: Double,
        val h: Double,
        val needsBlockPlace: Boolean = false,
        val actionType: ActionType = ActionType.WALK,
        val isMandatory: Boolean = false // For tight elevation areas - MUST step on this exact block
    ) : Comparable<Node> {
        val f: Double get() = g + h
        override fun compareTo(other: Node): Int = f.compareTo(other.f)
    }

    enum class ActionType {
        WALK,       // Normal walking
        JUMP,       // Jumping up 1-2 blocks
        FALL,       // Falling down safely
        SPRINT,     // Sprint jumping
        BRIDGE,     // Eagle bridging across void
        PILLAR_UP,  // Pillaring/towering up by placing blocks
        DROP_DOWN   // Dropping down < 4 blocks
    }

    private var currentPath: List<Node>? = null
    private var currentPathIndex = 0
    private var isFollowingPath = false
    private var pathfindingTimer: Timer? = null
    private var lastRotationUpdate = 0L
    private var stuckCounter = 0
    private var lastPlayerPos: BlockPos? = null
    private var debugMode = true // Enable debug messages
    private var bridgeModeActive = false
    private var bridgeExitTime = 0L
    private var isTransitioningFromBridge = false
    private var waitingForRotation = false
    private var bridgeSafetyNodesRemaining = 0 // Counter for +2 safety nodes after bridge section
    private var bridgeModeEnterTime = 0L // Track when bridge mode was activated
    private var bridgeValidationStartTime = 0L // Track when bridge completion validation started
    private var lastVoidWaypointLogged = -1 // Track last waypoint that logged void message to reduce spam
    private val bridgeValidationDelayMs = 200 // Delay before checking if bridge is complete (ms)
    private var cameraShakeOffset = 0.0 // Current camera shake offset for natural movement
    private var lastDescentWaypointLogged = -1 // Track last waypoint that logged descent message to reduce spam
    private var stuckRecoveryStartY = 0.0 // Track Y position when stuck recovery begins
    
    // Speedbridge tracking (for straight bridges only)
    private var speedbridgeCycle = 0 // 0 = sneak, 1 = unsneak
    private var lastSpeedbridgeToggle = 0L
    private var lastBlockCount = 0 // Track block count to detect placements
    private var blocksPlacedCount = 0 // Count successful block placements
    private var canSpeedbridge = false // Only true after first successful block placement
    
    // Configurable pathfinding parameters
    var pathRecomputeTimeoutSeconds = 2.0 // Time before recomputing path if waypoint not reached
    var denseVerticalWaypoints = true // Add intermediate nodes when climbing
    var sprintJumpMinDistance = 3 // Minimum horizontal distance to allow sprint jumping on Y changes
    var enableSpeedBridging = true // Safe speedbridge on straight bridges
    
    /**
     * Generate subtle camera shake for realistic movement
     */
    private fun applyCameraShake(shakeIntensity: Float = 0.02f): Float {
        // Use a simple oscillating function based on current time for consistent shake
        val timeMs = System.currentTimeMillis()
        val shakePattern = Math.sin((timeMs % 1000) / 1000.0 * Math.PI * 4) // Oscillate 4 times per second
        return (shakePattern * shakeIntensity).toFloat()
    }
    
    /**
     * Count blocks in hotbar to detect placements
     */
    private fun countBlocksInHotbar(): Int {
        val player = BedwarsAFK.mc.thePlayer ?: return 0
        var count = 0
        for (i in 0..8) {
            val stack = player.inventory.getStackInSlot(i)
            if (stack != null && stack.item is net.minecraft.item.ItemBlock) {
                count += stack.stackSize
            }
        }
        return count
    }

    fun goTo(x: Int, y: Int, z: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        // Use player's feet position as start
        val start = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
        val end = BlockPos(x, y - 1, z) // Target is also feet level

        stop()

        Thread {
            if (debugMode) {
                ChatUtils.info("§6[Pathfinder] §7Finding path...")
                ChatUtils.info("§7Start: §f${start.x}, ${start.y}, ${start.z}")
                ChatUtils.info("§7End: §f${end.x}, ${end.y}, ${end.z}")
                ChatUtils.info("§7Distance: §f${"%.1f".format(sqrt(start.distanceSq(end).toDouble()))} blocks")
            }
            
            val path = findPath3D(world, start, end)
            if (path != null && path.size > 1) {
                if (debugMode) {
                    ChatUtils.info("§a[Pathfinder] §7Path found: §f${path.size} §7steps")
                    
                    // Count action types
                    val walks = path.count { it.actionType == ActionType.WALK }
                    val jumps = path.count { it.actionType == ActionType.JUMP }
                    val falls = path.count { it.actionType == ActionType.FALL }
                    val sprints = path.count { it.actionType == ActionType.SPRINT }
                    val bridges = path.count { it.actionType == ActionType.BRIDGE }
                    
                    ChatUtils.info("§7Walk: §f$walks §7| Jump: §f$jumps §7| Fall: §f$falls §7| Sprint: §f$sprints §7| Bridge: §f$bridges")
                }
                
                currentPath = path
                
                // Skip first waypoint if we're already standing on it
                val playerBlockPos = BlockPos(player.posX, player.posY - 1, player.posZ)
                currentPathIndex = if (path.isNotEmpty() && path[0].pos.distanceSq(playerBlockPos) < 4.0) {
                    if (debugMode) ChatUtils.info("§7Skipping starting position")
                    1
                } else {
                    0
                }
                
                PathRenderer.setPath(path.map { it.pos })
                PathRenderer.setWaypointIndex(currentPathIndex)
                followPath(onComplete)
            } else {
                ChatUtils.error("§c[Pathfinder] No path found!")
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
        stuckCounter = 0
        lastPlayerPos = null
        bridgeModeActive = false
        bridgeExitTime = 0L
        isTransitioningFromBridge = false
        waitingForRotation = false
        bridgeSafetyNodesRemaining = 0
        bridgeModeEnterTime = 0L
        
        // CRITICAL: Stop eagle bridging completely if it was active
        // Must call stopBridging() directly to ensure all internal EagleBridge state is cleaned
        if (EagleBridge.isBridging()) {
            EagleBridge.stopBridging()
        }
    }

    /**
     * True 3D A* pathfinding with proper obstacle avoidance
     */
    private fun findPath3D(world: World, start: BlockPos, end: BlockPos): List<Node>? {
        val openList = PriorityQueue<Node>()
        val closedSet = HashSet<BlockPos>()
        val openSet = HashMap<BlockPos, Node>()

        val startNode = Node(start, null, 0.0, heuristic(start, end))
        openList.add(startNode)
        openSet[start] = startNode

        var iterations = 0
        val maxIterations = 20000 // Increased to 20k for complex paths with pillaring/dropping

        while (openList.isNotEmpty() && iterations < maxIterations) {
            iterations++
            
            if (debugMode && iterations % 2000 == 0) {
                ChatUtils.info("§7Searching... §f$iterations §7nodes explored")
            }

            val current = openList.poll()
            openSet.remove(current.pos)

            // Found destination (within 2 blocks)
            if (current.pos.distanceSq(end) < 4.0) {
                if (debugMode) {
                    ChatUtils.info("§a[Pathfinder] §7Found path after §f$iterations §7iterations")
                }
                return reconstructPath(current)
            }

            closedSet.add(current.pos)

            // Get all valid neighbors
            for (neighbor in getNeighbors3D(world, current)) {
                if (closedSet.contains(neighbor.pos)) continue

                val movementCost = calculateMovementCost(current.pos, neighbor.pos, neighbor.actionType)
                val tentativeG = current.g + movementCost

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

        if (debugMode) {
            ChatUtils.error("§c[Pathfinder] No path found after $iterations iterations")
        }
        return null
    }

    /**
     * Get all valid neighboring positions in 3D space
     * This is the KEY function that enables obstacle avoidance!
     * 
     * CRITICAL FIX: After bridging to solid ground, the algorithm must be able to recognize
     * that WALK paths are now viable from that position. This prevents the bot from being
     * locked into continued bridging when it reaches an island with solid ground.
     */
    private fun getNeighbors3D(world: World, current: Node): List<Node> {
        val neighbors = mutableListOf<Node>()
        val pos = current.pos

        // Cardinal directions (N, S, E, W) - Most common
        val cardinalDirections = listOf(
            BlockPos(1, 0, 0),   // East
            BlockPos(-1, 0, 0),  // West
            BlockPos(0, 0, 1),   // South
            BlockPos(0, 0, -1)   // North
        )

        // Diagonal directions (NE, SE, SW, NW)
        val diagonalDirections = listOf(
            BlockPos(1, 0, 1),   // SE
            BlockPos(1, 0, -1),  // NE
            BlockPos(-1, 0, 1),  // SW
            BlockPos(-1, 0, -1)  // NW
        )

        // CRITICAL: Detect if we just landed on solid ground after bridging
        // If current node is a BRIDGE and ground is now solid, enable normal WALK exploration
        val hasLandedOnSolidGround = current.actionType == ActionType.BRIDGE && 
                                      isPositionWalkable(world, pos)

        // Check all horizontal movements (same Y level) - WITH INTERMEDIATE WAYPOINTS
        for (dir in cardinalDirections + diagonalDirections) {
            val neighborPos = pos.add(dir)
            
            if (canWalkTo(world, pos, neighborPos)) {
                neighbors.add(Node(neighborPos, current, 0.0, 0.0, false, ActionType.WALK))
                
                // Add intermediate waypoints every 2-3 blocks for walking (finer granularity)
                if (denseVerticalWaypoints) {
                    for (distance in 2..3) {
                        val intermediatePos = pos.add(dir.x * distance, 0, dir.z * distance)
                        if (canWalkTo(world, pos, intermediatePos)) {
                            neighbors.add(Node(intermediatePos, current, 0.0, 0.0, false, ActionType.WALK))
                        }
                    }
                }
            }
            // Check jump up 1 block
            else if (canJumpTo(world, pos, neighborPos)) {
                val jumpPos = neighborPos.up()
                
                // If dense vertical waypoints enabled, add intermediate node at current Y level first
                if (denseVerticalWaypoints) {
                    // Add a "step" node at the base before going up - MANDATORY to step on
                    val stepBasePos = neighborPos
                    if (canWalkTo(world, pos, stepBasePos.down())) {
                        neighbors.add(Node(stepBasePos.down(), current, 0.0, 0.0, false, ActionType.WALK, isMandatory = true))
                    }
                }
                
                // Mark the elevated position as mandatory too - need precise foot placement
                neighbors.add(Node(jumpPos, current, 0.0, 0.0, false, ActionType.JUMP, isMandatory = true))
            }
            // Check jump up 2 blocks (sprint jump) - only if distance >= sprintJumpMinDistance
            // This requires jump + place block mechanic
            else if (canJumpTo(world, pos, neighborPos.up())) {
                val jumpPos = neighborPos.up(2)
                val horizontalDist = sqrt((dir.x * dir.x + dir.z * dir.z).toDouble())
                
                // Use JUMP instead of SPRINT if too close
                // For 2-block jumps, mark as needing block placement
                val actionType = if (horizontalDist < sprintJumpMinDistance) ActionType.JUMP else ActionType.SPRINT
                neighbors.add(Node(jumpPos, current, 0.0, 0.0, true, actionType, isMandatory = true))
            }
        }

        // Check falling down (1-4 blocks)
        for (dir in cardinalDirections + diagonalDirections) {
            for (fallDistance in 1..4) {
                val fallPos = pos.add(dir).down(fallDistance)
                if (canFallTo(world, pos, fallPos)) {
                    neighbors.add(Node(fallPos, current, 0.0, 0.0, false, ActionType.FALL))
                    break // Only add the first valid fall distance
                }
            }
        }

        // Check straight up (jumping in place)
        for (height in 1..2) {
            val upPos = pos.up(height)
            if (canJumpUp(world, pos, height)) {
                val actionType = if (height == 1) ActionType.JUMP else ActionType.SPRINT
                neighbors.add(Node(upPos, current, 0.0, 0.0, false, actionType))
            }
        }
        
        // Check pillaring up (placing blocks to climb vertically)
        // Only if we have blocks and need to go higher than 2 blocks
        if (EagleBridge.getBlockCount() > 0) {
            for (pillarHeight in 3..10) {
                val pillarPos = pos.up(pillarHeight)
                if (canPillarUp(world, pos, pillarHeight)) {
                    neighbors.add(Node(pillarPos, current, 0.0, 0.0, true, ActionType.PILLAR_UP, isMandatory = true))
                    break // Only add one pillar height
                }
            }
        }

        // Check straight down (falling in place)
        for (fallDistance in 1..4) {
            val downPos = pos.down(fallDistance)
            if (canFallDown(world, pos, fallDistance)) {
                neighbors.add(Node(downPos, current, 0.0, 0.0, false, ActionType.FALL))
                break
            }
        }
        
        // CRITICAL FIX: When transitioning from bridge to solid ground, search downward
        // to find the actual island walkable surface. This prevents being stuck at floating
        // block height and allows proper transition to WALK mode on the island.
        if (hasLandedOnSolidGround && current.actionType == ActionType.BRIDGE) {
            // Look down to find walkable ground (searching further than normal fall distance)
            for (fallDistance in 1..8) {
                val downPos = pos.down(fallDistance)
                
                // Check if this position is walkable
                if (isPositionWalkable(world, downPos)) {
                    // Verify path is clear between current and landing
                    var pathClear = true
                    for (i in 1..fallDistance) {
                        val checkPos = pos.down(i)
                        val feetBlock = world.getBlockState(checkPos).block
                        val headBlock = world.getBlockState(checkPos.up()).block
                        if (!isPassable(feetBlock) || !isPassable(headBlock)) {
                            pathClear = false
                            break
                        }
                    }
                    
                    if (pathClear) {
                        // Add as WALK node with lower cost to prefer direct descent to walkable ground
                        neighbors.add(Node(downPos, current, 0.0, 0.0, false, ActionType.WALK))
                        break // Found walkable island surface
                    }
                }
            }
        }
        
        // Check bridging across void (only cardinal directions for safety)
        for (dir in cardinalDirections) {
            // Try to bridge up to 10 blocks with INTERMEDIATE WAYPOINTS
            var lastValidBridgePos: BlockPos? = null
            for (bridgeDistance in 1..10) {
                val bridgePos = pos.add(dir.x * bridgeDistance, 0, dir.z * bridgeDistance)
                
                // Check if we can bridge to this position
                if (canBridgeTo(world, pos, bridgePos, bridgeDistance)) {
                    lastValidBridgePos = bridgePos
                    
                    // Add intermediate waypoints every 2-3 blocks for better control
                    if (bridgeDistance % 2 == 0 || bridgeDistance % 3 == 0) {
                        neighbors.add(Node(bridgePos, current, 0.0, 0.0, true, ActionType.BRIDGE))
                    }
                } else {
                    // Stop at first invalid position
                    break
                }
            }
            // Add final bridge position if we found valid bridges
            if (lastValidBridgePos != null) {
                neighbors.add(Node(lastValidBridgePos, current, 0.0, 0.0, true, ActionType.BRIDGE))
            }
        }

        return neighbors
    }
    
    /**
     * Check if we can bridge to a position across void
     */
    private fun canBridgeTo(world: World, from: BlockPos, to: BlockPos, distance: Int): Boolean {
        // Must have blocks in inventory to bridge
        if (EagleBridge.getBlockCount() == 0) return false
        
        // CRITICAL: Allow bridging 2-3 blocks BEFORE reaching the void
        // This gives safety margin and prevents falling off the edge
        var hasVoidBelow = false
        var distanceToVoid = 0
        
        // Look ahead in the direction of movement to find void
        for (i in 1..5) {
            val checkPos = from.add(
                if (to.x > from.x) i else if (to.x < from.x) -i else 0,
                0,
                if (to.z > from.z) i else if (to.z < from.z) -i else 0
            )
            
            var hasVoidAtThisPos = true
            for (j in 1..10) {
                if (world.getBlockState(checkPos.down(j)).block.material.isSolid) {
                    hasVoidAtThisPos = false
                    break
                }
            }
            
            if (hasVoidAtThisPos) {
                hasVoidBelow = true
                distanceToVoid = i
                break
            }
        }
        
        // Allow bridging if void is detected within 2-3 blocks ahead
        // This provides safety margin without activating too early
        if (!hasVoidBelow || distanceToVoid > 3) {
            return false
        }
        
        // Check if target position would be walkable (has ground or can place ground)
        val targetGround = world.getBlockState(to).block
        
        // If target already has ground, check if it's walkable
        if (targetGround.material.isSolid) {
            return isPositionWalkable(world, to)
        }
        
        // Check if path is clear (no blocks in the way)
        for (i in 1 until distance) {
            val dx = (to.x - from.x) / distance
            val dz = (to.z - from.z) / distance
            val checkPos = from.add(dx * i, 0, dz * i)
            
            val blockAt = world.getBlockState(checkPos).block
            val blockAbove = world.getBlockState(checkPos.up()).block
            
            // Path must be clear
            if (blockAt.material.isSolid || blockAbove.material.isSolid) return false
        }
        
        // Check if there's a solid landing spot
        for (i in 0..5) {
            val landingCheck = to.down(i)
            if (world.getBlockState(landingCheck).block.material.isSolid) {
                return i <= distance
            }
        }
        
        return true
    }

    /**
     * Check if we can walk horizontally to a position (same Y level)
     */
    private fun canWalkTo(world: World, from: BlockPos, to: BlockPos): Boolean {
        // Check if target position is walkable
        if (!isPositionWalkable(world, to)) return false

        // Strict forward collision check along the movement vector to avoid planning through thin walls
        val dx = to.x - from.x
        val dz = to.z - from.z
        val stepCount = Math.max(Math.abs(dx), Math.abs(dz))
        if (stepCount > 0) {
            val stepX = Integer.signum(dx)
            val stepZ = Integer.signum(dz)
            var cx = from.x
            var cz = from.z
            for (i in 1..stepCount) {
                cx += stepX
                cz += stepZ
                val check = BlockPos(cx, from.y, cz)
                val feet = world.getBlockState(check).block
                val head = world.getBlockState(check.up()).block
                if (!isPassable(feet) || !isPassable(head)) return false
            }
        }

        // Check for diagonal movement collision
        val dx2 = to.x - from.x
        val dz2 = to.z - from.z
        
        // If moving diagonally, check both cardinal directions are clear
        if (dx2 != 0 && dz2 != 0) {
            // Moving diagonally - need to check both sides
            // Example: Moving NE (1, 0, -1) needs to check N (0, 0, -1) and E (1, 0, 0)
            
            val sideX = from.add(dx2, 0, 0) // Check X direction
            val sideZ = from.add(0, 0, dz2) // Check Z direction
            
            // Check if either side is blocked at feet or head level
            val blockSideX = world.getBlockState(sideX).block
            val blockSideXAbove = world.getBlockState(sideX.up()).block
            val blockSideZ = world.getBlockState(sideZ).block
            val blockSideZAbove = world.getBlockState(sideZ.up()).block
            
            // If either cardinal direction is blocked, can't move diagonally
            val xBlocked = blockSideX.material.isSolid || blockSideXAbove.material.isSolid
            val zBlocked = blockSideZ.material.isSolid || blockSideZAbove.material.isSolid
            
            if (xBlocked || zBlocked) return false
        }

        return true
    }

    /**
     * Check if we can jump to a position (1 block up)
     */
    private fun canJumpTo(world: World, from: BlockPos, to: BlockPos): Boolean {
        // Check if target position is walkable
        if (!isPositionWalkable(world, to)) return false

        // Check if there's headroom to jump
        val blockAbove1 = world.getBlockState(from.up(2)).block
        val blockAbove2 = world.getBlockState(from.up(3)).block
        
        if (blockAbove1.material.isSolid || blockAbove2.material.isSolid) return false

        // Check if target has headroom
        val targetAbove1 = world.getBlockState(to.up()).block
        val targetAbove2 = world.getBlockState(to.up(2)).block
        
        if (targetAbove1.material.isSolid || targetAbove2.material.isSolid) return false

        // Check for diagonal movement collision (same as canWalkTo)
        val dx = to.x - from.x
        val dz = to.z - from.z
        
        // If moving diagonally, check both cardinal directions are clear
        if (dx != 0 && dz != 0) {
            val sideX = from.add(dx, 0, 0)
            val sideZ = from.add(0, 0, dz)
            
            // Check both ground level and above for both sides
            val blockSideX = world.getBlockState(sideX).block
            val blockSideXAbove = world.getBlockState(sideX.up()).block
            val blockSideZ = world.getBlockState(sideZ).block
            val blockSideZAbove = world.getBlockState(sideZ.up()).block
            
            val xBlocked = blockSideX.material.isSolid || blockSideXAbove.material.isSolid
            val zBlocked = blockSideZ.material.isSolid || blockSideZAbove.material.isSolid
            
            if (xBlocked || zBlocked) return false
        }

        return true
    }

    /**
     * Check if we can fall to a position
     */
    private fun canFallTo(world: World, from: BlockPos, to: BlockPos): Boolean {
        // Check if target position is walkable
        if (!isPositionWalkable(world, to)) return false

        // Make sure all blocks between from and to are passable (no floors in the way)
        val yDiff = from.y - to.y
        for (i in 1 until yDiff) {
            val checkPos = from.down(i)
            val block = world.getBlockState(checkPos).block
            if (block.material.isSolid) return false // Hit a floor
        }

        // Check for diagonal movement collision
        val dx = to.x - from.x
        val dz = to.z - from.z
        
        // If moving diagonally while falling, check both cardinal directions
        if (dx != 0 && dz != 0) {
            val sideX = from.add(dx, 0, 0)
            val sideZ = from.add(0, 0, dz)
            
            val blockSideX = world.getBlockState(sideX).block
            val blockSideXAbove = world.getBlockState(sideX.up()).block
            val blockSideZ = world.getBlockState(sideZ).block
            val blockSideZAbove = world.getBlockState(sideZ.up()).block
            
            val xBlocked = blockSideX.material.isSolid || blockSideXAbove.material.isSolid
            val zBlocked = blockSideZ.material.isSolid || blockSideZAbove.material.isSolid
            
            if (xBlocked || zBlocked) return false
        }

        return true
    }

    /**
     * Check if we can jump straight up
     */
    private fun canJumpUp(world: World, pos: BlockPos, height: Int): Boolean {
        // Check all blocks in the jump path
        for (i in 1..height) {
            val checkPos = pos.up(i)
            // Need 2 blocks of space at each level
            val block = world.getBlockState(checkPos).block
            val blockAbove = world.getBlockState(checkPos.up()).block
            
            if (block.material.isSolid || blockAbove.material.isSolid) return false
        }

        // Check if target position is walkable
        return isPositionWalkable(world, pos.up(height))
    }

    /**
     * Check if we can fall straight down
     */
    private fun canFallDown(world: World, pos: BlockPos, fallDistance: Int): Boolean {
        // Check all blocks in the fall path
        for (i in 1 until fallDistance) {
            val checkPos = pos.down(i)
            val block = world.getBlockState(checkPos).block
            if (block.material.isSolid) return false // Hit a floor
        }

        // Check if landing position is walkable
        return isPositionWalkable(world, pos.down(fallDistance))
    }
    
    /**
     * Check if we can pillar up by placing blocks
     */
    private fun canPillarUp(world: World, pos: BlockPos, height: Int): Boolean {
        // Must have blocks to place
        if (EagleBridge.getBlockCount() == 0) return false
        
        // Check all blocks in the pillar path
        for (i in 1..height) {
            val checkPos = pos.up(i)
            // Need 2 blocks of space at each level (for player + head)
            val block = world.getBlockState(checkPos).block
            val blockAbove = world.getBlockState(checkPos.up()).block
            
            // Must have air to place blocks into
            if (block.material.isSolid || blockAbove.material.isSolid) return false
        }
        
        // Target position should have space (we'll place blocks below it)
        val targetPos = pos.up(height)
        val feetBlock = world.getBlockState(targetPos).block
        val headBlock = world.getBlockState(targetPos.up()).block
        
        return isPassable(feetBlock) && isPassable(headBlock)
    }

    /**
     * Check if a position is walkable (has solid ground and passable air above)
     */
    private fun isPositionWalkable(world: World, pos: BlockPos): Boolean {
        // Ground is the block BELOW the standing position
        val groundBlock = world.getBlockState(pos.down()).block
        // Feet occupy the standing block; head is one above
        val feetBlock = world.getBlockState(pos).block
        val headBlock = world.getBlockState(pos.up()).block

        // Must have solid ground to stand on
        val hasSolidGround = groundBlock.material.isSolid

        // Must have passable space for player hitbox (2 blocks tall)
        val hasSpace = isPassable(feetBlock) && isPassable(headBlock)

        // Must not be dangerous
        val isSafe = !isDangerous(groundBlock) && !isDangerous(feetBlock) && !isDangerous(headBlock)

        return hasSolidGround && hasSpace && isSafe
    }

    /**
     * Check if a block is passable (player can move through it)
     */
    private fun isPassable(block: Block): Boolean {
        // Air, plants, liquids (water), signs, torches, etc are passable
        return block.material == Material.air ||
               block.material == Material.plants ||
               block.material == Material.vine ||
               block.material == Material.water || // Allow water
               block == Blocks.tallgrass ||
               block == Blocks.deadbush ||
               block == Blocks.yellow_flower ||
               block == Blocks.red_flower ||
               block == Blocks.snow_layer ||
               block == Blocks.carpet ||
               block == Blocks.torch ||
               block == Blocks.redstone_torch ||
               block == Blocks.ladder ||
               block == Blocks.wall_sign ||
               block == Blocks.standing_sign
    }

    /**
     * Check if a block is dangerous
     */
    private fun isDangerous(block: Block): Boolean {
        return block.material == Material.lava ||
               block.material == Material.fire ||
               block == Blocks.cactus
    }

    /**
     * Calculate movement cost based on action type
     */
    private fun calculateMovementCost(from: BlockPos, to: BlockPos, actionType: ActionType): Double {
        val distance = sqrt(from.distanceSq(to).toDouble())
        
        return when (actionType) {
            ActionType.WALK -> distance * 1.0      // Normal walking
            ActionType.JUMP -> distance * 1.2      // Slight penalty for jumping
            ActionType.SPRINT -> distance * 1.5    // Higher penalty for sprint jumping
            ActionType.FALL -> distance * 0.8      // Falling is faster
            ActionType.BRIDGE -> distance * 2.0    // Bridging is slow and risky
            ActionType.PILLAR_UP -> distance * 2.5 // Pillaring is slow but reliable
            ActionType.DROP_DOWN -> distance * 0.7 // Dropping is fast but risky
        }
    }

    /**
     * Heuristic function (Euclidean distance)
     */
    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(from.x - to.x)
        val dy = abs(from.y - to.y)
        val dz = abs(from.z - to.z)
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    /**
     * Reconstruct path from end node
     */
    private fun reconstructPath(node: Node): List<Node> {
        val path = mutableListOf<Node>()
        var current: Node? = node
        while (current != null) {
            path.add(0, current)
            current = current.parent
        }
        return simplifyPath(path)
    }

    /**
     * Simplify path by removing unnecessary waypoints
     * - Keeps waypoints in complex areas (turns, jumps, obstacles)
     * - Removes waypoints on straight paths (bridges, corridors)
     */
    private fun simplifyPath(path: List<Node>): List<Node> {
        if (path.size <= 2) return path
        
        val simplified = mutableListOf<Node>()
        simplified.add(path[0]) // Always keep start
        
        var i = 0
        while (i < path.size - 1) {
            val current = path[i]
            var lookAhead = i + 1
            
            // Try to skip as many waypoints as possible while maintaining a straight line
            while (lookAhead < path.size - 1) {
                val target = path[lookAhead]
                
                // Check if we should keep this waypoint
                val shouldKeep = shouldKeepWaypoint(path, i, lookAhead)
                
                if (shouldKeep) {
                    break // Stop here and add this waypoint
                }
                
                lookAhead++
            }
            
            // Add the waypoint we found
            if (lookAhead < path.size) {
                simplified.add(path[lookAhead])
                i = lookAhead
            } else {
                break
            }
        }
        
        // Always keep end
        if (simplified.isEmpty() || simplified.last() != path.last()) {
            simplified.add(path.last())
        }
        
        if (debugMode) {
            ChatUtils.info("§7Path simplified: §f${path.size} §7→ §f${simplified.size} §7waypoints")
        }
        
        return simplified
    }

    /**
     * Determine if a waypoint should be kept based on path complexity
     */
    private fun shouldKeepWaypoint(path: List<Node>, startIdx: Int, checkIdx: Int): Boolean {
        val start = path[startIdx]
        val check = path[checkIdx]
        
        // 1. Always keep waypoints with different action types (jump, fall, etc.)
        if (check.actionType != start.actionType) return true
        
        // 2. Check if there's a direction change (turn)
        if (checkIdx > startIdx + 1) {
            val prev = path[checkIdx - 1]
            
            // Calculate direction vectors
            val dir1X = check.pos.x - prev.pos.x
            val dir1Z = check.pos.z - prev.pos.z
            val dir2X = prev.pos.x - start.pos.x
            val dir2Z = prev.pos.z - start.pos.z
            
            // If direction changed significantly (not same direction), keep waypoint
            if (dir1X != dir2X || dir1Z != dir2Z) {
                // Allow small alignment adjustments on straight paths
                val straightPath = (dir1X == 0 && dir2X == 0) || (dir1Z == 0 && dir2Z == 0)
                if (!straightPath) return true
            }
        }
        
        // 3. Check if there's a height change
        if (check.pos.y != start.pos.y) return true
        
        // 4. Keep waypoints every 5 blocks max to maintain some control
        val distance = sqrt(
            Math.pow((check.pos.x - start.pos.x).toDouble(), 2.0) +
            Math.pow((check.pos.z - start.pos.z).toDouble(), 2.0)
        )
        if (distance > 5.0) return true
        
        // Otherwise, this waypoint can be skipped
        return false
    }

    /**
     * Follow the calculated path
     */
    private fun followPath(onComplete: () -> Unit) {
        isFollowingPath = true
        var path = currentPath?.toMutableList() ?: return

        if (debugMode) {
            ChatUtils.info("§6[Pathfinder] §7Following path")
        }

        var tickCount = 0
        var lastJumpTime = 0L
        var lastStuckCheckPos: BlockPos? = null
        var ticksSinceLastMove = 0
        var waypointStartTime = System.currentTimeMillis()
        var lastWaypointIndex = currentPathIndex
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
                        if (debugMode) ChatUtils.info("§a[Pathfinder] §7Destination reached!")
                        stop()
                        cancel()
                        onComplete()
                        return
                    }

                    // Continuous stuck detection: if player hasn't moved for 20 ticks (1 second)
                    // DISABLED during bridging to avoid interference
                    if (!bridgeModeActive) {
                        val currentBlockPos = BlockPos(player.posX, player.posY, player.posZ)
                        if (lastStuckCheckPos == null || currentBlockPos != lastStuckCheckPos) {
                            lastStuckCheckPos = currentBlockPos
                            ticksSinceLastMove = 0
                        } else {
                            ticksSinceLastMove++
                            // If stuck for 20 ticks and on ground, place block under and force jump
                            if (ticksSinceLastMove >= 20 && player.onGround && System.currentTimeMillis() - lastJumpTime > 500) {
                                if (debugMode) ChatUtils.info("§e[Pathfinder] §7Stuck detected - looking down, placing block, jumping")
                                
                                // Store current rotation FIRST
                                val originalYaw = player.rotationYaw
                                val originalPitch = player.rotationPitch
                                
                                // Look down and try to place a block under the player to help escape
                                val blockUnder = BlockPos(player.posX, player.posY - 1, player.posZ)
                                if (world.getBlockState(blockUnder).block.material == Material.air) {
                                    // Switch to blocks
                                    if (EagleBridge.switchToBlocks()) {
                                        // FIRST: Look straight down
                                        player.rotationPitch = 90f
                                        Thread.sleep(100) // Wait longer for rotation to fully apply
                                        
                                        // SECOND: Place block
                                        player.swingItem()
                                        Mouse.rClick()
                                        Thread.sleep(100) // Wait for server to process placement
                                        
                                        // THIRD: Jump while looking down (more effective)
                                        player.jump()
                                        Thread.sleep(50)
                                        
                                        // FOURTH: Restore rotation
                                        player.rotationYaw = originalYaw
                                        player.rotationPitch = originalPitch
                                    }
                                } else {
                                    // Block already there, just jump
                                    player.jump()
                                }
                                
                                lastJumpTime = System.currentTimeMillis()
                                ticksSinceLastMove = 0
                            }
                        }
                    }
                    
                    // Path recomputation: if waypoint hasn't been reached in timeout, recompute
                    if (currentPathIndex != lastWaypointIndex) {
                        // Advanced to new waypoint, reset timer
                        waypointStartTime = System.currentTimeMillis()
                        lastWaypointIndex = currentPathIndex
                    } else {
                        // Check if timeout exceeded (but NOT during bridging)
                        val elapsed = (System.currentTimeMillis() - waypointStartTime) / 1000.0
                        if (elapsed >= pathRecomputeTimeoutSeconds && !bridgeModeActive) {
                            if (debugMode) ChatUtils.info("§e[Pathfinder] §7Waypoint timeout - recomputing path")
                            // Recompute path from current position
                            val start = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
                            val originalEnd = path[path.size - 1].pos
                            cancel()
                            stop()
                            Thread {
                                val newPath = findPath3D(world, start, originalEnd)
                                if (newPath != null && newPath.size > 1) {
                                    currentPath = newPath
                                    currentPathIndex = 0
                                    PathRenderer.setPath(newPath.map { it.pos })
                                    PathRenderer.setWaypointIndex(0)
                                    followPath(onComplete)
                                } else {
                                    if (debugMode) ChatUtils.error("§c[Pathfinder] Recomputation failed")
                                    onComplete()
                                }
                            }.start()
                            return
                        }
                    }

                    // Handle transition pause from bridge mode
                    if (isTransitioningFromBridge) {
                        val transitionElapsed = System.currentTimeMillis() - bridgeExitTime
                        if (transitionElapsed < 500) {
                            // Still in 500ms pause - stay completely still (no sneaking)
                            // During this phase, check for platforms or safe drop zones for exit actions
                            Movement.clearAll()
                            
                            // Check for platform below
                            val platformCheckPos1 = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
                            val platformCheckPos2 = BlockPos(player.posX.toInt(), player.posY.toInt() - 2, player.posZ.toInt())
                            val platformBlock1 = world.getBlockState(platformCheckPos1).block
                            val platformBlock2 = world.getBlockState(platformCheckPos2).block
                            
                            val foundPlatformBelow = (platformBlock1.material.isSolid || platformBlock2.material.isSolid)
                            
                            if (foundPlatformBelow) {
                                if (debugMode) ChatUtils.info("§2[Exit Action] §7Platform detected below during transition")
                                // Platform waypoint would be inserted here if needed
                            } else {
                                // No platform - check for safe drop zone
                                var canDropSafely = false
                                var safeDropDistance = 0
                                for (dropDistance in 1..4) {
                                    val checkPos = BlockPos(player.posX.toInt(), player.posY.toInt() - dropDistance, player.posZ.toInt())
                                    val checkBlock = world.getBlockState(checkPos).block
                                    val checkBlockBelow = world.getBlockState(checkPos.down()).block
                                    
                                    // Found solid ground within 1 block below
                                    if (checkBlockBelow.material.isSolid && checkBlock == Blocks.air) {
                                        canDropSafely = true
                                        safeDropDistance = dropDistance
                                        break
                                    }
                                }
                                
                                if (debugMode) {
                                    if (canDropSafely) {
                                        ChatUtils.info("§2[Exit Action] §7Safe drop found at ${safeDropDistance} block(s) below")
                                    } else {
                                        ChatUtils.info("§e[Exit Action] ⚠ §7No safe drop zone found within 4 blocks")
                                    }
                                }
                            }
                            
                            return
                        } else {
                            // Pause complete - unshift and start waiting for rotation
                            isTransitioningFromBridge = false
                            waitingForRotation = true
                            EagleBridge.stopBridging()
                            if (debugMode) ChatUtils.info("§6[Pathfinder] §7Unshifted, waiting for rotation...")
                        }
                    }
                    
                    // After unshifting, wait for rotation to complete before moving
                    if (waitingForRotation) {
                        val currentNode = path[currentPathIndex]
                        val target = currentNode.pos
                        val diffX = target.x + 0.5 - player.posX
                        val diffZ = target.z + 0.5 - player.posZ
                        val targetYaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f
                        
                        val currentYaw = player.rotationYaw
                        var yawDiff = targetYaw - currentYaw
                        while (yawDiff > 180f) yawDiff -= 360f
                        while (yawDiff < -180f) yawDiff += 360f
                        
                        // Don't move until facing target (within 10 degrees)
                        if (Math.abs(yawDiff) > 10f) {
                            // Still rotating - use smooth interpolation (20% per tick, same as normal movement)
                            Movement.clearAll()
                            player.rotationYaw = currentYaw + (yawDiff * 0.2f)
                            if (debugMode) ChatUtils.info("§6[Pathfinder] §7Rotating... ${yawDiff.toInt()}° remaining")
                            return
                        }
                        
                        // Rotation complete - allow movement
                        waitingForRotation = false
                        if (debugMode) ChatUtils.info("§6[Pathfinder] §7Rotation complete, moving")
                    }

                    // Check if stuck (not moving)
                    val currentPlayerPos = BlockPos(player.posX, player.posY, player.posZ)
                    if (lastPlayerPos != null && lastPlayerPos == currentPlayerPos) {
                        stuckCounter++
                        
                        // If stuck during bridging, attempt to recover with position-based strafing
                        if (bridgeModeActive && stuckCounter > 20) { // Stuck for 1 second
                            if (debugMode && stuckCounter == 21) {
                                ChatUtils.info("§6[Pathfinder] §7Stuck during bridge - attempting recovery")
                                stuckRecoveryStartY = player.posY // Record Y position when recovery starts
                            }
                            
                            // Check if player has risen (successful backward jump detected)
                            if (player.posY > stuckRecoveryStartY + 0.1 && stuckCounter < 50) {
                                if (debugMode) {
                                    ChatUtils.info("§a[JUMP SUCCESS] §7Successful backward jump detected (Y change: ${String.format("%.2f", player.posY - stuckRecoveryStartY)}) - exiting bridge mode and checking for platforms")
                                }
                                Movement.clearAll()
                                EagleBridge.stopBridging()
                                bridgeModeActive = false
                                isTransitioningFromBridge = true
                                bridgeExitTime = System.currentTimeMillis()
                                stuckCounter = 0
                                return
                            }
                            
                            // First, check if there's a safe block BEHIND the player (in the direction of travel)
                            // Since we bridge backward, we need to check blocks in the direction we're traveling toward
                            val currentNode = path[currentPathIndex]
                            val travelDirX = if (currentPathIndex + 1 < path.size) {
                                val nextNode = path[currentPathIndex + 1]
                                nextNode.pos.x - currentNode.pos.x
                            } else {
                                0
                            }
                            val travelDirZ = if (currentPathIndex + 1 < path.size) {
                                val nextNode = path[currentPathIndex + 1]
                                nextNode.pos.z - currentNode.pos.z
                            } else {
                                0
                            }
                            
                            // Normalize travel direction
                            val travelDist = Math.sqrt((travelDirX * travelDirX + travelDirZ * travelDirZ).toDouble())
                            val normalizedTravelX = if (travelDist > 0) (travelDirX / travelDist).toInt() else 0
                            val normalizedTravelZ = if (travelDist > 0) (travelDirZ / travelDist).toInt() else 0
                            
                            val behindBlockPos = BlockPos(
                                player.posX.toInt() + normalizedTravelX,
                                player.posY.toInt() - 1,
                                player.posZ.toInt() + normalizedTravelZ
                            )
                            val behindBlock = world.getBlockState(behindBlockPos).block
                            
                            if (debugMode && stuckCounter == 21) {
                                ChatUtils.info("§6[Stuck Debug] §7Behind block check: ${behindBlock.getLocalizedName()} at (${behindBlockPos.x}, ${behindBlockPos.y}, ${behindBlockPos.z}) | TravelDir: (${normalizedTravelX},${normalizedTravelZ}) | OnGround: ${player.onGround} | InWater: ${player.isInWater}")
                            }
                            
                            // If there's a solid block behind and player is not falling, try to jump back
                            if (behindBlock != Blocks.air && !player.isInWater && player.onGround) {
                                if (debugMode && stuckCounter == 21) {
                                    ChatUtils.info("§a[FIRST RECOVERY] §7Found solid block ahead in travel direction at (${behindBlockPos.x}, ${behindBlockPos.y}, ${behindBlockPos.z}) - jumping backwards while bridging")
                                }
                                Movement.jump()
                                // Stay in bridge mode - continue backward movement (S key)
                                Movement.stopForward()
                                Movement.startBackward()
                                return
                            } else if (behindBlock != Blocks.air && stuckCounter > 50) {
                                // Keep trying to jump backward if stuck for extended period
                                if (stuckCounter == 51) {
                                    ChatUtils.info("§b[PERSISTENT RETRY] §7Solid block ahead in travel direction still present after ${stuckCounter} ticks - retrying jump every 10 ticks (staying in bridge mode)")
                                }
                                if (stuckCounter % 10 == 0) {
                                    if (debugMode) {
                                        ChatUtils.info("§b[PERSISTENT RETRY] §7Retry attempt at tick $stuckCounter")
                                    }
                                    Movement.jump()
                                }
                                // Stay in bridge mode - continue backward movement (S key)
                                Movement.stopForward()
                                Movement.startBackward()
                                return
                            }
                            
                            // If no block behind or jumping failed, use strafe recovery
                            if (debugMode && stuckCounter == 21 && (behindBlock == Blocks.air || player.isInWater || !player.onGround)) {
                                ChatUtils.info("§c[FALLBACK RECOVERY] §7No safe block behind (or conditions not met) - using waypoint-based strafing")
                            }
                            
                            // CHECK: Before strafing, see if there's a platform below we can drop to
                            val blockUnderPos = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
                            var foundPlatformBelow = false
                            var platformY = blockUnderPos.y
                            var platformX = blockUnderPos.x
                            var platformZ = blockUnderPos.z
                            
                            for (dropDist in 1..2) {
                                val platformCheckY = blockUnderPos.y - dropDist
                                
                                // Check if this level has any solid block in 3x3 area around player's position
                                var hasSolidPlatformBlock = false
                                var platformCheckPos = BlockPos.ORIGIN
                                
                                for (dx in -1..1) {
                                    for (dz in -1..1) {
                                        val checkX = blockUnderPos.x + dx
                                        val checkZ = blockUnderPos.z + dz
                                        val checkPos = BlockPos(checkX, platformCheckY, checkZ)
                                        val blockAtPos = world.getBlockState(checkPos).block
                                        val blockBelow = world.getBlockState(checkPos.down()).block
                                        
                                        // Found a solid block with solid ground beneath it
                                        if (blockAtPos != Blocks.air && blockBelow.material.isSolid) {
                                            hasSolidPlatformBlock = true
                                            platformCheckPos = checkPos
                                            break
                                        }
                                    }
                                    if (hasSolidPlatformBlock) break
                                }
                                
                                if (hasSolidPlatformBlock) {
                                    // Verify there's a clear 3x3 path from current position to platform
                                    var isPathClear = true
                                    for (intermediateY in blockUnderPos.y - 1 until platformCheckY) {
                                        for (dx in -1..1) {
                                            for (dz in -1..1) {
                                                val checkX = blockUnderPos.x + dx
                                                val checkZ = blockUnderPos.z + dz
                                                val blockCheck = world.getBlockState(BlockPos(checkX, intermediateY, checkZ)).block
                                                if (blockCheck != Blocks.air) {
                                                    isPathClear = false
                                                    break
                                                }
                                            }
                                            if (!isPathClear) break
                                        }
                                        if (!isPathClear) break
                                    }
                                    
                                    if (isPathClear) {
                                        foundPlatformBelow = true
                                        platformY = platformCheckY
                                        platformX = platformCheckPos.x
                                        platformZ = platformCheckPos.z
                                        if (debugMode && stuckCounter == 21) {
                                            ChatUtils.info("§6[Stuck Recovery] §7Found safe platform below at (${platformX}, ${platformY}, ${platformZ}) - exiting bridge mode")
                                        }
                                        break
                                    }
                                }
                            }
                            
                            // If platform found below, exit bridge mode to take it
                            if (foundPlatformBelow) {
                                Movement.clearAll()
                                EagleBridge.stopBridging()
                                bridgeModeActive = false
                                stuckCounter = 0 // Reset stuck counter
                                if (debugMode) ChatUtils.info("§a[Stuck Recovery] §7Exiting bridge mode to land on platform")
                                return
                            }
                            
                            // Create a strafe target waypoint 1 block ahead in direction of next waypoint
                            var strafeTargetX = currentNode.pos.x.toDouble()
                            var strafeTargetY = currentNode.pos.y.toDouble()
                            var strafeTargetZ = currentNode.pos.z.toDouble()
                            
                            // If there's a next waypoint, calculate direction to it (including elevation)
                            if (currentPathIndex + 1 < path.size) {
                                val nextNode = path[currentPathIndex + 1]
                                val dirX = nextNode.pos.x - currentNode.pos.x
                                val dirY = nextNode.pos.y - currentNode.pos.y
                                val dirZ = nextNode.pos.z - currentNode.pos.z
                                val distance = Math.sqrt((dirX * dirX + dirY * dirY + dirZ * dirZ).toDouble())
                                
                                if (distance > 0) {
                                    // Normalize and move 1 block in that direction (including elevation)
                                    val normX = dirX / distance
                                    val normY = dirY / distance
                                    val normZ = dirZ / distance
                                    strafeTargetX = currentNode.pos.x + normX
                                    strafeTargetY = currentNode.pos.y + normY
                                    strafeTargetZ = currentNode.pos.z + normZ
                                }
                            }
                            
                            // Calculate strafe target center (0.5, 0.5)
                            val strafeTargetCenterX = strafeTargetX + 0.5
                            val strafeTargetCenterZ = strafeTargetZ + 0.5
                            
                            // Check player offset from strafe target center
                            val offsetX = player.posX - strafeTargetCenterX
                            val offsetZ = player.posZ - strafeTargetCenterZ
                            val distanceFromTarget = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ)
                            
                            // Get player facing direction for strafe calculation
                            val playerYaw = Math.toRadians(player.rotationYaw.toDouble())
                            
                            // If more than 0.3 blocks off-target, strafe toward it
                            if (distanceFromTarget > 0.3) {
                                // Determine strafe direction based on player's facing
                                val facingX = Math.cos(playerYaw)
                                val facingZ = Math.sin(playerYaw)
                                
                                // Cross product tells us left/right relative to facing
                                val crossProduct = (offsetX * facingZ - offsetZ * facingX)
                                
                                if (crossProduct > 0.1) {
                                    // Player is to the right - strafe left toward target
                                    if (debugMode && stuckCounter % 10 == 0) {
                                        ChatUtils.info("§c[FALLBACK RECOVERY] §7Strafing LEFT toward target (offset=${String.format("%.2f", distanceFromTarget)})")
                                    }
                                    Movement.startLeft()
                                    Movement.stopRight()
                                } else if (crossProduct < -0.1) {
                                    // Player is to the left - strafe right toward target
                                    if (debugMode && stuckCounter % 10 == 0) {
                                        ChatUtils.info("§c[FALLBACK RECOVERY] §7Strafing RIGHT toward target (offset=${String.format("%.2f", distanceFromTarget)})")
                                    }
                                    Movement.stopLeft()
                                    Movement.startRight()
                                } else {
                                    // Nearly centered on target
                                    if (debugMode && stuckCounter % 10 == 0) {
                                        ChatUtils.info("§c[FALLBACK RECOVERY] §7Player centered on target")
                                    }
                                    Movement.stopLeft()
                                    Movement.stopRight()
                                }
                                
                                // Adjust camera pitch to aim at the block's center or edge
                                // When player is off-center, aim at the edge between block faces instead of down into void
                                
                                // Calculate player position relative to block center
                                val blockCenterX = strafeTargetX + 0.5
                                val blockCenterZ = strafeTargetZ + 0.5
                                val playerOffsetX = player.posX - blockCenterX
                                val playerOffsetZ = player.posZ - blockCenterZ
                                
                                // Determine which edge to aim at based on player position
                                val targetAimX = blockCenterX + (if (Math.abs(playerOffsetX) > 0.3) Math.signum(playerOffsetX.toFloat()) * 0.45 else 0.0)
                                val targetAimZ = blockCenterZ + (if (Math.abs(playerOffsetZ) > 0.3) Math.signum(playerOffsetZ.toFloat()) * 0.45 else 0.0)
                                
                                val playerEyeY = player.posY + 1.62 // Player eye height
                                val targetCenterY = strafeTargetY + 0.5 // Center of block
                                val yDifference = targetCenterY - playerEyeY
                                
                                // Calculate distance to the aim point (edge or center)
                                val aimOffsetX = targetAimX - player.posX
                                val aimOffsetZ = targetAimZ - player.posZ
                                val horizontalDist = Math.sqrt(aimOffsetX * aimOffsetX + aimOffsetZ * aimOffsetZ)
                                
                                // Smoothly adjust YAW to face the aim point
                                val targetYaw = Math.toDegrees(Math.atan2(aimOffsetZ, aimOffsetX)).toFloat() - 90f // Adjust for Minecraft yaw offset
                                var yawDifference = targetYaw - player.rotationYaw
                                
                                // Normalize yaw difference to shortest rotation (-180 to 180)
                                while (yawDifference > 180f) yawDifference -= 360f
                                while (yawDifference < -180f) yawDifference += 360f
                                
                                // Smoothly adjust yaw toward target with subtle camera shake
                                player.rotationYaw += yawDifference * 0.35f + applyCameraShake(0.03f)
                                
                                // Convert vertical difference to pitch angle to aim at the edge/center
                                val targetPitch = -Math.toDegrees(Math.atan(yDifference / horizontalDist)).toFloat()
                                
                                // Smoothly adjust pitch toward target with subtle camera shake
                                val pitchDifference = targetPitch - player.rotationPitch
                                player.rotationPitch += pitchDifference * 0.35f + applyCameraShake(0.03f)
                                
                                // Clamp pitch to valid range
                                if (player.rotationPitch > 90f) player.rotationPitch = 90f
                                if (player.rotationPitch < -90f) player.rotationPitch = -90f
                            } else {
                                // Player is centered on strafe target - stop strafing
                                if (debugMode) {
                                    ChatUtils.info("§a[FALLBACK RECOVERY] §7Successfully recentered player, exiting strafe mode")
                                }
                                Movement.stopLeft()
                                Movement.stopRight()
                            }
                            
                            return
                        }
                        
                        if (stuckCounter > 60) { // Stuck for 3 seconds
                            if (debugMode) ChatUtils.error("§c[Pathfinder] §7Stuck detected! Adding intermediate waypoints")
                            
                            // Try to add intermediate waypoints between stuck position and next waypoint
                            val stuckPos = currentPlayerPos
                            val nextPos = path[currentPathIndex].pos
                            
                            // Calculate intermediate waypoints (every 0.5-1 block between stuck and next)
                            val diffX = nextPos.x - stuckPos.x
                            val diffZ = nextPos.z - stuckPos.z
                            val distance = sqrt((diffX * diffX + diffZ * diffZ).toDouble())
                            
                            if (distance > 0) {
                                // Create intermediate waypoints at 0.5 block intervals
                                val stepCount = (distance * 2).toInt() + 1
                                val newWaypoints = mutableListOf<Node>()
                                
                                for (i in 1..stepCount) {
                                    val progress = i.toDouble() / (stepCount + 1)
                                    val intermediateX = stuckPos.x + (diffX * progress).toInt()
                                    val intermediateZ = stuckPos.z + (diffZ * progress).toInt()
                                    
                                    // Try to find walkable height at this intermediate position
                                    var intermediateY = stuckPos.y
                                    for (yOffset in 0..3) {
                                        val checkPos = BlockPos(intermediateX, stuckPos.y + yOffset, intermediateZ)
                                        val blockBelow = world.getBlockState(checkPos.down()).block
                                        if (blockBelow.material.isSolid) {
                                            intermediateY = checkPos.y
                                            break
                                        }
                                    }
                                    
                                    val intermediateWaypoint = Node(
                                        BlockPos(intermediateX, intermediateY, intermediateZ),
                                        if (newWaypoints.isEmpty()) path[currentPathIndex - 1] else newWaypoints.last(),
                                        0.0, 0.0, false, ActionType.WALK
                                    )
                                    newWaypoints.add(intermediateWaypoint)
                                }
                                
                                // Insert the new waypoints into the path at current position
                                if (newWaypoints.isNotEmpty()) {
                                    val newPath = path.take(currentPathIndex).toMutableList()
                                    newPath.addAll(newWaypoints)
                                    newPath.addAll(path.drop(currentPathIndex))
                                    path = newPath
                                    currentPath = path // Update the stored path
                                    if (debugMode) ChatUtils.info("§2[Unstuck] §7Added ${newWaypoints.size} intermediate waypoints")
                                }
                            }
                            
                            // Reset stuck counter to try navigating with new waypoints
                            stuckCounter = 0
                            lastPlayerPos = null
                            return
                        }
                    } else {
                        stuckCounter = 0
                    }
                    lastPlayerPos = currentPlayerPos

                    val currentNode = path[currentPathIndex]
                    val target = currentNode.pos
                    
                    // Smart targeting: aim for closest point on block bounding box
                    // This minimizes distance and prevents overshooting on bridges
                    val targetX = when {
                        player.posX < target.x -> target.x.toDouble() // Player is west, aim for west edge
                        player.posX > target.x + 1 -> (target.x + 1).toDouble() // Player is east, aim for east edge
                        else -> player.posX // Player is within X bounds, maintain X position
                    }
                    
                    val targetZ = when {
                        player.posZ < target.z -> target.z.toDouble() // Player is north, aim for north edge
                        player.posZ > target.z + 1 -> (target.z + 1).toDouble() // Player is south, aim for south edge
                        else -> player.posZ // Player is within Z bounds, maintain Z position
                    }
                    
                    val diffX = targetX - player.posX
                    val diffZ = targetZ - player.posZ
                    val diffY = target.y + 1 - player.posY // +1 because target is ground level
                    val horizontalDistance = sqrt(diffX * diffX + diffZ * diffZ)

                    tickCount++
                    if (debugMode && tickCount % 40 == 0) { // Every 2 seconds
                        ChatUtils.info("§7Step §f${currentPathIndex + 1}§7/§f${path.size} §7[§f${currentNode.actionType}§7] Dist=§f${"%.1f".format(horizontalDistance)}")
                    }

                    // Check if reached waypoint
                    // Accept both feet contact (diffY check) AND head contact (player head within node bounds)
                    // Use stricter threshold during bridging since we need precise placement
                    val distanceThreshold = if (bridgeModeActive) 0.3 else 0.8
                    
                    // SPECIAL CASE: If bridging and target waypoint is below current position, check if we can reach it via platform
                    val reachedViaDownwardPlatform = if (bridgeModeActive && diffY < -0.5 && horizontalDistance < 2.0) {
                        // Waypoint is below us - check if platform detection finds it
                        val blockUnderPos = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
                        var foundDownwardPlatform = false
                        
                        // Check if target waypoint is on a platform we can reach (up to 2 blocks down)
                        for (dropDist in 1..2) {
                            val platformCheckY = blockUnderPos.y - dropDist
                            
                            if (platformCheckY == target.y) {
                                // Found the target waypoint Y level - verify it's a solid platform
                                val targetPos = BlockPos(target.x, target.y, target.z)
                                val targetBlock = world.getBlockState(targetPos).block
                                val targetBlockBelow = world.getBlockState(targetPos.down()).block
                                
                                if (targetBlock != Blocks.air && targetBlockBelow.material.isSolid) {
                                    // Target is a solid platform - check if there's a clear path to it
                                    var isPathClear = true
                                    for (intermediateY in blockUnderPos.y - 1 until platformCheckY) {
                                        for (dx in -1..1) {
                                            for (dz in -1..1) {
                                                val checkX = blockUnderPos.x + dx
                                                val checkZ = blockUnderPos.z + dz
                                                val blockCheck = world.getBlockState(BlockPos(checkX, intermediateY, checkZ)).block
                                                if (blockCheck != Blocks.air) {
                                                    isPathClear = false
                                                    break
                                                }
                                            }
                                            if (!isPathClear) break
                                        }
                                        if (!isPathClear) break
                                    }
                                    
                                    if (isPathClear) {
                                        foundDownwardPlatform = true
                                        if (debugMode) {
                                            ChatUtils.info("§6[Downward Bridge] §7Can reach waypoint below via platform at (${target.x}, ${target.y}, ${target.z})")
                                        }
                                        break
                                    }
                                }
                            }
                        }
                        foundDownwardPlatform
                    } else {
                        false
                    }
                    
                    // For MANDATORY nodes (tight elevation areas), require EXACT foot placement
                    val reachedWaypoint = if (currentNode.isMandatory) {
                        // Mandatory: player's feet must be ON this exact block
                        val playerFeetBlockPos = BlockPos(player.posX, player.posY - 0.1, player.posZ)
                        playerFeetBlockPos.x == target.x && 
                        playerFeetBlockPos.z == target.z && 
                        abs(player.posY - (target.y + 1.0)) < 0.3 // Within 0.3 blocks of standing height
                    } else {
                        // Normal nodes: accept either feet or head contact, OR downward platform reach while bridging
                        val feetReached = horizontalDistance < distanceThreshold && abs(diffY) < 1.5
                        
                        val playerHeadY = player.posY + 1.8 // Player eye height
                        val nodeBlockMinY = target.y.toDouble()
                        val nodeBlockMaxY = target.y + 1.0
                        val headReached = horizontalDistance < distanceThreshold && 
                                          playerHeadY >= nodeBlockMinY && playerHeadY <= nodeBlockMaxY
                        
                        feetReached || headReached || reachedViaDownwardPlatform
                    }
                    
                    if (reachedWaypoint) {
                        // Critical: If in bridge mode, check if we're still over void
                        // If so, DON'T exit bridge mode yet - continue bridging until solid ground
                        if (bridgeModeActive && hasVoidBelow(player, world)) {
                            if (debugMode && lastVoidWaypointLogged != currentPathIndex) {
                                ChatUtils.info("§6[Pathfinder] §7Reached waypoint but still over void - continuing bridge")
                                lastVoidWaypointLogged = currentPathIndex
                            }
                            // Don't advance waypoint, stay in place and keep bridging
                            return
                        }
                        
                        // Reset void logging flag when we're no longer over void
                        if (!bridgeModeActive || !hasVoidBelow(player, world)) {
                            lastVoidWaypointLogged = -1
                        }
                        
                        // Check if in bridge mode at any waypoint to evaluate exit conditions
                        if (bridgeModeActive) {
                            // ENHANCED BRIDGE COMPLETION VALIDATION - CHECKED AT EVERY WAYPOINT WITH DELAY
                            // Initialize delay timer on first check
                            if (bridgeValidationStartTime == 0L) {
                                bridgeValidationStartTime = System.currentTimeMillis()
                                if (debugMode) {
                                    ChatUtils.info("§6[Bridge Check] §7Started validation delay: ${bridgeValidationDelayMs}ms")
                                }
                            }
                            
                            val timeSinceBridgeCheck = System.currentTimeMillis() - bridgeValidationStartTime
                            val delayPassed = timeSinceBridgeCheck >= bridgeValidationDelayMs
                            
                            if (debugMode && !delayPassed) {
                                ChatUtils.info("§6[Bridge Check] §7Waiting... ${timeSinceBridgeCheck}ms/${bridgeValidationDelayMs}ms")
                            }
                            
                            // Only perform validation checks after delay has passed
                            if (delayPassed) {
                                // Get the block directly under the player
                                val blockUnderPos = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
                                val blockUnder = world.getBlockState(blockUnderPos).block
                                
                                // Calculate ahead positions in direction of travel
                                // Minecraft yaw: 0° = +X(E), 90° = -Z(N), ±180° = -X(W), -90° = +Z(S)
                                // Player bridges backward (S key), so ahead = opposite direction of yaw
                                val yaw = player.rotationYaw
                                val aheadBlockPos1 = when {
                                    yaw in -45f..45f -> BlockPos(blockUnderPos.x - 1, blockUnderPos.y, blockUnderPos.z)      // Yaw ~0° (facing E), bridge backward = -X
                                    yaw in 45f..135f -> BlockPos(blockUnderPos.x, blockUnderPos.y, blockUnderPos.z + 1)      // Yaw ~90° (facing N), bridge backward = +Z
                                    yaw in 135f..180f || yaw in -180f..-135f -> BlockPos(blockUnderPos.x + 1, blockUnderPos.y, blockUnderPos.z)  // Yaw ~180° (facing W), bridge backward = +X
                                    else -> BlockPos(blockUnderPos.x, blockUnderPos.y, blockUnderPos.z - 1)                  // Yaw ~-90° (facing S), bridge backward = -Z
                                }
                                
                                val aheadBlockPos2 = when {
                                    yaw in -45f..45f -> BlockPos(blockUnderPos.x - 2, blockUnderPos.y, blockUnderPos.z)      // Yaw ~0° (facing E), bridge backward = -X
                                    yaw in 45f..135f -> BlockPos(blockUnderPos.x, blockUnderPos.y, blockUnderPos.z + 2)      // Yaw ~90° (facing N), bridge backward = +Z
                                    yaw in 135f..180f || yaw in -180f..-135f -> BlockPos(blockUnderPos.x + 2, blockUnderPos.y, blockUnderPos.z)  // Yaw ~180° (facing W), bridge backward = +X
                                    else -> BlockPos(blockUnderPos.x, blockUnderPos.y, blockUnderPos.z - 2)                  // Yaw ~-90° (facing S), bridge backward = -Z
                                }
                                
                                val blockAhead1 = world.getBlockState(aheadBlockPos1).block
                                val blockAhead2 = world.getBlockState(aheadBlockPos2).block
                                
                                // PRIMARY CHECK: Bridge completion (solid under player + BOTH solid blocks ahead)
                                val hasSolidUnder = blockUnder != Blocks.air
                                val hasSolidAhead = blockAhead1 != Blocks.air && blockAhead2 != Blocks.air
                                val isBridgeComplete = hasSolidUnder && hasSolidAhead
                                
                                // SECONDARY CHECK: Not still falling (safety net)
                                val isPlayerFalling = player.motionY < -0.5
                                
                                // TERTIARY CHECK: Player is on ground or stable
                                val isPlayerStable = player.onGround
                                
                                // PLATFORM CHECK: Look for safe platforms up to 2 blocks below
                                // Must ensure there's a clear 3x3 path from current position to platform
                                var foundPlatform = false
                                var platformY = blockUnderPos.y
                                var platformX = blockUnderPos.x
                                var platformZ = blockUnderPos.z
                                
                                for (dropDist in 1..2) {
                                    val platformCheckY = blockUnderPos.y - dropDist
                                    
                                    // Check if this level has any solid block in 3x3 area around player's position
                                    var hasSolidPlatformBlock = false
                                    var platformCheckPos = BlockPos.ORIGIN // Will be set if found
                                    
                                    for (dx in -1..1) {
                                        for (dz in -1..1) {
                                            val checkX = blockUnderPos.x + dx
                                            val checkZ = blockUnderPos.z + dz
                                            val checkPos = BlockPos(checkX, platformCheckY, checkZ)
                                            val blockAtPos = world.getBlockState(checkPos).block
                                            val blockBelow = world.getBlockState(checkPos.down()).block
                                            
                                            // Found a solid block with solid ground beneath it
                                            if (blockAtPos != Blocks.air && blockBelow.material.isSolid) {
                                                hasSolidPlatformBlock = true
                                                platformCheckPos = checkPos
                                                break
                                            }
                                        }
                                        if (hasSolidPlatformBlock) break
                                    }
                                    
                                    if (hasSolidPlatformBlock) {
                                        // Verify there's a clear 3x3 path from current position to platform
                                        var isPathClear = true
                                        for (intermediateY in blockUnderPos.y - 1 until platformCheckY) {
                                            // Check 3x3 area at this intermediate level
                                            for (dx in -1..1) {
                                                for (dz in -1..1) {
                                                    val checkX = blockUnderPos.x + dx
                                                    val checkZ = blockUnderPos.z + dz
                                                    val blockCheck = world.getBlockState(BlockPos(checkX, intermediateY, checkZ)).block
                                                    if (blockCheck != Blocks.air) {
                                                        isPathClear = false
                                                        break
                                                    }
                                                }
                                                if (!isPathClear) break
                                            }
                                            if (!isPathClear) break
                                        }
                                        
                                        if (isPathClear) {
                                            foundPlatform = true
                                            platformY = platformCheckY
                                            platformX = platformCheckPos.x
                                            platformZ = platformCheckPos.z
                                            if (debugMode) {
                                                ChatUtils.info("§6[Platform Detection] §7Found platform at (${platformX}, ${platformY}, ${platformZ}) with clear 3x3 path")
                                            }
                                            break
                                        }
                                    }
                                }
                                
                                // DIAGNOSTIC: Log all checks if debug mode enabled
                                if (debugMode) {
                                    ChatUtils.info("§6[Bridge Check @ WP] Under(${blockUnderPos.x},${blockUnderPos.y},${blockUnderPos.z}): ${blockUnder.getLocalizedName()} | " +
                                        "Ahead1(${aheadBlockPos1.x},${aheadBlockPos1.z}): ${blockAhead1.getLocalizedName()} | " +
                                        "Ahead2(${aheadBlockPos2.x},${aheadBlockPos2.z}): ${blockAhead2.getLocalizedName()} | " +
                                        "Falling: $isPlayerFalling | Stable: $isPlayerStable | Complete: $isBridgeComplete | Platform: $foundPlatform")
                                }
                                
                                // FINAL DECISION: Exit bridge mode validation
                                // Check if next waypoint is under the bot
                                var nextWaypointUnder = false
                                if (currentPathIndex + 1 < path.size) {
                                    val nextNode = path[currentPathIndex + 1]
                                    val botY = player.posY.toInt()
                                    if (nextNode.pos.y < botY) {
                                        nextWaypointUnder = true
                                        if (debugMode) ChatUtils.info("§e[Bridge Exit] §7Next waypoint is below bot at Y: ${nextNode.pos.y} (Bot Y: $botY)")
                                    }
                                }
                                
                                if ((isBridgeComplete && !isPlayerFalling && isPlayerStable) || nextWaypointUnder) {
                                    // Bridge exit validation passed - either bridge complete OR next waypoint is below
                                    Movement.clearAll()
                                    EagleBridge.stopBridging()
                                    bridgeModeActive = false
                                    bridgeValidationStartTime = 0L // Reset delay timer
                                    isTransitioningFromBridge = true
                                    bridgeExitTime = System.currentTimeMillis()
                                    bridgeSafetyNodesRemaining = 0
                                    
                                    if (debugMode) {
                                        val timeSinceBridgeStart = System.currentTimeMillis() - bridgeModeEnterTime
                                        val reason = if (nextWaypointUnder) "next WP under" else "complete"
                                        ChatUtils.info("§a[Bridge Exit] §7Complete after ${timeSinceBridgeStart}ms at waypoint - " +
                                            "Reason: $reason | FallRate: ${player.motionY}")
                                    }
                                } else {
                                    // Bridge not fully complete OR player unstable - continue bridging
                                    if (debugMode) {
                                        val reason = when {
                                            !hasSolidUnder -> "no solid under"
                                            !hasSolidAhead -> "no solid ahead"
                                            isPlayerFalling -> "player falling (motionY: ${player.motionY})"
                                            !isPlayerStable -> "player not on ground"
                                            else -> "unknown"
                                        }
                                        ChatUtils.info("§c[Bridge Continue] §7Reason: $reason at waypoint")
                                    }
                                    
                                    // Add intermediate waypoint 1-2 blocks ahead in direction of next waypoint
                                    if (currentPathIndex + 1 < path.size) {
                                        val currentNode = path[currentPathIndex]
                                        val nextNode = path[currentPathIndex + 1]
                                        
                                        // Calculate direction to next waypoint
                                        val dirX = nextNode.pos.x - currentNode.pos.x
                                        val dirY = nextNode.pos.y - currentNode.pos.y
                                        val dirZ = nextNode.pos.z - currentNode.pos.z
                                        
                                        // Normalize direction and add 1-2 blocks ahead
                                        val distance = 2 // Add waypoint 2 blocks ahead
                                        val newX = currentNode.pos.x + if (dirX > 0) distance else if (dirX < 0) -distance else 0
                                        val newY = currentNode.pos.y + if (dirY > 0) distance else if (dirY < 0) -distance else 0
                                        val newZ = currentNode.pos.z + if (dirZ > 0) distance else if (dirZ < 0) -distance else 0
                                        val newPos = BlockPos(newX, newY, newZ)
                                        
                                        // Only insert if not already at this position
                                        if (newPos != currentNode.pos && (currentPathIndex + 1 >= path.size || newPos != path[currentPathIndex + 1].pos)) {
                                            val intermediateNode = Node(newPos, currentNode, 0.0, 0.0, true, currentNode.actionType)
                                            path.add(currentPathIndex + 1, intermediateNode)
                                        }
                                    }
                                    
                                    currentPathIndex++
                                    PathRenderer.setWaypointIndex(currentPathIndex)
                                    lastDescentWaypointLogged = -1 // Reset descent logging flag for new waypoint
                                    // Continue bridging - stay in bridge mode and keep going
                                    return
                                }
                            } else {
                                // Delay not passed yet - stay at current waypoint and wait for check time to accumulate
                                // Don't advance waypoint, just return and continue waiting
                                return
                            }
                        }
                        
                        if (debugMode) ChatUtils.info("§a✓ §7Waypoint §f${currentPathIndex + 1}")
                        currentPathIndex++
                        PathRenderer.setWaypointIndex(currentPathIndex)
                        lastDescentWaypointLogged = -1 // Reset descent logging flag for new waypoint
                        
                        if (currentPathIndex >= path.size) {
                            if (debugMode) ChatUtils.info("§a[Pathfinder] §7Destination reached!")
                            stop()
                            cancel()
                            onComplete()
                            return
                        }
                        return
                    }

                    val currentTime = System.currentTimeMillis()

                    // Calculate target yaw and pitch
                    var targetYaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f
                    var targetPitch = 0f
                    
                    // Determine if this is a diagonal bridge based on movement direction
                    val absDiffX = abs(diffX)
                    val absDiffZ = abs(diffZ)
                    val isDiagonalBridge = absDiffX > 0.5 && absDiffZ > 0.5 && bridgeModeActive // Both X and Z movement, only when bridging
                    
                    // If eagle bridging is ACTIVE (regardless of node type), use proper bridging angles
                    // This allows extending bridge mode past the last BRIDGE node if still over void
                    if (bridgeModeActive) {
                        if (isDiagonalBridge) {
                            // DIAGONAL BRIDGE: Use fixed angles based on direction quadrant
                            // Since we're moving BACKWARDS (S key), we face OPPOSITE of movement direction
                            targetYaw = when {
                                diffX > 0 && diffZ > 0 -> 135f   // Moving SE, face SE (flipped 180°)
                                diffX > 0 && diffZ < 0 -> 45f    // Moving NE, face NE (flipped 180°)
                                diffX < 0 && diffZ > 0 -> -135f  // Moving SW, face SW (flipped 180°)
                                else -> -45f                     // Moving NW, face NW (flipped 180°)
                            }
                            targetPitch = 77f // Shallower for diagonal visibility
                        } else {
                            // STRAIGHT BRIDGE: Face opposite direction
                            targetYaw += 180f // Face backwards
                            if (targetYaw > 180f) targetYaw -= 360f
                            if (targetYaw < -180f) targetYaw += 360f
                            targetPitch = 83f // Steeper for straight bridges
                        }
                    }
                    
                    // For diagonal bridges, lock angle immediately without interpolation
                    // For normal movement, use smooth interpolation
                    var needsLargeTurn = false // Track if large yaw change is needed
                    
                    if (isDiagonalBridge) {
                        // Instant rotation lock for diagonal bridges
                        player.rotationYaw = targetYaw
                    } else {
                        // Smooth rotation interpolation to prevent jerky movements
                        val currentYaw = player.rotationYaw
                        var yawDiff = targetYaw - currentYaw
                        
                        // Calculate both rotation directions to reach target
                        var normalizedDiff = yawDiff
                        while (normalizedDiff > 180f) normalizedDiff -= 360f
                        while (normalizedDiff < -180f) normalizedDiff += 360f
                        
                        // Consider both left and right rotation paths
                        val rightTurnDistance = normalizedDiff // Positive = turn right
                        val leftTurnDistance = if (normalizedDiff > 0) normalizedDiff - 360f else normalizedDiff + 360f // Negative = turn left
                        
                        // Choose the shorter path, but consider both options are valid
                        yawDiff = if (Math.abs(rightTurnDistance) <= Math.abs(leftTurnDistance)) {
                            rightTurnDistance // Shorter right path
                        } else {
                            leftTurnDistance // Shorter left path
                        }
                        
                        // If yaw change is > 90 degrees, pause walking to turn
                        needsLargeTurn = Math.abs(yawDiff) > 90f
                        
                        // Smooth interpolation: rotate gradually instead of instantly
                        // Use 25% interpolation for slower, smoother movement
                        val smoothingFactor = 0.25f
                        val newYaw = currentYaw + (yawDiff * smoothingFactor)
                        
                        // Apply yaw rotation
                        player.rotationYaw = newYaw
                    }
                    
                    // Set pitch
                    if (bridgeModeActive) {
                        // For bridging, smoothly interpolate pitch for slower camera changes
                        // Use 20% interpolation for slower, smoother pitch adjustments while bridging
                        val pitchDiff = targetPitch - player.rotationPitch
                        val smoothPitchFactor = 0.2f
                        player.rotationPitch += pitchDiff * smoothPitchFactor
                    }

                    // Eagle bridging uses BACKWARDS movement (S key), others use forward (W key)
                    if (bridgeModeActive) {
                        // TRUE EAGLE BRIDGING: Move backwards while shifting
                        Movement.stopForward()
                        Movement.startBackward()
                        
                        // Check for block placement by monitoring hotbar block count
                        val currentBlockCount = countBlocksInHotbar()
                        if (currentBlockCount < lastBlockCount && lastBlockCount > 0) {
                            // Block was placed!
                            blocksPlacedCount++
                            canSpeedbridge = true // Enable speedbridge after first successful placement
                            ChatUtils.info("§6[Bridge] §7Block placed! Total: ${blocksPlacedCount} (${currentBlockCount} blocks left)")
                            lastBlockCount = currentBlockCount
                            // Reset speedbridge cycle to start fresh after placement
                            speedbridgeCycle = 0
                            lastSpeedbridgeToggle = currentTime
                        } else if (lastBlockCount == 0) {
                            // First tick, just update the count
                            lastBlockCount = currentBlockCount
                        }
                        
                        // SAFE SPEEDBRIDGE (straight bridges only)
                        // Only activate after first successful block placement
                        // Quick unsneak after block placement, then sneak again
                        if (enableSpeedBridging && !isDiagonalBridge && player.onGround && canSpeedbridge) {
                            val timeSinceToggle = currentTime - lastSpeedbridgeToggle
                            
                            when {
                                speedbridgeCycle == 0 && timeSinceToggle > 300 -> {
                                    // Quick unsneak after placing block (150ms)
                                    Movement.stopSneaking()
                                    speedbridgeCycle = 1
                                    lastSpeedbridgeToggle = currentTime
                                    if (debugMode) ChatUtils.info("§6[Speedbridge] §eUnsneak (${blocksPlacedCount} blocks)")
                                }
                                speedbridgeCycle == 1 && timeSinceToggle > 75 -> {
                                    // Back to sneak phase quickly (safe)
                                    Movement.startSneaking()
                                    speedbridgeCycle = 0
                                    lastSpeedbridgeToggle = currentTime
                                    if (debugMode) ChatUtils.info("§6[Speedbridge] §7Sneak (${blocksPlacedCount} blocks)")
                                }
                                speedbridgeCycle == 0 -> {
                                    // Maintain sneak phase
                                    Movement.startSneaking()
                                }
                                speedbridgeCycle == 1 -> {
                                    // Maintain unsneak phase
                                    Movement.stopSneaking()
                                }
                            }
                        } else {
                            // No speedbridge on diagonal, if disabled, or before first placement - always sneak
                            Movement.startSneaking()
                        }
                    } else {
                        // Normal forward movement
                        // If large yaw change is needed, pause walking to turn first
                        if (needsLargeTurn) {
                            Movement.stopForward()
                            Movement.stopBackward()
                            Movement.stopSprinting()
                            Movement.stopLeft()
                            Movement.stopRight()
                        } else {
                            Movement.stopBackward()
                            Movement.startForward()
                            
                            // Don't sprint on mandatory nodes (tight elevation areas) or Y-changing nodes
                            val hasElevationChange = currentNode.actionType == ActionType.JUMP || 
                                                    currentNode.actionType == ActionType.SPRINT ||
                                                    abs(diffY) > 0.5
                            if (currentNode.isMandatory || hasElevationChange) {
                                Movement.stopSprinting()
                            } else {
                                Movement.startSprinting()
                            }
                        }
                    }

                    // Handle movement based on action type
                    when (currentNode.actionType) {
                        ActionType.JUMP -> {
                            // Jump if we're close to the target and on ground
                            if (horizontalDistance < 1.5 && player.onGround && currentTime - lastJumpTime > 500) {
                                // If this node needs block placement (2-block jump), place block below while jumping
                                if (currentNode.needsBlockPlace) {
                                    val blockUnder = BlockPos(player.posX, player.posY - 1, player.posZ)
                                    if (world.getBlockState(blockUnder).block.material == Material.air) {
                                        if (EagleBridge.switchToBlocks()) {
                                            val originalPitch = player.rotationPitch
                                            player.rotationPitch = 90f
                                            Thread.sleep(30)
                                            player.swingItem()
                                            Mouse.rClick()
                                            player.rotationPitch = originalPitch
                                        }
                                    }
                                }
                                player.jump()
                                lastJumpTime = currentTime
                            }
                        }
                        ActionType.SPRINT -> {
                            // Sprint jump for 2-block heights
                            // NOTE: Sprint disabled on Y-changes < 3 blocks due to neighbor generation logic
                            if (horizontalDistance < 1.5 && player.onGround && currentTime - lastJumpTime > 500) {
                                // If this node needs block placement (2-block jump), place block below while jumping
                                if (currentNode.needsBlockPlace) {
                                    val blockUnder = BlockPos(player.posX, player.posY - 1, player.posZ)
                                    if (world.getBlockState(blockUnder).block.material == Material.air) {
                                        if (EagleBridge.switchToBlocks()) {
                                            val originalPitch = player.rotationPitch
                                            player.rotationPitch = 90f
                                            Thread.sleep(30)
                                            player.swingItem()
                                            Mouse.rClick()
                                            player.rotationPitch = originalPitch
                                        }
                                    }
                                }
                                player.jump()
                                lastJumpTime = currentTime
                            }
                        }
                        ActionType.FALL -> {
                            // Just walk forward and let gravity do its thing
                        }
                        ActionType.WALK -> {
                            // If we're in bridge mode, continue bridging
                            if (bridgeModeActive) {
                                // Continue eagle bridging - place blocks
                                Movement.stopBackward()
                                Movement.startBackward()
                                
                                // Check for block placement by monitoring hotbar block count
                                val currentBlockCount = countBlocksInHotbar()
                                if (currentBlockCount < lastBlockCount && lastBlockCount > 0) {
                                    // Block was placed!
                                    blocksPlacedCount++
                                    canSpeedbridge = true
                                    if (debugMode) ChatUtils.info("§6[Bridge] §7Block placed! Total: ${blocksPlacedCount} (${currentBlockCount} blocks left)")
                                    lastBlockCount = currentBlockCount
                                    speedbridgeCycle = 0
                                    lastSpeedbridgeToggle = currentTime
                                } else if (lastBlockCount == 0) {
                                    lastBlockCount = currentBlockCount
                                }
                                
                                // SAFE SPEEDBRIDGE (straight bridges only)
                                if (enableSpeedBridging && !isDiagonalBridge && player.onGround && canSpeedbridge) {
                                    val timeSinceToggle = currentTime - lastSpeedbridgeToggle
                                    
                                    when {
                                        speedbridgeCycle == 0 && timeSinceToggle > 300 -> {
                                            Movement.stopSneaking()
                                            speedbridgeCycle = 1
                                            lastSpeedbridgeToggle = currentTime
                                            if (debugMode) ChatUtils.info("§6[Speedbridge] §eUnsneak (${blocksPlacedCount} blocks)")
                                        }
                                        speedbridgeCycle == 1 && timeSinceToggle > 75 -> {
                                            Movement.startSneaking()
                                            speedbridgeCycle = 0
                                            lastSpeedbridgeToggle = currentTime
                                            if (debugMode) ChatUtils.info("§6[Speedbridge] §7Sneak (${blocksPlacedCount} blocks)")
                                        }
                                        speedbridgeCycle == 0 -> {
                                            Movement.startSneaking()
                                        }
                                        speedbridgeCycle == 1 -> {
                                            Movement.stopSneaking()
                                        }
                                    }
                                } else {
                                    Movement.startSneaking()
                                }
                            } else {
                                // Normal walking mode - forward movement
                                Movement.stopBackward()
                                Movement.startForward()
                                Movement.stopSneaking()
                                
                                // Enhanced auto-jump logic:
                                // 1. Jump if collided horizontally
                                // 2. Jump if next waypoint is higher than current position
                                // BUT: Don't jump while transitioning from bridge
                                val needsJump = !isTransitioningFromBridge && player.onGround && currentTime - lastJumpTime > 500 && (
                                    player.isCollidedHorizontally ||
                                    (diffY > 0.2 && horizontalDistance < 2.0)
                                )
                                if (needsJump) {
                                    player.jump()
                                    lastJumpTime = currentTime
                                }
                            }
                        }
                        ActionType.BRIDGE -> {
                            // Prevent bridging when going DOWNWARD
                            val isGoingDown = diffY < -0.5
                            
                            if (isGoingDown) {
                                // Skip bridge mode; treat as normal walk/fall
                                if (debugMode && lastDescentWaypointLogged != currentPathIndex) {
                                    ChatUtils.info("§e[Pathfinder] §7Descending - skipping bridge")
                                    lastDescentWaypointLogged = currentPathIndex
                                }
                            } else if (!bridgeModeActive) {
                                // Only activate bridge mode on FIRST BRIDGE node of a section
                                // Check if previous node was NOT a BRIDGE
                                val previousNodeWasBridge = currentPathIndex > 0 && path[currentPathIndex - 1].actionType == ActionType.BRIDGE
                                
                                if (!previousNodeWasBridge) {
                                    // This is the start of a new bridge section - activate bridge mode
                                    if (!EagleBridge.switchToBlocks()) {
                                        if (debugMode) ChatUtils.error("§c[Pathfinder] No blocks in hotbar!")
                                        stop()
                                        cancel()
                                        onComplete()
                                        return
                                    }
                                    
                                    // Set proper camera angle for bridging BEFORE starting
                                    // Use smooth interpolation instead of instant rotation
                                    val bridgeYaw = player.rotationYaw + 180f  // Face backwards for eagle bridge
                                    val bridgePitch = 83f  // Proper angle for bridging downward
                                    
                                    // Smoothly rotate to bridge angle using interpolation
                                    val yawDiff = bridgeYaw - player.rotationYaw
                                    var normalizedYawDiff = yawDiff
                                    while (normalizedYawDiff > 180f) normalizedYawDiff -= 360f
                                    while (normalizedYawDiff < -180f) normalizedYawDiff += 360f
                                    
                                    // Apply smooth interpolation (20% per tick for bridging entry)
                                    player.rotationYaw += normalizedYawDiff * 0.2f
                                    player.rotationPitch += (bridgePitch - player.rotationPitch) * 0.2f
                                    // Note: EagleBridge will handle movement only - AdvancedPathfinder controls rotation
                                    
                                    // Activate eagle bridging and STAY IN IT for entire bridge section
                                    // EagleBridge handles all movement and block placement internally
                                    EagleBridge.activateBridge()
                                    bridgeModeActive = true
                                    bridgeModeEnterTime = System.currentTimeMillis() // Track when bridge mode started
                                    bridgeValidationStartTime = 0L // Reset validation delay timer for new bridge section
                                    speedbridgeCycle = 0 // Start in sneak phase
                                    lastSpeedbridgeToggle = System.currentTimeMillis()
                                    lastBlockCount = countBlocksInHotbar() // Initialize block count
                                    blocksPlacedCount = 0 // Reset placement counter
                                    canSpeedbridge = false // Disable until first block placed
                                    if (debugMode) ChatUtils.info("§6[Pathfinder] §7Started eagle bridge section (${lastBlockCount} blocks)")
                                }
                            }
                            
                            // Continue bridging - movement handled by EagleBridge
                            // Bridge mode will be deactivated in waypoint reached check when next node is not BRIDGE
                        }
                        ActionType.PILLAR_UP -> {
                            // Pillaring up - place blocks beneath player and jump
                            if (player.onGround) {
                                val blockBelow = BlockPos(player.posX, player.posY - 1, player.posZ)
                                val blockAtFeet = BlockPos(player.posX, player.posY, player.posZ)
                                
                                // Check if we need to place a block
                                if (world.getBlockState(blockBelow).block.material == Material.air ||
                                    world.getBlockState(blockAtFeet).block.material == Material.air) {
                                    
                                    if (EagleBridge.switchToBlocks()) {
                                        // Look down to place block
                                        val originalPitch = player.rotationPitch
                                        player.rotationPitch = 90f
                                        Thread.sleep(30)
                                        
                                        // Place block
                                        player.swingItem()
                                        Mouse.rClick()
                                        Thread.sleep(50)
                                        
                                        // Restore pitch
                                        player.rotationPitch = originalPitch
                                    }
                                }
                                
                                // Jump up
                                if (currentTime - lastJumpTime > 300) {
                                    player.jump()
                                    lastJumpTime = currentTime
                                }
                            }
                        }
                        ActionType.DROP_DOWN -> {
                            // Just walk forward and fall - gravity handles it
                            // Same as FALL action
                        }
                    }

                } catch (e: Exception) {
                    ChatUtils.error("§c[Pathfinder] Error: ${e.message}")
                    stop()
                    cancel()
                    onComplete()
                }
            }
        }
        
        pathfindingTimer!!.scheduleAtFixedRate(task, 0, 50) // 20 times per second
    }

    /**
     * Check if there's void below a position
     */
    private fun hasVoidBelow(player: net.minecraft.entity.player.EntityPlayer, world: World): Boolean {
        val playerPos = BlockPos(player.posX, player.posY, player.posZ)
        for (i in 1..10) {
            val checkBelow = playerPos.down(i)
            if (world.getBlockState(checkBelow).block.material.isSolid) {
                return false
            }
        }
        return true
    }
    
    /**
     * Enable/disable debug messages
     */
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        ChatUtils.info("§6[Pathfinder] §7Debug mode: §f${if (enabled) "ON" else "OFF"}")
    }
}


