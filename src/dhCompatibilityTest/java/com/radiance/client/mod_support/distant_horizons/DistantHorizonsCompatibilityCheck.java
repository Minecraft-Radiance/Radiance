package com.radiance.client.mod_support.distant_horizons;

import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;

public final class DistantHorizonsCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        String version = args[0];
        boolean newer = version.equals("3.3.2");
        DistantHorizonsRenderApi adapter = DistantHorizonsRenderApi.create(version);
        String directionMethod = newer ? "getDepthDirection" : "getRenderDepth";
        check(AbstractDhRenderApiDefinition.class.getMethod(directionMethod).invoke(adapter).toString()
            .equals("FORWARD_Z"), "depth direction");
        check(AbstractDhRenderApiDefinition.class.getMethod("getDepthRange").invoke(adapter).toString()
            .equals("ZERO_TO_POS_ONE"), "depth range");

        ConfigEntry<String> config = new ConfigEntry.Builder<String>().set("original")
            .setUseApiOverwrite(true).build();
        adapter.setApiValue(config, "override");
        check(config.get().equals("override"), "configuration override");
        check(config.getTrueValue().equals("original"), "user configuration preserved");
        if (newer) {
            check(ConfigEntry.class.getMethod("getApiUser").invoke(config).equals("Radiance Vista"),
                "configuration owner");
        }
        adapter.setApiValue(config, null);
        check(config.get().equals("original"), "configuration override cleared");

        DistantHorizonsVertexBuffer buffer = new DistantHorizonsVertexBuffer();
        check(buffer.getVertexCount() == 0, "empty vertex count");
        if (newer) {
            check(IVertexBufferWrapper.class.getMethod("getVertexCount").invoke(buffer).equals(0),
                "new vertex interface dispatch");
        }
        buffer.close();
        try {
            DistantHorizonsRenderApi.create("3.3.3");
            throw new AssertionError("Unverified DH release accepted");
        } catch (IllegalArgumentException expected) {
        }
        String missingType = newer ? "core.render.EDhDepthRange" : "api.enums.config.EDhApiDepthRange";
        try {
            Class.forName("com.seibel.distanthorizons." + missingType);
            throw new AssertionError("Runtime contains both DH versions");
        } catch (ClassNotFoundException expected) {
        }
        System.out.println("Distant Horizons " + version + ": adapter, depth, config and buffer checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
