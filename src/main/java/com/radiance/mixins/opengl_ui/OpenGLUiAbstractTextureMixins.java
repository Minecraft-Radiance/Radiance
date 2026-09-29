package com.radiance.mixins.opengl_ui;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class OpenGLUiAbstractTextureMixins {

    @Shadow
    protected int glId;

    @Inject(method = "getGlId()I", at = @At(value = "HEAD"), cancellable = true)
    public void getExistingGlId(CallbackInfoReturnable<Integer> cir) {
        if (this.glId >= 0) {
            cir.setReturnValue(this.glId);
        }
    }

    @Inject(method = "setFilter(ZZ)V", at = @At(value = "HEAD"))
    public void syncNativeFilter(boolean bilinear, boolean mipmap, CallbackInfo ci) {
        if (glId < 0) {
            return;
        }
        TextureProxy.setFilter(glId,
            (bilinear ? VulkanConstants.VkFilter.VK_FILTER_LINEAR
                : VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
            mipmap ? (bilinear
                ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue()
                : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue())
                : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
    }

    @Inject(method = "clearGlId()V", at = @At(value = "HEAD"))
    public void releaseNativeTexture(CallbackInfo ci) {
        if (glId >= 0) {
            TextureProxy.releaseTexture(glId);
        }
    }
}
