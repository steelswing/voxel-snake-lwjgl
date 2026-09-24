package com.steelswing.voxelsnake;

import java.util.HashMap;
import java.util.HashSet;
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
    static final int HEIGHT = SIZE;

    private final long seed;
    private final Map<Long, Chunk> chunks = new HashMap<>();
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
        if (Math.floorMod(hash, 37) == 0) {
            for (int y = surface + 1; y <= surface + 4; y++) putIfLocal(chunk, chunkX, chunkY, chunkZ, x, y, z, (byte) 4);
            for (int ox = -2; ox <= 2; ox++) for (int oy = 3; oy <= 5; oy++) for (int oz = -2; oz <= 2; oz++) {
                if (Math.abs(ox) + Math.abs(oz) + Math.max(0, oy - 4) <= 3)
                    putIfLocal(chunk, chunkX, chunkY, chunkZ, x + ox, surface + oy, z + oz, (byte) 5);
            }
        } else if (Math.floorMod(hash, 29) == 0) {
            putIfLocal(chunk, chunkX, chunkY, chunkZ, x, surface + 1, z, (byte) 5);
        }
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
    }

    synchronized long revision() {
        return revision;
    }

    synchronized Set<Long> consumeDirtyChunks() {
        Set<Long> result = new HashSet<>(dirtyChunks);
        dirtyChunks.clear();
        return result;
    }

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
