# Voxel Snake LWJGL

Minecraft-like 3D voxel snake game written in Java 17 with LWJGL 3 and Gradle.

## Improvements

- Proper Gradle `application` project with `run`, `installDist`, `distZip`, and `distTar` tasks.
- LWJGL native dependencies for Linux, Windows, and macOS.
- Procedural textures now use deterministic per-pixel noise, layer-specific palettes, grass/stone/wood patterns, contrast variation, and face lighting.
- The renderer applies stable generated colours per block and face, so terrain has visible texture variation instead of flat colours.

## Run

Use the Gradle wrapper if it is present, otherwise install Gradle 8+:

```bash
gradle run
```

For a distributable application directory:

```bash
gradle installDist
```

The executable is then under `build/install/voxel-snake/bin/`.

## Controls

| Key | Action |
| --- | --- |
| `WASD` | Change snake direction |
| `Tab` | Toggle world-edit mode |
| `E` | Add a block in edit mode |
| `Q` | Remove a block in edit mode |
| `Esc` | Quit |

Texture generation is implemented independently in `TextureGenerator.java`, using the seeded random brightness and layered palette ideas observed in [Minecraft4K-full-reversed](https://github.com/steelswing/Minecraft4K-full-reversed).
