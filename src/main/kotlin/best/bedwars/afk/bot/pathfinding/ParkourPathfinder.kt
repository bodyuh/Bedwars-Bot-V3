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
 * Specialized pathfinder for parkour
 * Handles gaps, precision jumps, and momentum-based movement
 */
object ParkourPathfinder {

    data class ParkourNode(
        val pos: BlockPos,
        val parent: ParkourNode?,
        var g: Double,
        val h: Double,
        val isGap: Boolean = false // Is this a gap that requires jumping?
    ) : Comparable<ParkourNode> {
        val f: Double get() = g + h
        override fun compareTo(other: ParkourNode): Int = f.compareTo(other.f)
    }

    private var currentPath: List<BlockPos>? = null
    private var currentPathIndex = 0
    private var isFollowingPath = false
    private var pathfindingTimer: Timer? = null
    private var gapIndices = mutableSetOf<Int>() // Which waypoints have gaps before them

    fun goTo(x: Int, y: Int, z: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        val start = BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt())
        val end = BlockPos(x, y, z)

        stop()

        Thread {
            ChatUtils.info("[Parkour] Finding path...")
            val path = findParkourPath(world, start, end)
            if (path != null && path.size > 1) {
                ChatUtils.info("[Parkour] Path found: ${path.size} jumps")
                currentPath = path
                currentPathIndex = 0
                PathRenderer.setPath(path)
                followParkourPath(onComplete)
            } else {
                ChatUtils.error("[Parkour] No path found")
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
        gapIndices.clear()
    }

    private fun findParkourPath(world: World, start: BlockPos, end: BlockPos): List<BlockPos>? {
        val openList = PriorityQueue<ParkourNode>()
        val closedSet = HashSet<BlockPos>()
        val openSet = HashMap<BlockPos, ParkourNode>()

        val startNode = ParkourNode(start, null, 0.0, heuristic(start, end))
        openList.add(startNode)
        openSet[start] = startNode

        var iterations = 0
        val maxIterations = 3000

        while (openList.isNotEmpty() && iterations < maxIterations) {
            iterations++

            val current = openList.poll()
            openSet.remove(current.pos)

            // Found destination
            if (current.pos.distanceSq(end) < 4.0) {
                val path = reconstructPath(current)
                ChatUtils.info("[Parkour] Found after $iterations iterations")
                return path
            }

            closedSet.add(current.pos)

            // Check parkour neighbors (including jumps across gaps)
            for (neighbor in getParkourNeighbors(world, current.pos)) {
                if (closedSet.contains(neighbor.pos)) continue

                val distance = current.pos.distanceSq(neighbor.pos)
                val jumpPenalty = if (neighbor.isGap) 1.5 else 1.0
                val tentativeG = current.g + sqrt(distance.toDouble()) * jumpPenalty

                val existingNode = openSet[neighbor.pos]
                if (existingNode != null && tentativeG >= existingNode.g) {
                    continue
                }

                val neighborNode = ParkourNode(
                    neighbor.pos,
                    current,
                    tentativeG,
                    heuristic(neighbor.pos, end),
                    neighbor.isGap
                )
                openList.add(neighborNode)
                openSet[neighbor.pos] = neighborNode
            }
        }

        return null
    }

    private fun getParkourNeighbors(world: World, pos: BlockPos): List<ParkourNode> {
        val neighbors = mutableListOf<ParkourNode>()

        // Check straight movements (4 directions) and jumps across gaps
        val directions = listOf(
            BlockPos(1, 0, 0),   // East
            BlockPos(-1, 0, 0),  // West
            BlockPos(0, 0, 1),   // South
            BlockPos(0, 0, -1),  // North
            BlockPos(1, 0, 1),   // Southeast diagonal
            BlockPos(1, 0, -1),  // Northeast diagonal
            BlockPos(-1, 0, 1),  // Southwest diagonal
            BlockPos(-1, 0, -1)  // Northwest diagonal
        )

        for (dir in directions) {
            // Check 1-4 block jumps (parkour distances)
            for (distance in 1..4) {
                for (yOffset in -2..2) { // Can jump up or down
                    val targetPos = pos.add(dir.x * distance, yOffset, dir.z * distance)
                    
                    if (isParkourLandable(world, targetPos)) {
                        // Check if there's a gap (requires jump)
                        val hasGap = hasGapBetween(world, pos, targetPos)
                        neighbors.add(ParkourNode(targetPos, null, 0.0, 0.0, hasGap))
                    }
                }
            }
        }

        return neighbors
    }

    private fun isParkourLandable(world: World, pos: BlockPos): Boolean {
        val block = world.getBlockState(pos).block
        val blockAbove1 = world.getBlockState(pos.up()).block
        val blockAbove2 = world.getBlockState(pos.up(2)).block

        // Must be solid block (or can be air for falling)
        val isSolid = block.material.isSolid || block.material == Material.air
        
        // Need headroom
        val hasHeadroom = !blockAbove1.material.isSolid && !blockAbove2.material.isSolid

        // Not void/lava/fire
        val isSafe = block.material != Material.lava && block.material != Material.fire

        return isSolid && hasHeadroom && isSafe
    }

    private fun hasGapBetween(world: World, from: BlockPos, to: BlockPos): Boolean {
        // Check if there's air/void between two positions
        val dx = to.x - from.x
        val dz = to.z - from.z
        val steps = maxOf(abs(dx), abs(dz))
        
        if (steps <= 1) return false // Adjacent, no gap

        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val checkX = from.x + (dx * t).toInt()
            val checkZ = from.z + (dz * t).toInt()
            val checkY = from.y
            
            val checkPos = BlockPos(checkX, checkY, checkZ)
            val block = world.getBlockState(checkPos)
            
            // If there's air below, it's a gap
            if (!block.block.material.isSolid) {
                return true
            }
        }
        
        return false
    }

    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(from.x - to.x)
        val dy = abs(from.y - to.y)
        val dz = abs(from.z - to.z)
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    private fun reconstructPath(node: ParkourNode): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        var current: ParkourNode? = node
        var index = 0
        
        while (current != null) {
            path.add(0, current.pos)
            if (current.isGap) {
                gapIndices.add(index)
            }
            current = current.parent
            index++
        }
        return path
    }

    private fun followParkourPath(onComplete: () -> Unit) {
        isFollowingPath = true
        val path = currentPath ?: return

        ChatUtils.info("[Parkour] Starting parkour sequence")

        var tickCount = 0
        var lastJumpTime = 0L
        var isInJump = false
        
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
                        ChatUtils.info("[Parkour] Completed!")
                        stop()
                        cancel()
                        onComplete()
                        return
                    }

                    val target = path[currentPathIndex]
                    val diffX = target.x + 0.5 - player.posX
                    val diffZ = target.z + 0.5 - player.posZ
                    val diffY = target.y - player.posY
                    val distance = sqrt(diffX * diffX + diffZ * diffZ)

                    tickCount++

                    // Progress update
                    if (tickCount % 20 == 0) {
                        ChatUtils.info("[Parkour] Jump ${currentPathIndex + 1}/${path.size}")
                    }

                    // Check if we've reached the current waypoint
                    if (distance < 0.8 && abs(diffY) < 1.5 && player.onGround) {
                        currentPathIndex++
                        PathRenderer.setWaypointIndex(currentPathIndex)
                        isInJump = false
                        
                        if (currentPathIndex >= path.size) {
                            ChatUtils.info("[Parkour] Completed!")
                            stop()
                            cancel()
                            onComplete()
                            return
                        }
                        return
                    }

                    // Look at target
                    val yaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f
                    val pitch = Math.toDegrees(Math.atan2(-diffY, sqrt(diffX * diffX + diffZ * diffZ))).toFloat()
                    Mouse.lookAt(yaw, pitch)

                    // Movement logic
                    Movement.startForward()
                    Movement.startSprinting()

                    val currentTime = System.currentTimeMillis()
                    
                    // Parkour jump logic
                    if (player.onGround && !isInJump) {
                        // Check if we need to jump
                        val needsJump = distance > 0.5 || diffY > 0.5
                        
                        // For precise parkour, jump at the edge
                        val isAtEdge = isNearBlockEdge(player.posX, player.posZ, yaw)
                        
                        if (needsJump && (isAtEdge || currentTime - lastJumpTime > 300)) {
                            // Sprint for momentum
                            Movement.startSprinting()
                            Thread.sleep(50) // Brief sprint moment
                            
                            // Jump
                            Movement.startJumping()
                            isInJump = true
                            lastJumpTime = currentTime
                            
                            Thread {
                                Thread.sleep(100)
                                Movement.stopJumping()
                            }.start()
                            
                            ChatUtils.info("[Parkour] Jumping! Dist: ${"%.1f".format(distance)}")
                        }
                    }

                    // Mid-air control
                    if (!player.onGround && isInJump) {
                        // Keep looking at target and moving forward
                        Movement.startForward()
                        
                        // Strafe slightly if off-angle
                        val angleDiff = normalizeYaw(yaw - player.rotationYaw)
                        if (abs(angleDiff) > 15) {
                            if (angleDiff > 0) {
                                Movement.startLeft()
                                Movement.stopRight()
                            } else {
                                Movement.startRight()
                                Movement.stopLeft()
                            }
                        } else {
                            Movement.stopLeft()
                            Movement.stopRight()
                        }
                    }

                } catch (e: Exception) {
                    ChatUtils.error("[Parkour] Error: ${e.message}")
                    e.printStackTrace()
                    stop()
                    cancel()
                    onComplete()
                }
            }
        }
        pathfindingTimer!!.scheduleAtFixedRate(task, 0, 50)
    }

    private fun isNearBlockEdge(x: Double, z: Double, yaw: Float): Boolean {
        // Check if player is near the edge of current block in movement direction
        val blockX = x - kotlin.math.floor(x)
        val blockZ = z - kotlin.math.floor(z)
        
        // Calculate which edge based on yaw
        val radians = Math.toRadians(yaw.toDouble() + 90.0)
        val dirX = kotlin.math.cos(radians)
        val dirZ = kotlin.math.sin(radians)
        
        // Check if close to edge in movement direction
        return when {
            dirX > 0.5 -> blockX > 0.7  // Moving east
            dirX < -0.5 -> blockX < 0.3 // Moving west
            dirZ > 0.5 -> blockZ > 0.7  // Moving south
            dirZ < -0.5 -> blockZ < 0.3 // Moving north
            else -> false
        }
    }

    private fun normalizeYaw(yaw: Float): Float {
        var normalized = yaw
        while (normalized > 180f) normalized -= 360f
        while (normalized < -180f) normalized += 360f
        return normalized
    }
}









