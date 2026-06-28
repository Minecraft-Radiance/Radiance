package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.PBRVertexConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class ItemRendererMixins {

    @Inject(method =
        "getArmorGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;"
            +
            "Z)Lnet/minecraft/client/render/VertexConsumer;", at = @At(value = "HEAD"), cancellable = true)
    private static void redirectGetArmorGlintConsumer(VertexConsumerProvider provider,
        RenderLayer layer,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = provider.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                cir.setReturnValue(new PBRVertexConsumer.GLint(pbrVertexConsumer,
                    RenderLayer.getArmorEntityGlint()));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    VertexConsumers.union(provider.getBuffer(RenderLayer.getArmorEntityGlint()),
                        vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }

    @Inject(method =
        "getDynamicDisplayGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;" +
            "Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack$Entry;)"
            +
            "Lnet/minecraft/client/render/VertexConsumer;", at = @At(value = "HEAD"), cancellable = true)
    private static void redirectGetDynamicDisplayGlintConsumer(VertexConsumerProvider provider,
        RenderLayer layer,
        MatrixStack.Entry entry,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = provider.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            cir.setReturnValue(
                new PBRVertexConsumer.GLintOverlay(pbrVertexConsumer, RenderLayer.getGlint(), entry,
                    0.0078125F));
        } else {
            cir.setReturnValue(VertexConsumers.union(
                new OverlayVertexConsumer(provider.getBuffer(RenderLayer.getGlint()),
                    entry,
                    0.0078125F), vertexConsumer));
        }
    }

    @Inject(method =
        "getItemGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;"
            +
            "ZZ)Lnet/minecraft/client/render/VertexConsumer;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectGetItemGlintConsumer(VertexConsumerProvider vertexConsumers,
        RenderLayer layer,
        boolean solid,
        boolean glint,
        CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(layer);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            if (glint) {
                RenderLayer
                    glintRenderLayer =
                    MinecraftClient.isFabulousGraphicsOrBetter()
                        && layer == TexturedRenderLayers.getItemEntityTranslucentCull() ?
                        RenderLayer.getGlintTranslucent()
                        : (solid ? RenderLayer.getGlint() : RenderLayer.getEntityGlint());

                cir.setReturnValue(
                    new PBRVertexConsumer.GLint(pbrVertexConsumer, glintRenderLayer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        } else {
            if (glint) {
                cir.setReturnValue(
                    MinecraftClient.isFabulousGraphicsOrBetter()
                        && layer == TexturedRenderLayers.getItemEntityTranslucentCull() ?
                        VertexConsumers.union(
                            vertexConsumers.getBuffer(RenderLayer.getGlintTranslucent()),
                            vertexConsumer) :
                        VertexConsumers.union(vertexConsumers.getBuffer(
                                solid ? RenderLayer.getGlint() : RenderLayer.getEntityGlint()),
                            vertexConsumer));
            } else {
                cir.setReturnValue(vertexConsumer);
            }
        }
    }
}
