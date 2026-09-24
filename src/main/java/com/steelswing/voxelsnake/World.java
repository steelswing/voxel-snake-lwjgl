package com.steelswing.voxelsnake;

import java.util.HashMap;
import java.util.Map;

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
    private long version;

    World(long seed) {
        this.seed = seed;
    }

    byte get(int x, int y, int z) {
        if (y < 0 || y >= HEIGHT) return 0;
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        int chunkY = Math.floorDiv(y, CHUNK_SIZE);
        if (chunkY != 0) return 0;
        Chunk chunk = chunk(Math.floorDiv(x, CHUNK_SIZE), chunkY, Math.floorDiv(z, CHUNK_SIZE));
        return memGetByte(chunk.address + index(x, y, z));
    }

    void set(int x, int y, int z, byte type) {
        if (y < 0 || y >= HEIGHT) return;
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        Chunk chunk = chunk(Math.floorDiv(x, CHUNK_SIZE), Math.floorDiv(y, CHUNK_SIZE), Math.floorDiv(z, CHUNK_SIZE));
        long address = chunk.address + index(x, y, z);
        if (memGetByte(address) != type) {
            memPutByte(address, type);
            version++;
        }
    }

    int surface(int x, int z) {
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        for (int y = HEIGHT - 1; y >= 0; y--) if (get(x, y, z) != 0) return y;
        return 0;
    }

    private Chunk chunk(int chunkX, int chunkY, int chunkZ) {
        chunkX = Math.floorMod(chunkX, CHUNK_COUNT);
        chunkY = Math.floorMod(chunkY, CHUNK_COUNT);
        chunkZ = Math.floorMod(chunkZ, CHUNK_COUNT);
        long key = ((long) chunkX << 42) | ((long) chunkY << 21) | chunkZ;
        int finalChunkX = chunkX;
        int finalChunkY = chunkY;
        int finalChunkZ = chunkZ;
        return chunks.computeIfAbsent(key, ignored -> generate(finalChunkX, finalChunkY, finalChunkZ));
    }

    private Chunk generate(int chunkX, int chunkY, int chunkZ) {
        Chunk chunk = new Chunk();
        if (chunkY != 0) return chunk;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int x = chunkX * CHUNK_SIZE + localX;
                int z = chunkZ * CHUNK_SIZE + localZ;
                double waves = Math.sin(x * .031) * 4.0
                        + Math.cos(z * .027) * 3.0
                        + Math.sin((x + z) * .013) * 5.0;
                int surface = Math.max(1, Math.min(HEIGHT - 2, 8 + (int) Math.round(waves)));
                for (int y = 0; y <= surface; y++) {
                    byte type = (byte) (y == surface ? 1 : y > surface - 3 ? 2 : 3);
                    memPutByte(chunk.address + (localX | (y << 5) | (localZ << 10)), type);
                }
            }
        }
        return chunk;
    }

    void close() {
        for (Chunk chunk : chunks.values()) nmemFree(chunk.address);
        chunks.clear();
    }

    long version() {
        return version;
    }

    private static int index(int x, int y, int z) {
        return (x & 31) | ((y & 31) << 5) | ((z & 31) << 10);
    }

    private static final class Chunk {
        private final long address = nmemCalloc(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE, 1);
    }
}
