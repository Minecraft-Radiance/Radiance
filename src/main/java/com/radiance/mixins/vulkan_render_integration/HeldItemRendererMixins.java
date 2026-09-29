package com.radiance.mixins.vulkan_render_integration;

import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixins implements IHeldItemRendererExt {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    protected abstract void renderArmWithItem(AbstractClientPlayer player,
        float tickDelta,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        int light);

    @Override
    public void radiance$renderItem(float tickDelta,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        LocalPlayer player,
        int light) {
        float f = player.getAttackAnim(tickDelta);
        InteractionHand hand = MoreObjects.firstNonNull(player.swingingArm, InteractionHand.MAIN_HAND);
        float g = player.getXRot(tickDelta);
        ItemInHandRenderer.HandRenderSelection handRenderType = ItemInHandRenderer.evaluateWhichHandsToRender(player);
        float h = Mth.lerp(tickDelta, player.xBobO, player.xBob);
        float i = Mth.lerp(tickDelta, player.yBobO, player.yBob);
        matrices.mulPose(
            Axis.XP.rotationDegrees((player.getViewXRot(tickDelta) - h) * 0.1F));
        matrices.mulPose(
            Axis.YP.rotationDegrees((player.getViewYRot(tickDelta) - i) * 0.1F));
        if (handRenderType.renderMainHand) {
            float j = hand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.oMainHandHeight,
                this.mainHandHeight);
            this.renderArmWithItem(player, tickDelta, g, InteractionHand.MAIN_HAND, j, this.mainHandItem, k,
                matrices, vertexConsumers, light);
        }

        if (handRenderType.renderOffHand) {
            float j = hand == InteractionHand.OFF_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.oOffHandHeight,
                this.offHandHeight);
            this.renderArmWithItem(player, tickDelta, g, InteractionHand.OFF_HAND, j, this.offHandItem, k,
                matrices, vertexConsumers, light);
        }
    }
}
