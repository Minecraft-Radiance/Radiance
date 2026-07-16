package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.util.DyeColor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerBlockEntityRenderer.class)
public class BannerBlockEntityRendererMixins {

    @Unique
    private static final float radiance$layerDepthStep = 0.002F;

    @Redirect(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPart;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"))
    private static void cancelSolidRender(ModelPart canvas, MatrixStack matrices,
        VertexConsumer vertices,
        int light, int overlay, @Local(ordinal = 0, argsOnly = true) boolean isBanner,
        @Local(argsOnly = true) VertexConsumerProvider vertexConsumers,
        @Local(argsOnly = true) SpriteIdentifier baseSprite,
        @Local(ordinal = 1, argsOnly = true) boolean glint, @Local(ordinal = 2, argsOnly = true)
        boolean solid) {
        if (!isBanner) {
            canvas.render(matrices,
                baseSprite.getVertexConsumer(vertexConsumers, RenderLayer::getEntitySolid, solid,
                    glint), light, overlay);
        }
    }

    @Inject(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BannerBlockEntityRenderer;renderLayer(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;Lnet/minecraft/util/DyeColor;)V", ordinal = 0))
    private static void expandModelPre0(MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light, int overlay, ModelPart canvas, SpriteIdentifier baseSprite, boolean isBanner,
        DyeColor color, BannerPatternsComponent patterns, boolean glint, boolean solid,
        CallbackInfo ci) {
        radiance$pushCanvasLayer(matrices, canvas, 1);
    }

    @Inject(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BannerBlockEntityRenderer;renderLayer(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;Lnet/minecraft/util/DyeColor;)V", ordinal = 0, shift = Shift.AFTER))
    private static void expandModelPost0(MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light, int overlay, ModelPart canvas, SpriteIdentifier baseSprite, boolean isBanner,
        DyeColor color, BannerPatternsComponent patterns, boolean glint, boolean solid,
        CallbackInfo ci) {
        radiance$popCanvasLayer(matrices, canvas);
    }

    @Inject(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BannerBlockEntityRenderer;renderLayer(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;Lnet/minecraft/util/DyeColor;)V", ordinal = 1))
    private static void expandModelPre1(MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light, int overlay, ModelPart canvas, SpriteIdentifier baseSprite, boolean isBanner,
        DyeColor color, BannerPatternsComponent patterns, boolean glint, boolean solid,
        CallbackInfo ci, @Local(ordinal = 2) int i) {
        radiance$pushCanvasLayer(matrices, canvas, i + 2);
    }

    @Inject(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BannerBlockEntityRenderer;renderLayer(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;Lnet/minecraft/util/DyeColor;)V", ordinal = 1, shift = Shift.AFTER))
    private static void expandModelPost1(MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light, int overlay, ModelPart canvas, SpriteIdentifier baseSprite, boolean isBanner,
        DyeColor color, BannerPatternsComponent patterns, boolean glint, boolean solid,
        CallbackInfo ci) {
        radiance$popCanvasLayer(matrices, canvas);
    }

    @Unique
    private static void radiance$pushCanvasLayer(MatrixStack matrices, ModelPart canvas,
        int depthIndex) {
        matrices.push();
        Vector3f offset = new Vector3f(0.0F, 0.0F, -radiance$layerDepthStep * depthIndex);
        new Quaternionf().rotationZYX(canvas.roll, canvas.yaw, canvas.pitch)
            .transform(offset);
        matrices.translate(offset.x, offset.y, offset.z);
    }

    @Unique
    private static void radiance$popCanvasLayer(MatrixStack matrices, ModelPart canvas) {
        matrices.pop();
    }
}
