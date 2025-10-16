package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.bot.player.Movement
import best.bedwars.afk.bot.player.Mouse
import best.bedwars.afk.utils.ChatUtils
import best.bedwars.afk.utils.EntityUtils
import net.minecraft.block.Block
import net.minecraft.block.BlockCarpet
import net.minecraft.block.BlockSlab
import net.minecraft.block.BlockStairs
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.util.BlockPos
import net.minecraft.world.World
import java.util.*
import kotlin.math.sqrt

/**
 * Pathfinder implementation ported from NotEnoughFrags
 * Uses Dijkstra's algorithm with extensive collision avoidance
 */
object NotEnoughFragsPathfinder {

    data class Node(
        val pos: BlockPos,
        val parent: Node?,
        var g: Double,
        val h: Double
    ) : Comparable<Node> {
        val f: Double
            get() = g + h

        override fun compareTo(other: Node): Int {
            return when {
                this.f < other.f -> -1
                this.f > other.f -> 1
                else -> 0
            }
        }
    }

    private var currentPath: List<BlockPos>? = null
    private var currentPathIndex = 0
    private var isFollowingPath = false
    private var pathfindingTimer: Timer? = null

    fun goTo(x: Int, y: Int, z: Int, onComplete: () -> Unit) {
        val player = BedwarsAFK.mc.thePlayer ?: return
        val world = BedwarsAFK.mc.theWorld ?: return

        val start = BlockPos(player.posX, player.posY - 1, player.posZ)
        val end = BlockPos(x, y, z)

        // Stop any existing pathfinding
        stop()

        // Calculate path in a separate thread to avoid blocking
        Thread {
            ChatUtils.info("Start: ($${start.x}, ${start.y}, ${start.z})")
            ChatUtils.info("End: ($x, $y, $z)")
            ChatUtils.info("Calculating path...")
            
            // Check if start position is valid
            val startBlock = world.getBlockState(start)
            val startAbove1 = world.getBlockState(start.up())
            val startAbove2 = world.getBlockState(start.up(2))
            ChatUtils.info("Start block: ${startBlock.block.localizedName}, solid: ${startBlock.block.material.isSolid}")
            ChatUtils.info("Above1: ${startAbove1.block.localizedName}, Above2: ${startAbove2.block.localizedName}")
            
            val path = findPathDijkstra(world, start, end)
            if (path != null && path.isNotEmpty()) {
                ChatUtils.info("Path found with ${path.size} waypoints")
                currentPath = path
                currentPathIndex = 0
                PathRenderer.setPath(path)
                
                // Schedule followPath on the main game thread
                followPath(onComplete)
            } else {
                ChatUtils.error("No path found! Start might not be walkable.")
                ChatUtils.info("Try standing on solid ground")
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

    private fun followPath(onComplete: () -> Unit) {
        isFollowingPath = true
        val path = currentPath ?: return
        
        ChatUtils.info("Starting to follow path")

        var tickCount = 0
        pathfindingTimer = Timer()
        val task = object : TimerTask() {
            override fun run() {
                try {
                    if (!isFollowingPath || currentPath == null) {
                        ChatUtils.info("Stopped (flag or null)")
                        cancel()
                        return
                    }

                    val player = BedwarsAFK.mc.thePlayer
                    if (player == null) {
                        ChatUtils.error("Player is null")
                        stop()
                        cancel()
                        onComplete()
                        return
                    }

                    // Check if reached end of path
                    if (currentPathIndex >= path.size) {
                        ChatUtils.info("Reached end of path")
                        stop()
                        cancel()
                        onComplete()
                        return
                    }

                    // Get current target waypoint
                    val target = path[currentPathIndex]
                    val diffX = target.x + 0.5 - player.posX
                    val diffZ = target.z + 0.5 - player.posZ

                    // Calculate distance to current waypoint
                    val distance = sqrt(diffX * diffX + diffZ * diffZ)
                    
                    // Only show debug every 20 ticks (1 second) to avoid spam
                    tickCount++
                    if (tickCount % 20 == 0) {
                        ChatUtils.info("WP ${currentPathIndex}/${path.size}: (${target.x}, ${target.y}, ${target.z}) Dist=${"%.1f".format(distance)}")
                    }

                    // If close enough to waypoint, advance to next
                    if (distance < 1.5) {
                        currentPathIndex++
                        PathRenderer.setWaypointIndex(currentPathIndex)
                        ChatUtils.info("Advanced to WP $currentPathIndex")
                        if (currentPathIndex < path.size) {
                            // Continue to next waypoint
                            return
                        } else {
                            // Reached end
                            ChatUtils.info("Path completed!")
                            stop()
                            cancel()
                            onComplete()
                            return
                        }
                    }

                    // Calculate yaw to target
                    val yaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f

                    // Use Mouse for smooth rotation
                    val pitch = player.rotationPitch
                    Mouse.lookAt(yaw, pitch)

                    // Move forward and sprint
                    Movement.startForward()
                    Movement.startSprinting()
                    
                    // Check if next position is too close and skip it
                    if (currentPathIndex < path.size - 1) {
                        val nextPos = path[currentPathIndex + 1]
                        val nextDiffX = nextPos.x + 0.5 - player.posX
                        val nextDiffZ = nextPos.z + 0.5 - player.posZ
                        val nextDistance = sqrt(nextDiffX * nextDiffX + nextDiffZ * nextDiffZ)

                        if (nextDistance < 1.0) {
                            currentPathIndex++
                            PathRenderer.setWaypointIndex(currentPathIndex)
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
        pathfindingTimer!!.scheduleAtFixedRate(task, 0, 50) // Check every 50ms
    }

    private fun getDistance(pos1: BlockPos, pos2: BlockPos): Double {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val dz = pos1.z - pos2.z
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    fun findPathDijkstra(world: World, start: BlockPos, end: BlockPos): List<BlockPos>? {
        // Create the open and closed lists
        val openList = PriorityQueue<Node>()
        val closedList = HashSet<BlockPos>()

        // Add the starting node to the open list
        val startNode = Node(start, null, 0.0, 0.0)
        openList.add(startNode)

        var iterations = 0
        val maxIterations = 10000 // Prevent infinite loops

        // Loop until the open list is empty or the end node is reached
        while (openList.isNotEmpty()) {
            iterations++
            if (iterations > maxIterations) {
                ChatUtils.error("Path search timeout (${iterations} iterations)")
                return null
            }
            
            if (iterations % 1000 == 0) {
                ChatUtils.info("Searching... ${iterations} nodes checked")
            }

            // Find the node with the lowest f value in the open list
            val currentNode = openList.poll()

            // Move the current node from the open list to the closed list
            closedList.add(currentNode.pos)

            // Check if the end node has been reached
            if (currentNode.pos == end) {
                // Construct the path by tracing back from the end node to the start node
                val path = ArrayList<BlockPos>()
                var node: Node? = currentNode
                while (node != null) {
                    path.add(0, node.pos)
                    node = node.parent
                }
                ChatUtils.info("Path found after $iterations iterations")
                return path
            }

            // Generate the neighbors of the current node
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x == 0 && y == 0 && z == 0) continue

                        val neighborPos = currentNode.pos.add(x, y, z)
                        val blockState = world.getBlockState(neighborPos)

                        var g = currentNode.g + getDistance(currentNode.pos, neighborPos)

                        // Check if the block is solid
                        if (!blockState.block.material.isSolid) continue

                        // Check if the block is solid or a stair, slab or carpet
                        val block = blockState.block
                        val isStairOrSlabOrCarpet = block is BlockStairs || block is BlockSlab || block !is BlockCarpet
                        if (!block.material.isSolid && !isStairOrSlabOrCarpet) continue

                        // Check if the two blocks above this block are passable
                        val blockAbove1 = neighborPos.up()
                        val blockAbove2 = blockAbove1.up()
                        val blockStateAbove1 = world.getBlockState(blockAbove1)
                        val blockStateAbove2 = world.getBlockState(blockAbove2)
                        var collisionBoxAbove1 = blockStateAbove1.block.getCollisionBoundingBox(world, blockAbove1, blockStateAbove1)
                        var collisionBoxAbove2 = blockStateAbove2.block.getCollisionBoundingBox(world, blockAbove2, blockStateAbove2)

                        // Allow navigation over carpets even if there is not enough clearance above them
                        val blockAbove = world.getBlockState(neighborPos.up()).block
                        if (block is BlockCarpet || blockAbove is BlockCarpet) {
                            collisionBoxAbove1 = null
                            collisionBoxAbove2 = null
                        }

                        if (collisionBoxAbove1 != null || collisionBoxAbove2 != null) continue

                        // Check if the neighbor block is one block higher than the current block
                        if (neighborPos.y == currentNode.pos.y + 1) {
                            // Check if the neighbor block is a stair or slab
                            val isStairOrSlab = block is BlockStairs || block is BlockSlab
                            if (!isStairOrSlab) continue
                        }

                        // Check if this is a diagonal neighbor and if there is a clear path
                        if (x != 0 && z != 0) {
                            var isBlocked = false
                            for (i in 0..3) {
                                val adjacentNeighbor1 = currentNode.pos.add(x, i, 0)
                                val adjacentNeighbor2 = currentNode.pos.add(0, i, z)
                                val adjacentBlockState1 = world.getBlockState(adjacentNeighbor1)
                                val adjacentBlockState2 = world.getBlockState(adjacentNeighbor2)

                                if (adjacentBlockState1.block.material.isSolid || adjacentBlockState2.block.material.isSolid) {
                                    isBlocked = true
                                    break
                                }
                            }

                            // Calculate distance to nearest wall
                            var minDistanceToWall = Double.MAX_VALUE
                            for (i in -2..2) {
                                for (j in -2..2) {
                                    for (k in -2..2) {
                                        if (i == 0 && j == 0 && k == 0) continue

                                        val adjacentNeighbor = currentNode.pos.add(i, j, k)
                                        val adjacentBlockState = world.getBlockState(adjacentNeighbor)

                                        if (adjacentBlockState.block.material.isSolid) {
                                            val distanceToWall = sqrt((i * i + j * j + k * k).toDouble())
                                            minDistanceToWall = Math.min(minDistanceToWall, distanceToWall)
                                        }
                                    }
                                }
                            }

                            // Add penalty based on distance to wall
                            g += 2 / minDistanceToWall

                            // Wall proximity penalties
                            val maxDistance = 3
                            var minDistanceToWalls = Double.MAX_VALUE
                            for (i in -maxDistance..maxDistance) {
                                for (j in -maxDistance..maxDistance) {
                                    for (k in -maxDistance..maxDistance) {
                                        val adjacentNeighbor = currentNode.pos.add(i, j, k)
                                        val adjacentMaterial = world.getBlockState(adjacentNeighbor).block.material

                                        if (adjacentMaterial.isSolid) {
                                            val distanceToWall = sqrt((i * i + j * j + k * k).toDouble())
                                            minDistanceToWalls = Math.min(minDistanceToWalls, distanceToWall)
                                        }
                                    }
                                }
                            }

                            if (minDistanceToWalls < maxDistance) {
                                g += (maxDistance - minDistanceToWalls) * 5
                            }

                            if (isBlocked) continue
                        }

                        // Check if within 2 blocks of liquid
                        var isWithin2BlocksOfLiquid = false
                        for (i in -2..2) {
                            for (j in -2..2) {
                                for (k in -2..2) {
                                    val adjacentNeighbor = currentNode.pos.add(i, j, k)
                                    val adjacentMaterial = world.getBlockState(adjacentNeighbor).block.material

                                    if (adjacentMaterial.isLiquid) {
                                        isWithin2BlocksOfLiquid = true
                                        break
                                    }
                                }
                            }
                        }

                        if (isWithin2BlocksOfLiquid) {
                            g += 6
                        }

                        // Check if neighbor is already in closed list
                        if (closedList.contains(neighborPos)) continue

                        // Check if neighbor is already in open list
                        var inOpenList = false
                        for (node in openList) {
                            if (node.pos == neighborPos) {
                                inOpenList = true
                                // Update g value if shorter path found
                                if (g < node.g) {
                                    node.g = g
                                }
                                break
                            }
                        }

                        // Add neighbor to open list
                        if (!inOpenList) {
                            val neighborNode = Node(neighborPos, currentNode, g, 0.0)
                            openList.add(neighborNode)
                        }
                    }
                }
            }
        }

        // No path was found
        return null
    }
}

