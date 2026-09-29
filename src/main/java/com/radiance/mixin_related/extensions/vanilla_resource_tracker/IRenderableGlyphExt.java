package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.font.SheetGlyphInfo;

public interface IRenderableGlyphExt extends SheetGlyphInfo {

    void upload(int id, int x, int y);
}
