package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.radiance.client.vertex.PBRVertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class ItemRendererMixins {

    @Inject(method =
        "getArmorFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Z)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At(value = "HEAD"), cancellable = true)
    private static void redirectGetArmorGlintConsumer(MultiBufferSource provider,
        RenderType layer,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = provider.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                cir.setReturnValue(new PBRVertexConsumer.GLint(pbrVertexConsumer,
                    RenderType.armorEntityGlint()));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    VertexMultiConsumer.create(provider.getBuffer(RenderType.armorEntityGlint()),
                        vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }

    @Inject(method =
        "getCompassFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At(value = "HEAD"), cancellable = true)
    private static void redirectGetDynamicDisplayGlintConsumer(MultiBufferSource provider,
        RenderType layer,
        PoseStack.Pose entry,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = provider.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            cir.setReturnValue(
                new PBRVertexConsumer.GLintOverlay(pbrVertexConsumer, RenderType.glint(), entry,
                    0.0078125F));
        } else {
            cir.setReturnValue(VertexMultiConsumer.create(
                new SheetedDecalTextureGenerator(provider.getBuffer(RenderType.glint()),
                    entry,
                    0.0078125F), vertexConsumer));
        }
    }

    @Inject(method =
        "getFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectGetItemGlintConsumer(MultiBufferSource vertexConsumers,
        RenderType layer,
        boolean solid,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                RenderType
                    glintRenderLayer =
                    Minecraft.useShaderTransparency()
                        && layer == Sheets.translucentItemSheet() ?
                        RenderType.glintTranslucent()
                        : (solid ? RenderType.glint() : RenderType.entityGlint());

                cir.setReturnValue(
                    new PBRVertexConsumer.GLint(pbrVertexConsumer, glintRenderLayer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    Minecraft.useShaderTransparency()
                        && layer == Sheets.translucentItemSheet() ?
                        VertexMultiConsumer.create(
                            vertexConsumers.getBuffer(RenderType.glintTranslucent()),
                            vertexConsumer) :
                        VertexMultiConsumer.create(vertexConsumers.getBuffer(
                                solid ? RenderType.glint() : RenderType.entityGlint()),
                            vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }

    @Inject(method =
        "getFoilBufferDirect(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectGetDirectItemGlintConsumer(MultiBufferSource vertexConsumers,
        RenderType layer,
        boolean solid,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                cir.setReturnValue(
                    new PBRVertexConsumer.GLint(pbrVertexConsumer,
                        solid ? RenderType.glint() : RenderType.entityGlintDirect()));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    VertexMultiConsumer.create(vertexConsumers.getBuffer(
                            solid ? RenderType.glint() : RenderType.entityGlintDirect()),
                        vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }
}
