package com.radiance.client.gui;

import com.radiance.client.config.VRPerformanceConfig;
import com.radiance.client.gui.compat.GuiCompat;
//#if MC>=260200
//$$ import com.radiance.client.blaze3d.abi.VRProxy;
//#else
import com.radiance.client.proxy.vulkan.VRProxy;
//#endif

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * High-performance VR performance statistics HUD with real-time colorful charts
 *
 * Features:
 * - Real-time frame time tracking (GPU/CPU)
 * - FPS monitoring with color-coded status
 * - Frame headroom visualization
 * - Dropped frames counter
 * - Performance history graphs
 * - Optimized rendering for minimal VR performance impact
 */
public class VRPerformanceHUD {

    // Configuration constants
    private static final int CHART_WIDTH = 200;
    private static final int CHART_HEIGHT = 60;
    private static final int MARGIN = 5;

    // Dynamic configuration from VRPerformanceConfig
    private int historySize = VRPerformanceConfig.getHistorySizeSamples();
    private float smoothingFactor = VRPerformanceConfig.smoothingFactor;
    private long updateIntervalMs = 1000 / VRPerformanceConfig.getMaxUpdateFrequency();

    // Color constants (ARGB format)
    private static final int COLOR_BACKGROUND = 0x80000000; // Semi-transparent black
    private static final int COLOR_FRAME_GOOD = 0xFF00FF00;  // Green
    private static final int COLOR_FRAME_WARNING = 0xFFFFFF00; // Yellow
    private static final int COLOR_FRAME_BAD = 0xFFFF0000;    // Red
    private static final int COLOR_GPU_LINE = 0xFF00AAFF;     // Cyan
    private static final int COLOR_CPU_LINE = 0xFFFF6600;     // Orange
    private static final int COLOR_TARGET_LINE = 0xFFFFFFFF;  // White
    private static final int COLOR_TEXT = 0xFFFFFFFF;         // White
    private static final int COLOR_TEXT_BAD = 0xFFFF4444;     // Light red

    // Performance data storage
    private final Deque<Float> gpuFrameHistory = new ArrayDeque<>();
    private final Deque<Float> cpuFrameHistory = new ArrayDeque<>();
    private final Deque<Float> fpsHistory = new ArrayDeque<>();
    private final Deque<Float> headroomHistory = new ArrayDeque<>();

    // Smoothed values for display
    private float smoothedGpuTime = 0f;
    private float smoothedCpuTime = 0f;
    private float smoothedCpuWork = 0f;
    private float smoothedCpuWait = 0f;
    private float smoothedTargetTime = 0f;
    private float smoothedFps = 0f;
    private float smoothedHeadroom = 0f;
    private int totalDroppedFrames = 0;
    private int renderWidth = 0;
    private int renderHeight = 0;
    private int displayWidth = 0;
    private int displayHeight = 0;
    private boolean xrSessionRunning = false;

    // State tracking
    private boolean isVisible = false;
    private long lastUpdateTime = 0;

    // Position and layout
    private int hudX = 10;
    private int hudY = 10;
    private int hudWidth = CHART_WIDTH + MARGIN * 2;
    private int hudHeight = CHART_HEIGHT * 2 + 100; // Space for 2 charts + text

    /**
     * Update performance data from VRProxy
     * Call this each frame when VR is active
     */
    public void update() {
        // Update configuration-dependent values
        updateIntervalMs = 1000 / VRPerformanceConfig.getMaxUpdateFrequency();
        historySize = VRPerformanceConfig.getHistorySizeSamples();
        smoothingFactor = VRPerformanceConfig.smoothingFactor;
        xrSessionRunning = VRProxy.isSessionRunning();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < updateIntervalMs) {
            return; // Throttle updates to maintain performance
        }
        lastUpdateTime = currentTime;

        // Get fresh performance data
        float[] stats = VRProxy.getPerformanceStats();
        if (stats == null || stats.length < 8) {
            return;
        }

        // Extract values: [gpuMs, cpuFrameMs, cpuWorkMs, cpuWaitMs, targetMs, fps, drops, headroom]
        float gpuTime = stats[0];
        float cpuTime = stats[1];
        float cpuWork = stats[2];
        float cpuWait = stats[3];
        float targetTime = stats[4];
        float fps = stats[5];
        int droppedFrames = (int) stats[6];
        float headroom = stats[7];
        // Update smoothed values
        smoothedGpuTime = GuiCompat.lerpF(smoothingFactor, smoothedGpuTime, gpuTime);
        smoothedCpuTime = GuiCompat.lerpF(smoothingFactor, smoothedCpuTime, cpuTime);
        smoothedCpuWork = GuiCompat.lerpF(smoothingFactor, smoothedCpuWork, cpuWork);
        smoothedCpuWait = GuiCompat.lerpF(smoothingFactor, smoothedCpuWait, cpuWait);
        smoothedTargetTime = GuiCompat.lerpF(smoothingFactor, smoothedTargetTime, targetTime);
        smoothedFps = GuiCompat.lerpF(smoothingFactor, smoothedFps, fps);
        smoothedHeadroom = GuiCompat.lerpF(smoothingFactor, smoothedHeadroom, headroom);
        totalDroppedFrames = droppedFrames;

        // Update history (maintain fixed size for performance)
        addToHistory(gpuFrameHistory, gpuTime);
        addToHistory(cpuFrameHistory, cpuTime);
        addToHistory(fpsHistory, fps);
        addToHistory(headroomHistory, headroom);

        // Resolution stats: render (scaled) vs display/recommended (runtime target)
        renderWidth = VRProxy.getEyeRenderWidth();
        renderHeight = VRProxy.getEyeRenderHeight();
        int[] recommended = VRProxy.getRecommendedResolution();
        if (recommended != null && recommended.length >= 2) {
            displayWidth = recommended[0];
            displayHeight = recommended[1];
        }

        // Apply adaptive performance optimizations
        VRPerformanceConfig.optimizeForCurrentState(true, fps);
    }

    /**
     * Add value to history queue with size limit
     */
    private void addToHistory(Deque<Float> history, float value) {
        if (history.size() >= historySize) {
            history.removeFirst();
        }
        history.addLast(value);
    }

    /**
     * Render the performance HUD
     */
    public void render(Object context, int screenWidth, int screenHeight) {
        if (!isVisible) return;
        if (!VRPerformanceConfig.showDetailedCharts && !VRPerformanceConfig.showTextMetrics) return;

        updatePosition(screenWidth, screenHeight);
        int currentY = hudY;

        int bgColor = (int)(VRPerformanceConfig.getHudOpacityFloat() * 255) << 24;
        GuiCompat.fill(context, hudX, hudY, hudX + hudWidth, hudY + hudHeight, bgColor);

        Object font = GuiCompat.getClientFont();

        if (VRPerformanceConfig.showTextMetrics) {
            GuiCompat.drawText(context, font, GuiCompat.txtLit("VR Performance"),
                hudX + MARGIN, currentY + MARGIN, COLOR_TEXT, false);
        }
        currentY += 20;

        if (VRPerformanceConfig.showTextMetrics) {
            renderTextMetrics(context, currentY);
        }
        currentY += 72;

        if (VRPerformanceConfig.showDetailedCharts) {
            renderFrameTimeChart(context, hudX + MARGIN, currentY, CHART_WIDTH, CHART_HEIGHT);
            currentY += CHART_HEIGHT + 10;
            if (VRPerformanceConfig.showFpsChart || VRPerformanceConfig.showHeadroomChart) {
                renderPerformanceChart(context, hudX + MARGIN, currentY, CHART_WIDTH, CHART_HEIGHT);
            }
        }
    }

    /**
     * Render text-based performance metrics
     */
    private void renderTextMetrics(Object ctx, int y) {
        Object font = GuiCompat.getClientFont();
        int lineHeight = 10;
        int xLeft = hudX + MARGIN;
        int xRight = hudX + (CHART_WIDTH / 2) + 8;

        int fpsColor = getFpsColor(smoothedFps);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(xrSessionRunning ? String.format("FPS: %.1f", smoothedFps) : "FPS: N/A"),
            xLeft, y, fpsColor, false);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(String.format("GPU: %.1fms", smoothedGpuTime)),
            xLeft, y + lineHeight, COLOR_GPU_LINE, false);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(String.format("Frame: %.1fms", smoothedCpuTime)),
            xLeft, y + lineHeight * 2, COLOR_CPU_LINE, false);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(renderWidth > 0 ? String.format("Render: %dx%d", renderWidth, renderHeight) : "Render: N/A"),
            xLeft, y + lineHeight * 3, COLOR_TEXT, false);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(xrSessionRunning ? String.format("Work: %.1fms", smoothedCpuWork) : "Work: N/A"),
            xLeft, y + lineHeight * 4, COLOR_CPU_LINE, false);

        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(xrSessionRunning ? String.format("Target: %.1fms", smoothedTargetTime) : "Target: N/A"),
            xRight, y, COLOR_TARGET_LINE, false);
        int headroomColor = getHeadroomColor(smoothedHeadroom);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(xrSessionRunning ? String.format("Headroom: %.1f%%", smoothedHeadroom * 100) : "Headroom: N/A"),
            xRight, y + lineHeight, headroomColor, false);
        int dropColor = totalDroppedFrames > 0 ? COLOR_TEXT_BAD : COLOR_TEXT;
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(xrSessionRunning ? String.format("Drops: %d", totalDroppedFrames) : "Drops: N/A"),
            xRight, y + lineHeight * 2, dropColor, false);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(displayWidth > 0 ? String.format("Display: %dx%d", displayWidth, displayHeight) : "Display: N/A"),
            xRight, y + lineHeight * 3, COLOR_TEXT, false);
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(xrSessionRunning ? String.format("Wait: %.1fms", smoothedCpuWait) : "Wait: N/A"),
            xRight, y + lineHeight * 4, COLOR_TEXT, false);
    }

    /**
     * Render frame time chart (GPU vs CPU vs Target)
     */
    private void renderFrameTimeChart(Object ctx, int chartX, int chartY, int chartW, int chartH) {
        if (gpuFrameHistory.isEmpty()) return;
        GuiCompat.fill(ctx, chartX, chartY, chartX + chartW, chartY + chartH, 0x40000000);

        Object font = GuiCompat.getClientFont();
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(String.format("FT | R %dx%d D %dx%d", renderWidth, renderHeight, displayWidth, displayHeight)),
            chartX + 3, chartY + 2, COLOR_TEXT, false);

        float targetMs = VRProxy.isEnabled() ? 16.67f : 16.67f;
        float[] targetStats = VRProxy.getPerformanceStats();
        if (targetStats != null && targetStats.length >= 8 && targetStats[4] > 0) targetMs = targetStats[4];
        float maxMs = Math.max(targetMs * 1.5f, 30f);

        if (VRPerformanceConfig.showTargetLine) {
            int targetY = chartY + chartH - (int)((targetMs / maxMs) * chartH);
            GuiCompat.fill(ctx, chartX, targetY, chartX + chartW, targetY + 1, COLOR_TARGET_LINE);
        }
        if (VRPerformanceConfig.showGpuLine) renderLineChart(ctx, chartX, chartY, chartW, chartH, gpuFrameHistory, maxMs, COLOR_GPU_LINE);
        if (VRPerformanceConfig.showCpuLine) renderLineChart(ctx, chartX, chartY, chartW, chartH, cpuFrameHistory, maxMs, COLOR_CPU_LINE);
    }

    private void renderPerformanceChart(Object ctx, int chartX, int chartY, int chartW, int chartH) {
        if (fpsHistory.isEmpty()) return;

        GuiCompat.fill(ctx, chartX, chartY, chartX + chartW, chartY + chartH, 0x40000000);

        Object font = GuiCompat.getClientFont();
        GuiCompat.drawText(ctx, font,
            GuiCompat.txtLit(String.format("FPS/Headroom | R %dx%d D %dx%d", renderWidth, renderHeight, displayWidth, displayHeight)),
            chartX + 3, chartY + 2, COLOR_TEXT, false);

        int usedHeight = 0;
        if (VRPerformanceConfig.showFpsChart) {
            int fpsChartHeight = VRPerformanceConfig.showHeadroomChart ? chartH / 2 : chartH;
            renderLineChart(ctx, chartX, chartY + usedHeight, chartW, fpsChartHeight, fpsHistory, 120f, COLOR_FRAME_GOOD);
            usedHeight += fpsChartHeight;
        }
        if (VRPerformanceConfig.showHeadroomChart) {
            int headroomChartHeight = VRPerformanceConfig.showFpsChart ? chartH / 2 : chartH;
            renderHeadroomChart(ctx, chartX, chartY + usedHeight, chartW, headroomChartHeight);
        }
    }

    private void renderLineChart(Object ctx, int chartX, int chartY, int chartW, int chartH,
                                Deque<Float> data, float maxValue, int color) {
        if (data.size() < 2) return;
        Float[] values = data.toArray(new Float[0]);
        int dataSize = values.length;
        for (int i = 0; i < dataSize - 1; i++) {
            float val1 = GuiCompat.clampF(values[i] / maxValue, 0f, 1f);
            float val2 = GuiCompat.clampF(values[i + 1] / maxValue, 0f, 1f);
            int x1 = chartX + (i * chartW) / dataSize;
            int x2 = chartX + ((i + 1) * chartW) / dataSize;
            int y1 = chartY + chartH - (int)(val1 * chartH);
            int y2 = chartY + chartH - (int)(val2 * chartH);
            drawLine(ctx, x1, y1, x2, y2, color);
        }
    }

    private void renderHeadroomChart(Object ctx, int chartX, int chartY, int chartW, int chartH) {
        if (headroomHistory.isEmpty()) return;
        int zeroY = chartY + chartH / 2;
        GuiCompat.fill(ctx, chartX, zeroY, chartX + chartW, zeroY + 1, 0x80FFFFFF);

        Float[] values = headroomHistory.toArray(new Float[0]);
        int dataSize = values.length;
        for (int i = 0; i < dataSize - 1; i++) {
            float val1 = GuiCompat.clampF(values[i], -0.5f, 0.5f);
            float val2 = GuiCompat.clampF(values[i + 1], -0.5f, 0.5f);
            int x1 = chartX + (i * chartW) / dataSize;
            int x2 = chartX + ((i + 1) * chartW) / dataSize;
            int y1 = zeroY - (int)(val1 * chartH);
            int y2 = zeroY - (int)(val2 * chartH);
            int lineColor = val1 < 0 ? COLOR_FRAME_BAD : COLOR_FRAME_GOOD;
            drawLine(ctx, x1, y1, x2, y2, lineColor);
        }
    }

    private void drawLine(Object ctx, int x1, int y1, int x2, int y2, int color) {
        if (Math.abs(x2 - x1) > Math.abs(y2 - y1)) {
            if (x1 > x2) { int t = x1; x1 = x2; x2 = t; t = y1; y1 = y2; y2 = t; }
            for (int x = x1; x <= x2; x++) {
                int y = y1 + (y2 - y1) * (x - x1) / (x2 - x1);
                GuiCompat.fill(ctx, x, y, x + 1, y + 1, color);
            }
        } else {
            if (y1 > y2) { int t = x1; x1 = x2; x2 = t; t = y1; y1 = y2; y2 = t; }
            for (int y = y1; y <= y2; y++) {
                int x = x1 + (x2 - x1) * (y - y1) / (y2 - y1);
                GuiCompat.fill(ctx, x, y, x + 1, y + 1, color);
            }
        }
    }

    /**
     * Get color based on FPS value
     */
    private int getFpsColor(float fps) {
        if (fps >= VRPerformanceConfig.goodFpsThreshold) return COLOR_FRAME_GOOD;
        if (fps >= VRPerformanceConfig.warningFpsThreshold) return COLOR_FRAME_WARNING;
        return COLOR_FRAME_BAD;
    }

    /**
     * Get color based on headroom percentage
     */
    private int getHeadroomColor(float headroom) {
        if (headroom >= VRPerformanceConfig.goodHeadroomThreshold) return COLOR_FRAME_GOOD;
        if (headroom >= VRPerformanceConfig.warningHeadroomThreshold) return COLOR_FRAME_WARNING;
        return COLOR_FRAME_BAD;
    }

    /**
     * Update HUD position based on configuration
     */
    private void updatePosition(int screenWidth, int screenHeight) {
        // Apply HUD scale
        int scaledWidth = (int)(hudWidth * VRPerformanceConfig.hudScale);
        int scaledHeight = (int)(hudHeight * VRPerformanceConfig.hudScale);

        // Position based on configuration
        switch (VRPerformanceConfig.hudPosition) {
            case TOP_LEFT:
                hudX = 10;
                hudY = 10;
                break;
            case TOP_RIGHT:
                hudX = screenWidth - scaledWidth - 10;
                hudY = 10;
                break;
            case BOTTOM_LEFT:
                hudX = 10;
                hudY = screenHeight - scaledHeight - 10;
                break;
            case BOTTOM_RIGHT:
                hudX = screenWidth - scaledWidth - 10;
                hudY = screenHeight - scaledHeight - 10;
                break;
            case CUSTOM:
                // Keep current position if custom
                break;
        }

        // Ensure it stays on screen
        hudX = Math.max(0, Math.min(hudX, screenWidth - scaledWidth));
        hudY = Math.max(0, Math.min(hudY, screenHeight - scaledHeight));
    }

    // Public control methods
    public void setVisible(boolean visible) { this.isVisible = visible; }
    public boolean isVisible() { return isVisible; }
    public void toggle() { isVisible = !isVisible; }

    // Position control
    public void setPosition(int x, int y) {
        this.hudX = x;
        this.hudY = y;
    }

    public int getWidth() { return hudWidth; }
    public int getHeight() { return hudHeight; }
}
