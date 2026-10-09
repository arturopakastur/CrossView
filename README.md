# CrossView

Native stereoscopic 3D for Minecraft **26.3** (Fabric). No shaders or extra programs needed.

An independent reimplementation of the CrossView mod for Minecraft 1.16.5 by **AutogolazzoJr**, rebuilt from scratch for the new renderer.

## Modes

| Mode | What you see | How to view |
|---|---|---|
| **Off** | Normal Minecraft | - |
| **Side By Side** | The whole game drawn twice, left and right (world, hand, HUD and menus) | Cross your eyes (default), or turn on **Invert 3D** for parallel viewing |
| **Anaglyph** | One image where the two eyes use different color channels | Red-cyan, green-magenta or blue-yellow glasses |

## Controls

- **G** - open the 3D Settings screen
- **H** - turn 3D on or off (keeps the current mode)

Both keys can be changed in Options > Controls > Key Binds under **CrossView**. In Side By Side both halves of the screen are clickable.

## Options

| Option | Default | Notes |
|---|---|---|
| 3D Mode | Off | Off / Side By Side / Anaglyph |
| 3D | On | Master switch for the depth effect (the H key). With it off, Side By Side still shows two identical halves |
| Invert 3D | Off | Swaps the eyes (use it to switch between cross-eyed and parallel viewing) |
| 3D Power | 5 | 1-10, how far apart the eyes are |
| Vignette | Off | Only matters while a 3D mode is on; with 3D Mode Off the vanilla vignette is untouched |
| Crosshair | On | Show or hide the crosshair |
| Crosshair Depth | 0 | -50 to 50, moves the crosshair in front of or behind the screen (Side By Side only) |
| Anaglyph Type | Red-Cyan | Red-Cyan / Green-Magenta / Blue-Yellow |
| Legacy 3D | Off | Adds the older image-shift style of depth |
| Desaturate | 0 | 0-100. Fades the world to gray so the anaglyph colors stand out. Applies instantly |

Settings are saved to `config/crossview.json`.

## Requirements

- Minecraft **26.3**
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3
- Java 25

Put the CrossView jar and the Fabric API jar into your `mods` folder.

## Good to know

- The world is drawn twice, so expect roughly half the frame rate.
- Debug overlays (F3 shapes) appear in one eye only.
- F2 screenshots capture a single eye.
- Glow outlines in Anaglyph mode come from one eye only.
- The operating system mouse cursor is drawn once, not in both halves.
- Desaturate fades the world, not the GUI.

## Building from source

```
./gradlew build
```

The mod jar is created in `build/libs/` (use the one without `-sources`). Requires JDK 25.

## License

[CC0 1.0 Universal](LICENSE): public domain. Use, change and share it however you like, no credit required.
This project contains no Minecraft code or assets and none of the original mod's files.