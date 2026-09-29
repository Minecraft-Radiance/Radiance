package com.radiance.client.mod_support;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class ModSupport implements PreLaunchEntrypoint {
    public static final boolean FLYWHEEL = FabricLoader.getInstance().isModLoaded("flywheel");

    public static final boolean DISTANT_HORIZONS = FabricLoader.getInstance()
        .getModContainer("distanthorizons")
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("3.3.0"))
        .orElse(false);

    @Override
    public void onPreLaunch() {
        FabricLoader.getInstance().getModContainer("flywheel").ifPresent(mod -> {
            if (!(mod.getMetadata().getVersion() instanceof SemanticVersion)) {
                throw new IllegalStateException("Radiance requires a released Flywheel version from 1.0.1 to 1.0.6 on Minecraft 1.20.1; found "
                    + mod.getMetadata().getVersion().getFriendlyString());
            }
        });
    }

    public static void initialize() {
        if (FLYWHEEL) FlywheelSupport.initialize();
        var distantHorizons = FabricLoader.getInstance().getModContainer("distanthorizons");
        if (distantHorizons.isPresent() && !DISTANT_HORIZONS) {
            throw new IllegalStateException("Radiance Vista supports Distant Horizons 3.3.0 on Minecraft 1.20.1; found "
                + distantHorizons.get().getMetadata().getVersion().getFriendlyString()
                + ". Install that official Distant Horizons release or remove Distant Horizons to run Radiance independently.");
        }
    }
}
