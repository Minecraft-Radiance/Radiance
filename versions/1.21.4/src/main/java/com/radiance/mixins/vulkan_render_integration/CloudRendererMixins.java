package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.UnsafeManager;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
    private boolean field_53052;

    @Shadow
    private int centerX;

    @Shadow
    private int centerZ;

    @Shadow
    private CloudRenderer.ViewMode viewMode;

    @Shadow
    private CloudRenderMode renderMode;

    @Shadow
    private CloudRenderer.CloudCells cells;

    @Shadow
    private boolean renderClouds;

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

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/gl/VertexBuffer"))
    private VertexBuffer cancelBufferInit(GlUsage usage) {
        return UnsafeManager.INSTANCE.allocateInstance(VertexBuffer.class);
    }

    @Inject(method =
        "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
            +
            "Lnet/minecraft/util/math/Vec3d;F)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectCloudRendering(int color,
        CloudRenderMode cloudRenderMode,
        float cloudHeight,
        Matrix4f positionMatrix,
        Matrix4f projectionMatrix,
        Vec3d cameraPos,
        float ticks,
        CallbackInfo ci) {
        if (this.cells != null) {
            float f = (float) (cloudHeight - cameraPos.y);
            float g = f + 4.0F;
            CloudRenderer.ViewMode viewMode;
            if (g < 0.0F) {
                viewMode = CloudRenderer.ViewMode.ABOVE_CLOUDS;
            } else if (f > 0.0F) {
                viewMode = CloudRenderer.ViewMode.BELOW_CLOUDS;
            } else {
                viewMode = CloudRenderer.ViewMode.INSIDE_CLOUDS;
            }

            double d = cameraPos.x + ticks * 0.030000001F;
            double e = cameraPos.z + 3.96F;
            double h = this.cells.width() * 12.0;
            double i = this.cells.height() * 12.0;
            d -= MathHelper.floor(d / h) * h;
            e -= MathHelper.floor(e / i) * i;
            int j = MathHelper.floor(d / 12.0);
            int k = MathHelper.floor(e / 12.0);
            float l = (float) (d - j * 12.0F);
            float m = (float) (e - k * 12.0F);
            RenderLayer
                renderLayer =
                cloudRenderMode == CloudRenderMode.FANCY ? RenderLayer.getFastClouds()
                    : RenderLayer.getNoCullingClouds();

            if (this.field_53052 || j != this.centerX || k != this.centerZ
                || viewMode != this.viewMode ||
                cloudRenderMode != this.renderMode) {
                this.field_53052 = false;
                this.centerX = j;
                this.centerZ = k;
                this.viewMode = viewMode;
                this.renderMode = cloudRenderMode;

                this.tessellateClouds(color, j, k, cloudRenderMode, viewMode, renderLayer);
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
    private void tessellateClouds(int color, int x, int z, CloudRenderMode renderMode,
        CloudRenderer.ViewMode viewMode, RenderLayer layer) {
        float red = ColorHelper.getRedFloat(color);
        float green = ColorHelper.getGreenFloat(color);
        float blue = ColorHelper.getBlueFloat(color);
        int i = ColorHelper.fromFloats(0.8F, red, green, blue);
        int j = ColorHelper.fromFloats(0.8F, 0.9F * red, 0.9F * green, 0.9F * blue);
        int k = ColorHelper.fromFloats(0.8F, 0.7F * red, 0.7F * green, 0.7F * blue);
        int l = ColorHelper.fromFloats(0.8F, 0.8F * red, 0.8F * green, 0.8F * blue);

        if (storageVertexConsumerProvider != null) {
            for (EntityProxy.EntityRenderData entityRenderData : entityRenderDataList) {
                for (EntityProxy.EntityRenderLayer entityRenderLayer : entityRenderData) {
                    BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer();
                    vertexBuffer.close();
                }
            }

            storageVertexConsumerProvider.close();
        }

        storageVertexConsumerProvider = new StorageVertexConsumerProvider(0);
        entityRenderDataList = new EntityProxy.EntityRenderDataList();

        VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(layer);
        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            this.buildCloudCells(viewMode, pbrVertexConsumer, x, z, k, i, j, l,
                renderMode == CloudRenderMode.FANCY);
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
    private void buildCloudCells(CloudRenderer.ViewMode viewMode,
        VertexConsumer builder,
        int x,
        int z,
        int bottomColor,
        int topColor,
        int northSouthColor,
        int eastWestColor,
        boolean fancy) {
        if (this.cells != null) {
            int i = 32;
            long[] ls = this.cells.cells();
            int j = this.cells.width();
            int k = this.cells.height();

            for (int l = -32; l <= 32; l++) {
                for (int m = -32; m <= 32; m++) {
                    int n = Math.floorMod(x + m, j);
                    int o = Math.floorMod(z + l, k);
                    long p = ls[n + o * j];
                    if (p != 0L) {
                        int q = unpackColor(p);
                        if (fancy) {
                            this.buildCloudCellFancy(viewMode,
                                builder,
                                ColorHelper.mix(bottomColor, q),
                                ColorHelper.mix(topColor, q),
                                ColorHelper.mix(northSouthColor, q),
                                ColorHelper.mix(eastWestColor, q),
                                m,
                                l,
                                p);
                        } else {
                            this.buildCloudCellFast(builder, ColorHelper.mix(topColor, q), m, l);
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

        builder.vertex(f, 0.0F, h)
            .normal(0.0F, 1.0F, 0.0F)
            .color(color);
        builder.vertex(f, 0.0F, i)
            .normal(0.0F, 1.0F, 0.0F)
            .color(color);
        builder.vertex(g, 0.0F, i)
            .normal(0.0F, 1.0F, 0.0F)
            .color(color);
        builder.vertex(g, 0.0F, h)
            .normal(0.0F, 1.0F, 0.0F)
            .color(color);
    }

    @Unique
    private void buildCloudCellFancy(CloudRenderer.ViewMode viewMode,
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

        if (viewMode != CloudRenderer.ViewMode.BELOW_CLOUDS) {
            builder.vertex(f, 4.0F, j)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
            builder.vertex(f, 4.0F, k)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
            builder.vertex(g, 4.0F, k)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
            builder.vertex(g, 4.0F, j)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
        }

        if (viewMode != CloudRenderer.ViewMode.ABOVE_CLOUDS) {
            builder.vertex(g, 0.0F, j)
                .normal(0.0F, -1.0F, 0.0F)
                .color(bottomColor);
            builder.vertex(g, 0.0F, k)
                .normal(0.0F, -1.0F, 0.0F)
                .color(bottomColor);
            builder.vertex(f, 0.0F, k)
                .normal(0.0F, -1.0F, 0.0F)
                .color(bottomColor);
            builder.vertex(f, 0.0F, j)
                .normal(0.0F, -1.0F, 0.0F)
                .color(bottomColor);
        }

        if (hasBorderNorth(cell) && z > 0) {
            builder.vertex(f, 0.0F, j)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
            builder.vertex(f, 4.0F, j)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
            builder.vertex(g, 4.0F, j)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
            builder.vertex(g, 0.0F, j)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
        }

        if (hasBorderSouth(cell) && z < 0) {
            builder.vertex(g, 0.0F, k)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
            builder.vertex(g, 4.0F, k)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
            builder.vertex(f, 4.0F, k)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
            builder.vertex(f, 0.0F, k)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
        }

        if (hasBorderWest(cell) && x > 0) {
            builder.vertex(f, 0.0F, k)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(f, 4.0F, k)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(f, 4.0F, j)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(f, 0.0F, j)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
        }

        if (hasBorderEast(cell) && x < 0) {
            builder.vertex(g, 0.0F, j)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(g, 4.0F, j)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(g, 4.0F, k)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(g, 0.0F, k)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
        }

        boolean bl = Math.abs(x) <= 1 && Math.abs(z) <= 1;
        if (bl) {
            builder.vertex(g, 4.0F, j)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
            builder.vertex(g, 4.0F, k)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
            builder.vertex(f, 4.0F, k)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);
            builder.vertex(f, 4.0F, j)
                .normal(0.0F, 1.0F, 0.0F)
                .color(topColor);

            builder.vertex(f, 0.0F, j)
                .normal(0.0F, 1.0F, 0.0F)
                .color(bottomColor);
            builder.vertex(f, 0.0F, k)
                .normal(0.0F, 1.0F, 0.0F)
                .color(bottomColor);
            builder.vertex(g, 0.0F, k)
                .normal(0.0F, 1.0F, 0.0F)
                .color(bottomColor);
            builder.vertex(g, 0.0F, j)
                .normal(0.0F, 1.0F, 0.0F)
                .color(bottomColor);

            builder.vertex(g, 0.0F, j)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
            builder.vertex(g, 4.0F, j)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
            builder.vertex(f, 4.0F, j)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);
            builder.vertex(f, 0.0F, j)
                .normal(0.0F, 0.0F, 1.0F)
                .color(eastWestColor);

            builder.vertex(f, 0.0F, k)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
            builder.vertex(f, 4.0F, k)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
            builder.vertex(g, 4.0F, k)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);
            builder.vertex(g, 0.0F, k)
                .normal(0.0F, 0.0F, -1.0F)
                .color(eastWestColor);

            builder.vertex(f, 0.0F, j)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(f, 4.0F, j)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(f, 4.0F, k)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(f, 0.0F, k)
                .normal(1.0F, 0.0F, 0.0F)
                .color(northSouthColor);

            builder.vertex(g, 0.0F, k)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(g, 4.0F, k)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(g, 4.0F, j)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
            builder.vertex(g, 0.0F, j)
                .normal(-1.0F, 0.0F, 0.0F)
                .color(northSouthColor);
        }
    }

    @Inject(method = "close()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelBufferClose(CallbackInfo ci) {
        ci.cancel();
    }
}
