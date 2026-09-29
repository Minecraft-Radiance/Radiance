package com.radiance.client.mod_support;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.SemanticVersion;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.fabricmc.loader.api.FabricLoader;

public class ModSupport implements PreLaunchEntrypoint {
    public static final boolean SABLE = FabricLoader.getInstance().isModLoaded("sable");
    public static final boolean FLYWHEEL = FabricLoader.getInstance().isModLoaded("flywheel");
    public static final String DISTANT_HORIZONS_VERSION = FabricLoader.getInstance()
        .getModContainer("distanthorizons")
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
        .orElse(null);
    public static final boolean DISTANT_HORIZONS = "3.3.1".equals(DISTANT_HORIZONS_VERSION)
        || "3.3.2".equals(DISTANT_HORIZONS_VERSION);

    @Override
    public void onPreLaunch() {
        FabricLoader.getInstance().getModContainer("flywheel").ifPresent(mod -> {
            if (!(mod.getMetadata().getVersion() instanceof SemanticVersion)) {
                throw new IllegalStateException("Radiance requires a released Flywheel version from 1.0.1 to 1.0.6 on Minecraft 1.21.1; found "
                    + mod.getMetadata().getVersion().getFriendlyString());
            }
        });
    }

    public static void initialize() {
        if (FLYWHEEL) FlywheelSupport.initialize();
        var distantHorizons = FabricLoader.getInstance().getModContainer("distanthorizons");
        if (distantHorizons.isPresent() && !DISTANT_HORIZONS) {
            throw new IllegalStateException("Radiance Vista supports Distant Horizons 3.3.1 or 3.3.2 on Minecraft 1.21.1; found "
                + distantHorizons.get().getMetadata().getVersion().getFriendlyString()
                + ". Install one of those official Distant Horizons releases or remove Distant Horizons to run Radiance independently.");
        }
    }
}
