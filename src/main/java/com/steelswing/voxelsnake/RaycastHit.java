package com.steelswing.voxelsnake;

final class RaycastHit {
    final int x;
    final int y;
    final int z;
    final int normalX;
    final int normalY;
    final int normalZ;

    RaycastHit(int x, int y, int z, int normalX, int normalY, int normalZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    int[] block() {
        return new int[]{x, y, z};
    }
}
