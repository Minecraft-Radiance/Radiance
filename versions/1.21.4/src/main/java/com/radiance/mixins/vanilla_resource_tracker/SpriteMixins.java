package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Sprite.class)
public abstract class SpriteMixins implements ISpriteExt {

    @Final
    @Shadow
    private SpriteContents contents;

    public void radiance$setTargetID(int targetID) {
        ((ISpriteContentsExt) contents).radiance$setTargetID(targetID);
    }
}
