package com.radiance.mixins.vulkan_render_integration;

import com.google.common.base.MoreObjects;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixins implements IHeldItemRendererExt {

    @Shadow
    private ItemStack mainHand;

    @Shadow
    private ItemStack offHand;

    @Shadow
    private float equipProgressMainHand;

    @Shadow
    private float prevEquipProgressMainHand;

    @Shadow
    private float equipProgressOffHand;

    @Shadow
    private float prevEquipProgressOffHand;

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity player,
        float tickDelta,
        float pitch,
        Hand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light);

    @Override
    public void radiance$renderItem(float tickDelta,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        ClientPlayerEntity player,
        int light) {
        float swingProgress = player.getHandSwingProgress(tickDelta);
        Hand hand = MoreObjects.firstNonNull(player.preferredHand, Hand.MAIN_HAND);
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        HeldItemRenderer.HandRenderType handRenderType = HeldItemRenderer.getHandRenderType(
            player);
        float renderPitch = MathHelper.lerp(tickDelta, player.lastRenderPitch,
            player.renderPitch);
        float renderYaw = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);

        matrices.multiply(
            RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - renderPitch)
                * 0.1F));
        matrices.multiply(
            RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - renderYaw)
                * 0.1F));

        if (handRenderType.renderMainHand) {
            float handSwing = hand == Hand.MAIN_HAND ? swingProgress : 0.0F;
            float equipProgress = 1.0F - MathHelper.lerp(tickDelta,
                this.prevEquipProgressMainHand, this.equipProgressMainHand);
            this.renderFirstPersonItem(player, tickDelta, pitch, Hand.MAIN_HAND, handSwing,
                this.mainHand, equipProgress, matrices, vertexConsumers, light);
        }

        if (handRenderType.renderOffHand) {
            float handSwing = hand == Hand.OFF_HAND ? swingProgress : 0.0F;
            float equipProgress = 1.0F - MathHelper.lerp(tickDelta,
                this.prevEquipProgressOffHand, this.equipProgressOffHand);
            this.renderFirstPersonItem(player, tickDelta, pitch, Hand.OFF_HAND, handSwing,
                this.offHand, equipProgress, matrices, vertexConsumers, light);
        }
    }
}
