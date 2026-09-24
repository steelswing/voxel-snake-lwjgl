package com.steelswing.voxelsnake;

import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.nmemAlloc;

/**
 * Minecraft4K-inspired procedural atlas.
 *
 * <p>Each material occupies one 16x48 cell: top, side and bottom. The single
 * deterministic LCG stream is intentionally shared by the whole atlas, which
 * gives neighboring pixels the same clustered noise as the reference.</p>
 */
final class TextureGenerator {
    static final int TILE_SIZE = 16;
    static final int SECTION_COUNT = 3;
    static final int HEIGHT = TILE_SIZE * SECTION_COUNT;

    private static final int[] BASE = {
            0x6AAA40, 0x966C4A, 0x7F7F7F, 0x675231,
            0x50D937, 0x35B84A, 0xE63224, 0xB53A15
    };

    private static final long LCG_MULTIPLIER = 1664525L;
    private static final long LCG_INCREMENT = 1013904223L;
    private static final long LCG_MASK = 0xFFFFFFFFL;
    private static final long SEED = 151910774L;

    private TextureGenerator() {
    }

    static int width() {
        return TILE_SIZE * BASE.length;
    }

    static int height() {
        return HEIGHT;
    }

    static long pixels() {
        long address = nmemAlloc((long) width() * HEIGHT * 4L);
        long state = SEED;
        long at = address;
        for (int y = HEIGHT - 1; y >= 0; y--) {
            int section = y / TILE_SIZE;
            int localY = y & (TILE_SIZE - 1);
            for (int material = 0; material < BASE.length; material++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    state = (state * LCG_MULTIPLIER + LCG_INCREMENT) & LCG_MASK;
                    int random = (int) ((state >>> 16) % 96);
                    int rgb = color(material, section, x, localY, random);
                    memPutByte(at++, (byte) (rgb >> 16));
                    memPutByte(at++, (byte) (rgb >> 8));
                    memPutByte(at++, (byte) rgb);
                    memPutByte(at++, (byte) 0xFF);
                }
            }
        }
        return address;
    }

    private static int color(int material, int section, int x, int y, int random) {
        int brightness = 160 + random;
        int rgb = BASE[material];

        switch (material) {
            case 0:
                if (section == 0) {
                    rgb = shade(0x6AAA40, brightness);
                } else if (section == 1) {
                    int edge = ((x * x * 3 + x * 81) >> 2) & 3;
                    rgb = y < edge + 3 ? shade(0x6AAA40, brightness) : shade(0x966C4A, brightness * 2 / 3);
                } else {
                    rgb = shade(0x966C4A, brightness * 2 / 3);
                }
                break;
            case 1:
                rgb = shade(0x966C4A, brightness * 2 / 3);
                if (((x * 3 + y * 7) & 15) == 0) rgb = shade(rgb, 1.2f);
                break;
            case 2:
                if (((x / 2 + y / 2) & 3) == 0) brightness = 190 + random / 3;
                rgb = shade(0x7F7F7F, brightness);
                break;
            case 3:
                if (section == 0 || section == 2) {
                    int dx = x - 7;
                    int dy = y - 7;
                    int ring = (int) Math.sqrt(dx * dx + dy * dy);
                    rgb = shade(0xBC9862, ring % 2 == 0 ? brightness : brightness * 3 / 4);
                } else {
                    rgb = shade(0x675231, brightness * (150 - (x & 1) * 55) / 100);
                }
                break;
            case 4:
                rgb = shade(0x50D937, 190 + random / 2);
                break;
            case 5:
                rgb = shade(0x35B84A, 190 + random / 2);
                break;
            case 6:
                rgb = shade(0xE63224, 190 + random / 2);
                if ((x + y * 3) % 11 == 0) rgb = shade(0x7D201A, brightness);
                break;
            case 7:
                rgb = shade(0xB53A15, 190 + random / 2);
                if ((x + y) % 5 == 0) rgb = shade(0xF2C4A0, brightness);
                break;
            default:
                break;
        }
        return rgb;
    }

    private static int shade(int rgb, int brightness) {
        int r = Math.min(255, ((rgb >> 16) & 255) * brightness / 255);
        int g = Math.min(255, ((rgb >> 8) & 255) * brightness / 255);
        int b = Math.min(255, (rgb & 255) * brightness / 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int rgb, float brightness) {
        return shade(rgb, Math.round(brightness * 255f));
    }
}
