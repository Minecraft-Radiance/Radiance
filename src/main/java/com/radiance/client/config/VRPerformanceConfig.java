package com.radiance.client.config;

/**
 * Configuration settings for VR performance monitoring and display
 *
 * This class manages user preferences for the VR performance HUD,
 * including display options, performance settings, and visual customization.
 */
public class VRPerformanceConfig {

    // Display settings
    public static boolean enableF3Display = true;
    public static boolean enableStandaloneDisplay = false;
    public static boolean showDetailedCharts = true;
    public static boolean showTextMetrics = true;

    // Performance settings
    public static int updateFrequencyHz = 60;
    public static int historySizeSeconds = 2;
    public static float smoothingFactor = 0.1f;

    // Visual settings
    public static float hudScale = 1.0f;
    public static int hudOpacity = 80; // 0-100
    public static HudPosition hudPosition = HudPosition.TOP_RIGHT;

    // Chart settings
    public static boolean showGpuLine = true;
    public static boolean showCpuLine = true;
    public static boolean showTargetLine = true;
    public static boolean showFpsChart = true;
    public static boolean showHeadroomChart = true;

    // Performance thresholds for color coding
    public static float goodFpsThreshold = 85.0f;
    public static float warningFpsThreshold = 60.0f;
    public static float goodHeadroomThreshold = 0.1f; // 10%
    public static float warningHeadroomThreshold = 0.0f; // 0%

    // Advanced settings
    public static boolean enableDynamicScaling = true;
    public static boolean adaptiveUpdateRate = true;
    public static boolean optimizeForVR = true;

    public enum HudPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CUSTOM
    }

    /**
     * Get the maximum update frequency based on settings
     */
    public static int getMaxUpdateFrequency() {
        if (adaptiveUpdateRate) {
            // Reduce update frequency in VR to minimize performance impact
            return optimizeForVR ? Math.min(updateFrequencyHz, 45) : updateFrequencyHz;
        }
        return updateFrequencyHz;
    }

    /**
     * Get the history size in number of samples
     */
    public static int getHistorySizeSamples() {
        return historySizeSeconds * getMaxUpdateFrequency();
    }

    /**
     * Get the actual HUD opacity as a float value (0.0-1.0)
     */
    public static float getHudOpacityFloat() {
        return hudOpacity / 100.0f;
    }

    /**
     * Apply performance optimizations based on current VR state
     */
    public static void optimizeForCurrentState(boolean vrActive, float currentFps) {
        if (!adaptiveUpdateRate) return;

        if (vrActive && currentFps < warningFpsThreshold) {
            // Reduce update frequency if VR performance is struggling
            updateFrequencyHz = Math.max(updateFrequencyHz / 2, 15);
        } else if (!vrActive || currentFps > goodFpsThreshold) {
            // Restore normal update frequency when performance is good
            updateFrequencyHz = Math.min(updateFrequencyHz * 2, 60);
        }
    }

    /**
     * Reset all settings to defaults
     */
    public static void resetToDefaults() {
        enableF3Display = true;
        enableStandaloneDisplay = false;
        showDetailedCharts = true;
        showTextMetrics = true;
        updateFrequencyHz = 60;
        historySizeSeconds = 2;
        smoothingFactor = 0.1f;
        hudScale = 1.0f;
        hudOpacity = 80;
        hudPosition = HudPosition.TOP_RIGHT;
        showGpuLine = true;
        showCpuLine = true;
        showTargetLine = true;
        showFpsChart = true;
        showHeadroomChart = true;
        goodFpsThreshold = 85.0f;
        warningFpsThreshold = 60.0f;
        goodHeadroomThreshold = 0.1f;
        warningHeadroomThreshold = 0.0f;
        enableDynamicScaling = true;
        adaptiveUpdateRate = true;
        optimizeForVR = true;
    }

    /**
     * Convert configuration to a compact string for debugging
     */
    public static String toDebugString() {
        return String.format("VRPerfConfig[F3:%b,Charts:%b,Freq:%dHz,Pos:%s,Scale:%.1f,Opacity:%d%%]",
            enableF3Display, showDetailedCharts, updateFrequencyHz,
            hudPosition.name(), hudScale, hudOpacity);
    }
}