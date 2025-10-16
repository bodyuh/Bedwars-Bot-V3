package best.bedwars.afk.utils

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.ChatUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Items
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import kotlin.math.abs

object EntityUtils {

    /**
     * Get rotations to look at an entity
     */
    fun getRotations(player: EntityPlayer?, target: Entity?, raw: Boolean): FloatArray? {
        return if (target == null || player == null) {
            null
        } else {
            val pos = Vec3(target.posX, target.posY + target.eyeHeight, target.posZ)

            val diffX = pos.xCoord - player.posX
            val diffY = pos.yCoord - (player.posY + player.getEyeHeight().toDouble())
            val diffZ = pos.zCoord - player.posZ
            val dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ).toDouble()
            val yaw = (Math.atan2(diffZ, diffX) * 180.0 / 3.141592653589793).toFloat() - 90.0f
            val pitch = (-(Math.atan2(diffY, dist) * 180.0 / 3.141592653589793)).toFloat()

            if (raw) {
                floatArrayOf(
                    MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw),
                    MathHelper.wrapAngleTo180_float(pitch - player.rotationPitch)
                )
            } else floatArrayOf(
                player.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw),
                player.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - player.rotationPitch)
            )
        }
    }

    /**
     * Get rotations to coordinates
     */
    fun getRotationsToCoords(player: EntityPlayer?, x: Double, y: Double, z: Double): FloatArray? {
        return if (player == null) {
            null
        } else {
            val diffX = x - player.posX
            val diffY = y - (player.posY + player.getEyeHeight().toDouble())
            val diffZ = z - player.posZ
            val dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ).toDouble()
            val yaw = (Math.atan2(diffZ, diffX) * 180.0 / 3.141592653589793).toFloat() - 90.0f
            val pitch = (-(Math.atan2(diffY, dist) * 180.0 / 3.141592653589793)).toFloat()

            floatArrayOf(yaw, pitch)
        }
    }

    /**
     * Get distance without Y
     */
    fun getDistanceNoY(player: EntityPlayer?, target: Entity?): Float {
        return if (target == null || player == null) {
            0f
        } else {
            val diffX = player.posX - target.posX
            val diffZ = player.posZ - target.posZ
            MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ).toFloat()
        }
    }

    /**
     * Get all nearby players (excluding self)
     */
    fun getNearbyPlayers(radius: Float): List<EntityPlayer> {
        val players = mutableListOf<EntityPlayer>()
        if (BedwarsAFK.mc.theWorld != null && BedwarsAFK.mc.thePlayer != null) {
            for (entity in BedwarsAFK.mc.theWorld.playerEntities) {
                if (entity != BedwarsAFK.mc.thePlayer && 
                    BedwarsAFK.mc.thePlayer.getDistanceToEntity(entity) <= radius &&
                    entity.isEntityAlive) {
                    players.add(entity)
                }
            }
        }
        return players
    }

    /**
     * Check if player is wearing leather helmet (teammate detection)
     */
    fun hasLeatherHelmet(player: EntityPlayer): Boolean {
        val helmet = player.getCurrentArmor(3) // 3 = helmet slot
        return helmet != null && helmet.item == Items.leather_helmet
    }

    /**
     * Get teammates (players nearby - improved detection)
     */
    fun getTeammates(radius: Float): List<EntityPlayer> {
        val teammates = mutableListOf<EntityPlayer>()
        if (BedwarsAFK.mc.theWorld != null && BedwarsAFK.mc.thePlayer != null) {
            val myTeam = BedwarsAFK.mc.thePlayer.team
            
            for (entity in BedwarsAFK.mc.theWorld.playerEntities) {
                if (entity != BedwarsAFK.mc.thePlayer && 
                    BedwarsAFK.mc.thePlayer.getDistanceToEntity(entity) <= radius &&
                    entity.isEntityAlive) {
                    
                    // Check if they're on the same team
                    if (myTeam != null && entity.team == myTeam) {
                        teammates.add(entity)
                    }
                    // Fallback: check for leather helmet (original method)
                    else if (hasLeatherHelmet(entity)) {
                        teammates.add(entity)
                    }
                }
            }
        }
        return teammates
    }

    /**
     * Find item shop armor stand
     */
    fun findItemShop(): Entity? {
        if (BedwarsAFK.mc.theWorld != null) {
            for (entity in BedwarsAFK.mc.theWorld.loadedEntityList) {
                if (entity.name.contains("ITEM SHOP", ignoreCase = true)) {
                    return entity
                }
            }
        }
        return null
    }
}


