package best.bedwars.afk.bot.detector

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11
import java.awt.Color

/**
 * GeneratorDetector - Scans for armor stands with "Diamond" or "Emerald" in their nametag
 * Activated by pressing H key
 * Renders ESP boxes and outputs coordinates to chat
 */
object GeneratorDetector {
    
    private var isActive = false
    private val detectedGenerators = mutableMapOf<Int, GeneratorInfo>()
    private var lastScanTime = 0L
    private val scanInterval = 1000L // Scan every 1 second when active
    
    enum class GeneratorType {
        DIAMOND, EMERALD
    }
    
    data class GeneratorInfo(
        val entityId: Int,
        val pos: BlockPos,
        val name: String,
        val type: GeneratorType,
        val lastSeen: Long
    )
    
    /**
     * Toggle the generator detector on/off
     */
    fun toggle() {
        isActive = !isActive
        if (isActive) {
            ChatUtils.info("§6[Generator] §aDetector activated - scanning for Diamond and Emerald generators...")
            scanForGenerators()
        } else {
            ChatUtils.info("§6[Generator] §cDetector deactivated")
            detectedGenerators.clear()
        }
    }
    
    /**
     * Check if detector is currently active
     */
    fun isActive(): Boolean = isActive
    
    /**
     * Scan the world for armor stands with "Diamond" in their nametag
     */
    private fun scanForGenerators() {
        val world = BedwarsAFK.mc.theWorld ?: return
        val player = BedwarsAFK.mc.thePlayer ?: return
        
        // Get all armor stands in loaded chunks
        val armorStands = world.loadedEntityList
            .filterIsInstance<EntityArmorStand>()
            .filter { it.hasCustomName() }
        
        var foundCount = 0
        val currentTime = System.currentTimeMillis()
        
        // Clear old detections (not seen in last 5 seconds)
        detectedGenerators.entries.removeIf { currentTime - it.value.lastSeen > 5000 }
        
        for (stand in armorStands) {
            val customName = stand.customNameTag ?: continue
            
            // Check if the nametag contains "Diamond" or "Emerald" (case-insensitive)
            val type = when {
                customName.contains("Diamond", ignoreCase = true) -> GeneratorType.DIAMOND
                customName.contains("Emerald", ignoreCase = true) -> GeneratorType.EMERALD
                else -> null
            }
            
            if (type != null) {
                // Adjust Y position down by 3 blocks for accurate generator location
                val pos = BlockPos(stand.posX, stand.posY - 3.0, stand.posZ)
                val entityId = stand.entityId
                
                // Update or add to detected generators
                if (!detectedGenerators.containsKey(entityId)) {
                    detectedGenerators[entityId] = GeneratorInfo(
                        entityId = entityId,
                        pos = pos,
                        name = customName,
                        type = type,
                        lastSeen = currentTime
                    )
                    
                    // Calculate distance from player
                    val distance = player.getDistance(stand.posX, stand.posY, stand.posZ)
                    
                    val typeColor = if (type == GeneratorType.DIAMOND) "§b" else "§a"
                    ChatUtils.info("§6[Generator] §7Found: $typeColor$customName §7at §b$pos §7(${String.format("%.1f", distance)}m away)")
                    foundCount++
                } else {
                    // Update last seen time
                    detectedGenerators[entityId] = detectedGenerators[entityId]!!.copy(lastSeen = currentTime)
                }
            }
        }
        
        if (foundCount == 0 && detectedGenerators.isEmpty()) {
            ChatUtils.info("§6[Generator] §7No generators found in loaded chunks")
        } else if (foundCount == 0) {
            ChatUtils.info("§6[Generator] §7Tracking ${detectedGenerators.size} generator(s)")
        }
    }
    
    /**
     * Update on client tick - scan periodically when active
     */
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (!isActive || event.phase != TickEvent.Phase.START) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime >= scanInterval) {
            scanForGenerators()
            lastScanTime = currentTime
        }
    }
    
    /**
     * Render ESP boxes around detected generators
     */
    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!isActive || detectedGenerators.isEmpty()) return
        
        val world = BedwarsAFK.mc.theWorld ?: return
        val player = BedwarsAFK.mc.thePlayer ?: return
        val partialTicks = event.partialTicks
        
        // Get render position (interpolated player position)
        val renderPosX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        val renderPosY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
        val renderPosZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks
        
        GlStateManager.pushMatrix()
        GlStateManager.translate(-renderPosX, -renderPosY, -renderPosZ)
        GlStateManager.disableTexture2D()
        GlStateManager.disableDepth()
        GlStateManager.disableLighting()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        
        for (generator in detectedGenerators.values) {
            // Determine colors based on generator type
            val fillColor = when (generator.type) {
                GeneratorType.DIAMOND -> Color(0, 255, 255, 50)  // Cyan
                GeneratorType.EMERALD -> Color(0, 255, 0, 50)    // Green
            }
            val outlineColor = when (generator.type) {
                GeneratorType.DIAMOND -> Color(0, 255, 255, 255)  // Cyan
                GeneratorType.EMERALD -> Color(0, 255, 0, 255)    // Green
            }
            val dimFillColor = when (generator.type) {
                GeneratorType.DIAMOND -> Color(0, 200, 200, 30)
                GeneratorType.EMERALD -> Color(0, 200, 0, 30)
            }
            val dimOutlineColor = when (generator.type) {
                GeneratorType.DIAMOND -> Color(0, 200, 200, 150)
                GeneratorType.EMERALD -> Color(0, 200, 0, 150)
            }
            
            // Find the actual entity to get precise position
            val entity = world.getEntityByID(generator.entityId)
            if (entity != null && entity is EntityArmorStand) {
                // Use entity's actual position for smooth rendering, adjusted -3 blocks down
                val x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks
                val y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - 3.0
                val z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks
                
                // Create bounding box at the generator location
                val box = AxisAlignedBB(
                    x - 0.5, y, z - 0.5,
                    x + 0.5, y + 2.0, z + 0.5
                )
                
                // Render filled box
                renderFilledBox(box, fillColor)
                
                // Render outline
                renderBoxOutline(box, outlineColor)
            } else {
                // Entity not found, use stored position (already adjusted -3 blocks)
                val box = AxisAlignedBB(
                    generator.pos.x - 0.5, generator.pos.y.toDouble(), generator.pos.z - 0.5,
                    generator.pos.x + 0.5, generator.pos.y + 2.0, generator.pos.z + 0.5
                )
                
                // Render with slightly dimmed color to indicate stale data
                renderFilledBox(box, dimFillColor)
                renderBoxOutline(box, dimOutlineColor)
            }
        }
        
        GlStateManager.enableDepth()
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }
    
    /**
     * Render a filled box
     */
    private fun renderFilledBox(box: AxisAlignedBB, color: Color) {
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        
        GlStateManager.color(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION)
        
        // Bottom face
        worldRenderer.pos(box.minX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).endVertex()
        
        // Top face
        worldRenderer.pos(box.minX, box.maxY, box.minZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).endVertex()
        
        // North face
        worldRenderer.pos(box.minX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).endVertex()
        
        // South face
        worldRenderer.pos(box.minX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).endVertex()
        
        // West face
        worldRenderer.pos(box.minX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).endVertex()
        
        // East face
        worldRenderer.pos(box.maxX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).endVertex()
        
        tessellator.draw()
    }
    
    /**
     * Render box outline
     */
    private fun renderBoxOutline(box: AxisAlignedBB, color: Color) {
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        
        GlStateManager.color(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        GL11.glLineWidth(2.0f)
        
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION)
        
        // Bottom edges
        worldRenderer.pos(box.minX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.minZ).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.maxZ).endVertex()
        
        worldRenderer.pos(box.minX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.minY, box.minZ).endVertex()
        
        // Top edges
        worldRenderer.pos(box.minX, box.maxY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).endVertex()
        
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).endVertex()
        
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).endVertex()
        
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).endVertex()
        
        // Vertical edges
        worldRenderer.pos(box.minX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.minZ).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.minZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).endVertex()
        
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).endVertex()
        
        worldRenderer.pos(box.minX, box.minY, box.maxZ).endVertex()
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).endVertex()
        
        tessellator.draw()
        GL11.glLineWidth(1.0f)
    }
    
    /**
     * Get all detected generators
     */
    fun getDetectedGenerators(): List<GeneratorInfo> {
        return detectedGenerators.values.toList()
    }
    
    /**
     * Clear all detected generators
     */
    fun clear() {
        detectedGenerators.clear()
    }
}

