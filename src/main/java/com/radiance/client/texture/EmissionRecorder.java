package com.radiance.client.texture;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.texture.NativeImage;
import org.jetbrains.annotations.Nullable;

public final class EmissionRecorder {

    public static final int MAX_TEXTURES = 4096;
    private static final int TILE_SUBDIVISIONS = 8;
    private static final float DENSE_RECT_COVERAGE = 0.75f;

    private EmissionRecorder() {}

    @Nullable
    public static TileUpdate buildTileUpdate(int textureId, NativeImage albedo,
        @Nullable NativeImage specular, int offsetX, int offsetY, int unpackSkipPixels,
        int unpackSkipRows, int regionWidth, int regionHeight) {
        if (textureId < 0 || textureId >= MAX_TEXTURES || albedo == null) {
            return null;
        }

        TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(textureId);
        if (texture == null || texture.width() <= 0 || texture.height() <= 0) {
            return null;
        }

        List<EmissionCell> cells = buildRegionCells(albedo, specular, texture.width(),
            texture.height(), offsetX, offsetY, unpackSkipPixels, unpackSkipRows, regionWidth,
            regionHeight);
        return new TileUpdate(textureId, buildTileKey(offsetX, offsetY, regionWidth, regionHeight),
            cells);
    }

    public static long buildTileKey(int offsetX, int offsetY, int regionWidth, int regionHeight) {
        long seed = 0xcbf29ce484222325L;
        seed = hashCombine64(seed, offsetX);
        seed = hashCombine64(seed, offsetY);
        seed = hashCombine64(seed, regionWidth);
        seed = hashCombine64(seed, regionHeight);
        return seed;
    }

    private static long hashCombine64(long seed, long value) {
        return seed ^ (value + 0x9e3779b97f4a7c15L + (seed << 6) + (seed >>> 2));
    }

    private static int computeTileStep(int width, int height) {
        int tileWidthStep = Math.max(1, (width + TILE_SUBDIVISIONS - 1) / TILE_SUBDIVISIONS);
        int tileHeightStep = Math.max(1, (height + TILE_SUBDIVISIONS - 1) / TILE_SUBDIVISIONS);
        return Math.min(tileWidthStep, tileHeightStep);
    }

    private static List<EmissionCell> buildRegionCells(NativeImage albedo,
        @Nullable NativeImage specular, int textureWidth, int textureHeight, int offsetX,
        int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight) {
        if (specular == null || regionWidth <= 0 || regionHeight <= 0) {
            return new ArrayList<>();
        }

        int startX = Math.max(unpackSkipPixels, 0);
        int startY = Math.max(unpackSkipRows, 0);
        int width = Math.min(Math.max(0, albedo.getWidth() - startX), regionWidth);
        int height = Math.min(Math.max(0, albedo.getHeight() - startY), regionHeight);
        width = Math.min(width, Math.max(0, specular.getWidth() - startX));
        height = Math.min(height, Math.max(0, specular.getHeight() - startY));
        if (width <= 0 || height <= 0) {
            return new ArrayList<>();
        }

        int tileStep = computeTileStep(width, height);
        int gridWidth = (width + tileStep - 1) / tileStep;
        int gridHeight = (height + tileStep - 1) / tileStep;
        EmissionCell[][] grid = new EmissionCell[gridHeight][gridWidth];

        for (int gridY = 0, tileY = 0; tileY < height; gridY++, tileY += tileStep) {
            for (int gridX = 0, tileX = 0; tileX < width; gridX++, tileX += tileStep) {
                int tileWidth = Math.min(tileStep, width - tileX);
                int tileHeight = Math.min(tileStep, height - tileY);

                float weightedEmissionSum = 0.0f;
                float weightedRadianceR = 0.0f;
                float weightedRadianceG = 0.0f;
                float weightedRadianceB = 0.0f;
                int minLocalX = Integer.MAX_VALUE;
                int minLocalY = Integer.MAX_VALUE;
                int maxLocalX = Integer.MIN_VALUE;
                int maxLocalY = Integer.MIN_VALUE;

                for (int y = 0; y < tileHeight; y++) {
                    for (int x = 0; x < tileWidth; x++) {
                        int localX = tileX + x;
                        int localY = tileY + y;
                        int sampleX = startX + localX;
                        int sampleY = startY + localY;

                        int argb = MipmapUtil.getColorArgb(albedo, sampleX, sampleY);
                        float alphaCoverage = decodeAlphaCoverage(argb);
                        if (alphaCoverage <= 0.0f) {
                            continue;
                        }

                        float emission = decodeLabPbrEmission(
                            MipmapUtil.getColorArgb(specular, sampleX, sampleY));
                        if (emission <= 0.0f) {
                            continue;
                        }

                        float weightedEmission = emission * alphaCoverage;
                        weightedEmissionSum += weightedEmission;
                        weightedRadianceR += ((argb >> 16) & 0xFF) / 255.0f * weightedEmission;
                        weightedRadianceG += ((argb >> 8) & 0xFF) / 255.0f * weightedEmission;
                        weightedRadianceB += (argb & 0xFF) / 255.0f * weightedEmission;
                        minLocalX = Math.min(minLocalX, localX);
                        minLocalY = Math.min(minLocalY, localY);
                        maxLocalX = Math.max(maxLocalX, localX);
                        maxLocalY = Math.max(maxLocalY, localY);
                    }
                }

                if (weightedEmissionSum <= 0.0f || maxLocalX < minLocalX || maxLocalY < minLocalY) {
                    continue;
                }

                float boundsAreaPixels = Math.max(1.0f,
                    (maxLocalX - minLocalX + 1) * (maxLocalY - minLocalY + 1));
                EmissionCell cell = new EmissionCell();
                cell.u0 = (offsetX + minLocalX) / (float) textureWidth;
                cell.v0 = (offsetY + minLocalY) / (float) textureHeight;
                cell.u1 = (offsetX + maxLocalX + 1) / (float) textureWidth;
                cell.v1 = (offsetY + maxLocalY + 1) / (float) textureHeight;
                cell.avgEmission = weightedEmissionSum / boundsAreaPixels;
                cell.avgR = weightedRadianceR / weightedEmissionSum;
                cell.avgG = weightedRadianceG / weightedEmissionSum;
                cell.avgB = weightedRadianceB / weightedEmissionSum;
                grid[gridY][gridX] = cell;
            }
        }

        return mergeGrid(grid);
    }

    private static List<EmissionCell> mergeGrid(EmissionCell[][] grid) {
        int minX = grid[0].length;
        int minY = grid.length;
        int maxX = -1;
        int maxY = -1;
        int occupied = 0;

        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[y].length; x++) {
                if (grid[y][x] == null) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                occupied++;
            }
        }

        if (occupied == 0) {
            return new ArrayList<>();
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        if (occupied >= width * height * DENSE_RECT_COVERAGE) {
            List<EmissionCell> mergedCells = new ArrayList<>(1);
            mergedCells.add(mergeRect(grid, minX, minY, width, height));
            return mergedCells;
        }

        return mergeGreedy(grid);
    }

    private static List<EmissionCell> mergeGreedy(EmissionCell[][] grid) {
        int gridHeight = grid.length;
        if (gridHeight == 0) {
            return new ArrayList<>();
        }

        int gridWidth = grid[0].length;
        boolean[][] used = new boolean[gridHeight][gridWidth];
        List<EmissionCell> mergedCells = new ArrayList<>();

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (!canMerge(grid, used, x, y)) {
                    continue;
                }

                MergeRect rect = findLargestMergeRect(grid, used, x, y);
                mergedCells.add(mergeRect(grid, x, y, rect.width, rect.height));
                markMerged(used, x, y, rect.width, rect.height);
            }
        }

        return mergedCells;
    }

    private static MergeRect findLargestMergeRect(EmissionCell[][] grid, boolean[][] used,
        int startX, int startY) {
        int gridHeight = grid.length;
        int gridWidth = grid[0].length;
        int currentWidth = 0;
        while (startX + currentWidth < gridWidth
            && canMerge(grid, used, startX + currentWidth, startY)) {
            currentWidth++;
        }

        int bestWidth = currentWidth;
        int bestHeight = 1;
        int bestArea = currentWidth;

        for (int height = 2; startY + height - 1 < gridHeight && currentWidth > 0; height++) {
            int rowY = startY + height - 1;
            int rowWidth = 0;
            while (rowWidth < currentWidth && startX + rowWidth < gridWidth
                && canMerge(grid, used, startX + rowWidth, rowY)) {
                rowWidth++;
            }
            if (rowWidth == 0) {
                break;
            }

            currentWidth = rowWidth;
            int area = currentWidth * height;
            if (area > bestArea) {
                bestWidth = currentWidth;
                bestHeight = height;
                bestArea = area;
            }
        }

        return new MergeRect(bestWidth, bestHeight);
    }

    private static boolean canMerge(EmissionCell[][] grid, boolean[][] used, int x, int y) {
        return grid[y][x] != null && !used[y][x];
    }

    private static EmissionCell mergeRect(EmissionCell[][] grid, int startX, int startY,
        int width, int height) {
        float u0 = Float.POSITIVE_INFINITY;
        float v0 = Float.POSITIVE_INFINITY;
        float u1 = Float.NEGATIVE_INFINITY;
        float v1 = Float.NEGATIVE_INFINITY;
        float emissionArea = 0.0f;
        float weightedRadianceR = 0.0f;
        float weightedRadianceG = 0.0f;
        float weightedRadianceB = 0.0f;

        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                EmissionCell cell = grid[y][x];
                if (cell == null) {
                    continue;
                }
                float area = Math.max(0.0f, (cell.u1 - cell.u0) * (cell.v1 - cell.v0));
                float cellEmissionArea = cell.avgEmission * area;

                u0 = Math.min(u0, cell.u0);
                v0 = Math.min(v0, cell.v0);
                u1 = Math.max(u1, cell.u1);
                v1 = Math.max(v1, cell.v1);
                emissionArea += cellEmissionArea;
                weightedRadianceR += cell.avgR * cellEmissionArea;
                weightedRadianceG += cell.avgG * cellEmissionArea;
                weightedRadianceB += cell.avgB * cellEmissionArea;
            }
        }

        EmissionCell merged = new EmissionCell();
        merged.u0 = u0;
        merged.v0 = v0;
        merged.u1 = u1;
        merged.v1 = v1;
        float mergedArea = Math.max(0.0f, (u1 - u0) * (v1 - v0));
        merged.avgEmission = mergedArea > 0.0f ? emissionArea / mergedArea : 0.0f;
        if (emissionArea > 0.0f) {
            merged.avgR = weightedRadianceR / emissionArea;
            merged.avgG = weightedRadianceG / emissionArea;
            merged.avgB = weightedRadianceB / emissionArea;
        }
        return merged;
    }

    private static void markMerged(boolean[][] used, int startX, int startY, int width,
        int height) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                used[y][x] = true;
            }
        }
    }

    private record MergeRect(int width, int height) {}

    private static float decodeLabPbrEmission(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 255) {
            return 0.0f;
        }
        return alpha / 254.0f;
    }

    private static float decodeAlphaCoverage(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }

    public static final class TileUpdate {

        public final int textureId;
        public final long tileKey;
        public final List<EmissionCell> cells;

        public TileUpdate(int textureId, long tileKey, List<EmissionCell> cells) {
            this.textureId = textureId;
            this.tileKey = tileKey;
            this.cells = cells;
        }
    }

    public static final class EmissionCell {

        public float u0;
        public float v0;
        public float u1;
        public float v1;
        public float avgEmission;
        public float avgR;
        public float avgG;
        public float avgB;
    }
}
