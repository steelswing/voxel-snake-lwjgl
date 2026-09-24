package com.steelswing.voxelsnake;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import java.util.Random;

/** Generates a noisy, layered pixel atlas using the reference project's approach. */
final class TextureGenerator {
    private static final int TILE = 16;
    private static final int[] BASE = {0x6aaa40, 0x966c4a, 0x7f7f7f, 0x50d937, 0xb53a15};

    private TextureGenerator() { }

    static BufferedImage atlas() {
        BufferedImage image = new BufferedImage(TILE * BASE.length, TILE, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(0x1172981L);
        for (int layer = 0; layer < BASE.length; layer++) {
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    image.setRGB(layer * TILE + x, y, 0xff000000 | color(layer, x, y, random));
                }
            }
        }
        return image;
    }

    static ByteBuffer pixels() {
        BufferedImage image = atlas();
        ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                pixels.put((byte) (color >> 16)).put((byte) (color >> 8))
                        .put((byte) color).put((byte) 0xff);
            }
        }
        pixels.flip();
        return pixels;
    }

    /** Stable per-block colour: no flicker and no external image assets required. */
    static float[] color(int type, int x, int y, int z, int face) {
        int layer = Math.floorMod(type - 1, BASE.length);
        long hash = x * 73428767L ^ y * 912931L ^ z * 42317861L ^ face * 31L;
        Random random = new Random(hash);
        int brightness = 178 + random.nextInt(64);
        if (face == 2) brightness = Math.min(255, brightness + 26);
        if (face == 3) brightness = Math.max(80, brightness - 35);
        int c = BASE[layer];
        return new float[]{((c >> 16) & 255) * brightness / 65025f,
                ((c >> 8) & 255) * brightness / 65025f,
                (c & 255) * brightness / 65025f};
    }

    private static int color(int layer, int x, int y, Random random) {
        int brightness = 172 + random.nextInt(70);
        if (layer == 0) {
            int grassLine = 10 + (x * x * 3 + x * 81 >>> 2 & 3);
            if (y < grassLine) return shade(0x6aaa40, brightness);
            brightness = brightness * 2 / 3;
        } else if (layer == 1 && ((x + y * 3) & 7) == 0) {
            brightness = Math.min(255, brightness + 28);
        } else if (layer == 3 && random.nextBoolean()) {
            brightness /= 2;
        } else if (layer == 4 && (x % 4 == 0 || y % 4 == 0)) {
            return shade(0xbcafa5, brightness);
        }
        return shade(BASE[layer], brightness);
    }

    private static int shade(int rgb, int brightness) {
        return (((rgb >> 16) & 255) * brightness / 255 << 16)
                | (((rgb >> 8) & 255) * brightness / 255 << 8)
                | ((rgb & 255) * brightness / 255);
    }
}
