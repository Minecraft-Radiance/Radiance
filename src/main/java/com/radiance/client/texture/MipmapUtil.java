package com.radiance.client.texture;

import com.radiance.client.texture.compat.TextureCompat;
//#if MC>=260200
//$$ import com.mojang.blaze3d.platform.NativeImage;
//#else
import net.minecraft.client.texture.NativeImage;
//#endif
import net.minecraft.util.Util;

public class MipmapUtil {

    private static final float CUTOUT_ALPHA_COVERAGE_THRESHOLD = 0.5F;
    private static final float[] COLOR_FRACTIONS = Util.make(new float[256], list -> {
        for (int i = 0; i < list.length; i++) {
            list[i] = (float) Math.pow(i / 255.0F, 2.2);
        }
    });

    private MipmapUtil() {
    }

    private static NativeImage newLike(NativeImage source, int width, int height) {
        return TextureCompat.newLike(source, width, height);
    }

    private static int readColor(NativeImage image, int x, int y) {
        return TextureCompat.readColor(image, x, y);
    }

    private static void writeColor(NativeImage image, int x, int y, int color) {
        TextureCompat.writeColor(image, x, y, color);
    }

    private static int alpha(int color) {
        return TextureCompat.alpha(color);
    }

    private static int makeColor(int a, int r, int g, int b) {
        return TextureCompat.argb(a, r, g, b);
    }

    // straight forward, need speed up
    public static NativeImage getSpecificMipmapLevelImage(NativeImage original, int targetLevel) {
        if (targetLevel == 0) {
            NativeImage ret = newLike(original, original.getWidth(), original.getHeight());
            ret.copyFrom(original);
            return ret;
        }

        NativeImage currentSource = original;
        boolean bl = hasAlpha(original);

        boolean close = false;

        for (int i = 1; i <= targetLevel; i++) {
            int newWidth = currentSource.getWidth() >> 1;
            int newHeight = currentSource.getHeight() >> 1;

            if (newWidth == 0 || newHeight == 0) {
                break;
            }

            NativeImage nextLevel = newLike(currentSource, newWidth, newHeight);

            for (int x = 0; x < newWidth; x++) {
                for (int y = 0; y < newHeight; y++) {
                    writeColor(nextLevel, x, y, blend(readColor(currentSource, x * 2, y * 2),
                        readColor(currentSource, x * 2 + 1, y * 2),
                        readColor(currentSource, x * 2, y * 2 + 1),
                        readColor(currentSource, x * 2 + 1, y * 2 + 1), bl));
                }
            }

            if (close) {
                currentSource.close();
            }

            currentSource = nextLevel;
            close = true;
        }

        return currentSource;
    }

    public static NativeImage[] buildMipmapChain(NativeImage original) {
        int maxLevel = 0;
        int width = original.getWidth();
        int height = original.getHeight();
        while ((width >> 1) > 0 && (height >> 1) > 0) {
            width >>= 1;
            height >>= 1;
            maxLevel++;
        }

        NativeImage[] levels = new NativeImage[maxLevel + 1];
        levels[0] = original;
        boolean hasAlpha = hasAlpha(original);

        for (int level = 1; level <= maxLevel; level++) {
            NativeImage previous = levels[level - 1];
            NativeImage next = newLike(previous, previous.getWidth() >> 1,
                previous.getHeight() >> 1);

            for (int x = 0; x < next.getWidth(); x++) {
                for (int y = 0; y < next.getHeight(); y++) {
                    writeColor(next, x, y, blend(readColor(previous, x * 2, y * 2),
                        readColor(previous, x * 2 + 1, y * 2),
                        readColor(previous, x * 2, y * 2 + 1),
                        readColor(previous, x * 2 + 1, y * 2 + 1), hasAlpha));
                }
            }

            levels[level] = next;
        }

        return levels;
    }

    public static NativeImage[] getMipmapLevelsImages(NativeImage[] originals, int mipmap) {
        if (mipmap + 1 <= originals.length) {
            return originals;
        } else {
            NativeImage[] nativeImages = new NativeImage[mipmap + 1];
            nativeImages[0] = originals[0];
            boolean bl = hasAlpha(nativeImages[0]);

            for (int i = 1; i <= mipmap; i++) {
                if (i < originals.length) {
                    nativeImages[i] = originals[i];
                } else {
                    NativeImage nativeImage = nativeImages[i - 1];
                    NativeImage nativeImage2 = newLike(nativeImage, nativeImage.getWidth() >> 1,
                        nativeImage.getHeight() >> 1);
                    int j = nativeImage2.getWidth();
                    int k = nativeImage2.getHeight();

                    for (int l = 0; l < j; l++) {
                        for (int m = 0; m < k; m++) {
                            writeColor(nativeImage2, l, m,
                                blend(readColor(nativeImage, l * 2, m * 2),
                                    readColor(nativeImage, l * 2 + 1, m * 2),
                                    readColor(nativeImage, l * 2, m * 2 + 1),
                                    readColor(nativeImage, l * 2 + 1, m * 2 + 1), bl));
                        }
                    }

                    nativeImages[i] = nativeImage2;
                }
            }

            return nativeImages;
        }
    }

    public static boolean hasAlpha(NativeImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                if (alpha(readColor(image, i, j)) == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public static int blend(int one, int two, int three, int four, boolean checkAlpha) {
        float a1 = getColorFraction(one >> 24), r1 = getColorFraction(
            one >> 16), g1 = getColorFraction(one >> 8), b1 = getColorFraction(one >> 0);
        float a2 = getColorFraction(two >> 24), r2 = getColorFraction(
            two >> 16), g2 = getColorFraction(two >> 8), b2 = getColorFraction(two >> 0);
        float a3 = getColorFraction(three >> 24), r3 = getColorFraction(
            three >> 16), g3 = getColorFraction(three >> 8), b3 = getColorFraction(three >> 0);
        float a4 = getColorFraction(four >> 24), r4 = getColorFraction(
            four >> 16), g4 = getColorFraction(four >> 8), b4 = getColorFraction(four >> 0);

        float totalAlpha = a1 + a2 + a3 + a4;
        float outA, outR, outG, outB;

        if (totalAlpha > 0) {
            outA = totalAlpha / 4.0F;

            outR = (r1 * a1 + r2 * a2 + r3 * a3 + r4 * a4) / totalAlpha;
            outG = (g1 * a1 + g2 * a2 + g3 * a3 + g4 * a4) / totalAlpha;
            outB = (b1 * a1 + b2 * a2 + b3 * a3 + b4 * a4) / totalAlpha;
        } else {
            return 0;
        }

        double gamma = 0.45454545454545453;
        int resA = (int) (Math.pow(outA, gamma) * 255.0);
        int resR = (int) (Math.pow(outR, gamma) * 255.0);
        int resG = (int) (Math.pow(outG, gamma) * 255.0);
        int resB = (int) (Math.pow(outB, gamma) * 255.0);

        if (checkAlpha) {
            float alphaCoverage =
                (alpha(one) + alpha(two) + alpha(three) + alpha(four)) / (4.0F * 255.0F);
            resA = alphaCoverage >= CUTOUT_ALPHA_COVERAGE_THRESHOLD ? 255 : 0;
        }

        return makeColor(resA, resR, resG, resB);
    }

    public static int getColorComponent(int one, int two, int three, int four, int bits) {
        float f = getColorFraction(one >> bits);
        float g = getColorFraction(two >> bits);
        float h = getColorFraction(three >> bits);
        float i = getColorFraction(four >> bits);
        float j = (float) ((float) Math.pow((f + g + h + i) * 0.25, 0.45454545454545453));
        return (int) (j * 255.0);
    }

    public static float getColorFraction(int value) {
        return COLOR_FRACTIONS[value & 0xFF];
    }
}
