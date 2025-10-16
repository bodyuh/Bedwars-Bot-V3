package best.bedwars.afk.bot.player

import best.bedwars.afk.BedwarsAFK
import best.bedwars.afk.utils.RandomUtils
import best.bedwars.afk.utils.TimeUtils
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object LobbyMovement {

    private var tickYawChange = 0f
    private var intervals: ArrayList<Timer?> = ArrayList()
    private var active = false

    fun startCircling() {
        active = true
        val left = RandomUtils.randomBool()
        val speed = RandomUtils.randomDoubleInRange(2.0, 5.0).toFloat()
        
        tickYawChange = if (left) -speed else speed
        
        TimeUtils.setTimeout({
            Movement.startForward()
            intervals.add(TimeUtils.setInterval({
                tickYawChange = if (RandomUtils.randomBool()) {
                    RandomUtils.randomDoubleInRange(-5.0, -2.0).toFloat()
                } else {
                    RandomUtils.randomDoubleInRange(2.0, 5.0).toFloat()
                }
            }, 0, RandomUtils.randomIntInRange(2000, 4000)))
        }, RandomUtils.randomIntInRange(100, 300))
    }

    fun stop() {
        active = false
        Movement.clearAll()
        tickYawChange = 0f
        intervals.forEach { it?.cancel() }
        intervals.clear()
    }

    @SubscribeEvent
    fun onTick(ev: TickEvent.ClientTickEvent) {
        if (active && tickYawChange != 0f && BedwarsAFK.mc.thePlayer != null) {
            BedwarsAFK.mc.thePlayer.rotationYaw += tickYawChange
        }
    }
}


