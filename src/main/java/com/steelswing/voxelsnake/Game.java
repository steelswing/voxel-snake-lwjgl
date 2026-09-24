package com.steelswing.voxelsnake;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

final class Game {
    private long window;
    private int program;
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
    private double lastMouseX;
    private double lastMouseY;
    private boolean firstMouse = true;

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
        spawnApple();
        int[] head = snake.get(0);
        cameraX = head[0] + .5f;
        cameraY = head[1] + 2.5f;
        cameraZ = head[2] + .5f;
        cameraYaw = (float) Math.atan2(dz, dx);
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
                if (key == GLFW_KEY_W) { dx = 0; dz = -1; }
                if (key == GLFW_KEY_S) { dx = 0; dz = 1; }
                if (key == GLFW_KEY_A) { dx = -1; dz = 0; }
                if (key == GLFW_KEY_D) { dx = 1; dz = 0; }
            } else if (key == GLFW_KEY_Q || key == GLFW_KEY_E) {
                editBlock(key == GLFW_KEY_E);
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
    }

    private void addSnake(int x, int z) { snake.add(new int[]{x, world.surface(x, z) + 1, z}); }

    private void editBlock(boolean add) {
        int[] hit = raycast();
        if (hit == null) return;
        if (!add) {
            world.set(hit[0], hit[1], hit[2], (byte) 0);
            return;
        }
        int x = hit[0] + hit[3];
        int y = hit[1] + hit[4];
        int z = hit[2] + hit[5];
        if (world.get(x, y, z) == 0) world.set(x, y, z, (byte) 2);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            if (!editMode && now >= nextStep) { moveSnake(); nextStep = now + .22; }
            if (editMode) updateFreeCamera(1f / 60f);
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
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

    private int[] raycast() {
        float forwardX = (float) (Math.cos(cameraPitch) * Math.cos(cameraYaw));
        float forwardY = (float) Math.sin(cameraPitch);
        float forwardZ = (float) (Math.cos(cameraPitch) * Math.sin(cameraYaw));
        int previousX = Integer.MIN_VALUE;
        int previousY = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;
        for (float distance = .2f; distance < 12f; distance += .08f) {
            int x = (int) Math.floor(cameraX + forwardX * distance);
            int y = (int) Math.floor(cameraY + forwardY * distance);
            int z = (int) Math.floor(cameraZ + forwardZ * distance);
            if (x == previousX && y == previousY && z == previousZ) continue;
            if (world.get(x, y, z) != 0) {
                if (previousX == Integer.MIN_VALUE) return new int[]{x, y, z, 0, 1, 0};
                return new int[]{x, y, z, previousX - x, previousY - y, previousZ - z};
            }
            previousX = x;
            previousY = y;
            previousZ = z;
        }
        return null;
    }

    private void moveSnake() {
        int[] head = snake.get(0);
        int x = Math.floorMod(head[0] + dx, World.SIZE);
        int z = Math.floorMod(head[2] + dz, World.SIZE);
        int y = world.surface(x, z) + 1;
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

    private void render() {
        int[] size = new int[2];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, size, height);
        glViewport(0, 0, size[0], height[0]);
        glClearColor(.42f, .68f, .92f, 1);
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
            ex = head[0] + .5f - dx * 10f;
            ey = head[1] + 8f;
            ez = head[2] + .5f - dz * 10f;
            tx = head[0] + .5f;
            ty = head[1] + .25f;
            tz = head[2] + .5f;
        }
        FloatBuffer data = BufferUtils.createFloatBuffer((World.SIZE * World.HEIGHT * World.SIZE + snake.size()) * 36 * 6);
        for (int x = 0; x < World.SIZE; x++) for (int y = 0; y < World.HEIGHT; y++) for (int z = 0; z < World.SIZE; z++) {
            int type = world.get(x, y, z);
            if (type != 0) visibleCube(data, x, y, z, type);
        }
        for (int[] part : snake) cube(data, part[0], part[1], part[2], 6);
        cube(data, apple[0], apple[1], apple[2], 5);
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
                matrix(ex, ey, ez, tx, ty, tz, size[0] / (float) height[0]));
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

    private void visibleCube(FloatBuffer b, int x, int y, int z, int type) {
        if (world.get(x, y + 1, z) == 0) face(b, x, y, z, type, 0);
        if (world.get(x, y - 1, z) == 0) face(b, x, y, z, type, 1);
        if (world.get(x - 1, y, z) == 0) face(b, x, y, z, type, 2);
        if (world.get(x + 1, y, z) == 0) face(b, x, y, z, type, 3);
        if (world.get(x, y, z + 1) == 0) face(b, x, y, z, type, 4);
        if (world.get(x, y, z - 1) == 0) face(b, x, y, z, type, 5);
    }

    private static void face(FloatBuffer b, int x, int y, int z, int type, int face) {
        int[][] f={{3,2,6,6,7,3},{4,5,1,1,0,4},{4,0,3,3,7,4},{1,5,6,6,2,1},{5,4,7,7,6,5},{0,1,2,2,3,0}};
        float[][] p={{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
        float[] c = TextureGenerator.color(type, x, y, z, face);
        for (int i : f[face]) b.put(x+p[i][0]).put(y+p[i][1]).put(z+p[i][2]).put(c[0]).put(c[1]).put(c[2]);
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
        int vs=shader(GL_VERTEX_SHADER,"#version 120\nattribute vec3 position; attribute vec3 color; varying vec3 vColor; uniform mat4 matrix; void main(){gl_Position=matrix*vec4(position,1.0);vColor=color;}");
        int fs=shader(GL_FRAGMENT_SHADER,"#version 120\nvarying vec3 vColor; void main(){gl_FragColor=vec4(vColor,1.0);}");
        int p=glCreateProgram();glAttachShader(p,vs);glAttachShader(p,fs);glLinkProgram(p);glDeleteShader(vs);glDeleteShader(fs);return p;
    }
    private static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(s));return s;}
}
