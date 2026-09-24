package com.steelswing.voxelsnake;

import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_LINES;
import static org.lwjgl.opengl.GL11C.GL_LINE_SMOOTH;
import static org.lwjgl.opengl.GL11C.GL_POLYGON_OFFSET_LINE;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glLineWidth;
import static org.lwjgl.opengl.GL11C.glPolygonOffset;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.nglBufferData;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform4f;

final class SelectionRenderer {
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private final int vbo = org.lwjgl.opengl.GL15C.glGenBuffers();

    void render(int program, int[] block) {
        float x = block[0], y = block[1], z = block[2], e = 0.003f;
        float[][] c = {
                {x - e, y - e, z - e}, {x + 1 + e, y - e, z - e},
                {x + 1 + e, y + 1 + e, z - e}, {x - e, y + 1 + e, z - e},
                {x - e, y - e, z + 1 + e}, {x + 1 + e, y - e, z + 1 + e},
                {x + 1 + e, y + 1 + e, z + 1 + e}, {x - e, y + 1 + e, z + 1 + e}
        };
        NativeMesh mesh = new NativeMesh(144);
        for (int[] edge : EDGES) {
            float[] a = c[edge[0]], b = c[edge[1]];
            mesh.put(a[0], a[1], a[2], 0, 0, 1);
            mesh.put(b[0], b[1], b[2], 0, 0, 1);
        }
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        nglBufferData(GL_ARRAY_BUFFER, mesh.floats * 4L, mesh.address, GL_STREAM_DRAW);
        glDisable(GL_CULL_FACE);
        glEnable(GL_LINE_SMOOTH);
        glEnable(GL_POLYGON_OFFSET_LINE);
        glPolygonOffset(-2f, -2f);
        glLineWidth(2f);
        glUniform1f(glGetUniformLocation(program, "useTexture"), 0);
        glUniform4f(glGetUniformLocation(program, "tint"), 1f, 0.85f, 0.1f, 1f);
        glDrawArrays(GL_LINES, 0, mesh.floats / 6);
        glDisable(GL_POLYGON_OFFSET_LINE);
        glDisable(GL_LINE_SMOOTH);
        glEnable(GL_CULL_FACE);
        glUniform1f(glGetUniformLocation(program, "useTexture"), 1);
        mesh.free();
    }

    void free() {
        org.lwjgl.opengl.GL15C.glDeleteBuffers(vbo);
    }
}
