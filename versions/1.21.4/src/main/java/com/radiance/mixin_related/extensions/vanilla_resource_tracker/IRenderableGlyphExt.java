package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import net.minecraft.client.font.RenderableGlyph;

public interface IRenderableGlyphExt extends RenderableGlyph {

    void upload(int id, int x, int y);
}
