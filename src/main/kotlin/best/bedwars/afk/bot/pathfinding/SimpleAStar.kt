package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.block.material.Material
import net.minecraft.util.BlockPos
import net.minecraft.world.World
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Simple A* pathfinder that actually works for Bedwars
 * Much more lenient than NotEnoughFrags - focuses on getting to the destination
 */
object SimpleAStar {

    data class Node(
        val pos: BlockPos,
        val parent: Node?,
        var g: Double,
        val h: Double
    ) : Comparable<Node> {
        val f: Double get() = g + h
        override fun compareTo(other: Node): Int = f.compareTo(other.f)
    }

    private var currentPath: List<BlockPos>? = null
    private var currentPathIndex = 0
    private var isFollowingPath = false
    private var pathfindingTimer: Timer? = null

    fun goTo(x: Int, y: Int, z: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        // Use player's current Y position, not below
        val start = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
        val end = BlockPos(x, y, z)

        stop()

        Thread {
            ChatUtils.info("Finding path from (${start.x}, ${start.y}, ${start.z}) to ($x, $y, $z)")
            val path = findPath(world, start, end)
            if (path != null && path.size > 1) {
                ChatUtils.info("Path found: ${path.size} waypoints")
                currentPath = path
                currentPathIndex = 0
                PathRenderer.setPath(path)
                followPath(onComplete)
            } else {
                ChatUtils.error("Could not find path")
                onComplete()
            }
        }.start()
    }

    fun stop() {
        isFollowingPath = false
        pathfindingTimer?.cancel()
        pathfindingTimer = null
        Movement.clearAll()
        PathRenderer.clearPath()
        currentPath = null
    }

    private fun findPath(world: World, start: BlockPos, end: BlockPos): List<BlockPos>? {
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
            
            val current = openList.poll()
            openSet.remove(current.pos)

            // Found destination
            if (current.pos.distanceSq(end) < 9.0) { // Within 3 blocks
                return reconstructPath(current)
            }

            closedSet.add(current.pos)

            // Check neighbors (including diagonals)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue

                        val neighbor = current.pos.add(dx, dy, dz)
                        
                        if (closedSet.contains(neighbor)) continue
                        if (!isWalkable(world, neighbor)) continue

                        val tentativeG = current.g + if (dx != 0 && dz != 0) 1.414 else 1.0

                        val existingNode = openSet[neighbor]
                        if (existingNode != null && tentativeG >= existingNode.g) {
                            continue
                        }

                        val neighborNode = Node(neighbor, current, tentativeG, heuristic(neighbor, end))
                        openList.add(neighborNode)
                        openSet[neighbor] = neighborNode
                    }
                }
            }
        }

        ChatUtils.error("No path after $iterations iterations")
        return null
    }

    private fun isWalkable(world: World, pos: BlockPos): Boolean {
        // The block we're standing on should be solid (or air if we're falling)
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

    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(from.x - to.x)
        val dy = abs(from.y - to.y)
        val dz = abs(from.z - to.z)
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    private fun reconstructPath(node: Node): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        var current: Node? = node
        while (current != null) {
            path.add(0, current.pos)
            current = current.parent
        }
        return path
    }

    private fun followPath(onComplete: () -> Unit) {
        isFollowingPath = true
        val path = currentPath ?: return

        ChatUtils.info("Following path")

        var tickCount = 0
        var lastRotationUpdate = 0L
        var lastJumpTime = 0L
        
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
                        Mouse.stopTracking() // Stop cursor lock when done
                        cancel()
                        onComplete()
                        return
                    }

                    // Look ahead to next few waypoints for smoother movement
                    val lookAheadIndex = kotlin.math.min(currentPathIndex + 2, path.size - 1)
                    val target = path[lookAheadIndex]
                    val currentWaypoint = path[currentPathIndex]
                    
                    val diffX = currentWaypoint.x + 0.5 - player.posX
                    val diffZ = currentWaypoint.z + 0.5 - player.posZ
                    val distance = sqrt(diffX * diffX + diffZ * diffZ)

                    tickCount++
                    if (tickCount % 20 == 0) {
                        ChatUtils.info("WP ${currentPathIndex + 1}/${path.size} Dist=${"%.1f".format(distance)}")
                    }

                    // Check if close enough to current waypoint to advance
                    if (distance < 1.5) {
                        currentPathIndex++
                        PathRenderer.setWaypointIndex(currentPathIndex)
                        if (currentPathIndex >= path.size) {
                            ChatUtils.info("Destination reached!")
                            stop()
                            Mouse.stopTracking() // Stop cursor lock when done
                            cancel()
                            onComplete()
                            return
                        }
                        return
                    }

                    // Only update rotation every 150ms to reduce jerking
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastRotationUpdate > 150) {
                        val lookDiffX = target.x + 0.5 - player.posX
                        val lookDiffZ = target.z + 0.5 - player.posZ
                        val yaw = Math.toDegrees(Math.atan2(lookDiffZ, lookDiffX)).toFloat() - 90f
                        Mouse.lookAt(yaw, player.rotationPitch)
                        lastRotationUpdate = currentTime
                    }

                    // Move
                    Movement.startForward()
                    Movement.startSprinting()

                    // Predictive jumping - check if we need to jump soon
                    val shouldJump = if (currentPathIndex + 1 < path.size) {
                        val nextWaypoint = path[currentPathIndex + 1]
                        val currentY = player.posY.toInt()
                        val nextY = nextWaypoint.y
                        
                        // Jump if next waypoint is higher
                        val needsToGoUp = nextY > currentY
                        
                        // Check block in front of player (predictive)
                        val playerPos = BlockPos(player.posX, player.posY, player.posZ)
                        val frontBlock = world.getBlockState(
                            playerPos.offset(player.horizontalFacing)
                        )
                        val hasSolidBlockAhead = frontBlock.block.material.isSolid
                        
                        // Jump if: going up, or solid block ahead, or already colliding
                        needsToGoUp || hasSolidBlockAhead || player.isCollidedHorizontally
                    } else {
                        player.isCollidedHorizontally
                    }

                    // Sprint jump (jump while sprinting and moving forward)
                    if (shouldJump && player.onGround && (currentTime - lastJumpTime > 400)) {
                        Movement.startJumping()
                        lastJumpTime = currentTime
                        // Keep jumping for a short time to ensure we clear the obstacle
                        Thread {
                            Thread.sleep(100)
                            Movement.stopJumping()
                        }.start()
                    }

                } catch (e: Exception) {
                    ChatUtils.error("Error: ${e.message}")
                    stop()
                    Mouse.stopTracking()
                    cancel()
                    onComplete()
                }
            }
        }
        pathfindingTimer!!.scheduleAtFixedRate(task, 0, 50)
    }
}

