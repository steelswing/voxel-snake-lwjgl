package com.steelswing.voxelsnake;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.nmemCalloc;
import static org.lwjgl.system.MemoryUtil.nmemFree;

final class World {
    static final int CHUNK_SIZE = 32;
    static final int CHUNK_COUNT = 32;
    static final int SIZE = CHUNK_SIZE * CHUNK_COUNT;
    static final int HEIGHT = 128;

    private final long seed;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final Map<Long, byte[]> lightMaps = new HashMap<>();
    private final Set<Long> dirtyChunks = new HashSet<>();
    private long revision;

    World(long seed) {
        this.seed = seed;
    }

    synchronized byte get(int x, int y, int z) {
        if (y < 0 || y >= HEIGHT) return 0;
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        int chunkY = Math.floorDiv(y, CHUNK_SIZE);
        Chunk chunk = chunk(Math.floorDiv(x, CHUNK_SIZE), chunkY, Math.floorDiv(z, CHUNK_SIZE));
        return memGetByte(chunk.address + index(x, y, z));
    }

    synchronized boolean set(int x, int y, int z, byte type) {
        if (y < 0 || y >= HEIGHT) return false;
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        Chunk chunk = chunk(Math.floorDiv(x, CHUNK_SIZE), Math.floorDiv(y, CHUNK_SIZE), Math.floorDiv(z, CHUNK_SIZE));
        long address = chunk.address + index(x, y, z);
        if (memGetByte(address) != type) {
            memPutByte(address, type);
            revision++;
            lightMaps.clear();
            markDirty(x, y, z);
            return true;
        }
        return false;
    }

    int surface(int x, int z) {
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        return surfaceHeight(x, z);
    }

    private Chunk chunk(int chunkX, int chunkY, int chunkZ) {
        chunkX = Math.floorMod(chunkX, CHUNK_COUNT);
        chunkY = Math.floorMod(chunkY, CHUNK_COUNT);
        chunkZ = Math.floorMod(chunkZ, CHUNK_COUNT);
        long key = chunkKey(chunkX, chunkY, chunkZ);
        int finalChunkX = chunkX;
        int finalChunkY = chunkY;
        int finalChunkZ = chunkZ;
        return chunks.computeIfAbsent(key, ignored -> generate(finalChunkX, finalChunkY, finalChunkZ));
    }

    private Chunk generate(int chunkX, int chunkY, int chunkZ) {
        Chunk chunk = new Chunk();
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int x = chunkX * CHUNK_SIZE + localX;
                int z = chunkZ * CHUNK_SIZE + localZ;
                int surface = surfaceHeight(x, z);
                int startY = chunkY * CHUNK_SIZE;
                int endY = startY + CHUNK_SIZE;
                for (int y = Math.max(0, startY); y < Math.min(HEIGHT, endY); y++) {
                    byte type = (byte) (y == surface ? 1 : y < surface && y >= surface - 3 ? 2 : y < surface ? 3 : 0);
                    if (type != 0) memPutByte(chunk.address + (localX | ((y & 31) << 5) | (localZ << 10)), type);
                }
                decorate(chunk, chunkX, chunkY, chunkZ, localX, localZ, x, z, surface);
            }
        }
        return chunk;
    }

    private int surfaceHeight(int x, int z) {
        double waves = Math.sin(x * .031) * 4.0
                + Math.cos(z * .027) * 3.0
                + Math.sin((x + z) * .013) * 5.0;
        return Math.max(2, Math.min(HEIGHT - 8, 8 + (int) Math.round(waves)));
    }

    private void decorate(Chunk chunk, int chunkX, int chunkY, int chunkZ, int localX, int localZ,
                          int x, int z, int surface) {
        long hash = x * 341873128712L ^ z * 132897987541L ^ seed;
        if (isTreeBase(x, z)) {
            for (int y = surface + 1; y <= surface + 4; y++) putIfLocal(chunk, chunkX, chunkY, chunkZ, x, y, z, (byte) 4);
            for (int ox = -2; ox <= 2; ox++) for (int oy = 3; oy <= 5; oy++) for (int oz = -2; oz <= 2; oz++) {
                if (Math.abs(ox) + Math.abs(oz) + Math.max(0, oy - 4) <= 3
                        && (Math.abs(ox) + Math.abs(oz) < 3 || oy == 5))
                    putIfLocal(chunk, chunkX, chunkY, chunkZ, x + ox, surface + oy, z + oz, (byte) 5);
            }
        } else if (Math.floorMod(hash, 29) == 0 && !isTreeBase(x, z)) {
            putIfLocal(chunk, chunkX, chunkY, chunkZ, x, surface + 1, z, (byte) 5);
        }
    }

    private boolean isTreeBase(int x, int z) {
        long own = x * 341873128712L ^ z * 132897987541L ^ seed;
        if (Math.floorMod(own, 37) != 0) return false;
        for (int ox = -4; ox <= 4; ox++) {
            for (int oz = -4; oz <= 4; oz++) {
                if (ox == 0 && oz == 0) continue;
                long other = (x + ox) * 341873128712L ^ (z + oz) * 132897987541L ^ seed;
                if (Math.floorMod(other, 37) == 0 && other < own) return false;
            }
        }
        return true;
    }

    private void putIfLocal(Chunk chunk, int chunkX, int chunkY, int chunkZ, int x, int y, int z, byte type) {
        if (Math.floorDiv(x, CHUNK_SIZE) == chunkX && Math.floorDiv(y, CHUNK_SIZE) == chunkY
                && Math.floorDiv(z, CHUNK_SIZE) == chunkZ && y >= 0 && y < HEIGHT) {
            memPutByte(chunk.address + index(x, y, z), type);
        }
    }

    synchronized void close() {
        for (Chunk chunk : chunks.values()) nmemFree(chunk.address);
        chunks.clear();
        lightMaps.clear();
    }

    synchronized long revision() {
        return revision;
    }

    synchronized Set<Long> consumeDirtyChunks() {
        Set<Long> result = new HashSet<>(dirtyChunks);
        dirtyChunks.clear();
        return result;
    }

    synchronized float light(int x, int y, int z) {
        if (y < 0 || y >= HEIGHT) return .35f;
        int chunkX = Math.floorDiv(Math.floorMod(x, SIZE), CHUNK_SIZE);
        int chunkZ = Math.floorDiv(Math.floorMod(z, SIZE), CHUNK_SIZE);
        long key = renderChunkKey(chunkX, chunkZ);
        byte[] map = lightMaps.computeIfAbsent(key, ignored -> buildLightMap(chunkX, chunkZ));
        int localX = Math.floorMod(x, CHUNK_SIZE);
        int localZ = Math.floorMod(z, CHUNK_SIZE);
        int level = map[localX | (y << 5) | (localZ << 12)] & 0xFF;
        return .35f + level / 15f * .65f;
    }

    private byte[] buildLightMap(int chunkX, int chunkZ) {
        int volume = CHUNK_SIZE * HEIGHT * CHUNK_SIZE;
        byte[] map = new byte[volume];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int startX = chunkX * CHUNK_SIZE;
        int startZ = chunkZ * CHUNK_SIZE;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int index = localX | ((HEIGHT - 1) << 5) | (localZ << 12);
                map[index] = 15;
                queue.add(index);
            }
        }
        for (int x = startX; x < startX + CHUNK_SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = startZ; z < startZ + CHUNK_SIZE; z++) {
                    if (get(x, y, z) == 6) {
                        int index = (x - startX) | (y << 5) | ((z - startZ) << 12);
                        if ((map[index] & 0xFF) < 14) {
                            map[index] = 14;
                            queue.add(index);
                        }
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int level = map[index] & 0xFF;
            if (level <= 1) continue;
            int localX = index & 31;
            int y = (index >>> 5) & 127;
            int localZ = (index >>> 12) & 31;
            for (int[] direction : LIGHT_DIRECTIONS) {
                int nx = localX + direction[0];
                int ny = y + direction[1];
                int nz = localZ + direction[2];
                if (nx < 0 || nx >= 32 || ny < 0 || ny >= HEIGHT || nz < 0 || nz >= 32) continue;
                if (get(startX + nx, ny, startZ + nz) != 0) continue;
                int next = nx | (ny << 5) | (nz << 12);
                if ((map[next] & 0xFF) < level - 1) {
                    map[next] = (byte) (level - 1);
                    queue.addLast(next);
                }
            }
        }
        return map;
    }

    private static final int[][] LIGHT_DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private void markDirty(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkY = Math.floorDiv(y, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    if (Math.abs(ox) + Math.abs(oy) + Math.abs(oz) <= 1) {
                        dirtyChunks.add(renderChunkKey(chunkX + ox, chunkZ + oz));
                    }
                }
            }
        }
    }

    static long chunkKey(int x, int y, int z) {
        return ((long) Math.floorMod(x, CHUNK_COUNT) << 42)
                | ((long) Math.floorMod(y, CHUNK_COUNT) << 21)
                | Math.floorMod(z, CHUNK_COUNT);
    }

    static long renderChunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int index(int x, int y, int z) {
        return (x & 31) | ((y & 31) << 5) | ((z & 31) << 10);
    }

    private static final class Chunk {
        private final long address = nmemCalloc(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE, 1);
    }
}
