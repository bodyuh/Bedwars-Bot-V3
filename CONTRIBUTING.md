# Contributing to Bedwars AFK Bot

Welcome! This guide will help you contribute using Cursor AI.

## 🎯 Current Priority Tasks

See [README TODO](README.md#-todo---help-wanted) for current tasks. Quick summary:

1. **Fix block placement accuracy** - Bot misses edges when bridging
2. **Smooth pathfinding movement** - Jerky movements on complex paths  
3. **Fix Watchdog anti-cheat flags** - Too many placement packets
4. **Generator detection & tracking** - Auto-detect and save generator positions

## 🚀 Quick Setup (5 minutes)

### 1. Install Cursor
Download from [cursor.sh](https://cursor.sh)

### 2. Clone Repository
```bash
git clone https://github.com/familiar/Bedwars-Bot-V3.git
cd BedwarsAFK-Kotlin
```

### 3. Talk to Cursor Agent
Press `Ctrl+L` (or `Cmd+L` on Mac) to open Cursor Agent:

```
You: "Read PATHFINDING_GUIDE.md and EAGLE_BRIDGING_GUIDE.md to understand the codebase"

[Agent reads files]

You: "I want to fix [TASK FROM TODO]. Show me the relevant files and explain how."

[Agent explains and guides you]

You: "Implement the fix"

[Agent writes code]
```

### 4. Test
```bash
./gradlew build
# JAR is in build/libs/
# Copy to .minecraft/mods/ and test
```

In-game debug commands:
- `#goto <x> <y> <z>` - Test pathfinding
- `#bridge start/stop` - Test bridging
- `#debug on/off` - Toggle verbose logging
- `#pos` - Show position

### 5. Submit
```bash
git add .
git commit -m "Your change description"
git push
```

## 📚 Understanding the Codebase

### Key Files
- `AdvancedPathfinder.kt` - 3D A* pathfinding, navigation
- `EagleBridge.kt` - Eagle bridging mechanics
- `Movement.kt` - WASD/sneak controls
- `Mouse.kt` - Looking and clicking

### Architecture
```
AdvancedPathfinder.kt → Navigation, jumping, bridging
└─ EagleBridge.kt → Sneaking, block placement, rotation
   └─ Movement.kt → Low-level WASD/sneak
   └─ Mouse.kt → Looking/clicking
```

### Common Patterns

**Get player position:**
```kotlin
val player = mc.thePlayer ?: return
val pos = BlockPos(player.posX, player.posY, player.posZ)
```

**Smooth rotation:**
```kotlin
val delta = (targetYaw - player.rotationYaw) * 0.3f
player.rotationYaw += delta
```

**Safe movement:**
```kotlin
Movement.stopMovement()  // Always stop first!
Movement.startBackward()
```

## 💡 Tips

### Using Cursor AI Effectively
- **Be specific**: "Fix pitch in EagleBridge.kt line 150" > "fix the bug"
- **Ask for context**: "Show me where this is called"
- **Iterate**: "Still broken, the pitch seems too high"
- **Request explanations**: "Why does this flag anti-cheat?"

### Common Pitfalls

❌ Don't hold conflicting keys:
```kotlin
Movement.startForward()
Movement.startBackward()  // BUG!
```

✅ Always stop first:
```kotlin
Movement.stopMovement()
Movement.startBackward()
```

❌ Don't spam packets:
```kotlin
for (i in 0..100) {
    rightClickBlock()  // Watchdog flag!
}
```

✅ Add cooldowns:
```kotlin
if (System.currentTimeMillis() - lastPlaceTime > 100) {
    rightClickBlock()
    lastPlaceTime = System.currentTimeMillis()
}
```

## 🐛 Found a Bug?

Open an issue with:
1. What's wrong?
2. Steps to reproduce
3. Expected vs actual behavior
4. Logs/screenshots

## ❓ Questions?

Ask Cursor AI first - it has full codebase context:

```
"How does edge detection work?"
"Why is pitch fixed at 83 degrees?"
"What triggers bridge mode?"
```

## 🎉 Thank You!

Every contribution helps. Happy coding! 🚀

