package com.steelswing.voxelsnake;

import static org.lwjgl.system.MemoryUtil.memPutFloat;
import static org.lwjgl.system.MemoryUtil.nmemAlloc;
import static org.lwjgl.system.MemoryUtil.nmemFree;
import static org.lwjgl.system.MemoryUtil.nmemRealloc;

final class NativeMesh {
    long address;
    int floats;
    private int capacity;

    NativeMesh(int initialFloats) {
        capacity = Math.max(64, initialFloats);
        address = nmemAlloc(capacity * 4L);
    }

    void put(float x, float y, float z, float nx, float ny, float nz, float u, float v, float light) {
        ensure(9);
        long at = address + floats * 4L;
        memPutFloat(at, x);
        memPutFloat(at + 4, y);
        memPutFloat(at + 8, z);
        memPutFloat(at + 12, nx);
        memPutFloat(at + 16, ny);
        memPutFloat(at + 20, nz);
        memPutFloat(at + 24, u);
        memPutFloat(at + 28, v);
        memPutFloat(at + 32, light);
        floats += 9;
    }

    void free() {
        if (address != 0) {
            nmemFree(address);
            address = 0;
        }
    }

    private void ensure(int count) {
        if (floats + count <= capacity) return;
        while (floats + count > capacity) capacity = Math.max(capacity + 1, capacity * 2);
        address = nmemRealloc(address, capacity * 4L);
    }
}
