package com.radiance.client.texture.compat;

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;
//#if MC>=260200
//$$ import net.minecraft.resources.FileToIdConverter;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.server.packs.resources.Resource;
//$$ import net.minecraft.server.packs.resources.ResourceManager;
//#else
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
//#endif

public final class TextureResourceCompat {

    //#if MC>=260200
    //$$ private static final FileToIdConverter TEXTURE_ID_CONVERTER =
    //$$     new FileToIdConverter("textures", ".png");
    //#endif

    private TextureResourceCompat() {
    }

    public static boolean hasBaseSpriteResource(Object resourceManager, Object baseSpriteId) {
        //#if MC>=260200
        //$$ return ((ResourceManager) resourceManager)
        //$$     .getResource(TEXTURE_ID_CONVERTER.idToFile((Identifier) baseSpriteId))
        //$$     .isPresent();
        //#else
        return ((ResourceManager) resourceManager)
            .getResource(AtlasSource.RESOURCE_FINDER.toResourcePath((Identifier) baseSpriteId))
            .isPresent();
        //#endif
    }

    public static Map<Identifier, Resource> listTextureResources(Object resourceManager,
        Predicate<Identifier> predicate) {
        //#if MC>=260200
        //$$ return ((ResourceManager) resourceManager).listResources("textures", predicate);
        //#else
        return ((ResourceManager) resourceManager).findResources("textures", predicate);
        //#endif
    }

    public static InputStream open(Object resource) throws IOException {
        //#if MC>=260200
        //$$ return ((Resource) resource).open();
        //#else
        return ((Resource) resource).getInputStream();
        //#endif
    }
}
