package com.radiance.client.mod_support;

import net.neoforged.fml.loading.FMLLoader;

public class ModSupport {
    private static final String DISTANT_HORIZONS_VERSION = FMLLoader.getLoadingModList().getMods().stream()
        .filter(mod -> mod.getModId().equals("distanthorizons"))
        .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
    public static final boolean DISTANT_HORIZONS = "3.3.1".equals(DISTANT_HORIZONS_VERSION);

    public static void initialize() {
        if (DISTANT_HORIZONS_VERSION != null && !DISTANT_HORIZONS) {
            throw new IllegalStateException("Radiance Vista supports Distant Horizons 3.3.1 on Minecraft 1.21.4; found "
                + DISTANT_HORIZONS_VERSION
                + ". Install that official Distant Horizons release or remove Distant Horizons to run Radiance independently.");
        }
    }
}
