package best.bedwars.afk.bot.pathfinding

import best.bedwars.afk.BedwarsAFK
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
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
        
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        
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
            
            drawBox(pos, r, g, b, a, worldRenderer)
        }
        
        // Draw lines connecting waypoints
        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in currentWaypointIndex until path.size) {
            val pos = path[i]
            val (r, g, b, a) = if (i < currentWaypointIndex) {
                floatArrayOf(0.3f, 0.3f, 0.3f, 0.5f) // Gray
            } else {
                floatArrayOf(1f, 1f, 0f, 0.8f) // Yellow
            }
            
            worldRenderer.pos(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                .color(r, g, b, a).endVertex()
        }
        tessellator.draw()
        
        // Draw goal marker (large box at destination)
        if (path.isNotEmpty()) {
            val goal = path.last()
            drawLargeBox(goal, 0f, 1f, 0f, 0.3f, worldRenderer) // Green goal
        }
        
        GlStateManager.enableTexture2D()
        GlStateManager.enableDepth()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }
    
    private fun drawBox(pos: BlockPos, r: Float, g: Float, b: Float, a: Float, worldRenderer: net.minecraft.client.renderer.WorldRenderer) {
        val box = AxisAlignedBB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        )
        
        drawFilledBox(box, r, g, b, a, worldRenderer)
        drawOutlinedBox(box, r, g, b, 1f, worldRenderer)
    }
    
    private fun drawLargeBox(pos: BlockPos, r: Float, g: Float, b: Float, a: Float, worldRenderer: net.minecraft.client.renderer.WorldRenderer) {
        val box = AxisAlignedBB(
            pos.x - 0.2, pos.y.toDouble(), pos.z - 0.2,
            pos.x + 1.2, pos.y + 1.5, pos.z + 1.2
        )
        
        drawFilledBox(box, r, g, b, a, worldRenderer)
        drawOutlinedBox(box, r, g, b, 1f, worldRenderer)
    }
    
    private fun drawFilledBox(box: AxisAlignedBB, r: Float, g: Float, b: Float, a: Float, worldRenderer: net.minecraft.client.renderer.WorldRenderer) {
        val tessellator = Tessellator.getInstance()
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        
        // Bottom
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        
        // Top
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        
        // Sides
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        
        tessellator.draw()
    }
    
    private fun drawOutlinedBox(box: AxisAlignedBB, r: Float, g: Float, b: Float, a: Float, worldRenderer: net.minecraft.client.renderer.WorldRenderer) {
        val tessellator = Tessellator.getInstance()
        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        
        // Bottom square
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        
        // Top square
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        
        tessellator.draw()
        
        // Vertical lines
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(r, g, b, a).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(r, g, b, a).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(r, g, b, a).endVertex()
        
        tessellator.draw()
    }
}


