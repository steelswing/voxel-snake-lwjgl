# Voxel Snake LWJGL

Minecraft-like 3D voxel snake game written in Java with LWJGL and Gradle.

## Features

- Deterministically procedurally generated voxel terrain
- Runtime-generated pixel palette inspired by `Minecraft4K-full-reversed`
- Automatic snake movement and WASD direction controls
- `Tab` toggles world-edit mode
- `E` adds a block and `Q` removes a block in front of the snake while editing

## Run

Requires JDK 17+ and an OpenGL-capable desktop:

```bash
gradle run
```

## Controls

| Key | Action |
| --- | --- |
| `WASD` | Change direction |
| `Tab` | Toggle world-edit mode |
| `E` | Add block in edit mode |
| `Q` | Remove block in edit mode |
| `Esc` | Quit |

The texture approach is based on the reference repository's seeded random brightness and layer-specific base colours, implemented independently in `TextureGenerator.java`.
