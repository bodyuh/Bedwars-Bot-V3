# Changelog

All notable changes to the Bedwars Bot V3 project.

---

## [Unreleased] - October 2025

### 🎯 Major Features Added

#### Vertical Movement System
- **Pillaring Up (PILLAR_UP)**: Bot can now climb vertically by placing blocks beneath itself
  - Supports 3-10 block heights
  - Automatically used when pathfinding encounters tall obstacles
  - Movement cost: 2.5x (slower but reliable)
  - Implementation: `AdvancedPathfinder.kt` lines 301-311, 582-603, 1286-1317

- **Drop Down Navigation (DROP_DOWN)**: Fast descent for heights < 4 blocks
  - Only used when landing is safe (solid ground verified)
  - Movement cost: 0.7x (faster than walking around)
  - Pathfinder automatically chooses fastest route
  - Implementation: `AdvancedPathfinder.kt` lines 313-318

#### ActionType Enum Expansion
```kotlin
enum class ActionType {
    WALK,       // Normal walking
    JUMP,       // Jumping up 1-2 blocks
    FALL,       // Falling down safely (1-4 blocks)
    SPRINT,     // Sprint jumping (2 blocks)
    BRIDGE,     // Eagle bridging across void
    PILLAR_UP,  // NEW: Towering/pillaring up
    DROP_DOWN   // NEW: Dropping down < 4 blocks
}
```

### 🐛 Critical Bug Fixes

#### Diagonal Bridging 180° Angle Correction
- **Problem**: Diagonal bridges went in exact opposite direction (180° off)
- **Cause**: Angles were set to face opposite of movement direction
- **Solution**: Corrected angle calculations in `AdvancedPathfinder.kt`
  - Moving SE: Now faces -45° (was 135°)
  - Moving NE: Now faces 45° (was -135°)
  - Moving SW: Now faces -135° (was 45°)
  - Moving NW: Now faces 135° (was -45°)
- **Location**: `AdvancedPathfinder.kt` lines 1093-1102

#### Diagonal Bridge Angle Lock
- **Problem**: Smooth rotation interpolation caused angle drift during diagonal bridges
- **Solution**: Disabled interpolation for diagonal bridges (instant angle lock)
- **Benefit**: Prevents falling off due to gradual angle changes
- **Location**: `AdvancedPathfinder.kt` lines 1119-1121

### ⚡ Performance Improvements

#### Pathfinding Node Limit
- **Old**: 10,000 nodes maximum
- **New**: 20,000 nodes maximum
- **Reason**: Complex paths with vertical movement need more exploration
- **Location**: `AdvancedPathfinder.kt` line 165

### 🎨 UX Improvements

#### Logging Cleanup
- ❌ Removed world join diagnostic message ("diag version loaded")
- ❌ Removed diagonal bridge debug spam (`[DIAG FIX]` messages)
- ❌ Removed excessive diagnostic banners
- ✅ All debug info now behind `debugMode` flag
- ✅ Cleaner chat experience during normal gameplay

**Files Modified**:
- `BedwarsAFK.kt`: Removed world join messages (lines 75-78)
- `AdvancedPathfinder.kt`: Removed diagonal debug logging (line 1061)
- `BridgingPathfinder.kt`: Removed aggressive diagnostic banners (lines 52-54)

### 🔧 Technical Changes

#### Diagonal Bridge Simplification
**Old Behavior**:
- Complex smooth rotation interpolation
- Speedbridge toggling on all bridges
- Multiple diagnostic messages

**New Behavior**:
- Instant angle lock (no interpolation)
- Always sneak on diagonal bridges (no speedbridge)
- Simple logic: Lock angle → Hold S → Sneak → Place blocks
- Pitch fixed at 77° for optimal visibility

**Comparison Table**:
| Feature | Straight Bridge | Diagonal Bridge |
|---------|----------------|-----------------|
| Angle | Calculated + 180° | Fixed diagonal |
| Rotation | Smooth interpolation | Instant lock |
| Speedbridge | Yes (cycle) | No (always sneak) |
| Pitch | 83° | 77° |

#### Movement Cost System
New movement costs for pathfinding optimization:
- `WALK`: 1.0x (baseline)
- `JUMP`: 1.2x (slight penalty)
- `FALL`: 0.8x (faster than walking)
- `SPRINT`: 1.5x (higher penalty)
- `BRIDGE`: 2.0x (slow and risky)
- `PILLAR_UP`: 2.5x (slowest but reliable)
- `DROP_DOWN`: 0.7x (fastest for descents)

**Location**: `AdvancedPathfinder.kt` lines 665-672

### 📝 Documentation Updates

#### New Documentation
- Added "Latest Enhancements (October 2025)" section to `PATHFINDER_3D_UPGRADE.md`
  - Diagonal bridging fix details
  - Vertical movement system documentation
  - Performance improvements
  - Logging cleanup
  - Technical details with code locations

- Added "Diagonal Bridging Update" section to `EAGLE_BRIDGING_GUIDE.md`
  - 180° angle error explanation
  - Diagonal bridging mechanics
  - Angle reference diagram
  - Testing procedures
  - Performance notes

- Updated `README.md` with recent updates summary

- Created `CHANGELOG.md` (this file)

### 🎮 Testing

#### New Capabilities to Test
1. **Pillaring**: Navigate to top of 5+ block walls
2. **Drop Down**: Bot should drop instead of taking stairs
3. **Diagonal Bridging**: Bridge at correct angles (-45°, 45°, -135°, 135°)

#### Debug Commands
- `#pathfinder debug` - Toggle debug mode
- `#pathfinder test` - Run pathfinding tests
- `#goto <x> <y> <z>` - Test navigation

---

## Previous Updates

See commit history and individual documentation files for earlier changes:
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Original Kotlin implementation
- [PATHFINDER_3D_UPGRADE.md](PATHFINDER_3D_UPGRADE.md) - 3D pathfinding rewrite
- [EAGLE_BRIDGING_GUIDE.md](EAGLE_BRIDGING_GUIDE.md) - Eagle bridging integration
- [COMPARISON.md](COMPARISON.md) - Original vs Kotlin comparison

---

## Contributing

Found a bug or want to contribute? See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines!

