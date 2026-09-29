package com.radiance.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class CloudRenderer {

    private static final ResourceLocation CLOUDS_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
        "textures/environment/clouds.png");

    private CloudCells cloudCells;
    private StorageVertexConsumerProvider storageVertexConsumerProvider;
    private EntityProxy.EntityRenderDataList renderDataList;
    private int centerX = Integer.MIN_VALUE;
    private int centerZ = Integer.MIN_VALUE;
    private CloudViewMode viewMode = CloudViewMode.INSIDE_CLOUDS;
    private CloudStatus renderMode;
    private Vec3 color = Vec3.ZERO;

    public synchronized void invalidate() {
        this.closeCache();
        this.cloudCells = null;
    }

    public void renderClouds(ClientLevel world, Camera camera, int ticks, float tickDelta) {
        CloudCells cloudCells = this.getCloudCells();
        if (cloudCells == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        CloudStatus renderMode = client.options.getCloudsType();
        if (renderMode == CloudStatus.OFF) {
            return;
        }

        float cloudHeight = world.effects()
            .getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        float cloudOffsetY = (float) (cloudHeight - cameraPos.y);
        float cloudOffsetTop = cloudOffsetY + 4.0F;
        CloudViewMode viewMode;
        if (cloudOffsetTop < 0.0F) {
            viewMode = CloudViewMode.ABOVE_CLOUDS;
        } else if (cloudOffsetY > 0.0F) {
            viewMode = CloudViewMode.BELOW_CLOUDS;
        } else {
            viewMode = CloudViewMode.INSIDE_CLOUDS;
        }

        double x = cameraPos.x + (ticks + tickDelta) * 0.030000001F;
        double z = cameraPos.z + 3.96F;
        double width = cloudCells.width() * 12.0;
        double height = cloudCells.height() * 12.0;
        x -= Mth.floor(x / width) * width;
        z -= Mth.floor(z / height) * height;
        int centerX = Mth.floor(x / 12.0);
        int centerZ = Mth.floor(z / 12.0);
        float offsetX = (float) (x - centerX * 12.0F);
        float offsetZ = (float) (z - centerZ * 12.0F);

        Vec3 cloudsColor = world.getCloudColor(tickDelta);
        float red = (float) cloudsColor.x;
        float green = (float) cloudsColor.y;
        float blue = (float) cloudsColor.z;
        int topColor = argbFromFloats(0.8F, red, green, blue);
        int bottomColor = argbFromFloats(0.8F, red * 0.7F, green * 0.7F, blue * 0.7F);
        int northSouthColor = argbFromFloats(0.8F, red * 0.9F, green * 0.9F, blue * 0.9F);
        int eastWestColor = argbFromFloats(0.8F, red * 0.8F, green * 0.8F, blue * 0.8F);

        if (this.shouldRebuild(centerX, centerZ, viewMode, renderMode, cloudsColor)) {
            this.closeCache();

            this.storageVertexConsumerProvider = new StorageVertexConsumerProvider(0);
            this.renderDataList = new EntityProxy.EntityRenderDataList();

            VertexConsumer builder = this.storageVertexConsumerProvider.getBuffer(
                RenderLayers.createFastCloudsLayer(CLOUDS_TEXTURE));
            buildCloudCells(viewMode, builder, cloudCells, centerX, centerZ, bottomColor,
                topColor, northSouthColor, eastWestColor, renderMode == CloudStatus.FANCY);

            EntityProxy.processWorldEntityRenderData(this.storageVertexConsumerProvider,
                System.identityHashCode("clouds"), cameraPos.x - offsetX, cloudHeight,
                cameraPos.z - offsetZ, Constants.RayTracingFlags.CLOUD, false,
                this.renderDataList);

            this.centerX = centerX;
            this.centerZ = centerZ;
            this.viewMode = viewMode;
            this.renderMode = renderMode;
            this.color = cloudsColor;
        } else {
            for (EntityProxy.EntityRenderData data : this.renderDataList) {
                data.setX(cameraPos.x - offsetX);
                data.setY(cloudHeight);
                data.setZ(cameraPos.z - offsetZ);
            }
        }

        if (this.renderDataList != null && !this.renderDataList.isEmpty()) {
            EntityProxy.queueBuildWithoutClose(this.renderDataList, 0.0f,
                Constants.Coordinates.WORLD, false);
        }
    }

    private synchronized CloudCells getCloudCells() {
        if (this.cloudCells != null) {
            return this.cloudCells;
        }

        try (InputStream inputStream = Minecraft.getInstance()
            .getResourceManager()
            .open(CLOUDS_TEXTURE); NativeImage nativeImage = NativeImage.read(inputStream)) {
            int width = nativeImage.getWidth();
            int height = nativeImage.getHeight();
            long[] cells = new long[width * height];

            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    int color = argbToAbgr(nativeImage.getPixelRGBA(x, z));
                    if (isEmptyCell(color)) {
                        cells[x + z * width] = 0L;
                        continue;
                    }

                    boolean borderNorth = isEmptyCell(
                        argbToAbgr(nativeImage.getPixelRGBA(x, Math.floorMod(z - 1, height))));
                    boolean borderEast = isEmptyCell(
                        argbToAbgr(nativeImage.getPixelRGBA(Math.floorMod(x + 1, width), z)));
                    boolean borderSouth = isEmptyCell(
                        argbToAbgr(nativeImage.getPixelRGBA(x, Math.floorMod(z + 1, height))));
                    boolean borderWest = isEmptyCell(
                        argbToAbgr(nativeImage.getPixelRGBA(Math.floorMod(x - 1, width), z)));
                    cells[x + z * width] = packCell(color, borderNorth, borderEast, borderSouth,
                        borderWest);
                }
            }

            this.cloudCells = new CloudCells(cells, width, height);
            return this.cloudCells;
        } catch (IOException e) {
            return null;
        }
    }

    private void closeCache() {
        if (this.renderDataList != null) {
            for (EntityProxy.EntityRenderData entityRenderData : this.renderDataList) {
                for (EntityProxy.EntityRenderLayer entityRenderLayer : entityRenderData) {
                    entityRenderLayer.builtBuffer()
                        .close();
                }
            }
            this.renderDataList.clear();
            this.renderDataList = null;
        }

        if (this.storageVertexConsumerProvider != null) {
            this.storageVertexConsumerProvider.close();
            this.storageVertexConsumerProvider = null;
        }

        this.centerX = Integer.MIN_VALUE;
        this.centerZ = Integer.MIN_VALUE;
        this.viewMode = CloudViewMode.INSIDE_CLOUDS;
        this.renderMode = null;
        this.color = Vec3.ZERO;
    }

    private boolean shouldRebuild(int centerX, int centerZ, CloudViewMode viewMode,
        CloudStatus renderMode, Vec3 color) {
        return this.storageVertexConsumerProvider == null
            || this.renderDataList == null
            || centerX != this.centerX
            || centerZ != this.centerZ
            || viewMode != this.viewMode
            || renderMode != this.renderMode
            || this.color.distanceToSqr(color) > 2.0E-4;
    }

    private static boolean isEmptyCell(int color) {
        return FastColor.ARGB32.alpha(color) < 10;
    }

    private static int argbToAbgr(int color) {
        return FastColor.ABGR32.color(FastColor.ARGB32.alpha(color),
            FastColor.ARGB32.blue(color), FastColor.ARGB32.green(color),
            FastColor.ARGB32.red(color));
    }

    private static int argbFromFloats(float alpha, float red, float green, float blue) {
        return FastColor.ARGB32.color((int) (alpha * 255.0F), (int) (red * 255.0F),
            (int) (green * 255.0F), (int) (blue * 255.0F));
    }

    private static long packCell(int color, boolean borderNorth, boolean borderEast,
        boolean borderSouth, boolean borderWest) {
        return (long) color << 4 | (borderNorth ? 1L : 0L) << 3 | (borderEast ? 1L : 0L) << 2
            | (borderSouth ? 1L : 0L) << 1 | (borderWest ? 1L : 0L);
    }

    private static int unpackCellColor(long packed) {
        return (int) (packed >> 4 & 4294967295L);
    }

    private static boolean hasBorderNorth(long packed) {
        return (packed >> 3 & 1L) != 0L;
    }

    private static boolean hasBorderEast(long packed) {
        return (packed >> 2 & 1L) != 0L;
    }

    private static boolean hasBorderSouth(long packed) {
        return (packed >> 1 & 1L) != 0L;
    }

    private static boolean hasBorderWest(long packed) {
        return (packed & 1L) != 0L;
    }

    private static void vertex(VertexConsumer builder, float x, float y, float z, float u,
        float v, float normalX, float normalY, float normalZ, int color) {
        builder.vertex(x, y, z)
            .uv(u, v)
            .normal(normalX, normalY, normalZ)
            .color(color);
    }

    private static void buildFastCell(VertexConsumer builder, int color, int x, int z,
        int cellX, int cellZ, int width, int height) {
        float minX = x * 12.0F;
        float maxX = minX + 12.0F;
        float minZ = z * 12.0F;
        float maxZ = minZ + 12.0F;
        float minU = (float) cellX / width;
        float maxU = (float) (cellX + 1) / width;
        float minV = (float) cellZ / height;
        float maxV = (float) (cellZ + 1) / height;
        vertex(builder, minX, 0.0F, minZ, minU, minV, 0.0F, 1.0F, 0.0F, color);
        vertex(builder, minX, 0.0F, maxZ, minU, maxV, 0.0F, 1.0F, 0.0F, color);
        vertex(builder, maxX, 0.0F, maxZ, maxU, maxV, 0.0F, 1.0F, 0.0F, color);
        vertex(builder, maxX, 0.0F, minZ, maxU, minV, 0.0F, 1.0F, 0.0F, color);
    }

    private static void buildFancyCell(CloudViewMode viewMode, VertexConsumer builder,
        int bottomColor, int topColor, int northSouthColor, int eastWestColor, int x, int z,
        int cellX, int cellZ, int width, int height, long cell) {
        float minX = x * 12.0F;
        float maxX = minX + 12.0F;
        float minZ = z * 12.0F;
        float maxZ = minZ + 12.0F;
        float minU = (float) cellX / width;
        float maxU = (float) (cellX + 1) / width;
        float minV = (float) cellZ / height;
        float maxV = (float) (cellZ + 1) / height;
        float centerU = (float) (cellX + 0.5F) / width;
        float centerV = (float) (cellZ + 0.5F) / height;

        if (viewMode != CloudViewMode.BELOW_CLOUDS) {
            vertex(builder, minX, 4.0F, minZ, minU, minV, 0.0F, 1.0F, 0.0F, topColor);
            vertex(builder, minX, 4.0F, maxZ, minU, maxV, 0.0F, 1.0F, 0.0F, topColor);
            vertex(builder, maxX, 4.0F, maxZ, maxU, maxV, 0.0F, 1.0F, 0.0F, topColor);
            vertex(builder, maxX, 4.0F, minZ, maxU, minV, 0.0F, 1.0F, 0.0F, topColor);
        }

        if (viewMode != CloudViewMode.ABOVE_CLOUDS) {
            vertex(builder, maxX, 0.0F, minZ, maxU, minV, 0.0F, -1.0F, 0.0F, bottomColor);
            vertex(builder, maxX, 0.0F, maxZ, maxU, maxV, 0.0F, -1.0F, 0.0F, bottomColor);
            vertex(builder, minX, 0.0F, maxZ, minU, maxV, 0.0F, -1.0F, 0.0F, bottomColor);
            vertex(builder, minX, 0.0F, minZ, minU, minV, 0.0F, -1.0F, 0.0F, bottomColor);
        }

        if (hasBorderNorth(cell) && z > 0) {
            vertex(builder, minX, 0.0F, minZ, minU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
            vertex(builder, minX, 4.0F, minZ, minU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
            vertex(builder, maxX, 4.0F, minZ, maxU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
            vertex(builder, maxX, 0.0F, minZ, maxU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
        }

        if (hasBorderSouth(cell) && z < 0) {
            vertex(builder, maxX, 0.0F, maxZ, maxU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
            vertex(builder, maxX, 4.0F, maxZ, maxU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
            vertex(builder, minX, 4.0F, maxZ, minU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
            vertex(builder, minX, 0.0F, maxZ, minU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
        }

        if (hasBorderWest(cell) && x > 0) {
            vertex(builder, minX, 0.0F, maxZ, centerU, maxV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, minX, 4.0F, maxZ, centerU, maxV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, minX, 4.0F, minZ, centerU, minV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, minX, 0.0F, minZ, centerU, minV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
        }

        if (hasBorderEast(cell) && x < 0) {
            vertex(builder, maxX, 0.0F, minZ, centerU, minV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, maxX, 4.0F, minZ, centerU, minV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, maxX, 4.0F, maxZ, centerU, maxV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, maxX, 0.0F, maxZ, centerU, maxV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
        }

        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            vertex(builder, maxX, 4.0F, minZ, maxU, minV, 0.0F, 1.0F, 0.0F, topColor);
            vertex(builder, maxX, 4.0F, maxZ, maxU, maxV, 0.0F, 1.0F, 0.0F, topColor);
            vertex(builder, minX, 4.0F, maxZ, minU, maxV, 0.0F, 1.0F, 0.0F, topColor);
            vertex(builder, minX, 4.0F, minZ, minU, minV, 0.0F, 1.0F, 0.0F, topColor);

            vertex(builder, minX, 0.0F, minZ, minU, minV, 0.0F, 1.0F, 0.0F, bottomColor);
            vertex(builder, minX, 0.0F, maxZ, minU, maxV, 0.0F, 1.0F, 0.0F, bottomColor);
            vertex(builder, maxX, 0.0F, maxZ, maxU, maxV, 0.0F, 1.0F, 0.0F, bottomColor);
            vertex(builder, maxX, 0.0F, minZ, maxU, minV, 0.0F, 1.0F, 0.0F, bottomColor);

            vertex(builder, maxX, 0.0F, minZ, maxU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
            vertex(builder, maxX, 4.0F, minZ, maxU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
            vertex(builder, minX, 4.0F, minZ, minU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);
            vertex(builder, minX, 0.0F, minZ, minU, centerV, 0.0F, 0.0F, 1.0F,
                eastWestColor);

            vertex(builder, minX, 0.0F, maxZ, minU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
            vertex(builder, minX, 4.0F, maxZ, minU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
            vertex(builder, maxX, 4.0F, maxZ, maxU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);
            vertex(builder, maxX, 0.0F, maxZ, maxU, centerV, 0.0F, 0.0F, -1.0F,
                eastWestColor);

            vertex(builder, minX, 0.0F, minZ, centerU, minV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, minX, 4.0F, minZ, centerU, minV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, minX, 4.0F, maxZ, centerU, maxV, 1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, minX, 0.0F, maxZ, centerU, maxV, 1.0F, 0.0F, 0.0F,
                northSouthColor);

            vertex(builder, maxX, 0.0F, maxZ, centerU, maxV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, maxX, 4.0F, maxZ, centerU, maxV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, maxX, 4.0F, minZ, centerU, minV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
            vertex(builder, maxX, 0.0F, minZ, centerU, minV, -1.0F, 0.0F, 0.0F,
                northSouthColor);
        }
    }

    private static void buildCloudCells(CloudViewMode viewMode, VertexConsumer builder,
        CloudCells cloudCells, int x, int z, int bottomColor, int topColor, int northSouthColor,
        int eastWestColor, boolean fancy) {
        long[] cells = cloudCells.cells();
        int width = cloudCells.width();
        int height = cloudCells.height();

        for (int dz = -32; dz <= 32; dz++) {
            for (int dx = -32; dx <= 32; dx++) {
                int cellX = Math.floorMod(x + dx, width);
                int cellZ = Math.floorMod(z + dz, height);
                long cell = cells[cellX + cellZ * width];
                if (cell == 0L) {
                    continue;
                }

                int color = unpackCellColor(cell);
                if (fancy) {
                    buildFancyCell(viewMode, builder,
                        FastColor.ARGB32.multiply(bottomColor, color),
                        FastColor.ARGB32.multiply(topColor, color),
                        FastColor.ARGB32.multiply(northSouthColor, color),
                        FastColor.ARGB32.multiply(eastWestColor, color),
                        dx, dz, cellX, cellZ, width, height, cell);
                } else {
                    buildFastCell(builder, FastColor.ARGB32.multiply(topColor, color), dx, dz,
                        cellX, cellZ, width, height);
                }
            }
        }
    }

    private enum CloudViewMode {
        ABOVE_CLOUDS,
        BELOW_CLOUDS,
        INSIDE_CLOUDS
    }

    private record CloudCells(long[] cells, int width, int height) {
    }
}
