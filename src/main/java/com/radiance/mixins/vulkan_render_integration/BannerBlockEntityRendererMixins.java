package com.radiance.mixins.vulkan_render_integration;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.DyeColor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerBlockEntityRenderer.class)
public class BannerBlockEntityRendererMixins {

    @Unique
    private static final float layerDepthStep = 0.002F;

    @Inject(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLjava/util/List;Z)V", at = @At("HEAD"), cancellable = true)
    private static void redirectRenderCanvas(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        int overlay,
        ModelPart canvas,
        SpriteIdentifier baseSprite,
        boolean isBanner,
        List<Pair<RegistryEntry<BannerPattern>, DyeColor>> patterns,
        boolean glint,
        CallbackInfo ci
    ) {
        if (!isBanner) {
            canvas.render(matrices, baseSprite.getVertexConsumer(vertexConsumers,
                RenderLayer::getEntitySolid, glint), light, overlay);
        }

        for (int i = 0; i < 17 && i < patterns.size(); i++) {
            Pair<RegistryEntry<BannerPattern>, DyeColor> pair = patterns.get(i);
            float[] colors = pair.getSecond().getColorComponents();
            int depthIndex = i + 1;
            pair.getFirst().getKey().map(key -> isBanner
                ? TexturedRenderLayers.getBannerPatternTextureId(key)
                : TexturedRenderLayers.getShieldPatternTextureId(key)).ifPresent(sprite -> {
                pushCanvasLayer(matrices, canvas, depthIndex);
                renderLayer(matrices, vertexConsumers, light, overlay, canvas, sprite,
                    colors);
                popCanvasLayer(matrices);
            });
        }

        ci.cancel();
    }

    @Unique
    private static void renderLayer(MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        int overlay,
        ModelPart canvas,
        SpriteIdentifier sprite,
        float[] colors) {
        canvas.render(matrices, sprite.getVertexConsumer(vertexConsumers,
            RenderLayer::getEntityNoOutline), light, overlay, colors[0], colors[1], colors[2],
            1.0F);
    }

    @Unique
    private static void pushCanvasLayer(MatrixStack matrices, ModelPart canvas,
        int depthIndex) {
        matrices.push();
        Vector3f offset = new Vector3f(0.0F, 0.0F, -layerDepthStep * depthIndex);
        new Quaternionf().rotationZYX(canvas.roll, canvas.yaw, canvas.pitch)
            .transform(offset);
        matrices.translate(offset.x, offset.y, offset.z);
    }

    @Unique
    private static void popCanvasLayer(MatrixStack matrices) {
        matrices.pop();
    }
}
