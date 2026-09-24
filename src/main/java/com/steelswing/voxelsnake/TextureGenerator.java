package com.steelswing.voxelsnake;

import java.awt.image.BufferedImage;
import java.util.Random;

/** Seeded pixel palette inspired by the reference Minecraft4K texture atlas. */
final class TextureGenerator {
    private TextureGenerator() { }

    static BufferedImage atlas() {
        int tile = 16;
        BufferedImage image = new BufferedImage(tile * 4, tile, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(0x1172981L);
        int[] base = {0x6aaa40, 0x966c4a, 0x7f7f7f, 0x50d937};
        for (int layer = 0; layer < base.length; layer++) {
            for (int y = 0; y < tile; y++) for (int x = 0; x < tile; x++) {
                int brightness = 160 + random.nextInt(96);
                if (layer == 0 && y > 11) brightness = brightness * 2 / 3;
                if (layer == 3 && random.nextBoolean()) brightness /= 2;
                int c = base[layer];
                int r = ((c >> 16) & 255) * brightness / 255;
                int g = ((c >> 8) & 255) * brightness / 255;
                int b = (c & 255) * brightness / 255;
                image.setRGB(layer * tile + x, y, 0xff000000 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }
}
