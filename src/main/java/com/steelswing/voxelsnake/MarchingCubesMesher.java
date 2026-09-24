package com.steelswing.voxelsnake;

final class MarchingCubesMesher {
    private static final int[][] TETRAHEDRA = {
            {0, 5, 1, 6}, {0, 1, 2, 6}, {0, 2, 3, 6},
            {0, 3, 7, 6}, {0, 7, 4, 6}, {0, 4, 5, 6}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 0}, {0, 3}, {1, 3}, {2, 3}
    };
    private static final float[][] CORNERS = {
            {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0},
            {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}
    };

    private MarchingCubesMesher() {
    }

    static void build(World world, NativeMesh mesh, int startX, int startZ) {
        for (int x = startX; x < startX + World.CHUNK_SIZE; x++) {
            for (int y = 0; y < World.CHUNK_SIZE; y++) {
                for (int z = startZ; z < startZ + World.CHUNK_SIZE; z++) {
                    polygonize(world, mesh, x, y, z);
                }
            }
        }
    }

    private static void polygonize(World world, NativeMesh mesh, int x, int y, int z) {
        float[] values = new float[8];
        for (int i = 0; i < 8; i++) {
            float[] corner = CORNERS[i];
            values[i] = world.get(x + (int) corner[0], y + (int) corner[1], z + (int) corner[2]) == 0 ? 0f : 1f;
        }
        for (int[] tetra : TETRAHEDRA) {
            polygonizeTetra(mesh, x, y, z, tetra, values);
        }
    }

    private static void polygonizeTetra(NativeMesh mesh, int x, int y, int z,
                                        int[] tetra, float[] values) {
        float[][] intersections = new float[6][];
        int count = 0;
        for (int[] edge : EDGES) {
            int a = tetra[edge[0]];
            int b = tetra[edge[1]];
            boolean insideA = values[a] >= .5f;
            boolean insideB = values[b] >= .5f;
            if (insideA == insideB) continue;
            float[] pa = CORNERS[a];
            float[] pb = CORNERS[b];
            float t = (.5f - values[a]) / (values[b] - values[a]);
            intersections[count++] = new float[]{
                    x + pa[0] + (pb[0] - pa[0]) * t,
                    y + pa[1] + (pb[1] - pa[1]) * t,
                    z + pa[2] + (pb[2] - pa[2]) * t
            };
        }
        if (count < 3) return;
        for (int i = 1; i + 1 < count; i++) {
            put(mesh, intersections[0], intersections[i], intersections[i + 1]);
        }
    }

    private static void put(NativeMesh mesh, float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 0.0001f) return;
        nx /= length; ny /= length; nz /= length;
        putVertex(mesh, a, nx, ny, nz);
        putVertex(mesh, b, nx, ny, nz);
        putVertex(mesh, c, nx, ny, nz);
    }

    private static void putVertex(NativeMesh mesh, float[] point, float nx, float ny, float nz) {
        float u = (point[0] - (float) Math.floor(point[0])) * .5f;
        float v = (point[2] - (float) Math.floor(point[2])) * .5f;
        mesh.put(point[0], point[1], point[2], nx, ny, nz, u, v, 1f);
    }
}
