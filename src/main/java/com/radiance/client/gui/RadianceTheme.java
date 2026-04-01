package com.radiance.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class RadianceTheme {

    private RadianceTheme() {
    }

    private static final int BASE_PANEL = 0x0A0A0A;
    private static final int BASE_WIDGET = 0x1A1A2A;
    private static final int BASE_HOVER = 0x2A2A4A;
    private static final int BASE_ACTIVE = 0x3A3A5A;
    private static final int BASE_DROPDOWN = 0x101018;
    private static final int BASE_HEADER = 0x0A0A0A;
    private static final int BASE_BORDER = 0x808080;
    private static final int BASE_BORDER_FOCUS = 0xC0C0E0;
    private static final int BASE_TEXT_PRIMARY = 0xE0E0E0;
    private static final int BASE_TEXT_SECONDARY = 0x909090;
    private static final int BASE_TEXT_ACCENT = 0x8888CC;

    public static final int TEXT_ERROR = 0xFFFF5555;
    public static final int TEXT_SUCCESS = 0xFF55FF55;
    public static final int TEXT_LINK = 0xFF55FFFF;
    public static final int TEXT_PATH = 0xFFFFAA00;
    public static final int SELECTED_BAR = 0xFF6060FF;
    public static final int GPU_TAG = 0xFF707090;

    public static int panelBg;
    public static int widgetBg;
    public static int widgetBgHover;
    public static int widgetBgActive;
    public static int dropdownBg;
    public static int headerBg;
    public static int borderDefault;
    public static int borderFocused;
    public static int textPrimary;
    public static int textSecondary;
    public static int textAccent;

    private static float globalAlpha = 0.55f;
    private static float effectiveAlpha = 0.55f;
    private static final java.util.Map<String, Float> screenAlphaOverrides = new java.util.HashMap<>();

    public static ClickableWidget activeSlider = null;
    private static long fadeStartMs = 0;
    private static boolean fadingOut = false;
    public static final long FADE_OUT_MS = 100;
    public static final long FADE_IN_MS = 150;

    public static boolean peekActive = false;
    private static boolean adaptiveDimmingEnabled = false;
    private static float sceneBrightness = 0.5f;

    static {
        recompute();
    }

    public static void setGlobalAlpha(float alpha) {
        globalAlpha = Math.max(0f, Math.min(1f, alpha));
        recompute();
    }

    public static void setScreenAlpha(String screenName, float alpha) {
        if (alpha < 0) {
            screenAlphaOverrides.remove(screenName);
        } else {
            screenAlphaOverrides.put(screenName, Math.max(0f, Math.min(1f, alpha)));
        }
    }

    public static void setAdaptiveDimmingEnabled(boolean enabled) {
        adaptiveDimmingEnabled = enabled;
        recompute();
    }

    public static void setSceneBrightness(float brightness) {
        sceneBrightness = Math.max(0f, Math.min(1f, brightness));
        if (adaptiveDimmingEnabled) {
            recompute();
        }
    }

    public static void recompute() {
        effectiveAlpha = globalAlpha;
        if (adaptiveDimmingEnabled) {
            float adjustment = (sceneBrightness - 0.5f) * 0.2f;
            effectiveAlpha = Math.max(0f, Math.min(1f, globalAlpha + adjustment));
        }

        panelBg = withAlpha(BASE_PANEL, effectiveAlpha * 0.7f);
        widgetBg = withAlpha(BASE_WIDGET, effectiveAlpha);
        widgetBgHover = withAlpha(BASE_HOVER, effectiveAlpha);
        widgetBgActive = withAlpha(BASE_ACTIVE, effectiveAlpha);
        dropdownBg = withAlpha(BASE_DROPDOWN, Math.min(1f, effectiveAlpha + 0.15f));
        headerBg = withAlpha(BASE_HEADER, effectiveAlpha * 0.5f);
        borderDefault = withAlpha(BASE_BORDER, effectiveAlpha * 0.6f);
        borderFocused = withAlpha(BASE_BORDER_FOCUS, effectiveAlpha);
        textPrimary = withAlpha(BASE_TEXT_PRIMARY, 1.0f);
        textSecondary = withAlpha(BASE_TEXT_SECONDARY, 0.8f);
        textAccent = withAlpha(BASE_TEXT_ACCENT, 1.0f);
    }

    public static int currentPanelBg(Screen screen) {
        if (screen == null) {
            return panelBg;
        }
        Float override = screenAlphaOverrides.get(screen.getClass().getSimpleName());
        if (override == null) {
            return panelBg;
        }
        return withAlpha(BASE_PANEL, override * 0.7f);
    }

    public static void beginSliderFocus(ClickableWidget slider) {
        activeSlider = slider;
        fadeStartMs = System.currentTimeMillis();
        fadingOut = true;
    }

    public static void endSliderFocus() {
        activeSlider = null;
        fadeStartMs = System.currentTimeMillis();
        fadingOut = false;
    }

    public static float inactiveFadeFactor() {
        if (peekActive) {
            return 0f;
        }
        if (activeSlider == null && fadeStartMs == 0) {
            return 1f;
        }

        long elapsed = System.currentTimeMillis() - fadeStartMs;
        if (fadingOut || activeSlider != null) {
            return Math.max(0f, 1f - (elapsed / (float) FADE_OUT_MS));
        }
        float factor = Math.min(1f, elapsed / (float) FADE_IN_MS);
        if (factor >= 1f) {
            fadeStartMs = 0;
        }
        return factor;
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float multiplier) {
        int a = (argb >>> 24) & 0xFF;
        a = Math.max(0, Math.min(255, (int) (a * multiplier)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static void drawOutlinedText(DrawContext ctx, TextRenderer renderer, Text text, int x,
        int y, int color) {
        drawOutlinedText(ctx, renderer, text, x, y, color, 1f);
    }

    public static void drawOutlinedText(DrawContext ctx, TextRenderer renderer, Text text, int x,
        int y, int color, float alphaMult) {
        int outlineColor = withAlpha(0x000000, 0.4f * alphaMult);
        int mainColor = scaleAlpha(color, alphaMult);

        ctx.drawText(renderer, text, x - 1, y, outlineColor, false);
        ctx.drawText(renderer, text, x + 1, y, outlineColor, false);
        ctx.drawText(renderer, text, x, y - 1, outlineColor, false);
        ctx.drawText(renderer, text, x, y + 1, outlineColor, false);
        ctx.drawText(renderer, text, x, y, mainColor, false);
    }

    public static void drawCategoryHeader(DrawContext ctx, TextRenderer renderer, Text text, int x,
        int y, int width, int entryHeight) {
        drawCategoryHeader(ctx, renderer, text, x, y, width, entryHeight, 1f);
    }

    public static void drawCategoryHeader(DrawContext ctx, TextRenderer renderer, Text text, int x,
        int y, int width, int entryHeight, float alphaMult) {
        if (alphaMult <= 0f) {
            return;
        }

        int lineY = y + entryHeight / 2;
        int textW = renderer.getWidth(text);
        int textX = x + (width - textW) / 2;
        int textY = y + entryHeight - 9 - 1;
        int lineColor = withAlpha(BASE_TEXT_ACCENT, 0.3f * alphaMult);

        if (textX > x + 4) {
            ctx.fill(x, lineY, textX - 4, lineY + 1, lineColor);
        }
        if (textX + textW + 4 < x + width) {
            ctx.fill(textX + textW + 4, lineY, x + width, lineY + 1, lineColor);
        }
        drawOutlinedText(ctx, renderer, text, textX, textY,
            textAccent & 0x00FFFFFF | 0xFF000000, alphaMult);
    }
}
