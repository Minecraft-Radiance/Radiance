package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.RenderableGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontStorage.class)
public class FontStorageMixins {

    @Inject(method = "bake(Lnet/minecraft/client/font/RenderableGlyph;)Lnet/minecraft/client/font/BakedGlyph;", at = @At(value = "HEAD"))
    public void ensureIRenderableGlyph(RenderableGlyph c, CallbackInfoReturnable<BakedGlyph> cir) {
        if (!(c instanceof IRenderableGlyphExt)) {
            throw new RuntimeException(
                "RenderableGlyph expected to be instance of IRenderableGlyphExt");
        }
    }

    @Redirect(method = "bake(Lnet/minecraft/client/font/RenderableGlyph;)Lnet/minecraft/client/font/BakedGlyph;",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/font/GlyphAtlasTexture;bake(Lnet/minecraft/client/font/RenderableGlyph;)"
                    +
                    "Lnet/minecraft/client/font/BakedGlyph;"))
    public BakedGlyph redirectBakeToOneWithID(GlyphAtlasTexture instance, RenderableGlyph glyph) {
        IRenderableGlyphExt renderableGlyphExt = (IRenderableGlyphExt) glyph;
        return ((IGlyphAtlasTextureExt) instance).radiance$bake(renderableGlyphExt);
    }
}
