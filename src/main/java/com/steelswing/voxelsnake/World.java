package com.steelswing.voxelsnake;

import java.util.Random;

final class World {
    static final int SIZE = 32;
    static final int HEIGHT = 16;
    private final byte[][][] blocks = new byte[SIZE][HEIGHT][SIZE];

    World(long seed) {
        Random random = new Random(seed);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double waves = Math.sin(x * .31) * 1.5 + Math.cos(z * .27) * 1.3;
                int surface = Math.max(1, Math.min(HEIGHT - 2,
                        3 + (int) Math.round(waves + random.nextDouble() * 1.5)));
                for (int y = 0; y <= surface; y++) {
                    blocks[x][y][z] = (byte) (y == surface ? 1 : y > surface - 3 ? 2 : 3);
                }
            }
        }
    }

    byte get(int x, int y, int z) {
        return inBounds(x, y, z) ? blocks[x][y][z] : 0;
    }

    void set(int x, int y, int z, byte type) {
        if (inBounds(x, y, z)) blocks[x][y][z] = type;
    }

    int surface(int x, int z) {
        x = Math.floorMod(x, SIZE);
        z = Math.floorMod(z, SIZE);
        for (int y = HEIGHT - 1; y >= 0; y--) if (blocks[x][y][z] != 0) return y;
        return 0;
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < HEIGHT && z >= 0 && z < SIZE;
    }
}
