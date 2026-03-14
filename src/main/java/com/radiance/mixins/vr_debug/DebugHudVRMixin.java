package com.radiance.mixins.vr_debug;

import com.radiance.client.gui.VRPerformanceManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin to integrate VR performance statistics into the F3 debug overlay
 *
 * This mixin hooks into the debug HUD rendering to add comprehensive VR
 * performance visualization when VR is active and F3 is enabled.
 */
@Mixin(DebugHud.class)
public class DebugHudVRMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private TextRenderer textRenderer;

    /**
     * Inject VR performance text into the left side debug text
     */
    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void addVRPerformanceText(CallbackInfoReturnable<List<String>> cir) {
        List<String> debugText = cir.getReturnValue();

        // Only add VR performance text if VR is active
        if (VRPerformanceManager.getInstance().isF3DisplayEnabled()) {
            String vrSummary = VRPerformanceManager.getInstance().getPerformanceSummary();
            if (vrSummary != null) {
                // Add to the debug text list
                debugText.add("");
                debugText.add("§6VR Performance:");
                debugText.add(vrSummary);

                // Add additional detailed VR info if available
                try {
                    // This will be populated with more detailed stats
                    addDetailedVRStats(debugText);
                } catch (Exception e) {
                    // Silently ignore errors to avoid breaking F3
                }
            }
        }

        cir.setReturnValue(debugText);
    }

    /**
     * Inject VR performance charts into the debug overlay rendering
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void renderVRPerformanceCharts(DrawContext context, CallbackInfo ci) {
        // Render the visual VR performance charts
        try {
            VRPerformanceManager.getInstance().update();
            VRPerformanceManager.getInstance().renderInF3(context,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight());
        } catch (Exception e) {
            // Silently ignore rendering errors to avoid breaking the debug overlay
        }
    }

    /**
     * Add detailed VR statistics to the debug text
     */
    private void addDetailedVRStats(List<String> debugText) {
        VRPerformanceManager manager = VRPerformanceManager.getInstance();

        // This could be expanded to include more detailed information
        // For now, we keep it simple to avoid clutter

        // System information could be added here:
        // - HMD model
        // - Tracking system status
        // - OpenXR runtime info
        // - Render scale
        // - IPD settings
        // etc.
    }
}