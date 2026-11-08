package best.skywars.bot

import java.util.*

object TimeHelper {

    fun setTimeout(action: () -> Unit, delayMs: Long): Timer {
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    action()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    timer.cancel()
                }
            }
        }, delayMs)
        return timer
    }
}
