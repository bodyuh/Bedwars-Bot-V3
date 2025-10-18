# Generator Detector Module

A standalone testing module for detecting and visualizing Diamond generators in Bedwars.

## Features

- 🔍 **Automatic Scanning**: Scans for armor stands with "Diamond" in their nametag
- 👁️ **ESP Rendering**: Renders cyan boxes around detected generators
- 📍 **Coordinate Display**: Shows generator coordinates and distance in chat
- ⌨️ **Simple Toggle**: Press `H` to activate/deactivate

## Usage

### Activation

1. **Press `H`** to toggle the detector on/off
2. When activated, it will immediately scan for Diamond generators
3. Continues scanning every 1 second while active

### What You'll See

**In Chat:**
```
[Generator] Detector activated - scanning for Diamond generators...
[Generator] Found: §bDiamond §7at §bBlockPos{x=100, y=64, z=200} §7(15.3m away)
[Generator] Tracking 2 generator(s)
```

**In-Game:**
- **Cyan ESP boxes** around each detected generator
- **Filled semi-transparent box** for visibility
- **Solid outline** for precise location
- Boxes are **2 blocks tall** to match armor stand height

### Deactivation

- **Press `H` again** to turn off the detector
- All ESP boxes will disappear
- Detected generators list is cleared

## Technical Details

### Detection Method

- Scans all loaded entities in the world
- Filters for `EntityArmorStand` with custom names
- Case-insensitive search for "Diamond" in nametag
- Updates every 1000ms (1 second)

### ESP Rendering

- **Color**: Cyan (RGB: 0, 255, 255)
- **Fill Alpha**: 50 (semi-transparent)
- **Outline Alpha**: 255 (solid)
- **Size**: 1x2x1 block bounding box
- **Render Mode**: Through walls (no depth testing)

### Tracking

- Generators are tracked by entity ID
- Auto-removes generators not seen for 5+ seconds
- Smooth interpolated rendering using partial ticks
- Distance calculated from player position

## Code Structure

```
src/main/kotlin/best/bedwars/afk/bot/detector/
└── GeneratorDetector.kt
    ├── toggle()              - Enable/disable detector
    ├── scanForGenerators()   - Find armor stands
    ├── onClientTick()        - Update loop
    ├── onKeyInput()          - H key handler
    ├── onRenderWorld()       - ESP rendering
    └── renderFilledBox()     - Box drawing
```

## Integration

The module is **completely independent** from the main Bedwars bot:

- Registered in `BedwarsAFK.kt` as a Forge event handler
- Works standalone - no bot activation required
- Can be used for testing and debugging
- No interference with other bot functions

## Future Enhancements

Potential improvements for contributors:

- [ ] Add support for other generator types (Emerald, Iron, Gold)
- [ ] Add distance-based filtering (only show nearby generators)
- [ ] Add text labels above generators
- [ ] Save generator positions to file
- [ ] Add pathfinding integration (auto-navigate to generator)
- [ ] Add color coding by generator type
- [ ] Add sound notification when generator found

## Testing

To test the module:

1. Build the mod: `.\gradlew.bat build`
2. Copy JAR to Minecraft mods folder
3. Launch Minecraft 1.8.9 with Forge
4. Join a Bedwars game
5. Press `H` to activate detector
6. Look for Diamond generators with ESP boxes

## Troubleshooting

**No generators detected:**
- Make sure you're in a Bedwars game
- Generators must be loaded (within render distance)
- Armor stand must have "Diamond" in its custom name
- Check chat for "No Diamond generators found" message

**ESP not rendering:**
- Ensure detector is active (press H)
- Check if generators are actually detected in chat
- Make sure you're not in a GUI/menu

**Key not working:**
- Verify you're not in a GUI (press ESC first)
- Check for key conflicts with other mods
- Look in chat for activation message
