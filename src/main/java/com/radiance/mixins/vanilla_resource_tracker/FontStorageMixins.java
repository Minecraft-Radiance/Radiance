package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontSet.class)
public class FontStorageMixins {

    @Inject(method = "stitch(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;", at = @At(value = "HEAD"))
    public void ensureIRenderableGlyph(SheetGlyphInfo c,
        CallbackInfoReturnable<BakedGlyph> cir) {
        if (!(c instanceof IRenderableGlyphExt)) {
            throw new RuntimeException(
                "RenderableGlyph expected to be instance of IRenderableGlyphExt");
        }
    }

    @Redirect(method = "stitch(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gui/font/FontTexture;add(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"))
    public BakedGlyph redirectGetGlyphRendererWithID(FontTexture instance,
        SheetGlyphInfo glyph) {
        IRenderableGlyphExt renderableGlyphExt = (IRenderableGlyphExt) glyph;
        return ((IGlyphAtlasTextureExt) instance).radiance$getGlyphRenderer(renderableGlyphExt);
    }
}
