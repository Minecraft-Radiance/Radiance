package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public class BannerBlockEntityRendererMixins {

    @Unique
    private static final float layerDepthStep = 0.002F;

    @Inject(method = "renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLjava/util/List;Z)V", at = @At("HEAD"), cancellable = true)
    private static void redirectRenderCanvas(
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light,
        int overlay,
        ModelPart canvas,
        Material baseSprite,
        boolean isBanner,
        List<Pair<Holder<BannerPattern>, DyeColor>> patterns,
        boolean glint,
        CallbackInfo ci
    ) {
        if (!isBanner) {
            canvas.render(matrices, baseSprite.buffer(vertexConsumers,
                RenderType::entitySolid, glint), light, overlay);
        }

        for (int i = 0; i < 17 && i < patterns.size(); i++) {
            Pair<Holder<BannerPattern>, DyeColor> pair = patterns.get(i);
            float[] colors = pair.getSecond().getTextureDiffuseColors();
            int depthIndex = i + 1;
            pair.getFirst().unwrapKey().map(key -> isBanner
                ? Sheets.getBannerMaterial(key)
                : Sheets.getShieldMaterial(key)).ifPresent(sprite -> {
                pushCanvasLayer(matrices, canvas, depthIndex);
                renderLayer(matrices, vertexConsumers, light, overlay, canvas, sprite,
                    colors);
                popCanvasLayer(matrices);
            });
        }

        ci.cancel();
    }

    @Unique
    private static void renderLayer(PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light,
        int overlay,
        ModelPart canvas,
        Material sprite,
        float[] colors) {
        canvas.render(matrices, sprite.buffer(vertexConsumers,
            RenderType::entityNoOutline), light, overlay, colors[0], colors[1], colors[2],
            1.0F);
    }

    @Unique
    private static void pushCanvasLayer(PoseStack matrices, ModelPart canvas,
        int depthIndex) {
        matrices.pushPose();
        Vector3f offset = new Vector3f(0.0F, 0.0F, -layerDepthStep * depthIndex);
        new Quaternionf().rotationZYX(canvas.zRot, canvas.yRot, canvas.xRot)
            .transform(offset);
        matrices.translate(offset.x, offset.y, offset.z);
    }

    @Unique
    private static void popCanvasLayer(PoseStack matrices) {
        matrices.popPose();
    }
}
