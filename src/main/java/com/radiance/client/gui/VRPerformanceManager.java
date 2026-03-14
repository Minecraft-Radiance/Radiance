package com.radiance.client.gui;

import com.radiance.client.proxy.vulkan.VRProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Client-side manager for VR performance visualization
 *
 * Coordinates between the VR performance HUD component and Minecraft's
 * rendering pipeline to provide seamless integration with F3 debug overlay.
 */
public class VRPerformanceManager {

    private static VRPerformanceManager instance;
    private final VRPerformanceHUD performanceHUD;
    private boolean enabledInF3 = true;
    private boolean enabledStandalone = false;

    // Performance settings
    private int updateFrequency = 60; // Updates per second
    private boolean showDetailedCharts = true;
    private boolean showTextMetrics = true;

    private VRPerformanceManager() {
        this.performanceHUD = new VRPerformanceHUD();
    }

    public static VRPerformanceManager getInstance() {
        if (instance == null) {
            instance = new VRPerformanceManager();
        }
        return instance;
    }

    /**
     * Update VR performance data (call each frame)
     */
    public void update() {
        if (!shouldShow()) {
            return;
        }

        performanceHUD.update();
    }

    /**
     * Render VR performance overlay in F3 debug context
     */
    public void renderInF3(DrawContext context, int screenWidth, int screenHeight) {
        if (!enabledInF3 || !shouldShow()) {
            return;
        }

        renderHUD(context, screenWidth, screenHeight);
    }

    /**
     * Render VR performance as standalone overlay
     */
    public void renderStandalone(DrawContext context, int screenWidth, int screenHeight) {
        if (!enabledStandalone || !shouldShow()) {
            return;
        }

        renderHUD(context, screenWidth, screenHeight);
    }

    /**
     * Internal HUD rendering
     */
    private void renderHUD(DrawContext context, int screenWidth, int screenHeight) {
        // Configure HUD display based on settings
        boolean wasVisible = performanceHUD.isVisible();
        performanceHUD.setVisible(true);

        try {
            performanceHUD.render(context, screenWidth, screenHeight);
        } catch (Exception e) {
            // Silently handle any rendering errors to avoid crashing VR session
        } finally {
            if (!wasVisible) {
                performanceHUD.setVisible(wasVisible);
            }
        }
    }

    /**
     * Check if VR performance display should be shown
     */
    private boolean shouldShow() {
        return MinecraftClient.getInstance().player != null;
    }

    // Configuration methods

    public void toggleF3Display() {
        enabledInF3 = !enabledInF3;
    }

    public void toggleStandaloneDisplay() {
        enabledStandalone = !enabledStandalone;
    }

    public void setF3DisplayEnabled(boolean enabled) {
        this.enabledInF3 = enabled;
    }

    public void setStandaloneDisplayEnabled(boolean enabled) {
        this.enabledStandalone = enabled;
    }

    public boolean isF3DisplayEnabled() {
        return enabledInF3;
    }

    public boolean isStandaloneDisplayEnabled() {
        return enabledStandalone;
    }

    public void setShowDetailedCharts(boolean show) {
        this.showDetailedCharts = show;
    }

    public void setShowTextMetrics(boolean show) {
        this.showTextMetrics = show;
    }

    public void setUpdateFrequency(int frequency) {
        this.updateFrequency = Math.max(1, Math.min(120, frequency));
    }

    public VRPerformanceHUD getHUD() {
        return performanceHUD;
    }

    /**
     * Reset all performance data (e.g., when entering/leaving VR)
     */
    public void reset() {
        // The HUD will automatically reset its data when VR is disabled
        performanceHUD.setVisible(false);
    }

    /**
     * Get a formatted summary string for debug purposes
     */
    public String getPerformanceSummary() {
        float[] stats = VRProxy.getPerformanceStats();
        if (stats == null || stats.length < 8) {
            return "VR: No data";
        }

        boolean xrRunning = VRProxy.isSessionRunning();

        int renderW = VRProxy.getEyeRenderWidth();
        int renderH = VRProxy.getEyeRenderHeight();
        int displayW = 0;
        int displayH = 0;
        int[] recommended = VRProxy.getRecommendedResolution();
        if (recommended != null && recommended.length >= 2) {
            displayW = recommended[0];
            displayH = recommended[1];
        }

        // [gpuMs, cpuFrameMs, cpuWorkMs, cpuWaitMs, targetMs, fps, drops, headroom]
        if (!xrRunning) {
            return String.format("VR: N/A | GPU: %.1fms | Frame: %.1fms | Drops: N/A | Headroom: N/A | Render: %s | Display: %s",
                stats[0],
                stats[1],
                renderW > 0 && renderH > 0 ? String.format("%dx%d", renderW, renderH) : "N/A",
                displayW > 0 && displayH > 0 ? String.format("%dx%d", displayW, displayH) : "N/A"
            );
        }

        return String.format("VR: %.1fms (%.1f FPS) | GPU: %.1fms | CPU: %.1fms+%.1fms | Drops: %d | Headroom: %.1f%% | Render: %dx%d | Display: %dx%d",
            Math.max(stats[0], stats[2]), // max(gpu, cpuWork) as overall frame budget usage
            stats[5], // fps
            stats[0], // GPU ms
            stats[2], // cpuWorkMs
            stats[3], // cpuWaitMs
            (int)stats[6], // dropped frames
            stats[7] * 100, // headroom %
            renderW, renderH,
            displayW, displayH
        );
    }
}