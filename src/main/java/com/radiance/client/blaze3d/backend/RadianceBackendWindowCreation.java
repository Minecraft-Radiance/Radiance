package com.radiance.client.blaze3d.backend;

public final class RadianceBackendWindowCreation {
    private static final ThreadLocal<Boolean> RADIANCE_WINDOW_HINTS_APPLIED =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    private RadianceBackendWindowCreation() {
    }

    public static void markRadianceWindowHintsApplied() {
        RADIANCE_WINDOW_HINTS_APPLIED.set(Boolean.TRUE);
    }

    public static boolean consumeRadianceWindowHintsApplied() {
        boolean result = Boolean.TRUE.equals(RADIANCE_WINDOW_HINTS_APPLIED.get());
        RADIANCE_WINDOW_HINTS_APPLIED.remove();
        return result;
    }
}
