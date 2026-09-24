package com.steelswing.voxelsnake;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.nmemFree;

final class Game {
    private long window;
    private int program;
    private int texture;
    private int entityVbo;
    private SelectionRenderer selectionRenderer;
    private final World world = new World(0x5EEDL);
    private final List<int[]> snake = new ArrayList<>();
    private final Random random = new Random(0xA11CE);
    private int[] apple;
    private boolean editMode;
    private int dx = 1;
    private int dz;
    private double nextStep;
    private int snakeLength = 5;
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private float cameraX;
    private float cameraY;
    private float cameraZ;
    private float cameraYaw;
    private float cameraPitch;
    private float followX;
    private float followY;
    private float followZ;
    private float followTargetX;
    private float followTargetY;
    private float followTargetZ;
    private double lastFrameTime;
    private double lastMouseX;
    private double lastMouseY;
    private boolean firstMouse = true;
    private int selectedBlock = 2;
    private final Map<Long, ChunkMesh> meshes = new HashMap<>();
    private final Map<Long, Future<ChunkBuild>> pendingMeshes = new HashMap<>();
    private final Set<Long> invalidatedMeshes = new HashSet<>();
    private final ExecutorService meshExecutor = Executors.newFixedThreadPool(
            Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
    private boolean snakeDead;
    private int pendingDx = 1;
    private int pendingDz;
    private final Path logFile = Path.of("voxel-snake.log").toAbsolutePath();

    void run() {
        try {
            log("Starting Voxel Snake, Java " + System.getProperty("java.version")
                    + ", OS " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            init();
            loop();
        } catch (RuntimeException | Error failure) {
            log("FATAL: game loop stopped", failure);
            throw failure;
        } finally {
            log("Stopping game");
            for (ChunkMesh mesh : meshes.values()) glDeleteBuffers(mesh.vbo);
            meshes.clear();
            for (Future<ChunkBuild> pending : pendingMeshes.values()) pending.cancel(true);
            pendingMeshes.clear();
            meshExecutor.shutdownNow();
            try {
                if (!meshExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("Chunk mesh workers did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            world.close();
            if (selectionRenderer != null) selectionRenderer.free();
            if (window != NULL) glfwDestroyWindow(window);
            glfwTerminate();
            log("Shutdown complete");
        }

    }

    private void init() {
        log("Initializing GLFW");
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        window = glfwCreateWindow(960, 600, "Voxel Snake - TAB: edit mode", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Window creation failed");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        log("Creating OpenGL capabilities");
        GL.createCapabilities();
        log("OpenGL vendor=" + glGetString(GL_VENDOR) + ", renderer=" + glGetString(GL_RENDERER)
                + ", version=" + glGetString(GL_VERSION));
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        log("Creating shaders");
        program = createProgram();
        checkGl("shader program");
        log("Creating texture atlas");
        texture = createTexture();
        checkGl("texture");
        entityVbo = glGenBuffers();
        selectionRenderer = new SelectionRenderer();
        addSnake(16, 16);
        addSnake(15, 16);
        addSnake(14, 16);
        spawnApple();
        int[] head = snake.get(0);
        cameraX = head[0] + .5f;
        cameraY = head[1] + 2.5f;
        cameraZ = head[2] + .5f;
        cameraYaw = (float) Math.atan2(dz, dx);
        followX = head[0] + .5f - dx * 10f;
        followY = head[1] + 8f;
        followZ = head[2] + .5f - dz * 10f;
        followTargetX = head[0] + .5f;
        followTargetY = head[1] + .25f;
        followTargetZ = head[2] + .5f;
        lastFrameTime = glfwGetTime();
        nextStep = lastFrameTime + .22;
        log("Initialization complete");
        glfwSetKeyCallback(window, (w, key, scan, action, mods) -> {
            if (key >= 0 && key < keys.length) keys[key] = action != GLFW_RELEASE;
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) glfwSetWindowShouldClose(window, true);
            if (key == GLFW_KEY_TAB) {
                editMode = !editMode;
                firstMouse = true;
                glfwSetInputMode(window, GLFW_CURSOR, editMode ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            }
            if (!editMode) {
                if (key == GLFW_KEY_W) turn(0, -1);
                if (key == GLFW_KEY_S) turn(0, 1);
                if (key == GLFW_KEY_A) turn(-1, 0);
                if (key == GLFW_KEY_D) turn(1, 0);
            } else if (key == GLFW_KEY_Q || key == GLFW_KEY_E) {
                editBlock(key == GLFW_KEY_E);
            } else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_5) {
                selectedBlock = key - GLFW_KEY_0;
            }
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (!editMode) return;
            if (firstMouse) {
                lastMouseX = x;
                lastMouseY = y;
                firstMouse = false;
                return;
            }
            cameraYaw += (float) (x - lastMouseX) * .0025f;
            cameraPitch -= (float) (y - lastMouseY) * .0025f;
            cameraPitch = Math.max(-1.5f, Math.min(1.5f, cameraPitch));
            lastMouseX = x;
            lastMouseY = y;
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (editMode && action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) editBlock(false);
                if (button == GLFW_MOUSE_BUTTON_RIGHT) editBlock(true);
            }
        });
    }

    private void addSnake(int x, int z) { snake.add(new int[]{x, world.surface(x, z) + 1, z}); }

    private void turn(int nextDx, int nextDz) {
        if (nextDx == -dx && nextDz == -dz) return;
        pendingDx = nextDx;
        pendingDz = nextDz;
    }

    private void editBlock(boolean add) {
        RaycastHit hit = raycast();
        if (hit == null) return;
        if (!add) {
            world.set(hit.x, hit.y, hit.z, (byte) 0);
            return;
        }
        int x = hit.x + hit.normalX;
        int y = hit.y + hit.normalY;
        int z = hit.z + hit.normalZ;
        if (world.get(x, y, z) == 0) world.set(x, y, z, (byte) selectedBlock);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            double now = glfwGetTime();
            float frameTime = (float) Math.min(.1, Math.max(0, now - lastFrameTime));
            lastFrameTime = now;
            if (!editMode) pollSnakeInput();
            if (!editMode && now >= nextStep) {
                moveSnake();
                nextStep += .22;
                if (nextStep < now) nextStep = now + .22;
            }
            if (editMode) updateFreeCamera(frameTime);
            render(frameTime);
            glfwSwapBuffers(window);
        }
    }

    private void pollSnakeInput() {
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) turn(0, -1);
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) turn(0, 1);
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) turn(-1, 0);
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) turn(1, 0);
    }

    private void updateFreeCamera(float dt) {
        float forwardX = (float) Math.cos(cameraPitch) * (float) Math.cos(cameraYaw);
        float forwardY = (float) Math.sin(cameraPitch);
        float forwardZ = (float) Math.cos(cameraPitch) * (float) Math.sin(cameraYaw);
        float rightX = (float) -Math.sin(cameraYaw);
        float rightZ = (float) Math.cos(cameraYaw);
        float speed = keys[GLFW_KEY_LEFT_SHIFT] ? 16f : 7f;
        if (keys[GLFW_KEY_W]) { cameraX += forwardX * speed * dt; cameraY += forwardY * speed * dt; cameraZ += forwardZ * speed * dt; }
        if (keys[GLFW_KEY_S]) { cameraX -= forwardX * speed * dt; cameraY -= forwardY * speed * dt; cameraZ -= forwardZ * speed * dt; }
        if (keys[GLFW_KEY_A]) { cameraX -= rightX * speed * dt; cameraZ -= rightZ * speed * dt; }
        if (keys[GLFW_KEY_D]) { cameraX += rightX * speed * dt; cameraZ += rightZ * speed * dt; }
        if (keys[GLFW_KEY_SPACE]) cameraY += speed * dt;
        if (keys[GLFW_KEY_LEFT_CONTROL]) cameraY -= speed * dt;
    }

    private RaycastHit raycast() {
        float directionX = (float) (Math.cos(cameraPitch) * Math.cos(cameraYaw));
        float directionY = (float) Math.sin(cameraPitch);
        float directionZ = (float) (Math.cos(cameraPitch) * Math.sin(cameraYaw));
        int x = (int) Math.floor(cameraX);
        int y = (int) Math.floor(cameraY);
        int z = (int) Math.floor(cameraZ);
        int stepX = Float.compare(directionX, 0);
        int stepY = Float.compare(directionY, 0);
        int stepZ = Float.compare(directionZ, 0);
        double tMaxX = nextBoundary(cameraX, directionX, x, stepX);
        double tMaxY = nextBoundary(cameraY, directionY, y, stepY);
        double tMaxZ = nextBoundary(cameraZ, directionZ, z, stepZ);
        double tDeltaX = delta(directionX);
        double tDeltaY = delta(directionY);
        double tDeltaZ = delta(directionZ);
        int normalX = 0;
        int normalY = 0;
        int normalZ = 0;

        for (double distance = 0; distance <= 12.0; ) {
            if (world.get(x, y, z) != 0) {
                return new RaycastHit(x, y, z, normalX, normalY, normalZ);
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                distance = tMaxX;
                x += stepX;
                normalX = -stepX;
                normalY = 0;
                normalZ = 0;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                distance = tMaxY;
                y += stepY;
                normalX = 0;
                normalY = -stepY;
                normalZ = 0;
                tMaxY += tDeltaY;
            } else {
                distance = tMaxZ;
                z += stepZ;
                normalX = 0;
                normalY = 0;
                normalZ = -stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    private static double nextBoundary(float origin, float direction, int cell, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - origin) / direction;
    }

    private static double delta(float direction) {
        return direction == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction);
    }

    private void moveSnake() {
        if (snakeDead) return;
        dx = pendingDx;
        dz = pendingDz;
        int[] head = snake.get(0);
        int x = Math.floorMod(head[0] + dx, World.SIZE);
        int z = Math.floorMod(head[2] + dz, World.SIZE);
        int y = world.surface(x, z) + 1;
        for (int[] part : snake) {
            if (part[0] == x && part[1] == y && part[2] == z) {
                snakeDead = true;
                return;
            }
        }
        snake.add(0, new int[]{x, y, z});
        if (apple[0] == x && apple[1] == y && apple[2] == z) {
            snakeLength++;
            spawnApple();
        }
        while (snake.size() > snakeLength) snake.remove(snake.size() - 1);
    }

    private void spawnApple() {
        for (int attempt = 0; attempt < 256; attempt++) {
            int x = random.nextInt(World.SIZE);
            int z = random.nextInt(World.SIZE);
            int y = world.surface(x, z) + 1;
            boolean occupied = false;
            for (int[] part : snake) {
                if (part[0] == x && part[1] == y && part[2] == z) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) {
                apple = new int[]{x, y, z};
                return;
            }
        }
        apple = new int[]{0, world.surface(0, 0) + 1, 0};
    }

    private void render(float frameTime) {
        int[] size = new int[2];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, size, height);
        glViewport(0, 0, size[0], height[0]);
        glClearColor(.56f, .72f, .88f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        int[] head = snake.get(0);
        float ex;
        float ey;
        float ez;
        float tx;
        float ty;
        float tz;
        if (editMode) {
            ex = cameraX;
            ey = cameraY;
            ez = cameraZ;
            tx = ex + (float) (Math.cos(cameraPitch) * Math.cos(cameraYaw));
            ty = ey + (float) Math.sin(cameraPitch);
            tz = ez + (float) (Math.cos(cameraPitch) * Math.sin(cameraYaw));
        } else {
            float desiredX = head[0] + .5f - dx * 10f;
            float desiredY = head[1] + 8f;
            float desiredZ = head[2] + .5f - dz * 10f;
            float desiredTargetX = head[0] + .5f;
            float desiredTargetY = head[1] + .25f;
            float desiredTargetZ = head[2] + .5f;
            float smoothing = 1f - (float) Math.exp(-frameTime * 12f);
            followX += (desiredX - followX) * smoothing;
            followY += (desiredY - followY) * smoothing;
            followZ += (desiredZ - followZ) * smoothing;
            followTargetX += (desiredTargetX - followTargetX) * smoothing;
            followTargetY += (desiredTargetY - followTargetY) * smoothing;
            followTargetZ += (desiredTargetZ - followTargetZ) * smoothing;
            ex = followX;
            ey = followY;
            ez = followZ;
            tx = followTargetX;
            ty = followTargetY;
            tz = followTargetZ;
        }
        for (long dirtyKey : world.consumeDirtyChunks()) {
            invalidatedMeshes.add(dirtyKey);
        }
        glUseProgram(program);
        glUniform1f(glGetUniformLocation(program, "useTexture"), 1f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(program, "atlas"), 0);
        int position = glGetAttribLocation(program, "position");
        int texCoord = glGetAttribLocation(program, "texCoord");
        int light = glGetAttribLocation(program, "light");
        glEnableVertexAttribArray(position); glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, 0);
        glEnableVertexAttribArray(texCoord); glVertexAttribPointer(texCoord, 2, GL_FLOAT, false, 24, 12);
        glEnableVertexAttribArray(light); glVertexAttribPointer(light, 1, GL_FLOAT, false, 24, 20);
        glUniformMatrix4fv(glGetUniformLocation(program, "matrix"), false,
                matrix(ex, ey, ez, tx, ty, tz, size[0] / (float) height[0]));
        glUniform3f(glGetUniformLocation(program, "fogOrigin"), ex, ey, ez);
        glUniform1f(glGetUniformLocation(program, "fogStart"), 72f);
        glUniform1f(glGetUniformLocation(program, "fogEnd"), 256f);
        int centerX = editMode ? (int) cameraX : head[0];
        int centerZ = editMode ? (int) cameraZ : head[2];
        int centerChunkX = Math.floorDiv(centerX, World.CHUNK_SIZE);
        int centerChunkZ = Math.floorDiv(centerZ, World.CHUNK_SIZE);
        int chunkRadius = 8;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                int offsetX = chunkX - centerChunkX;
                int offsetZ = chunkZ - centerChunkZ;
                if (offsetX * offsetX + offsetZ * offsetZ > chunkRadius * chunkRadius) continue;
                ChunkMesh mesh = chunkMesh(chunkX, chunkZ);
                if (mesh == null) continue;
                glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
                glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, 0);
                glVertexAttribPointer(texCoord, 2, GL_FLOAT, false, 24, 12);
                glVertexAttribPointer(light, 1, GL_FLOAT, false, 24, 20);
                glDrawArrays(GL_TRIANGLES, 0, mesh.vertices);
            }
        }
        NativeMesh entities = new NativeMesh((snake.size() + 1) * 216);
        try {
            for (int[] part : snake) cube(entities, part[0], part[1], part[2], 6);
            cube(entities, apple[0], apple[1], apple[2], 7);
            glBindBuffer(GL_ARRAY_BUFFER, entityVbo);
            glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, 0);
            glVertexAttribPointer(texCoord, 2, GL_FLOAT, false, 24, 12);
            glVertexAttribPointer(light, 1, GL_FLOAT, false, 24, 20);
            glBufferData(GL_ARRAY_BUFFER, memByteBuffer(entities.address, entities.floats * 4), GL_STREAM_DRAW);
            glDrawArrays(GL_TRIANGLES, 0, entities.floats / 6);
        } finally {
            entities.free();
        }
        RaycastHit hit = editMode ? raycast() : null;
        if (hit != null) selectionRenderer.render(program, position, texCoord, light, hit.block());
    }

    private ChunkMesh chunkMesh(int chunkX, int chunkZ) {
        long key = World.renderChunkKey(chunkX, chunkZ);
        ChunkMesh cached = meshes.get(key);
        Future<ChunkBuild> pending = pendingMeshes.get(key);
        if (pending != null && pending.isDone()) {
            pendingMeshes.remove(key);
            try {
                ChunkBuild build = pending.get();
                if (build.revision != world.revision()) {
                    build.mesh.free();
                    return cached;
                }
                int vbo = glGenBuffers();
                try {
                    glBindBuffer(GL_ARRAY_BUFFER, vbo);
                    glBufferData(GL_ARRAY_BUFFER, memByteBuffer(build.mesh.address, build.mesh.floats * 4), GL_STATIC_DRAW);
                    checkGl("chunk upload " + chunkX + "," + chunkZ);
                    ChunkMesh replacement = new ChunkMesh(vbo, build.mesh.floats / 6);
                    ChunkMesh previous = meshes.put(key, replacement);
                    if (previous != null) glDeleteBuffers(previous.vbo);
                    invalidatedMeshes.remove(key);
                    return replacement;
                } finally {
                    build.mesh.free();
                }
            } catch (CancellationException e) {
                return cached;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return cached;
            } catch (ExecutionException e) {
                log("Chunk mesh failed at " + chunkX + "," + chunkZ, e.getCause());
                return cached;
            }
        }
        if (cached != null && !invalidatedMeshes.contains(key)) return cached;
        if (pending == null) {
            pending = meshExecutor.submit(() -> buildChunk(chunkX, chunkZ));
            pendingMeshes.put(key, pending);
            return cached;
        }
        return cached;
    }

    private ChunkBuild buildChunk(int chunkX, int chunkZ) {
        NativeMesh data = new NativeMesh(262144);
        try {
            int startX = chunkX * World.CHUNK_SIZE;
            int startZ = chunkZ * World.CHUNK_SIZE;
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Chunk build interrupted");
            }
            MarchingCubesMesher.build(world, data, startX, startZ);
            return new ChunkBuild(world.revision(), data);
        } catch (CancellationException e) {
            data.free();
            throw e;
        } catch (RuntimeException | Error e) {
            data.free();
            throw e;
        }
    }

    private static final class ChunkMesh {
        private final int vbo;
        private final int vertices;

        private ChunkMesh(int vbo, int vertices) {
            this.vbo = vbo;
            this.vertices = vertices;
        }

    }

    private static final class ChunkBuild {
        private final long revision;
        private final NativeMesh mesh;

        private ChunkBuild(long revision, NativeMesh mesh) {
            this.revision = revision;
            this.mesh = mesh;
        }
    }

    private void cube(NativeMesh b, int x, int y, int z, int type) {
        float[][] p={{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
        int[][] f={{0,1,2,2,3,0},{5,4,7,7,6,5},{4,0,3,3,7,4},{1,5,6,6,2,1},{3,2,6,6,7,3},{4,5,1,1,0,4}};
        for (int face = 0; face < f.length; face++) texturedFace(b, x, y, z, type, face, 1f);
    }

    private void visibleCube(NativeMesh b, int x, int y, int z, int type) {
        if (world.get(x, y + 1, z) == 0) texturedFace(b, x, y, z, type, 0, .98f);
        if (world.get(x, y - 1, z) == 0) texturedFace(b, x, y, z, type, 1, .56f);
        if (world.get(x - 1, y, z) == 0) texturedFace(b, x, y, z, type, 2, .72f);
        if (world.get(x + 1, y, z) == 0) texturedFace(b, x, y, z, type, 3, .84f);
        if (world.get(x, y, z + 1) == 0) texturedFace(b, x, y, z, type, 4, .78f);
        if (world.get(x, y, z - 1) == 0) texturedFace(b, x, y, z, type, 5, .68f);
    }

    private void texturedFace(NativeMesh b, int x, int y, int z, int type, int face, float faceLight) {
        int[][] f={{3,7,6,6,2,3},{0,1,5,5,4,0},{0,4,7,7,3,0},{1,2,6,6,5,1},{4,5,6,6,7,4},{0,3,2,2,1,0}};
        float[][] p={{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
        int tile = tileFor(type, face);
        for (int i : f[face]) {
            int section = face == 0 ? 0 : face == 1 ? 2 : 1;
            float u = (tile * 16f + textureU(p[i], face) * 15f + .5f) / TextureGenerator.width();
            float v = ((2 - section) * 16f + textureV(p[i], face) * 15f + .5f) / TextureGenerator.height();
            float ao = ambientOcclusion(x, y, z, face, p[i]);
            b.put(x+p[i][0], y+p[i][1], z+p[i][2], u, v, faceLight * ao);
        }

    }

    private static int tileFor(int type, int face) {
        return Math.max(0, Math.min(7, type - 1));
    }

    private float ambientOcclusion(int x, int y, int z, int face, float[] point) {
        int[] normal = {0, 0, 0};
        int[] tangent = {0, 0, 0};
        int[] bitangent = {0, 0, 0};
        switch (face) {
            case 0 -> { normal[1] = 1; tangent[0] = 1; bitangent[2] = 1; }
            case 1 -> { normal[1] = -1; tangent[0] = 1; bitangent[2] = 1; }
            case 2 -> { normal[0] = -1; tangent[2] = 1; bitangent[1] = 1; }
            case 3 -> { normal[0] = 1; tangent[2] = 1; bitangent[1] = 1; }
            case 4 -> { normal[2] = 1; tangent[0] = 1; bitangent[1] = 1; }
            case 5 -> { normal[2] = -1; tangent[0] = 1; bitangent[1] = 1; }
            default -> throw new IllegalArgumentException("Unknown face: " + face);
        }
        int tangentSign = coordinateSign(point, tangent);
        int bitangentSign = coordinateSign(point, bitangent);
        int sideAX = x + normal[0] + tangent[0] * tangentSign;
        int sideAY = y + normal[1] + tangent[1] * tangentSign;
        int sideAZ = z + normal[2] + tangent[2] * tangentSign;
        int sideBX = x + normal[0] + bitangent[0] * bitangentSign;
        int sideBY = y + normal[1] + bitangent[1] * bitangentSign;
        int sideBZ = z + normal[2] + bitangent[2] * bitangentSign;
        int cornerX = sideAX + bitangent[0] * bitangentSign;
        int cornerY = sideAY + bitangent[1] * bitangentSign;
        int cornerZ = sideAZ + bitangent[2] * bitangentSign;
        boolean sideA = world.get(sideAX, sideAY, sideAZ) != 0;
        boolean sideB = world.get(sideBX, sideBY, sideBZ) != 0;
        boolean corner = world.get(cornerX, cornerY, cornerZ) != 0;
        int occlusion = sideA && sideB ? 3 : (sideA ? 1 : 0) + (sideB ? 1 : 0) + (corner ? 1 : 0);
        return 1f - occlusion * .15f;
    }

    private static int coordinateSign(float[] point, int[] axis) {
        if (axis[0] != 0) return point[0] < .5f ? -1 : 1;
        if (axis[1] != 0) return point[1] < .5f ? -1 : 1;
        return point[2] < .5f ? -1 : 1;
    }

    private static float textureU(float[] p, int face) {
        return face <= 1 || face >= 4 ? p[0] : p[2];
    }

    private static float textureV(float[] p, int face) {
        return face <= 1 ? p[2] : p[1];
    }

    private static float[] matrix(float ex,float ey,float ez,float tx,float ty,float tz,float aspect) {
        float[] f=normalize(tx-ex,ty-ey,tz-ez),up={0,1,0},s=normalize(cross(f,up)),u=cross(s,f),m=new float[16];
        float scale=1f/(float)Math.tan(Math.toRadians(60)/2);
        float near=0.1f, far=256f, a=(far+near)/(near-far), b=2f*far*near/(near-far);
        m[0]=scale/aspect*s[0];m[4]=scale/aspect*s[1];m[8]=scale/aspect*s[2];m[12]=scale/aspect*-dot(s,ex,ey,ez);
        m[1]=scale*u[0];m[5]=scale*u[1];m[9]=scale*u[2];m[13]=scale*-dot(u,ex,ey,ez);
        m[2]=a*-f[0];m[6]=a*-f[1];m[10]=a*-f[2];m[14]=a*dot(f,ex,ey,ez)+b;
        m[3]=f[0];m[7]=f[1];m[11]=f[2];m[15]=-dot(f,ex,ey,ez);
        return m;
    }
    private static float[] normalize(float x,float y,float z){float l=(float)Math.sqrt(x*x+y*y+z*z);return new float[]{x/l,y/l,z/l};}
    private static float[] normalize(float[] v){return normalize(v[0],v[1],v[2]);}
    private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static float dot(float[] a,float x,float y,float z){return a[0]*x+a[1]*y+a[2]*z;}

    private static int createProgram() {
        int vs=shader(GL_VERTEX_SHADER,"#version 120\nattribute vec3 position; attribute vec2 texCoord; attribute float light; varying vec2 vTexCoord; varying float vLight; varying float vDistance; uniform mat4 matrix; uniform vec3 fogOrigin; void main(){gl_Position=matrix*vec4(position,1.0);vTexCoord=texCoord;vLight=light;vDistance=distance(position,fogOrigin);}");
        int fs=shader(GL_FRAGMENT_SHADER,"#version 120\nuniform sampler2D atlas; uniform float useTexture; uniform vec4 tint; uniform float fogStart; uniform float fogEnd; varying vec2 vTexCoord; varying float vLight; varying float vDistance; void main(){vec4 color=useTexture > 0.5 ? texture2D(atlas,vTexCoord)*vLight : tint; float fog=clamp((vDistance-fogStart)/(fogEnd-fogStart),0.0,1.0); color.rgb=mix(color.rgb,vec3(0.56,0.72,0.88),fog); gl_FragColor=color;}");
        int p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) {
            String info = glGetProgramInfoLog(p);
            glDeleteProgram(p);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new IllegalStateException("Shader link failed: " + info);
        }
        glDeleteShader(vs);glDeleteShader(fs);return p;
    }

    private static int createTexture() {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        long pixels = TextureGenerator.pixels();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, TextureGenerator.width(), TextureGenerator.height(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        nmemFree(pixels);
        return id;
    }
    private static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(s));return s;}

    private void checkGl(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("OpenGL error 0x"
                + Integer.toHexString(error) + " after " + stage);
    }

    private void log(String message) {
        try {
            Files.writeString(logFile, message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            System.err.println(message);
        }
        System.err.println(message);
    }

    private void log(String message, Throwable failure) {
        log(message + ": " + failure);
        failure.printStackTrace(System.err);
        try {
            Files.writeString(logFile, stackTrace(failure),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // The original exception has already been printed to stderr.
        }
    }

    private static String stackTrace(Throwable failure) {
        java.io.StringWriter output = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(output));
        return output + System.lineSeparator();
    }
}
