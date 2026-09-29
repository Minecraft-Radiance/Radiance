package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IAbstractTextureExt;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class AbstractTextureMixins implements IAbstractTextureExt {

    @Shadow
    protected int id;

    @Inject(method = "bind()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelBindTexture(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "setFilter(ZZ)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectSetFilter(boolean bilinear, boolean mipmap, CallbackInfo ci) {
        TextureProxy.setFilter(id,
            (bilinear ? VulkanConstants.VkFilter.VK_FILTER_LINEAR :
                VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
            mipmap ? (bilinear
                ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue() :
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue()) :
                VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
        ci.cancel();
    }

    @Inject(method = "releaseId()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelClearGlId(CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public int radiance$getGlIDUnsafe() {
        if (this.id < 0) {
            throw new IllegalStateException("glId is not initialized");
        }
        return this.id;
    }

    @Inject(method = "Lnet/minecraft/client/renderer/texture/AbstractTexture;getId()I", at = @At(value = "HEAD"), cancellable = true)
    public void redirectGetGlId(CallbackInfoReturnable<Integer> cir) {
        synchronized (AbstractTextureMixins.class) {
            if (this.id == -1) {
                this.id = TextureUtil.generateTextureId();
            }

            cir.setReturnValue(this.id);
        }
    }
}
