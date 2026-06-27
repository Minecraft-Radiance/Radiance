package com.radiance.client.texture.compat;

//#if MC>=260200
//$$ import com.radiance.client.texture.AuxiliaryTextureReloader;
//$$ import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.server.packs.PackType;
//#endif

public final class TextureReloadRegistrationCompat {

    private TextureReloadRegistrationCompat() {
    }

    public static void registerClientReloaders() {
        //#if MC>=260200
        //$$ ResourceLoader.get(PackType.CLIENT_RESOURCES)
        //$$     .registerReloadListener(Identifier.fromNamespaceAndPath("radiance",
        //$$         "auxiliary_textures"), new AuxiliaryTextureReloader());
        //#endif
    }
}
