# Bedwars AFK Bot - Kotlin Edition

A Minecraft 1.8.9 Forge mod that recreates the functionality of the original Bedwars AFKv1 macro bot using Duck Dueller's advanced movement and looking framework.

## 🆕 Recent Updates (November 2025)

### **Pathfinding & Bridging Improvements**
- ✅ **Bridge state synchronization** - Fixed desynchronization between AdvancedPathfinder and EagleBridge
- ✅ **Stuck recovery enhancements** - Three-tier recovery system with successful jump detection
- ✅ **Camera smoothing** - Multiple interpolation speeds for natural movement (0.1f-0.25f)
- ✅ **Large turn pause** - Complete movement stop when turning >90°
- ✅ **Bidirectional rotation** - Intelligently chooses shortest rotation path
- ✅ **Exit validation separation** - Bridge exit logic separated from platform/safedrop checks
- ✅ **Documentation consolidation** - Reduced redundant docs, improved organization

See [PATHFINDING_ARCHITECTURE_ANALYSIS.md](PATHFINDING_ARCHITECTURE_ANALYSIS.md) for complete details.
See [PATHFINDING_GUIDE.md](PATHFINDING_GUIDE.md) for user-facing features.

---

## 🚀 **TODO - Help Wanted!**

We're actively looking for contributors! Here are the priority features that need work:

### High Priority
- [ ] **Fix pitch/yaw block placement accuracy** - Bot sometimes misses the edge when bridging and falls off. Need more precise edge detection and crosshair positioning.
- [ ] **Smooth pathfinding movement** - Current movement can be jerky on complex paths. Implement better rotation interpolation and acceleration/deceleration.
- [ ] **Fix Watchdog flag on bridging** - Bot sends 35+ block placement packets to the same position, triggering anti-cheat. Implement placement cooldown and validation.
- [ ] **Generator detection & tracking** - Implement automatic generator position detection using pathfinding and save positions for resource collection optimization.

### Contributing
See the [Contributing Guide](CONTRIBUTING.md) for detailed instructions on getting started with Cursor AI!

**Quick Start:**
1. Install [Cursor](https://cursor.sh)
2. Clone: `https://github.com/familiar/Bedwars-Bot-V3.git`
3. Ask Cursor Agent: *"Read PATHFINDING_GUIDE.md and help me work on [TASK]"*

---

## Features

- **Automatic Game Management**: Joins bedwars games and handles full game lifecycle
- **Smart Resource Collection**: AFKs at generator while monitoring inventory
- **Auto-Shopping**: Automatically purchases items when enough resources collected
- **Teammate Resource Sharing**: Detects nearby teammates and drops resources
- **Enemy Defense**: Detects damage and activates defense mode (fireballs + golden apples)
- **Respawn Handling**: Automatically returns to generator after death
- **Staff Detection**: Immediately leaves games when staff members detected
- **Failsafe Systems**: Handles disconnects, limbo, errors, and edge cases

## Original Bot Recreation

This mod recreates the **exact** behavior of the Bedwars AFKv1 macro bot:

### Game Start Sequence
1. Detects game start via chat ("Protect your bed")
2. Walks backward into generator (80 ticks)
3. Saves generator coordinates
4. Detects and logs teammates
5. Starts idle AFK routine

### Idle Behavior
- Alternates left (130ms) and right (130ms) key presses every 5 seconds
- Continuously monitors iron count in inventory
- Scans for nearby teammates (7 block radius)
- Monitors health for enemy detection

### Purchase Sequence
- Triggers when iron count ≥ 12
- Finds "ITEM SHOP" entity
- Navigates to shop using smooth rotation
- Purchases items via rapid clicking
- Returns to generator coordinates

### Defense Mode
- Activated when player takes damage
- Looks backward (180° + 80° pitch)
- Uses spawn eggs (fireballs)
- Rapidly consumes 35 golden apples
- Returns to idle state

### Chat Event Handling
All original chat triggers implemented:
- Game modes: Regular, Lucky Blocks, Swappage
- Death: Final elimination, bed broken
- Game end: Reward summary
- Respawn: Return to generator
- Staff: [YOUTUBE], [HELPER], [MOD], [ADMIN]
- Errors: Connection issues, limbo, timeouts

## Technical Implementation

### Movement System (Duck Dueller Framework)
- Smooth, incremental rotation with speed limiting
- State-managed key controls
- Distance-based look speed scaling
- Randomization for human-like behavior

### Looking System
- Entity tracking with smooth rotation
- Fixed angle targeting
- Configurable rotation speeds (15° horizontal, 10° vertical)
- Random jitter (±0.5°) for natural appearance

### Event Architecture
- Forge event handlers for chat and world events
- State machine for game phases
- Timer-based routines for AFK movement
- Asynchronous task scheduling

## Usage

1. Build the mod: `./gradlew build`
2. Place in `.minecraft/mods` folder
3. Launch Minecraft 1.8.9 with Forge
4. Press `R` to toggle bot on/off
5. Bot automatically joins `/play bedwars_four_four`

## Configuration

**No configuration needed!** The bot automatically detects your Minecraft username when you start it.

## Key Differences from Original

| Feature | Original (Macro) | This (Kotlin) |
|---------|------------------|---------------|
| **Looking** | Instant angle snap | Smooth incremental rotation |
| **Movement** | Direct key commands | State-managed API |
| **Timing** | Tick-based (1t = 50ms) | Millisecond timers |
| **Integration** | External macro mod | Native Forge mod |
| **Error Handling** | Basic restarts | Comprehensive failsafes |

## Architecture

```
BedwarsAFK.kt (Main)
├── BedwarsBot.kt (Core Logic)
│   ├── State Machine (LOBBY → STARTING → IDLE → PURCHASING → etc.)
│   ├── Game Lifecycle Management
│   ├── Timer Management
│   └── Coordinate Navigation
├── ChatEventHandler.kt (Event Detection)
│   ├── Game Start/End
│   ├── Death/Respawn
│   ├── Staff Detection
│   └── Error Handling
└── Player Systems
    ├── Movement.kt (WASD + Sprint/Sneak)
    ├── Mouse.kt (Looking + Clicking)
    ├── Inventory.kt (Item Management)
    └── LobbyMovement.kt (Pre-game behavior)
```

## Credits

- **Original Bot**: Bedwars AFKv1 by Rice
- **Framework**: Duck Dueller movement system
- **Implementation**: AI-assisted Kotlin port

## Disclaimer

This bot is for educational purposes. Using automation on multiplayer servers may violate their terms of service.

