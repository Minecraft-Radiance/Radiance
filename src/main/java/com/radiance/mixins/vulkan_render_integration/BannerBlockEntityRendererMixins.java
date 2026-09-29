package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public class BannerBlockEntityRendererMixins {

    @Unique
    private static final float radiance$layerDepthStep = 0.002F;

    @Redirect(method = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private static void cancelSolidRender(ModelPart canvas, PoseStack matrices,
        VertexConsumer vertices,
        int light, int overlay, @Local(ordinal = 0, argsOnly = true) boolean isBanner,
        @Local(argsOnly = true) MultiBufferSource vertexConsumers,
        @Local(argsOnly = true) Material baseSprite,
        @Local(ordinal = 1, argsOnly = true) boolean glint, @Local(ordinal = 2, argsOnly = true)
        boolean solid) {
        if (!isBanner) {
            canvas.render(matrices,
                baseSprite.buffer(vertexConsumers, RenderType::entitySolid, solid,
                    glint), light, overlay);
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatternLayer(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;Lnet/minecraft/world/item/DyeColor;)V", ordinal = 0))
    private static void expandModelPre0(PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light, int overlay, ModelPart canvas, Material baseSprite, boolean isBanner,
        DyeColor color, BannerPatternLayers patterns, boolean glint, boolean solid,
        CallbackInfo ci) {
        radiance$pushCanvasLayer(matrices, canvas, 1);
    }

    @Inject(method = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatternLayer(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;Lnet/minecraft/world/item/DyeColor;)V", ordinal = 0, shift = Shift.AFTER))
    private static void expandModelPost0(PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light, int overlay, ModelPart canvas, Material baseSprite, boolean isBanner,
        DyeColor color, BannerPatternLayers patterns, boolean glint, boolean solid,
        CallbackInfo ci) {
        radiance$popCanvasLayer(matrices, canvas);
    }

    @Inject(method = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatternLayer(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;Lnet/minecraft/world/item/DyeColor;)V", ordinal = 1))
    private static void expandModelPre1(PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light, int overlay, ModelPart canvas, Material baseSprite, boolean isBanner,
        DyeColor color, BannerPatternLayers patterns, boolean glint, boolean solid,
        CallbackInfo ci, @Local(ordinal = 2) int i) {
        radiance$pushCanvasLayer(matrices, canvas, i + 2);
    }

    @Inject(method = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatternLayer(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;Lnet/minecraft/world/item/DyeColor;)V", ordinal = 1, shift = Shift.AFTER))
    private static void expandModelPost1(PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light, int overlay, ModelPart canvas, Material baseSprite, boolean isBanner,
        DyeColor color, BannerPatternLayers patterns, boolean glint, boolean solid,
        CallbackInfo ci) {
        radiance$popCanvasLayer(matrices, canvas);
    }

    @Unique
    private static void radiance$pushCanvasLayer(PoseStack matrices, ModelPart canvas,
        int depthIndex) {
        matrices.pushPose();
        Vector3f offset = new Vector3f(0.0F, 0.0F, -radiance$layerDepthStep * depthIndex);
        new Quaternionf().rotationZYX(canvas.zRot, canvas.yRot, canvas.xRot)
            .transform(offset);
        matrices.translate(offset.x, offset.y, offset.z);
    }

    @Unique
    private static void radiance$popCanvasLayer(PoseStack matrices, ModelPart canvas) {
        matrices.popPose();
    }
}
