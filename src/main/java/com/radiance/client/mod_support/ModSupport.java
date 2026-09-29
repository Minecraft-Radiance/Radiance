package com.radiance.client.mod_support;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.neoforged.fml.loading.FMLLoader;

public class ModSupport {
    public static final boolean FLYWHEEL = FMLLoader.getLoadingModList().getMods().stream()
        .anyMatch(mod -> mod.getModId().equals("flywheel"));
    public static final boolean SABLE = FMLLoader.getLoadingModList().getMods().stream()
        .anyMatch(mod -> mod.getModId().equals("sable"));
    public static final String DISTANT_HORIZONS_VERSION = FMLLoader.getLoadingModList().getMods().stream()
        .filter(mod -> mod.getModId().equals("distanthorizons"))
        .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
    public static final boolean DISTANT_HORIZONS = "3.3.1".equals(DISTANT_HORIZONS_VERSION)
        || "3.3.2".equals(DISTANT_HORIZONS_VERSION);

    public static void initialize() {
        if (FLYWHEEL) FlywheelSupport.initialize();
        if (DISTANT_HORIZONS_VERSION != null && !DISTANT_HORIZONS) {
            throw new IllegalStateException("Radiance Vista supports Distant Horizons 3.3.1 or 3.3.2 on Minecraft 1.21.1; found "
                + DISTANT_HORIZONS_VERSION
                + ". Install one of those official Distant Horizons releases or remove Distant Horizons to run Radiance independently.");
        }
    }
}
