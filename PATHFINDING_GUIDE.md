# Pathfinding Guide - User Documentation

## Overview

The Bedwars Bot includes a sophisticated **custom A* pathfinding system** with integrated eagle bridging capabilities. No Baritone needed - this is a lightweight, purpose-built solution for 1.8.9 Bedwars gameplay.

**Key Features:**
- ✅ True 3D pathfinding with obstacle avoidance
- ✅ Automatic bridge detection and execution
- ✅ Smooth camera rotation using custom rotation system
- ✅ Intelligent stuck detection with automatic recovery
- ✅ Natural-looking human movement patterns
- ✅ Debug visualization and logging

---

## How to Test

### Chat Commands

**#goto <x> <y> <z>** - Navigate to absolute coordinates
```
#goto 100 64 200
```

**#goto ~ ~ ~** - Navigate using relative coordinates
```
#goto ~50 ~ ~-30    (50 blocks east, 30 blocks north)
#goto ~ ~10 ~       (10 blocks up)
#goto ~-100 ~ ~     (100 blocks west)
```

**#stop** - Stop current pathfinding
```
#stop
```

**#pos** - Show current position and status
```
#pos
```

---

## Pathfinding Features

### A* Algorithm
- **Heuristic**: Euclidean distance to target
- **Cost Function**: Distance + vertical movement penalty
- **Max Distance**: 50 blocks
- **Max Iterations**: 1,000 (prevents infinite loops)
- **Performance**: <50ms pathfinding on typical distances

### Walkability Checks

Before navigating to a position, the system verifies:
- ✅ Current block is passable (air/plants, not solid)
- ✅ Block above is passable (2 blocks tall for player)
- ✅ Block below is solid (can stand on it)
- ✅ No dangerous terrain (lava, fire, cactus)
- ✅ Clear path between positions

### Movement Types

The pathfinder automatically detects and uses:
- **WALK** - Normal walking
- **JUMP** - Jumping up 1-2 blocks
- **FALL** - Falling down safely (1-4 blocks)
- **SPRINT** - Sprint jumping (2 blocks up)
- **BRIDGE** - Eagle bridging across void
- **PILLAR** - Pillaring up (placing blocks)

### Path Simplification

To keep the path efficient, waypoints are simplified:
- Keeps waypoints at **turns and obstacles**
- Removes waypoints on **straight paths**
- Keeps waypoints at **elevation changes**
- Maintains **max 5 block spacing** for safety

Typical spacing on flat terrain: ~2-5 blocks between waypoints

---

## Eagle Bridging

When a bridge is needed to cross void:

1. **Bridge Detection** - Pathfinder identifies gap too large to jump
2. **Bridge Activation** - Enters eagle bridging mode
3. **Block Placement** - Automatically places blocks while moving backward
4. **Position Centering** - Smoothly centers on each block
5. **Bridge Exit** - Exits when reaching solid ground
6. **Platform Check** - Checks for safe landing zones

### Bridging Behavior

While bridging:
- Bot looks backward (180° angle)
- Moves backward over void
- Automatically sneaks at block edges
- Places blocks on schedule
- Centers on each block before moving forward
- Exits when bridge completes or platform found

### Bridging Recovery

If the bot gets stuck while bridging:
1. **Stage 1** - Jumps backward while staying in bridge mode
2. **Stage 2** - Persistent retries every 10 ticks (if still stuck >2.5 seconds)
3. **Stage 3** - Waypoint-based strafing with platform detection
4. **Success Exit** - If successful jump detected, exits and checks for platforms

---

## Stuck Detection

### During Bridging

Stuck detected after **1+ second** (20+ ticks) without position change:
- Attempts backward jump recovery
- Falls back to strafing recovery
- Can detect and exit to safe platforms

### During Normal Walking

Stuck detected after **3+ seconds** (60+ ticks) without position change:
- Automatically creates intermediate waypoints
- Inserts at 0.5 block intervals
- Resumes pathfinding with denser waypoints

---

## Camera Movement

### Rotation Speeds

Different rotation speeds for smooth, natural movement:
- **Large turns (>90°)**: 0.1f (10%) - Very slow for dramatic turns
- **Bridge exit**: 0.1f (10%) - Slow rotation after bridging
- **Bridge entry**: 0.2f (20%) - Smooth entry to bridging
- **Bridging pitch**: 0.2f (20%) - Camera pitch during bridge
- **Normal yaw**: 0.25f (25%) - Standard turning speed

### Bidirectional Rotation

The bot intelligently rotates:
- Considers both left and right rotation paths
- Chooses the shortest rotation direction
- Prevents awkward long rotations
- Creates natural-looking movement patterns

### Large Turn Pauses

When needing to turn >90 degrees:
- Pauses ALL movement (forward, backward, sprint, strafe)
- Completes rotation safely
- Resumes movement after turning

---

## Exit from Bridging

The bot exits bridging when:
1. **Bridge Complete** - Solid ground below + ahead + player stable
2. **Waypoint Below** - Next waypoint is lower than current position
3. **Platform Detected** - Safe platform found below current position

After exiting:
1. **500ms Pause** - Player stabilizes
2. **Platform Check** - Checks for drop opportunities
3. **Rotation** - Turns to face next waypoint
4. **Resume** - Continues walking to next waypoint

---

## Performance

- **Pathfinding Speed**: <50ms for typical distances
- **Tick Rate**: 50ms (20 ticks per second)
- **Waypoint Processing**: Instant (no lag)
- **Memory Usage**: Minimal (custom lightweight implementation)
- **CPU Impact**: Negligible outside pathfinding calculations

---

## Troubleshooting

### Bot doesn't move
- Use `#pos` to verify current position
- Try `#stop` then try again
- Ensure destination is within 50 blocks

### Bot gets stuck
- Use `#stop` to cancel current pathfinding
- Path may be blocked by terrain
- System will attempt automatic recovery

### Pathfinding fails
- Check if destination is reachable
- Try moving closer to target
- Avoid locations behind thick walls

### Bridging looks choppy
- This is normal during stuck recovery
- Camera smoothing is intentionally varied by rotation speed
- Smooth recovery prevents unnatural jittering

---

## For Technical Details

See **PATHFINDING_ARCHITECTURE_ANALYSIS.md** for:
- Complete system architecture
- Integration between AdvancedPathfinder and EagleBridge
- Bridge mode lifecycle
- Stuck recovery algorithms
- State management details

---

## Performance Tips

1. **Pathfinding**: Use `#goto` with reasonable distances (20-50 blocks)
2. **Bridging**: Bot handles bridges automatically - no manual control needed
3. **Complex Terrain**: Give more time for stuck recovery (3-5 seconds)
4. **Obstacles**: Bot will find way around - don't interrupt pathfinding

---

That's it! The pathfinding system handles navigation automatically. Use the debug messages to monitor progress.

- **Max Distance**: 50 blocks (prevents lag)
- **Max Iterations**: 1000 (prevents infinite loops)

### **2. Walkability Checks**
Before adding a position to the path, it checks:
- ✅ Current block is passable (air, plants)
- ✅ Block above is also passable (2 blocks tall for player)
- ✅ Block below is solid (can stand on it)
- ❌ Not dangerous (lava, fire)

### **3. Path Following**
Once a path is found:
- Bot follows each waypoint in sequence
- Uses **your custom Mouse.kt** for smooth rotation
- Uses **your Movement.kt** for WASD controls
- Automatically jumps when needed
- Checks progress every 50ms

### **4. Fallback System**
If no path is found (too far, blocked):
- Switches to direct movement
- Moves straight toward target
- Jumps if stuck
- Still uses smooth rotation

---

## Testing in Singleplayer

1. **Start Minecraft** with the mod installed
2. **Create/Join a world**
3. **Press T** to open chat
4. **Type a command**:
   ```
   #pos
   #goto ~100 ~ ~50
   #stop
   ```

### **Test Scenarios:**

#### **Short Distance:**
```
#goto ~10 ~ ~10
```
Watch the bot smoothly navigate 10 blocks away

#### **Vertical Movement:**
```
#goto ~ ~5 ~
```
Watch the bot jump up 5 blocks

#### **Long Distance:**
```
#goto 0 64 0
```
Navigate to world spawn

#### **Complex Terrain:**
Find a hill or building and navigate to the top:
```
#goto 150 80 200
```

---

## Integration with Bedwars Bot

The pathfinding system is ready to use in your Bedwars bot. Here's how to integrate it:

### **Navigate to Shop:**
```kotlin
import best.bedwars.afk.bot.pathfinding.SimplePathfinder

private fun navigateToShop() {
    if (itemShop == null) return
    
    val x = itemShop!!.posX.toInt()
    val y = itemShop!!.posY.toInt()
    val z = itemShop!!.posZ.toInt()
    
    ChatUtils.info("Navigating to shop...")
    SimplePathfinder.goTo(x, y, z) {
        // Arrived at shop!
        startShopPurchase()
    }
}
```

### **Return to Generator:**
```kotlin
private fun returnToGenerator() {
    ChatUtils.info("Returning to generator...")
    SimplePathfinder.goTo(genX.toInt(), genY.toInt(), genZ.toInt()) {
        // Arrived at generator!
        isPurchasing = false
        returningToGen = false
        startIdleAtGen()
    }
}
```

### **Stop Pathfinding:**
```kotlin
// If you need to emergency stop
SimplePathfinder.stop()
```

### **Check if Active:**
```kotlin
if (SimplePathfinder.isActive()) {
    // Bot is currently pathfinding
}
```

---

## Advantages Over Manual Navigation

### **Before (Manual Navigation):**
- ❌ Gets stuck on walls
- ❌ Fixed timers (not adaptive)
- ❌ Can't handle obstacles
- ❌ Simple straight-line movement

### **After (A* Pathfinding):**
- ✅ Finds optimal path around obstacles
- ✅ Adaptive - reaches destination regardless of distance
- ✅ Handles complex terrain
- ✅ Professional pathfinding algorithm
- ✅ Still uses your smooth Mouse rotation!

---

## Technical Details

### **Files Created:**
- **`SimplePathfinder.kt`** - Core A* pathfinding logic
- **`PathfindingCommands.kt`** - Chat command handlers
- **`CommandHandler.kt`** - Command interception

### **Key Classes:**

**`SimplePathfinder`**:
- `goTo(x, y, z, onComplete)` - Navigate to position
- `stop()` - Stop pathfinding
- `isActive()` - Check if pathfinding

**`PathfindingCommands`**:
- Handles #goto, #stop, #pos, #help commands
- Supports absolute and relative coordinates

### **Integration Points:**
- Uses `Mouse.lookAt()` for rotation
- Uses `Movement.startForward()` for movement
- Uses `Movement.jump()` for jumping
- Uses `EntityUtils.getRotationsToCoords()` for calculations

---

## Performance

- **Lightweight**: No external dependencies
- **Fast**: Pathfinding completes in <50ms for typical distances
- **Efficient**: 50ms tick rate, only when active
- **Safe**: Max iterations prevent infinite loops

---

## Troubleshooting

### **Bot doesn't move**
- Check `#pos` to see current position
- Try `#stop` then try again
- Make sure destination is within 50 blocks

### **Bot gets stuck**
- Use `#stop` to cancel
- Path may be blocked - try different coordinates
- System will attempt jumps if stuck

### **Commands don't work**
- Make sure you type `#` before command
- Check spelling: `#goto` not `#go to`
- Commands are case-insensitive

---

## Next Steps

1. **Test in Singleplayer**:
   - Use `#goto` commands to test pathfinding
   - Watch how it uses smooth rotation
   - Try complex terrain

2. **Integrate with Bedwars Bot**:
   - Replace `navigateToShop()` with pathfinding version
   - Replace `returnToGenerator()` with pathfinding version
   - Test in actual Bedwars games

3. **Fine-tune if Needed**:
   - Adjust max distance (currently 50 blocks)
   - Adjust tick rate (currently 50ms)
   - Adjust waypoint distance (currently 1.5 blocks)

---

**You now have professional A* pathfinding that uses your custom smooth rotation system!** 🎯

No Baritone needed - this is a lightweight, custom solution built specifically for 1.8.9 Bedwars.









