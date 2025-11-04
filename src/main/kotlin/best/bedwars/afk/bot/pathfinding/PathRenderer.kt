package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11

/**
 * Renders the pathfinding path in the world (like Baritone)
 */
object PathRenderer {
    
    private var currentPath: List<BlockPos>? = null
    private var currentWaypointIndex: Int = 0
    
    fun setPath(path: List<BlockPos>?) {
        currentPath = path
        currentWaypointIndex = 0
    }
    
    fun setWaypointIndex(index: Int) {
        currentWaypointIndex = index
    }
    
    fun clearPath() {
        currentPath = null
        currentWaypointIndex = 0
    }
    
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        val path = currentPath ?: return
        if (path.isEmpty()) return
        
        val player = BedwarsAFK.mc.thePlayer ?: return
        val partialTicks = event.partialTicks
        
        // Calculate player's interpolated position for smooth rendering
        val playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        val playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
        val playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks
        
        GlStateManager.pushMatrix()
        GlStateManager.disableTexture2D()
        GlStateManager.disableDepth()
        GlStateManager.disableLighting()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        
        // Translate to player position
        GlStateManager.translate(-playerX, -playerY, -playerZ)
        
        // Draw path blocks
        for (i in currentWaypointIndex until path.size) {
            val pos = path[i]
            val isCurrent = (i == currentWaypointIndex)
            val isLookAhead = (i == kotlin.math.min(currentWaypointIndex + 3, path.size - 1))
            
            // Color coding:
            // Current waypoint: Bright Green
            // Look-ahead: Cyan
            // Completed: Dark Gray
            // Upcoming: Yellow
            val (r, g, b, a) = when {
                isCurrent -> floatArrayOf(0f, 1f, 0f, 0.4f) // Green
                isLookAhead -> floatArrayOf(0f, 1f, 1f, 0.3f) // Cyan
                i < currentWaypointIndex -> floatArrayOf(0.3f, 0.3f, 0.3f, 0.2f) // Gray (passed)
                else -> floatArrayOf(1f, 1f, 0f, 0.25f) // Yellow (upcoming)
            }
            
            drawBox(pos, r, g, b, a)
        }
        
        // Draw lines connecting waypoints
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GL11.glLineWidth(2.0f)
        GL11.glBegin(GL11.GL_LINE_STRIP)
        
        for (i in currentWaypointIndex until path.size) {
            val pos = path[i]
            val (r, g, b, a) = if (i < currentWaypointIndex) {
                floatArrayOf(0.3f, 0.3f, 0.3f, 0.5f) // Gray
            } else {
                floatArrayOf(1f, 1f, 0f, 0.8f) // Yellow
            }
            
                GL11.glColor4f(r, g, b, a)
            GL11.glVertex3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        }
        
        GL11.glEnd()
        
        // Draw goal marker (large box at destination)
        if (path.isNotEmpty()) {
            val goal = path.last()
            drawLargeBox(goal, 0f, 1f, 0f, 0.3f) // Green goal
        }
        
        GlStateManager.enableTexture2D()
        GlStateManager.enableDepth()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }
    
    private fun drawBox(pos: BlockPos, r: Float, g: Float, b: Float, a: Float) {
        val box = AxisAlignedBB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        )
        
        drawFilledBox(box, r, g, b, a)
        drawOutlinedBox(box, r, g, b, 1f)
    }
    
    private fun drawLargeBox(pos: BlockPos, r: Float, g: Float, b: Float, a: Float) {
        val box = AxisAlignedBB(
            pos.x - 0.2, pos.y.toDouble(), pos.z - 0.2,
            pos.x + 1.2, pos.y + 1.5, pos.z + 1.2
        )
        
        drawFilledBox(box, r, g, b, a)
        drawOutlinedBox(box, r, g, b, 1f)
    }
    
    private fun drawFilledBox(box: AxisAlignedBB, r: Float, g: Float, b: Float, a: Float) {
        // Draw filled box using GL_QUADS
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GL11.glColor4f(r, g, b, a)
        
        // Bottom face
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex3d(box.minX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ)
        GL11.glVertex3d(box.minX, box.minY, box.maxZ)        // Top face
        GL11.glVertex3d(box.minX, box.maxY, box.minZ)
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ)
        
        // Front face
        GL11.glVertex3d(box.minX, box.minY, box.maxZ)
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ)
        
        // Back face
        GL11.glVertex3d(box.minX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ)
        GL11.glVertex3d(box.minX, box.maxY, box.minZ)
        
        // Left face
        GL11.glVertex3d(box.minX, box.minY, box.minZ)
        GL11.glVertex3d(box.minX, box.minY, box.maxZ)
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.minX, box.maxY, box.minZ)
        
        // Right face
        GL11.glVertex3d(box.maxX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ)
        
        GL11.glEnd()
    }
    
    private fun drawOutlinedBox(box: AxisAlignedBB, r: Float, g: Float, b: Float, a: Float) {
        // Draw lines
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glLineWidth(2.0f)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glColor4f(r, g, b, a)
        GL11.glBegin(GL11.GL_LINES)
        
        // Bottom square
        GL11.glVertex3d(box.minX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.minY, box.minZ)
        
        GL11.glVertex3d(box.maxX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ)
        
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ)
        GL11.glVertex3d(box.minX, box.minY, box.maxZ)
        
        GL11.glVertex3d(box.minX, box.minY, box.maxZ)
        GL11.glVertex3d(box.minX, box.minY, box.minZ)
        
        // Top square
        GL11.glVertex3d(box.minX, box.maxY, box.minZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ)
        
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ)
        
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ)
        
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ)
        GL11.glVertex3d(box.minX, box.maxY, box.minZ)
        
        // Vertical lines
        GL11.glVertex3d(box.minX, box.minY, box.minZ)
        GL11.glVertex3d(box.minX, box.maxY, box.minZ)
        
        GL11.glVertex3d(box.maxX, box.minY, box.minZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ)
        
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ)
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ)
        
        GL11.glVertex3d(box.minX, box.minY, box.maxZ)
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ)
        
        GL11.glEnd()
    }
}









