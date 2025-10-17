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
        val actionType: ActionType = ActionType.WALK
    ) : Comparable<Node> {
        val f: Double get() = g + h
        override fun compareTo(other: Node): Int = f.compareTo(other.f)
    }

    enum class ActionType {
        WALK,       // Normal walking
        JUMP,       // Jumping up 1-2 blocks
        FALL,       // Falling down safely
        SPRINT,     // Sprint jumping
        BRIDGE      // Eagle bridging across void
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
        
        // Stop eagle bridging if it was active
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
        val maxIterations = 10000 // Increased for better pathfinding

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

        // Check all horizontal movements (same Y level)
        for (dir in cardinalDirections + diagonalDirections) {
            val neighborPos = pos.add(dir)
            
            if (canWalkTo(world, pos, neighborPos)) {
                neighbors.add(Node(neighborPos, current, 0.0, 0.0, false, ActionType.WALK))
            }
            // Check jump up 1 block
            else if (canJumpTo(world, pos, neighborPos)) {
                val jumpPos = neighborPos.up()
                neighbors.add(Node(jumpPos, current, 0.0, 0.0, false, ActionType.JUMP))
            }
            // Check jump up 2 blocks (sprint jump)
            else if (canJumpTo(world, pos, neighborPos.up())) {
                val jumpPos = neighborPos.up(2)
                neighbors.add(Node(jumpPos, current, 0.0, 0.0, false, ActionType.SPRINT))
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

        // Check straight down (falling in place)
        for (fallDistance in 1..4) {
            val downPos = pos.down(fallDistance)
            if (canFallDown(world, pos, fallDistance)) {
                neighbors.add(Node(downPos, current, 0.0, 0.0, false, ActionType.FALL))
                break
            }
        }
        
        // Check bridging across void (only cardinal directions for safety)
        for (dir in cardinalDirections) {
            // Try to bridge up to 10 blocks
            for (bridgeDistance in 1..10) {
                val bridgePos = pos.add(dir.x * bridgeDistance, 0, dir.z * bridgeDistance)
                
                // Check if we can bridge to this position
                if (canBridgeTo(world, pos, bridgePos, bridgeDistance)) {
                    // Add bridge node with needsBlockPlace = true
                    neighbors.add(Node(bridgePos, current, 0.0, 0.0, true, ActionType.BRIDGE))
                    break // Only add one bridge distance per direction
                }
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
        
        // Check if target position would be walkable (has ground or can place ground)
        val targetGround = world.getBlockState(to).block
        
        // If target already has ground, check if it's walkable
        // IMPORTANT: Still allow bridging TO solid ground to complete the bridge
        if (targetGround.material.isSolid) {
            return isPositionWalkable(world, to)
        }
        
        // Check if there's void below target OR if we're currently over void
        // We need to check BOTH from and to positions
        val fromPos = from
        var hasVoidAtStart = true
        for (i in 1..10) {
            val checkPos = fromPos.down(i)
            if (world.getBlockState(checkPos).block.material.isSolid) {
                hasVoidAtStart = false
                break
            }
        }
        
        // If starting position has void below, we MUST continue bridging
        // even if target has solid ground nearby (to complete the bridge)
        if (hasVoidAtStart) return true
        
        // Otherwise check if target has void
        var hasVoidBelow = true
        for (i in 1..10) {
            val checkPos = to.down(i)
            if (world.getBlockState(checkPos).block.material.isSolid) {
                hasVoidBelow = false
                break
            }
        }
        
        // Only allow bridging if there's void somewhere
        if (!hasVoidBelow) return false
        
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
        
        // Check if there's a solid landing spot OR if we can reach solid ground
        // Look ahead to see if there's solid ground at the target
        for (i in 0..5) {
            val landingCheck = to.down(i)
            if (world.getBlockState(landingCheck).block.material.isSolid) {
                // Found solid ground, check if reachable
                return i <= distance // Can bridge if ground is within bridging distance
            }
        }
        
        return true // Allow bridging even if we don't see landing (might be far island)
    }

    /**
     * Check if we can walk horizontally to a position (same Y level)
     */
    private fun canWalkTo(world: World, from: BlockPos, to: BlockPos): Boolean {
        // Check if target position is walkable
        if (!isPositionWalkable(world, to)) return false

        // Check if there's a block in the way (collision check)
        val blockAt = world.getBlockState(to).block
        val blockAbove = world.getBlockState(to.up()).block
        
        // Can't walk if there's a solid block at feet or head level
        if (blockAt.material.isSolid || blockAbove.material.isSolid) return false

        // Check for diagonal movement collision
        val dx = to.x - from.x
        val dz = to.z - from.z
        
        // If moving diagonally, check both cardinal directions are clear
        if (dx != 0 && dz != 0) {
            // Moving diagonally - need to check both sides
            // Example: Moving NE (1, 0, -1) needs to check N (0, 0, -1) and E (1, 0, 0)
            
            val sideX = from.add(dx, 0, 0) // Check X direction
            val sideZ = from.add(0, 0, dz) // Check Z direction
            
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
     * Check if a position is walkable (has solid ground and passable air above)
     */
    private fun isPositionWalkable(world: World, pos: BlockPos): Boolean {
        val groundBlock = world.getBlockState(pos).block
        val feetBlock = world.getBlockState(pos.up()).block
        val headBlock = world.getBlockState(pos.up(2)).block

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
        val path = currentPath ?: return

        if (debugMode) {
            ChatUtils.info("§6[Pathfinder] §7Following path")
        }

        var tickCount = 0
        var lastJumpTime = 0L
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

                    // Handle transition pause from bridge mode
                    if (isTransitioningFromBridge) {
                        val transitionElapsed = System.currentTimeMillis() - bridgeExitTime
                        if (transitionElapsed < 500) {
                            // Still in 500ms pause - stay still and keep sneaking
                            Movement.clearAll()
                            Movement.startSneak()
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
                            // Still rotating - actively rotate faster (90% per tick)
                            Movement.clearAll()
                            player.rotationYaw = currentYaw + (yawDiff * 0.9f)
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
                        if (stuckCounter > 60) { // Stuck for 3 seconds
                            if (debugMode) ChatUtils.error("§c[Pathfinder] §7Stuck! Cancelling")
                            stop()
                            cancel()
                            onComplete()
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
                    // Use stricter tolerance when in bridge mode and on last bridge node or safety nodes
                    val isInBridgeEndPhase = bridgeModeActive && 
                                             (currentNode.actionType != ActionType.BRIDGE || bridgeSafetyNodesRemaining > 0)
                    val distanceThreshold = if (isInBridgeEndPhase) 0.3 else 0.8 // Stricter for bridge end
                    val reachedWaypoint = horizontalDistance < distanceThreshold && abs(diffY) < 1.5
                    
                    if (reachedWaypoint) {
                        // Detect when we finish the last BRIDGE node - start safety counter
                        if (bridgeModeActive && currentNode.actionType == ActionType.BRIDGE) {
                            val nextIsNotBridge = currentPathIndex + 1 >= path.size || 
                                                  path[currentPathIndex + 1].actionType != ActionType.BRIDGE
                            if (nextIsNotBridge) {
                                // This is the last BRIDGE node - activate +2 safety nodes
                                bridgeSafetyNodesRemaining = 2
                                if (debugMode) ChatUtils.info("§6[Pathfinder] §7Last bridge node - extending +2 safety nodes")
                            }
                        }
                        
                        // Decrement safety counter if active
                        if (bridgeSafetyNodesRemaining > 0) {
                            bridgeSafetyNodesRemaining--
                            if (debugMode) ChatUtils.info("§6[Pathfinder] §7Safety node complete, ${bridgeSafetyNodesRemaining} remaining")
                            
                            // Exit bridge mode after completing all safety nodes
                            if (bridgeSafetyNodesRemaining == 0) {
                                // SIMPLE EXIT SEQUENCE:
                                // 1. Stop movement completely
                                Movement.clearAll()
                                
                                // 2. Deactivate bridge mode
                                bridgeModeActive = false
                                
                                // 3. Start 500ms pause before resuming
                                isTransitioningFromBridge = true
                                bridgeExitTime = System.currentTimeMillis()
                                
                                if (debugMode) ChatUtils.info("§6[Pathfinder] §7Exited bridge (+2 safety complete), pausing...")
                            }
                        }
                        if (debugMode) ChatUtils.info("§a✓ §7Waypoint §f${currentPathIndex + 1}")
                        currentPathIndex++
                        PathRenderer.setWaypointIndex(currentPathIndex)
                        
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
                    
                    // If eagle bridging is ACTIVE (regardless of node type), use BridgingPathfinder approach:
                    // - Face OPPOSITE direction (180 degrees)
                    // - Look DOWN (pitch ~82-85 to properly see the edge)
                    // This allows extending bridge mode past the last BRIDGE node if still over void
                    if (bridgeModeActive) {
                        targetYaw += 180f // Face backwards
                        if (targetYaw > 180f) targetYaw -= 360f
                        if (targetYaw < -180f) targetYaw += 360f
                        targetPitch = 83f // Look down more to see edge and place properly
                    }
                    
                    // Smooth rotation interpolation to prevent jerky movements
                    val currentYaw = player.rotationYaw
                    var yawDiff = targetYaw - currentYaw
                    
                    // Normalize to [-180, 180] to take shortest path
                    while (yawDiff > 180f) yawDiff -= 360f
                    while (yawDiff < -180f) yawDiff += 360f
                    
                    // Smooth interpolation: rotate gradually instead of instantly
                    // Use 50% interpolation for responsive but smooth movement
                    val smoothingFactor = 0.5f
                    val newYaw = currentYaw + (yawDiff * smoothingFactor)
                    
                    // Apply yaw rotation
                    player.rotationYaw = newYaw
                    
                    // Set pitch
                    if (bridgeModeActive) {
                        // For bridging, just use the base pitch directly
                        // Crosshair validation is too complex and causes issues
                        player.rotationPitch = targetPitch
                    }

                    // Eagle bridging uses BACKWARDS movement (S key), others use forward (W key)
                    if (bridgeModeActive) {
                        // TRUE EAGLE BRIDGING: Move backwards while shifting
                        Movement.stopForward()
                        Movement.startBackward()
                        Movement.stopSprinting() // Never sprint while eagle bridging
                    } else {
                        // Normal forward movement
                        Movement.stopBackward()
                        Movement.startForward()
                        Movement.startSprinting()
                    }

                    // Handle movement based on action type
                    when (currentNode.actionType) {
                        ActionType.JUMP -> {
                            // Jump if we're close to the target and on ground
                            if (horizontalDistance < 1.5 && player.onGround && currentTime - lastJumpTime > 500) {
                                player.jump()
                                lastJumpTime = currentTime
                            }
                        }
                        ActionType.SPRINT -> {
                            // Sprint jump for 2-block heights
                            if (horizontalDistance < 1.5 && player.onGround && currentTime - lastJumpTime > 500) {
                                player.jump()
                                lastJumpTime = currentTime
                            }
                        }
                        ActionType.FALL -> {
                            // Just walk forward and let gravity do its thing
                        }
                        ActionType.WALK -> {
                            // Auto-jump if there's a block in front
                            if (player.onGround && player.isCollidedHorizontally && currentTime - lastJumpTime > 500) {
                                player.jump()
                                lastJumpTime = currentTime
                            }
                        }
                        ActionType.BRIDGE -> {
                            // Activate bridge mode when entering a BRIDGE node (if not already active)
                            if (!bridgeModeActive) {
                                // Switch to blocks if not already holding them
                                if (!EagleBridge.switchToBlocks()) {
                                    if (debugMode) ChatUtils.error("§c[Pathfinder] No blocks in hotbar!")
                                    stop()
                                    cancel()
                                    onComplete()
                                    return
                                }
                                
                                // Activate eagle bridging and STAY IN IT for entire bridge section
                                EagleBridge.startBridging()
                                bridgeModeActive = true
                                if (debugMode) ChatUtils.info("§6[Pathfinder] §7Started eagle bridge section")
                            }
                            
                            // Stay in bridge mode - don't deactivate until we leave the BRIDGE nodes
                            // The deactivation happens in the waypoint reached check above
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


