package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class TextureManagerMixins {

    @Inject(method = "registerTexture(Lnet/minecraft/util/Identifier;Lnet/minecraft/client/texture/AbstractTexture;)V", at = @At("HEAD"))
    private void profileTextureRegister(Identifier id, AbstractTexture texture, CallbackInfo ci) {
        TextureTracker.textureID2GLID.put(id, texture.getGlId());
//        System.out.println("Registered texture: " + id + " (GL_ID = " + texture.getGlId() + ")");
    }
}
