package com.steelswing.voxelsnake;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

final class Game {
    private long window;
    private int program;
    private final World world = new World(0x5EEDL);
    private final List<int[]> snake = new ArrayList<>();
    private boolean editMode;
    private int dx = 1;
    private int dz;
    private double nextStep;
    private double cameraAngle;

    void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        window = glfwCreateWindow(960, 600, "Voxel Snake - TAB: edit mode", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Window creation failed");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        program = createProgram();
        addSnake(16, 16);
        addSnake(15, 16);
        addSnake(14, 16);
        glfwSetKeyCallback(window, (w, key, scan, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) glfwSetWindowShouldClose(window, true);
            if (key == GLFW_KEY_TAB) editMode = !editMode;
            if (key == GLFW_KEY_W) { dx = 0; dz = -1; }
            if (key == GLFW_KEY_S) { dx = 0; dz = 1; }
            if (key == GLFW_KEY_A) { dx = -1; dz = 0; }
            if (key == GLFW_KEY_D) { dx = 1; dz = 0; }
            if (editMode && (key == GLFW_KEY_Q || key == GLFW_KEY_E)) editBlock(key == GLFW_KEY_E);
        });
    }

    private void addSnake(int x, int z) { snake.add(new int[]{x, world.surface(x, z) + 1, z}); }

    private void editBlock(boolean add) {
        int[] head = snake.get(0);
        int x = Math.floorMod(head[0] + dx, World.SIZE);
        int z = Math.floorMod(head[2] + dz, World.SIZE);
        int y = world.surface(x, z) + (add ? 1 : 0);
        world.set(x, y, z, (byte) (add ? 2 : 0));
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            if (!editMode && now >= nextStep) { moveSnake(); nextStep = now + .22; }
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void moveSnake() {
        int[] head = snake.get(0);
        int x = Math.floorMod(head[0] + dx, World.SIZE);
        int z = Math.floorMod(head[2] + dz, World.SIZE);
        snake.add(0, new int[]{x, world.surface(x, z) + 1, z});
        while (snake.size() > 9) snake.remove(snake.size() - 1);
    }

    private void render() {
        int[] size = new int[2];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, size, height);
        glViewport(0, 0, size[0], height[0]);
        glClearColor(.42f, .68f, .92f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        int[] head = snake.get(0);
        cameraAngle += .002;
        float ex = head[0] + (float) Math.cos(cameraAngle) * 22;
        float ey = head[1] + 15;
        float ez = head[2] + (float) Math.sin(cameraAngle) * 22;
        FloatBuffer data = BufferUtils.createFloatBuffer((World.SIZE * World.HEIGHT * World.SIZE + snake.size()) * 36 * 6);
        for (int x = 0; x < World.SIZE; x++) for (int y = 0; y < World.HEIGHT; y++) for (int z = 0; z < World.SIZE; z++) {
            int type = world.get(x, y, z);
            if (type != 0) cube(data, x, y, z, type);
        }
        for (int[] part : snake) cube(data, part[0], part[1], part[2], 6);
        data.flip();
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
        glUseProgram(program);
        int position = glGetAttribLocation(program, "position");
        int color = glGetAttribLocation(program, "color");
        glEnableVertexAttribArray(position); glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, 0);
        glEnableVertexAttribArray(color); glVertexAttribPointer(color, 3, GL_FLOAT, false, 24, 12);
        glUniformMatrix4fv(glGetUniformLocation(program, "matrix"), false,
                matrix(ex, ey, ez, head[0], head[1], head[2], size[0] / (float) height[0]));
        glDrawArrays(GL_TRIANGLES, 0, data.limit() / 6);
        glDeleteBuffers(vbo);
    }

    private static void cube(FloatBuffer b, int x, int y, int z, int type) {
        float[][] p={{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
        int[][] f={{0,1,2,2,3,0},{5,4,7,7,6,5},{4,0,3,3,7,4},{1,5,6,6,2,1},{3,2,6,6,7,3},{4,5,1,1,0,4}};
        for (int face = 0; face < f.length; face++) {
            float[] c = TextureGenerator.color(type, x, y, z, face);
            for (int i : f[face]) b.put(x+p[i][0]).put(y+p[i][1]).put(z+p[i][2]).put(c[0]).put(c[1]).put(c[2]);
        }
    }

    private static float[] matrix(float ex,float ey,float ez,float tx,float ty,float tz,float aspect) {
        float[] f=normalize(tx-ex,ty-ey,tz-ez),up={0,1,0},s=normalize(cross(f,up)),u=cross(s,f),m=new float[16];
        float scale=1f/(float)Math.tan(Math.toRadians(55)/2);
        m[0]=scale/aspect*s[0];m[4]=scale/aspect*s[1];m[8]=scale/aspect*s[2];m[1]=scale*u[0];m[5]=scale*u[1];m[9]=scale*u[2];m[2]=-f[0];m[6]=-f[1];m[10]=-f[2];m[12]=-dot(s,ex,ey,ez);m[13]=-dot(u,ex,ey,ez);m[14]=dot(f,ex,ey,ez);m[15]=1;return m;
    }
    private static float[] normalize(float x,float y,float z){float l=(float)Math.sqrt(x*x+y*y+z*z);return new float[]{x/l,y/l,z/l];}
    private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static float dot(float[] a,float x,float y,float z){return a[0]*x+a[1]*y+a[2]*z;}

    private static int createProgram() {
        int vs=shader(GL_VERTEX_SHADER,"#version 120\nattribute vec3 position; attribute vec3 color; varying vec3 vColor; uniform mat4 matrix; void main(){gl_Position=matrix*vec4(position,1.0);vColor=color;}");
        int fs=shader(GL_FRAGMENT_SHADER,"#version 120\nvarying vec3 vColor; void main(){gl_FragColor=vec4(vColor,1.0);}");
        int p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);glDeleteShader(vs);glDeleteShader(fs);return p;
    }
    private static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(s));return s;}
}
