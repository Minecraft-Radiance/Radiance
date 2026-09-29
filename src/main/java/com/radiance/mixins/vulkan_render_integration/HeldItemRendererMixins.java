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
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        float g = Mth.lerp(tickDelta, player.xRotO, player.getXRot());
        int handRenderMask = radiance$evaluateWhichHandsToRender(player);
        float h = Mth.lerp(tickDelta, player.xBobO, player.xBob);
        float i = Mth.lerp(tickDelta, player.yBobO, player.yBob);
        matrices.mulPose(
            Axis.XP.rotationDegrees((player.getViewXRot(tickDelta) - h) * 0.1F));
        matrices.mulPose(
            Axis.YP.rotationDegrees((player.getViewYRot(tickDelta) - i) * 0.1F));
        if ((handRenderMask & MAIN_HAND_RENDER_MASK) != 0) {
            float j = hand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.oMainHandHeight,
                this.mainHandHeight);
            this.renderArmWithItem(player, tickDelta, g, InteractionHand.MAIN_HAND, j, this.mainHandItem, k,
                matrices, vertexConsumers, light);
        }

        if ((handRenderMask & OFF_HAND_RENDER_MASK) != 0) {
            float j = hand == InteractionHand.OFF_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(tickDelta, this.oOffHandHeight,
                this.offHandHeight);
            this.renderArmWithItem(player, tickDelta, g, InteractionHand.OFF_HAND, j, this.offHandItem, k,
                matrices, vertexConsumers, light);
        }
    }

    private static int radiance$evaluateWhichHandsToRender(LocalPlayer player) {
        ItemStack mainHandItem = player.getMainHandItem();
        ItemStack offHandItem = player.getOffhandItem();
        boolean holdsBow = mainHandItem.is(Items.BOW) || offHandItem.is(Items.BOW);
        boolean holdsCrossbow = mainHandItem.is(Items.CROSSBOW) || offHandItem.is(Items.CROSSBOW);
        if (!holdsBow && !holdsCrossbow) {
            return BOTH_HANDS_RENDER_MASK;
        }

        if (player.isUsingItem()) {
            ItemStack usedItem = player.getUseItem();
            InteractionHand usedHand = player.getUsedItemHand();
            if (usedItem.is(Items.BOW) || usedItem.is(Items.CROSSBOW)) {
                return usedHand == InteractionHand.MAIN_HAND
                    ? MAIN_HAND_RENDER_MASK
                    : OFF_HAND_RENDER_MASK;
            }
            if (usedHand == InteractionHand.MAIN_HAND && radiance$isChargedCrossbow(offHandItem)) {
                return MAIN_HAND_RENDER_MASK;
            }
            return BOTH_HANDS_RENDER_MASK;
        }

        return radiance$isChargedCrossbow(mainHandItem)
            ? MAIN_HAND_RENDER_MASK
            : BOTH_HANDS_RENDER_MASK;
    }

    private static boolean radiance$isChargedCrossbow(ItemStack item) {
        return item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item);
    }

    private static final int MAIN_HAND_RENDER_MASK = 1;
    private static final int OFF_HAND_RENDER_MASK = 2;
    private static final int BOTH_HANDS_RENDER_MASK = 3;
}
