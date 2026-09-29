package com.radiance.mixins.opengl_ui;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class OpenGLUiAbstractTextureMixins {

    @Shadow
    protected int id;

    @Inject(method = "getId()I", at = @At(value = "HEAD"), cancellable = true)
    public void getExistingGlId(CallbackInfoReturnable<Integer> cir) {
        if (this.id >= 0) {
            cir.setReturnValue(this.id);
        }
    }

    @Inject(method = "setFilter(ZZ)V", at = @At(value = "HEAD"))
    public void syncNativeFilter(boolean bilinear, boolean mipmap, CallbackInfo ci) {
        if (id < 0) {
            return;
        }
        TextureProxy.setFilter(id,
            (bilinear ? VulkanConstants.VkFilter.VK_FILTER_LINEAR
                : VulkanConstants.VkFilter.VK_FILTER_NEAREST).getValue(),
            mipmap ? (bilinear
                ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue()
                : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue())
                : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
    }

    @Inject(method = "releaseId()V", at = @At(value = "HEAD"))
    public void releaseNativeTexture(CallbackInfo ci) {
        if (id >= 0) {
            TextureProxy.releaseTexture(id);
        }
    }
}
