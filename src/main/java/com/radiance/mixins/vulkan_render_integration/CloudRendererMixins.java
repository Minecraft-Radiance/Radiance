package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.UnsafeManager;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CloudRendererMixins {

    @Shadow
    private boolean needsRebuild;

    @Shadow
    private int prevCellX;

    @Shadow
    private int prevCellZ;

    @Shadow
    private CloudRenderer.RelativeCameraPos prevRelativeCameraPos;

    @Shadow
    private CloudStatus prevType;

    @Shadow
    private CloudRenderer.TextureData texture;

    @Shadow
    private boolean vertexBufferEmpty;

    @Unique
    private StorageVertexConsumerProvider storageVertexConsumerProvider = null;

    @Unique
    private EntityProxy.EntityRenderDataList entityRenderDataList = null;

    @Unique
    private static int unpackColor(long packed) {
        return (int) (packed >> 4 & 4294967295L);
    }

    @Unique
    private static boolean hasBorderNorth(long packed) {
        return (packed >> 3 & 1L) != 0L;
    }

    @Unique
    private static boolean hasBorderEast(long packed) {
        return (packed >> 2 & 1L) != 0L;
    }

    @Unique
    private static boolean hasBorderSouth(long packed) {
        return (packed >> 1 & 1L) != 0L;
    }

    @Unique
    private static boolean hasBorderWest(long packed) {
        return (packed >> 0 & 1L) != 0L;
    }

    @Redirect(method = "Lnet/minecraft/client/renderer/CloudRenderer;<init>()V", at = @At(value = "NEW", target = "com/mojang/blaze3d/vertex/VertexBuffer"))
    private VertexBuffer cancelBufferInit(BufferUsage usage) {
        return UnsafeManager.INSTANCE.allocateInstance(VertexBuffer.class);
    }

    @Inject(method =
        "render(ILnet/minecraft/client/CloudStatus;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
            +
            "Lnet/minecraft/world/phys/Vec3;F)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectCloudRendering(int color,
        CloudStatus cloudRenderMode,
        float cloudHeight,
        Matrix4f positionMatrix,
        Matrix4f projectionMatrix,
        Vec3 cameraPos,
        float ticks,
        CallbackInfo ci) {
        if (this.texture != null) {
            float f = (float) (cloudHeight - cameraPos.y);
            float g = f + 4.0F;
            CloudRenderer.RelativeCameraPos prevRelativeCameraPos;
            if (g < 0.0F) {
                prevRelativeCameraPos = CloudRenderer.RelativeCameraPos.ABOVE_CLOUDS;
            } else if (f > 0.0F) {
                prevRelativeCameraPos = CloudRenderer.RelativeCameraPos.BELOW_CLOUDS;
            } else {
                prevRelativeCameraPos = CloudRenderer.RelativeCameraPos.INSIDE_CLOUDS;
            }

            double d = cameraPos.x + ticks * 0.030000001F;
            double e = cameraPos.z + 3.96F;
            double h = this.texture.width() * 12.0;
            double i = this.texture.height() * 12.0;
            d -= Mth.floor(d / h) * h;
            e -= Mth.floor(e / i) * i;
            int j = Mth.floor(d / 12.0);
            int k = Mth.floor(e / 12.0);
            float l = (float) (d - j * 12.0F);
            float m = (float) (e - k * 12.0F);
            RenderType
                renderLayer =
                cloudRenderMode == CloudStatus.FANCY ? RenderType.clouds()
                    : RenderType.flatClouds();

            if (this.needsRebuild || j != this.prevCellX || k != this.prevCellZ
                || prevRelativeCameraPos != this.prevRelativeCameraPos ||
                cloudRenderMode != this.prevType) {
                this.needsRebuild = false;
                this.prevCellX = j;
                this.prevCellZ = k;
                this.prevRelativeCameraPos = prevRelativeCameraPos;
                this.prevType = cloudRenderMode;

                this.tessellateClouds(color, j, k, cloudRenderMode, prevRelativeCameraPos, renderLayer);
            }

            if (storageVertexConsumerProvider != null) {
                for (EntityProxy.EntityRenderData data : entityRenderDataList) {
                    data.setX((float) (cameraPos.x - l));
                    data.setY(cloudHeight);
                    data.setZ((float) (cameraPos.z - m));
                }

                EntityProxy.queueBuildWithoutClose(entityRenderDataList);
            }

        }

        ci.cancel();
    }

    @Unique
    private void tessellateClouds(int color, int x, int z, CloudStatus prevType,
        CloudRenderer.RelativeCameraPos prevRelativeCameraPos, RenderType layer) {
        float red = ARGB.redFloat(color);
        float green = ARGB.greenFloat(color);
        float blue = ARGB.blueFloat(color);
        int i = ARGB.colorFromFloat(0.8F, red, green, blue);
        int j = ARGB.colorFromFloat(0.8F, 0.9F * red, 0.9F * green, 0.9F * blue);
        int k = ARGB.colorFromFloat(0.8F, 0.7F * red, 0.7F * green, 0.7F * blue);
        int l = ARGB.colorFromFloat(0.8F, 0.8F * red, 0.8F * green, 0.8F * blue);

        if (storageVertexConsumerProvider != null) {
            for (EntityProxy.EntityRenderData entityRenderData : entityRenderDataList) {
                for (EntityProxy.EntityRenderLayer entityRenderLayer : entityRenderData) {
                    MeshData vertexBuffer = entityRenderLayer.builtBuffer();
                    vertexBuffer.close();
                }
            }

            storageVertexConsumerProvider.close();
        }

        storageVertexConsumerProvider = new StorageVertexConsumerProvider(0);
        entityRenderDataList = new EntityProxy.EntityRenderDataList();

        VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(layer);
        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            this.buildCloudCells(prevRelativeCameraPos, pbrVertexConsumer, x, z, k, i, j, l,
                prevType == CloudStatus.FANCY);
        } else {
            throw new RuntimeException("CloudRenderer only supports PBRVertexConsumer");
        }

        EntityProxy.processWorldEntityRenderData(storageVertexConsumerProvider,
            System.identityHashCode("clouds"),
            0,
            0,
            0,
            Constants.RayTracingFlags.CLOUD,
            false,
            entityRenderDataList);
    }

    @Unique
    private void buildCloudCells(CloudRenderer.RelativeCameraPos prevRelativeCameraPos,
        VertexConsumer builder,
        int x,
        int z,
        int bottomColor,
        int topColor,
        int northSouthColor,
        int eastWestColor,
        boolean fancy) {
        if (this.texture != null) {
            int i = 32;
            long[] ls = this.texture.cells();
            int j = this.texture.width();
            int k = this.texture.height();

            for (int l = -32; l <= 32; l++) {
                for (int m = -32; m <= 32; m++) {
                    int n = Math.floorMod(x + m, j);
                    int o = Math.floorMod(z + l, k);
                    long p = ls[n + o * j];
                    if (p != 0L) {
                        int q = unpackColor(p);
                        if (fancy) {
                            this.buildCloudCellFancy(prevRelativeCameraPos,
                                builder,
                                ARGB.multiply(bottomColor, q),
                                ARGB.multiply(topColor, q),
                                ARGB.multiply(northSouthColor, q),
                                ARGB.multiply(eastWestColor, q),
                                m,
                                l,
                                p);
                        } else {
                            this.buildCloudCellFast(builder, ARGB.multiply(topColor, q), m, l);
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void buildCloudCellFast(VertexConsumer builder, int color, int x, int z) {
        float f = x * 12.0F;
        float g = f + 12.0F;
        float h = z * 12.0F;
        float i = h + 12.0F;

        builder.addVertex(f, 0.0F, h)
            .setNormal(0.0F, 1.0F, 0.0F)
            .setColor(color);
        builder.addVertex(f, 0.0F, i)
            .setNormal(0.0F, 1.0F, 0.0F)
            .setColor(color);
        builder.addVertex(g, 0.0F, i)
            .setNormal(0.0F, 1.0F, 0.0F)
            .setColor(color);
        builder.addVertex(g, 0.0F, h)
            .setNormal(0.0F, 1.0F, 0.0F)
            .setColor(color);
    }

    @Unique
    private void buildCloudCellFancy(CloudRenderer.RelativeCameraPos prevRelativeCameraPos,
        VertexConsumer builder,
        int bottomColor,
        int topColor,
        int northSouthColor,
        int eastWestColor,
        int x,
        int z,
        long cell) {
        float f = x * 12.0F;
        float g = f + 12.0F;
        float h = 0.0F;
        float i = 4.0F;
        float j = z * 12.0F;
        float k = j + 12.0F;

        if (prevRelativeCameraPos != CloudRenderer.RelativeCameraPos.BELOW_CLOUDS) {
            builder.addVertex(f, 4.0F, j)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
            builder.addVertex(f, 4.0F, k)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
            builder.addVertex(g, 4.0F, k)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
            builder.addVertex(g, 4.0F, j)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
        }

        if (prevRelativeCameraPos != CloudRenderer.RelativeCameraPos.ABOVE_CLOUDS) {
            builder.addVertex(g, 0.0F, j)
                .setNormal(0.0F, -1.0F, 0.0F)
                .setColor(bottomColor);
            builder.addVertex(g, 0.0F, k)
                .setNormal(0.0F, -1.0F, 0.0F)
                .setColor(bottomColor);
            builder.addVertex(f, 0.0F, k)
                .setNormal(0.0F, -1.0F, 0.0F)
                .setColor(bottomColor);
            builder.addVertex(f, 0.0F, j)
                .setNormal(0.0F, -1.0F, 0.0F)
                .setColor(bottomColor);
        }

        if (hasBorderNorth(cell) && z > 0) {
            builder.addVertex(f, 0.0F, j)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
            builder.addVertex(f, 4.0F, j)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
            builder.addVertex(g, 4.0F, j)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
            builder.addVertex(g, 0.0F, j)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
        }

        if (hasBorderSouth(cell) && z < 0) {
            builder.addVertex(g, 0.0F, k)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
            builder.addVertex(g, 4.0F, k)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
            builder.addVertex(f, 4.0F, k)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
            builder.addVertex(f, 0.0F, k)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
        }

        if (hasBorderWest(cell) && x > 0) {
            builder.addVertex(f, 0.0F, k)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(f, 4.0F, k)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(f, 4.0F, j)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(f, 0.0F, j)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
        }

        if (hasBorderEast(cell) && x < 0) {
            builder.addVertex(g, 0.0F, j)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(g, 4.0F, j)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(g, 4.0F, k)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(g, 0.0F, k)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
        }

        boolean bl = Math.abs(x) <= 1 && Math.abs(z) <= 1;
        if (bl) {
            builder.addVertex(g, 4.0F, j)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
            builder.addVertex(g, 4.0F, k)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
            builder.addVertex(f, 4.0F, k)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);
            builder.addVertex(f, 4.0F, j)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(topColor);

            builder.addVertex(f, 0.0F, j)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(bottomColor);
            builder.addVertex(f, 0.0F, k)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(bottomColor);
            builder.addVertex(g, 0.0F, k)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(bottomColor);
            builder.addVertex(g, 0.0F, j)
                .setNormal(0.0F, 1.0F, 0.0F)
                .setColor(bottomColor);

            builder.addVertex(g, 0.0F, j)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
            builder.addVertex(g, 4.0F, j)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
            builder.addVertex(f, 4.0F, j)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);
            builder.addVertex(f, 0.0F, j)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setColor(eastWestColor);

            builder.addVertex(f, 0.0F, k)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
            builder.addVertex(f, 4.0F, k)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
            builder.addVertex(g, 4.0F, k)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);
            builder.addVertex(g, 0.0F, k)
                .setNormal(0.0F, 0.0F, -1.0F)
                .setColor(eastWestColor);

            builder.addVertex(f, 0.0F, j)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(f, 4.0F, j)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(f, 4.0F, k)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(f, 0.0F, k)
                .setNormal(1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);

            builder.addVertex(g, 0.0F, k)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(g, 4.0F, k)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(g, 4.0F, j)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
            builder.addVertex(g, 0.0F, j)
                .setNormal(-1.0F, 0.0F, 0.0F)
                .setColor(northSouthColor);
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/CloudRenderer;close()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelBufferClose(CallbackInfo ci) {
        ci.cancel();
    }
}
