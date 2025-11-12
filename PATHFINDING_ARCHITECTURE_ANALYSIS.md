# Bedwars Bot - Pathfinding & Bridging Architecture

## System Overview

The bot uses a **two-system architecture** for complete autonomous navigation:

1. **AdvancedPathfinder.kt** - Pathfinding, navigation, and bridge coordination
2. **EagleBridge.kt** - Bridge execution (block placement, sneaking, strafing)

---

## AdvancedPathfinder.kt - Navigation Authority

**Purpose**: Main 3D pathfinding with A* algorithm and complete game navigation  
**Lines**: ~2,200  
**Authority**: Single source of truth for bridge mode state

### Core Responsibilities

1. **A* Pathfinding Algorithm** (`findPath3D`)
   - 3D obstacle avoidance with true walkability checks
   - Action type detection (WALK, JUMP, FALL, SPRINT, BRIDGE)
   - Neighbor generation with cardinal + diagonal + vertical directions
   - Intermediate waypoint creation every 2-3 blocks

2. **Path Following Loop** (50ms tick rate)
   - Smart targeting (aims for closest point on block bounding box)
   - Rotation management with smooth camera interpolation
   - Waypoint progression tracking
   - Destination detection

3. **Bridge Mode Coordination** (ONLY system that controls bridge state)
   - `bridgeModeActive` flag is sole authority
   - Calls `EagleBridge.activateBridge()` when BRIDGE nodes detected
   - Calls `EagleBridge.stopBridging()` on exit
   - Maintains bridge mode synchronization

4. **Stuck Detection & Recovery**
   - **During Bridging** (1+ second stuck):
     - Stage 1: Jump backward while staying in bridge mode
     - Stage 2: Persistent retry every 10 ticks (if still stuck >2.5 seconds)
     - Stage 3: Waypoint-based strafing with platform checking
     - SUCCESS: If Y increases >0.1 blocks, exit bridge and check for platforms
   - **During Walking** (3+ second stuck):
     - Creates intermediate waypoints at 0.5 block intervals
     - Automatically inserts into path for re-navigation

5. **Bridge Exit Logic**
   - **Exit Validation**: Bridge complete (`isBridgeComplete && !isPlayerFalling && isPlayerStable`) OR next waypoint below OR platform detected
   - **Exit Actions** (500ms transition):
     - Checks for platforms below (1-2 blocks)
     - Checks for safe drop zones (1-4 blocks)
     - Logs debug info about exit reason

### Key Features Added Recently

- **Exit Validation Separation**: Simple condition for when to exit (just `isBridgeComplete && !isPlayerFalling && isPlayerStable`)
- **Exit Actions**: Platform/safedrop checks happen during post-exit transition, not as exit conditions
- **Successful Backward Jump Detection**: When stuck during bridging, if jump succeeds (Y increases), exits bridge and checks platforms
- **Large Yaw Turn Pause** (>90°): Stops ALL movement when large rotation needed
- **Camera Smoothing**: 
  - Bridge exit rotation: 0.1f (10%)
  - Normal yaw: 0.25f (25%)
  - Bridging pitch: 0.2f (20%)
  - Bridge entry: 0.2f (20%)
- **Bidirectional Rotation**: Considers both left and right paths, chooses shortest

### Waypoint Frequency (Current)

- **Normal terrain**: Sparse (simplified, up to 5 blocks apart on straight paths)
- **Complex terrain**: Dense (at every turn/obstacle)
- **Height changes**: One waypoint per elevation change
- **Stuck recovery**: 0.5 block intervals (very dense)
- **Bridging**: 2-3 block spacing

---

## EagleBridge.kt - Bridge Execution (PASSIVE)

**Purpose**: Execute actual bridging behavior while AdvancedPathfinder handles navigation  
**Lines**: ~554  
**Design**: Completely passive system (only runs when AdvancedPathfinder activates)

### Core Responsibilities

1. **Block Placement**
   - Rotation math to place blocks behind player
   - Only fires when conditions met (block in hotbar, distance >0.05, pitch valid)
   - Human-like delays (2-3 ticks randomized)

2. **Sneaking Management**
   - Automatic sneaking when moving near edges
   - Maintains sneak state during active bridging
   - Auto-unshift on bridge exit

3. **Movement Control**
   - Sets `player.motionX/Z` for backward strafing
   - Respects Minecraft physics engine (no direct position manipulation)
   - Smooth easing with 0.9 friction coefficient

4. **Position Interpolation** (Strafing)
   - Smoothly centers player on block (150ms duration with easing)
   - Prevents falling off edges
   - Natural-looking movement

5. **Progress Tracking**
   - Detects stuck state (no block placement progress)
   - Monitors position changes
   - Resets on successful placement

### Public API

```kotlin
activateBridge()              // Start bridging
stopBridging()                // Stop and reset ALL state
isBridging(): Boolean         // Check if actively bridging
canActivateBridge()           // Check if void below + blocks available
getBlockCount(): Int          // Get inventory block count
switchToBlocks(): Boolean     // Switch to block hotbar slot
```

### Critical Design: PASSIVE System

- **NOT** responsible for bridge mode state (AdvancedPathfinder owns this)
- **ONLY** runs when AdvancedPathfinder explicitly calls `activateBridge()`
- **ONLY** stops when AdvancedPathfinder explicitly calls `stopBridging()`
- **Internal state** completely reset by `stopBridging()` call

### State Cleanup on stopBridging()

```kotlin
- readyToPlaceStartTime = null     // Block placement timing
- strafeTarget = null               // Position target
- strafeStartTime = null            // Strafing timing
- lastProgressPos = null            // Progress tracking
- stuckTicks = 0                    // Stuck counter
- Movement.stopSneak()              // Clear sneaking
- Mouse.stopTracking()              // Clear aim
```

---

## Bridge Mode Lifecycle

### Activation (When BRIDGE node detected by AdvancedPathfinder)

```
1. AdvancedPathfinder sets bridgeModeActive = true
2. AdvancedPathfinder calls EagleBridge.activateBridge()
3. AdvancedPathfinder faces backward (180° + offset for bridging)
   - Uses 0.1f interpolation for smooth rotation
   - Calculates pitch (83° for straight, 77° for diagonal)
4. Movement.startBackward() initiated
5. EagleBridge begins block placement every tick
```

### Active State (50ms tick loop)

```
Each AdvancedPathfinder tick while bridging:
1. Check if bridge complete (solid under + ahead blocks + player stable)
2. If complete: prepare to exit (stage validation)
3. If not complete: 
   - EagleBridge places blocks if conditions met
   - Handles sneaking/strafing
   - Checks stuck state
   - Retries if failed
```

### Exit (When validation passes)

```
Stage 1 - Exit Validation:
  → Bridge complete AND player stable AND not falling
  → OR next waypoint is below bot
  → OR platform detected below

Stage 2 - Exit Actions (500ms transition pause):
  → Movement cleared, bridge deactivated
  → Camera smooth rotation to next waypoint
  → Platform checks logged
  → Wait for rotation completion
  → Resume normal walking
```

### Successful Backward Jump (During Stuck Recovery)

```
If bot jumps backward while stuck (1-2.5 seconds into recovery):
1. Detect Y position increase (>0.1 blocks)
2. Exit bridge mode immediately
3. Call stopBridging() for cleanup
4. Check for platforms below
5. Resume normal pathfinding
```

---

## Integration Points

### Between AdvancedPathfinder and EagleBridge

| Operation | AdvancedPathfinder | EagleBridge |
|-----------|-------------------|-------------|
| **Bridge Activation** | Calls `activateBridge()` | Starts block placement |
| **Bridge Operation** | Controls rotation/waypoints | Places blocks, strafe |
| **Bridge Exit** | Calls `stopBridging()` | Clears ALL state |
| **Bridge Authority** | `bridgeModeActive` flag | Passive, no state |
| **Rotation Control** | Yaw/pitch only | Not involved |
| **Movement Control** | General movement | Only motionX/Z during bridge |

### Conflict Resolution

- **No Direct Position Manipulation**: Motion vectors respected, physics active
- **Clear Separation of Concerns**: Rotation (PathFinder) vs Movement (EagleBridge)
- **Single Bridge State**: AdvancedPathfinder is sole authority
- **Complete Cleanup**: stopBridging() resets all EagleBridge tracking

---

## Other Pathfinding Systems

### BridgingPathfinder.kt
- **Purpose**: Alternative bridging path planning (not currently used)
- **Status**: Maintained separately, possible future use
- **Note**: AdvancedPathfinder + EagleBridge is active system

### Legacy Systems (Unused)
- SimpleAStar.kt - Basic pathfinding
- ParkourPathfinder.kt - Parkour-specific
- NotEnoughFragsPathfinder.kt - Alternative implementation

---

## Recent Updates (November 2025)

### Bridge State Synchronization (Nov 11)
- ✅ Fixed bridge mode desynchronization between systems
- ✅ Removed duplicate state flags from EagleBridge
- ✅ Enhanced stopBridging() with complete cleanup
- ✅ All movement control conflicts resolved

### Stuck Recovery Improvements (Nov 11-12)
- ✅ Successful backward jump detection during recovery
- ✅ Automatic bridge exit on successful jump with Y change
- ✅ Platform checks triggered after successful jump recovery
- ✅ Three-tier stuck recovery for bridging (jump, persistent retry, strafe)
- ✅ Waypoint generation for walking stuck (0.5 block intervals)

### Camera Smoothing & Exit Logic (Nov 11-12)
- ✅ Multiple interpolation speeds (0.1f, 0.2f, 0.25f)
- ✅ Large turn pause (>90°) stops all movement
- ✅ Bidirectional rotation (considers both left/right paths)
- ✅ Separated exit validation from exit actions
- ✅ Platform/safedrop checks in exit actions (not validation)

### Build Status
✅ BUILD SUCCESSFUL - All changes compile without errors

---

## Architecture Quality

### ✅ Strengths
- **Single Authority**: Bridge mode has clear owner (AdvancedPathfinder)
- **Clear Separation**: Pathfinding vs Execution vs Movement
- **Physics Respected**: No direct position manipulation, motion vectors only
- **Complete Cleanup**: State reset on exit prevents residual behavior
- **Extensible**: Easy to add new pathfinder systems or bridge improvements

### ⚠️ Areas for Future Improvement
- Could add more sophisticated collision detection
- Could implement better stuck detection heuristics
- Could add waypoint caching for repeated paths
- BridgingPathfinder could be consolidated into AdvancedPathfinder

