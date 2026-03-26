package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IAbstractTextureExt;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class AbstractTextureMixins implements IAbstractTextureExt {

    @Shadow
    protected int glId;

    private int radiance$ensureGlId() {
        if (this.glId == -1) {
            this.glId = TextureUtil.generateTextureId();
        }
        return this.glId;
    }

    @Inject(method = "bindTexture()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelBindTexture(CallbackInfo ci) {
        TextureTracker.currentBoundTextureID = radiance$ensureGlId();
        ci.cancel();
    }

    @Inject(method = "setFilter(ZZ)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectSetFilter(boolean bilinear, boolean mipmap, CallbackInfo ci) {
        TextureProxy.setFilter(radiance$ensureGlId(),
            (bilinear ? VulkanConstants.VkFilter.VK_FILTER_LINEAR :
                VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
            mipmap ? (bilinear
                ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue() :
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue()) :
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
        ci.cancel();
    }

    @Inject(method = "clearGlId()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelClearGlId(CallbackInfo ci) {
        if (TextureTracker.currentBoundTextureID == this.glId) {
            TextureTracker.currentBoundTextureID = -1;
        }
        ci.cancel();
    }

    @Override
    public int radiance$getGlIDUnsafe() {
        if (this.glId < 0) {
            throw new IllegalStateException("glId is not initialized");
        }
        return this.glId;
    }

    @Inject(method = "getGlId()I", at = @At(value = "HEAD"), cancellable = true)
    public void redirectGetGlId(CallbackInfoReturnable<Integer> cir) {
        synchronized (AbstractTextureMixins.class) {
            cir.setReturnValue(radiance$ensureGlId());
        }
    }
}
