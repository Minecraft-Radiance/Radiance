package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LightningEntityRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningEntityRenderer.class)
public class LightningEntityRendererMixins {

    @Inject(method = "drawBranch(Lorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumer;FFIFFFFFFFZZZZ)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectDrawBranch(Matrix4f matrix,
        VertexConsumer buffer,
        float x1,
        float z1,
        int y,
        float x2,
        float z2,
        float red,
        float green,
        float blue,
        float offset2,
        float offset1,
        boolean shiftEast1,
        boolean shiftSouth1,
        boolean shiftEast2,
        boolean shiftSouth2,
        CallbackInfo ci) {
        buffer.vertex(matrix, x1 + (shiftEast1 ? offset1 : -offset1), (float) (y * 16),
                z1 + (shiftSouth1 ? offset1 : -offset1))
            .color(red, green, blue, 0.3F)
            .texture(0.0F, 0.0F);

        buffer.vertex(matrix, x2 + (shiftEast1 ? offset2 : -offset2), (float) ((y + 1) * 16),
                z2 + (shiftSouth1 ? offset2 : -offset2))
            .color(red, green, blue, 0.3F)
            .texture(1.0F, 0.0F);

        buffer.vertex(matrix, x2 + (shiftEast2 ? offset2 : -offset2), (float) ((y + 1) * 16),
                z2 + (shiftSouth2 ? offset2 : -offset2))
            .color(red, green, blue, 0.3F)
            .texture(1.0F, 1.0F);

        buffer.vertex(matrix, x1 + (shiftEast2 ? offset1 : -offset1), (float) (y * 16),
                z1 + (shiftSouth2 ? offset1 : -offset1))
            .color(red, green, blue, 0.3F)
            .texture(0.0F, 1.0F);

        ci.cancel();
    }
}
