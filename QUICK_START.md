# Quick Start Guide

Get the Bedwars AFK Bot running in 5 minutes!

---

## 🚀 Installation (3 Steps)

### Step 1: Build the Mod
```bash
cd BedwarsAFK-Kotlin
./gradlew build
```

### Step 2: Install
Copy the JAR from `build/libs/` to your `.minecraft/mods/` folder

### Step 3: Launch & Play
**No configuration needed!** Just launch Minecraft 1.8.9 with Forge and press `R` to start the bot. Your username is detected automatically.

---

## 🎮 Usage

1. **Launch Minecraft 1.8.9 with Forge**
2. **Join Hypixel** (`mc.hypixel.net`)
3. **Press `R`** to toggle the bot ON
4. Bot automatically:
   - Joins bedwars games
   - Farms resources at generator
   - Purchases items
   - Defends against enemies
   - Requeues after games

---

## 📋 What the Bot Does

### Automatic Behavior

```
[BOT ON] → Joins Game → Walks to Generator → Starts AFK Loop
                                                    ↓
                                         ┌──────────┴──────────┐
                                         ↓                     ↓
                              Iron ≥ 12?                  Enemy?
                                 ↓ Yes                     ↓ Yes
                          Go to Shop              Defense Mode
                          Buy Items               (Fireballs + Apples)
                          Return to Gen                    ↓
                                 ↓                         ↓
                                 └─────────────┬───────────┘
                                               ↓
                                     [Game Ends] → Requeue
```

### Key Features

✅ **Fully Automated** - No manual input required  
✅ **Smart Shopping** - Buys items when you have resources  
✅ **Team Support** - Drops resources to nearby teammates  
✅ **Self Defense** - Fights back when attacked  
✅ **Auto Requeue** - Plays continuously  
✅ **Staff Detection** - Leaves when staff join  
✅ **Error Recovery** - Handles disconnects and errors  

---

## 🎯 Controls

| Key | Action |
|-----|--------|
| `R` | Toggle bot ON/OFF |

That's it! Everything else is automatic.

---

## 💬 Chat Messages

The bot shows status messages in chat:

```
[BW] Started Bot
[BW] Open Source - Programmed by Rice
[BW] Walking Back Into Generator
[BW] Saved Generator Coordinates
[BW] Full Team: sunsi | Player1 | Player2 | Player3
[BW] Purchasing Iron Armor
[BW] Detected Enemy Near
[BW] Game Ended. Requeueing.
```

---

## ⚙️ How It Works

### Game Start
1. Detects "Protect your bed" in chat
2. Walks backward 80 ticks into generator
3. Saves X/Y/Z coordinates
4. Detects teammates

### AFK Loop
- Alternates pressing `A` (130ms) and `D` (130ms)
- Checks inventory every second
- Scans for teammates every 500ms
- Monitors health for enemies

### Shopping
- Triggers when iron count ≥ 12
- Finds "ITEM SHOP" entity
- Navigates using smooth rotation
- Rapid clicks to purchase
- Returns to generator coordinates

### Defense
- Detects damage (health decrease)
- Looks backward (180° + 80° pitch)
- Uses spawn eggs (fireballs)
- Eats 35 golden apples
- Returns to AFK loop

### Respawn
- Detects "You have respawned!" in chat
- Automatically returns to generator
- Resumes AFK loop

### Game End
- Detects "Reward Summary" in chat
- Says "gg" in chat
- Waits 3-5 seconds
- Joins new game

---

## 🛡️ Safety Features

### Staff Detection
Bot immediately leaves if it detects:
- [YOUTUBE] rank
- [HELPER] rank
- [MOD] rank
- [ADMIN] rank

### Error Handling
Auto-recovers from:
- Disconnections
- Connection errors
- Limbo spawns
- Command spam
- Full inventory

---

## 🎓 Tips

### Optimal Settings
- **Render Distance**: 2-4 chunks (performance)
- **Graphics**: Fast (performance)
- **Particles**: Minimal (performance)
- **FOV**: Normal (consistency)

### Best Practices
1. Start bot in lobby (not in-game)
2. Let bot run for 5-10 games to stabilize
3. Monitor first few games to ensure it works
4. Don't manually control player while bot is active

### Troubleshooting

**Bot not moving?**
- Make sure you pressed `R` to toggle ON
- Check chat for "[BW] Started Bot" message

**Bot not joining games?**
- Ensure you're on Hypixel
- Try manually typing `/play bedwars_four_four` first

**Bot walks off platform?**
- This shouldn't happen - coordinates are saved
- Try restarting bot (press `R` twice)

**Bot doesn't defend?**
- Make sure you have spawn eggs and golden apples
- Defense triggers on health decrease

---

## 📊 Expected Performance

### Resource Collection
- **Iron**: ~200-400 per game
- **Gold**: ~40-80 per game  
- **Emeralds**: ~5-15 per game

### Game Stats (Estimated)
- **Games/Hour**: ~8-12 games
- **Win Rate**: 10-20% (depends on teammates)
- **Avg Game Time**: 5-7 minutes

---

## ⚠️ Disclaimer

This bot is for **educational purposes only**. Using automation on multiplayer servers may violate their terms of service and result in bans. Use at your own risk.

---

## 🔗 More Information

- **Full Documentation**: See `README.md`
- **Code Comparison**: See `COMPARISON.md`
- **Implementation Details**: See `IMPLEMENTATION_SUMMARY.md`

---

## 🆘 Support

If you encounter issues:

1. Check `logs/latest.log` for errors
2. Verify Forge version (11.15.1.2318)
3. Ensure Minecraft version (1.8.9)
4. Rebuild the mod (`./gradlew clean build`)

---

**Enjoy your automated Bedwars experience!** 🎮

