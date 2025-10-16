package best.bedwars.afk.utils

import kotlin.random.Random

object RandomUtils {
    
    fun randomIntInRange(min: Int, max: Int): Int {
        return Random.nextInt(min, max + 1)
    }
    
    fun randomDoubleInRange(min: Double, max: Double): Double {
        return min + (max - min) * Random.nextDouble()
    }
    
    fun randomBool(): Boolean {
        return Random.nextBoolean()
    }
}


