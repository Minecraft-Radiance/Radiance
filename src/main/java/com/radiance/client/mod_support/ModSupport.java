package com.radiance.client.mod_support;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.minecraftforge.fml.loading.FMLLoader;

public class ModSupport {
    public static final boolean FLYWHEEL = FMLLoader.getLoadingModList().getMods().stream()
        .anyMatch(mod -> mod.getModId().equals("flywheel"));
    private static final String DISTANT_HORIZONS_VERSION = FMLLoader.getLoadingModList().getMods().stream()
        .filter(mod -> mod.getModId().equals("distanthorizons"))
        .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
    public static final boolean DISTANT_HORIZONS = "3.3.0".equals(DISTANT_HORIZONS_VERSION);

    public static void initialize() {
        if (FLYWHEEL) FlywheelSupport.initialize();
        if (DISTANT_HORIZONS_VERSION != null && !DISTANT_HORIZONS) {
            throw new IllegalStateException("Radiance Vista supports Distant Horizons 3.3.0 on Minecraft 1.20.1; found "
                + DISTANT_HORIZONS_VERSION
                + ". Install that official Distant Horizons release or remove Distant Horizons to run Radiance independently.");
        }
    }
}
