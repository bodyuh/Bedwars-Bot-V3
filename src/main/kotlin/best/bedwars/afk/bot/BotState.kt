package best.bedwars.afk.bot

enum class BotState {
    LOBBY,              // In lobby/not in game
    STARTING,           // Game starting, moving to generator
    IDLE_AT_GEN,        // AFK at generator
    PURCHASING,         // Going to shop
    AT_SHOP,            // At shop buying items
    RETURNING_TO_GEN,   // Walking back to generator
    DEFENSE_MODE,       // Enemy detected, defending
    RESPAWNING,         // Dead, waiting to respawn
    DEFENDING_BED,      // Placing blocks around bed
    BRIDGING_TO_DIAMOND,// Bridging to diamond generator
    COLLECTING_DIAMONDS,// Collecting diamonds
    RETURNING_FROM_DIAMOND  // Returning from diamond island
}


