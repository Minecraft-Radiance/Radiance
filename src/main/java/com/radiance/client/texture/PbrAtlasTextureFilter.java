package com.radiance.client.texture;

import net.minecraft.util.Identifier;

public final class PbrAtlasTextureFilter {

    private PbrAtlasTextureFilter() {
    }

    public static boolean shouldSkipAtlasEntry(Identifier identifier) {
        String path = identifier.getPath();
        if (!(path.endsWith("_s") || path.endsWith("_n") || path.endsWith("_f"))) {
            return false;
        }

        return path.startsWith("block/")
            || path.startsWith("item/")
            || path.startsWith("entity/");
    }
}
