# Voxel Snake LWJGL

Minecraft-like 3D voxel snake game written in Java with [LWJGL](https://www.lwjgl.org/) and Gradle.

The project combines classic snake gameplay with a procedurally generated voxel world, textured blocks, and an in-game world-edit mode.

## Features

- 3D voxel world with procedural generation
- Snake gameplay in a Minecraft-inspired environment
- Block textures
- World-edit mode activated with `Tab`
- Java + LWJGL + Gradle

## Requirements

- Java Development Kit (JDK) 17 or newer
- A graphics card and drivers supporting OpenGL
- Gradle Wrapper included in the project

## Run

Clone the repository and start the application with the Gradle Wrapper:

```bash
git clone https://github.com/steelswing/voxel-snake-lwjgl.git
cd voxel-snake-lwjgl

# Linux/macOS
./gradlew run

# Windows
gradlew.bat run
```

If the project does not yet contain a runnable Gradle task, import it into a Gradle-compatible IDE and use the configured application entry point.

## Controls

| Key | Action |
| --- | --- |
| `Tab` | Toggle world-edit mode |

Additional gameplay controls may vary as the project evolves.

## Project status

This is an experimental game project under active development. APIs, controls, visuals, and gameplay systems may change.

## License

No license has been specified yet. All rights are reserved unless stated otherwise.

---

## Русский

**Voxel Snake LWJGL** — Minecraft-подобная 3D-игра про змею из блоков, написанная на Java с использованием [LWJGL](https://www.lwjgl.org/) и Gradle.

В проекте классический игровой процесс Snake объединён с процедурно генерируемым воксельным миром, текстурами блоков и режимом редактирования мира.

### Возможности

- Процедурно генерируемый 3D-воксельный мир
- Игровой процесс Snake в Minecraft-подобном окружении
- Текстуры блоков
- Режим редактирования мира по клавише `Tab`
- Java + LWJGL + Gradle

### Требования

- Java Development Kit (JDK) 17 или новее
- Видеокарта и драйверы с поддержкой OpenGL
- Gradle Wrapper в составе проекта

### Запуск

```bash
git clone https://github.com/steelswing/voxel-snake-lwjgl.git
cd voxel-snake-lwjgl

# Linux/macOS
./gradlew run

# Windows
gradlew.bat run
```

Если в проекте пока нет готовой задачи запуска Gradle, импортируйте его в IDE с поддержкой Gradle и используйте настроенную точку входа приложения.

### Управление

| Клавиша | Действие |
| --- | --- |
| `Tab` | Переключить режим редактирования мира |

Остальные клавиши управления могут измениться по мере развития проекта.

### Статус проекта

Это экспериментальный игровой проект в активной разработке. API, управление, графика и игровые механики могут меняться.
