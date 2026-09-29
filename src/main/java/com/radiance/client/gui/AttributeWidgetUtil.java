package com.radiance.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.Radiance;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.joml.Vector2ic;

final class AttributeWidgetUtil {

    private static final ResourceLocation RESET_TEX = ResourceLocation.fromNamespaceAndPath(Radiance.MOD_ID,
        "textures/gui/reset.png");
    static final String DEFAULT_GROUP = "shader_pack_settings_screen.group.general";

    private static final String DESCRIPTION_SUFFIX = ".description";
    private static final String PERFORMANCE_IMPACT = "settings.performance_impact";
    private static final String PERFORMANCE_LOW = "settings.performance_impact.low";
    private static final String PERFORMANCE_MEDIUM = "settings.performance_impact.medium";
    private static final String PERFORMANCE_HIGH = "settings.performance_impact.high";
    private static final String RESET = "settings.reset";
    private static final String RESET_ALL = "settings.reset_all";
    private static final float GROUP_HEADER_SCALE = 1.2f;
    private static final int GROUP_HEADER_TEXT_COLOR = 0xFFFFFFFF;
    private static final int GROUP_HEADER_LINE_COLOR = 0xFF6F7884;
    private static final int GROUP_HEADER_SIDE_PADDING = 8;
    private static final int GROUP_HEADER_LINE_GAP = 10;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_RIGHT_PADDING = 6;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 24;
    private static final int SCROLLBAR_TRACK_COLOR = 0x66000000;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFF6F7884;
    private static final int SCROLLBAR_THUMB_HOVER_COLOR = 0xFF9AA4B2;
    private static final int RESET_BUTTON_WIDTH = 20;
    private static final int RESET_BUTTON_GAP = 2;
    private static final int GLOBAL_RESET_BUTTON_WIDTH = 24;
    private static final int RESET_TEXTURE_SIZE = 64;
    private static final float RESET_ICON_SIZE_RATIO = 0.5f;
    private static final int TOOLTIP_EDGE_MARGIN = 4;
    private static final int TOOLTIP_CURSOR_OFFSET_X = 12;
    private static final int TOOLTIP_VERTICAL_GAP = 6;
    private static final int TOOLTIP_BOTTOM_PADDING = 3;
    private static final float TOOLTIP_MAX_WIDTH_SCREEN_RATIO = 0.7f;
    private static final int TOOLTIP_MAX_WRAP_WIDTH = 420;
    private static final int TOOLTIP_MIN_WRAP_WIDTH = 120;

    private AttributeWidgetUtil() {
    }

    static Button buildGlobalResetButton(int x, int y, Runnable onPress) {
        return new ResetIconButton(x, y, GLOBAL_RESET_BUTTON_WIDTH, 20, RESET_ALL,
            button -> onPress.run());
    }

    static String groupKey(AttributeConfig cfg) {
        if (cfg == null || cfg.group == null || cfg.group.isBlank()) {
            return DEFAULT_GROUP;
        }
        return cfg.group.trim();
    }

    static void drawGroupHeader(GuiGraphics context, Font textRenderer, Component title,
        int x, int y, int width, int height) {
        Component header = title.copy().withStyle(ChatFormatting.BOLD);
        int textWidth = Math.round(textRenderer.width(header) * GROUP_HEADER_SCALE);
        int textHeight = Math.round(textRenderer.lineHeight * GROUP_HEADER_SCALE);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - textHeight) / 2;
        int lineY = y + height / 2;
        int leftLineStart = x + GROUP_HEADER_SIDE_PADDING;
        int leftLineEnd = textX - GROUP_HEADER_LINE_GAP;
        int rightLineStart = textX + textWidth + GROUP_HEADER_LINE_GAP;
        int rightLineEnd = x + width - GROUP_HEADER_SIDE_PADDING;

        if (leftLineEnd > leftLineStart) {
            context.fill(leftLineStart, lineY, leftLineEnd, lineY + 1, GROUP_HEADER_LINE_COLOR);
        }
        if (rightLineEnd > rightLineStart) {
            context.fill(rightLineStart, lineY, rightLineEnd, lineY + 1, GROUP_HEADER_LINE_COLOR);
        }

        context.pose().pushPose();
        context.pose().scale(GROUP_HEADER_SCALE, GROUP_HEADER_SCALE, 1f);
        context.drawString(textRenderer, header,
            Math.round(textX / GROUP_HEADER_SCALE),
            Math.round(textY / GROUP_HEADER_SCALE), GROUP_HEADER_TEXT_COLOR);
        context.pose().popPose();
    }

    static int clampScroll(int scrollY, int viewportHeight, int contentHeight) {
        int maxScroll = Math.max(0, contentHeight - Math.max(0, viewportHeight));
        if (scrollY > 0) {
            return 0;
        }
        if (scrollY < -maxScroll) {
            return -maxScroll;
        }
        return scrollY;
    }

    static boolean needsVerticalScrollbar(int viewportHeight, int contentHeight) {
        return viewportHeight > 0 && contentHeight > viewportHeight;
    }

    static void drawVerticalScrollbar(GuiGraphics context, int screenWidth, int viewportTop,
        int viewportHeight, int contentHeight, int scrollY, boolean dragging, int mouseX,
        int mouseY) {
        if (!needsVerticalScrollbar(viewportHeight, contentHeight)) {
            return;
        }

        int x = verticalScrollbarX(screenWidth);
        int thumbY = verticalScrollbarThumbY(viewportTop, viewportHeight, contentHeight, scrollY);
        int thumbHeight = verticalScrollbarThumbHeight(viewportHeight, contentHeight);
        boolean hovered = dragging || isVerticalScrollbarThumbHovered(mouseX, mouseY, screenWidth,
            viewportTop, viewportHeight, contentHeight, scrollY);
        int thumbColor = hovered ? SCROLLBAR_THUMB_HOVER_COLOR : SCROLLBAR_THUMB_COLOR;

        context.fill(x, viewportTop, x + SCROLLBAR_WIDTH, viewportTop + viewportHeight,
            SCROLLBAR_TRACK_COLOR);
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
        context.fill(x + 1, thumbY + 1, x + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight - 1,
            thumbColor);
    }

    static boolean isVerticalScrollbarMouseOver(double mouseX, double mouseY, int screenWidth,
        int viewportTop, int viewportHeight, int contentHeight) {
        if (!needsVerticalScrollbar(viewportHeight, contentHeight)) {
            return false;
        }

        int x = verticalScrollbarX(screenWidth);
        return mouseX >= x - 2 && mouseX <= x + SCROLLBAR_WIDTH + 2
            && mouseY >= viewportTop && mouseY <= viewportTop + viewportHeight;
    }

    static int verticalScrollbarDragOffsetY(double mouseY, int viewportTop, int viewportHeight,
        int contentHeight, int scrollY) {
        int thumbY = verticalScrollbarThumbY(viewportTop, viewportHeight, contentHeight, scrollY);
        int thumbHeight = verticalScrollbarThumbHeight(viewportHeight, contentHeight);
        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            return (int) Math.round(mouseY) - thumbY;
        }
        return thumbHeight / 2;
    }

    static int scrollFromVerticalScrollbarDrag(double mouseY, int dragOffsetY, int viewportTop,
        int viewportHeight, int contentHeight) {
        if (!needsVerticalScrollbar(viewportHeight, contentHeight)) {
            return 0;
        }

        int thumbHeight = verticalScrollbarThumbHeight(viewportHeight, contentHeight);
        int travel = viewportHeight - thumbHeight;
        if (travel <= 0) {
            return 0;
        }

        int thumbY = (int) Math.round(mouseY) - dragOffsetY;
        float progress = Mth.clamp((thumbY - viewportTop) / (float) travel, 0f, 1f);
        return -Math.round(progress * (contentHeight - viewportHeight));
    }

    private static int verticalScrollbarX(int screenWidth) {
        return screenWidth - SCROLLBAR_RIGHT_PADDING - SCROLLBAR_WIDTH;
    }

    private static int verticalScrollbarThumbHeight(int viewportHeight, int contentHeight) {
        int thumbHeight = Math.round(viewportHeight * (viewportHeight / (float) contentHeight));
        return Mth.clamp(thumbHeight, SCROLLBAR_MIN_THUMB_HEIGHT, viewportHeight);
    }

    private static int verticalScrollbarThumbY(int viewportTop, int viewportHeight,
        int contentHeight, int scrollY) {
        int thumbHeight = verticalScrollbarThumbHeight(viewportHeight, contentHeight);
        int travel = viewportHeight - thumbHeight;
        if (travel <= 0) {
            return viewportTop;
        }

        int maxScroll = contentHeight - viewportHeight;
        float progress = maxScroll <= 0 ? 0f : Mth.clamp(-scrollY / (float) maxScroll,
            0f, 1f);
        return viewportTop + Math.round(progress * travel);
    }

    private static boolean isVerticalScrollbarThumbHovered(int mouseX, int mouseY, int screenWidth,
        int viewportTop, int viewportHeight, int contentHeight, int scrollY) {
        int x = verticalScrollbarX(screenWidth);
        int thumbY = verticalScrollbarThumbY(viewportTop, viewportHeight, contentHeight, scrollY);
        int thumbHeight = verticalScrollbarThumbHeight(viewportHeight, contentHeight);
        return mouseX >= x - 2 && mouseX <= x + SCROLLBAR_WIDTH + 2
            && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }

    static TooltipFocus tooltipFocus(int left, int top, int right, int bottom) {
        int x0 = Math.min(left, right);
        int y0 = Math.min(top, bottom);
        int x1 = Math.max(left, right);
        int y1 = Math.max(top, bottom);
        return new TooltipFocus(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    static void drawAttributeTooltip(GuiGraphics context, Font textRenderer, Module module,
        AttributeConfig cfg, TooltipFocus focus, int mouseX, int mouseY) {
        List<Component> lines = buildAttributeTooltip(module, cfg);
        if (!lines.isEmpty()) {
            List<FormattedCharSequence> orderedLines = wrapTooltipLines(textRenderer, lines,
                tooltipMaxWidth(context));
            context.renderTooltip(textRenderer, orderedLines, new AttributeTooltipPositioner(focus),
                mouseX, mouseY);
        }
    }

    private static int tooltipMaxWidth(GuiGraphics context) {
        int screenWidth = Math.max(1, context.guiWidth());
        int availableWidth = Math.max(1, screenWidth - TOOLTIP_EDGE_MARGIN * 2);
        int ratioWidth = Math.max(1, Math.round(screenWidth * TOOLTIP_MAX_WIDTH_SCREEN_RATIO));
        int cappedWidth = Math.min(ratioWidth, TOOLTIP_MAX_WRAP_WIDTH);
        int minWidth = Math.min(TOOLTIP_MIN_WRAP_WIDTH, availableWidth);
        return Math.max(minWidth, Math.min(cappedWidth, availableWidth));
    }

    private static List<FormattedCharSequence> wrapTooltipLines(Font textRenderer, List<Component> lines,
        int maxWidth) {
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        for (Component line : lines) {
            wrappedLines.addAll(textRenderer.split(line, maxWidth));
        }
        return wrappedLines;
    }

    private static List<Component> buildAttributeTooltip(Module module, AttributeConfig cfg) {
        List<Component> lines = new ArrayList<>();
        Component description = descriptionText(module, cfg);
        if (description != null) {
            lines.add(description);
        }

        Component performance = performanceImpactText(cfg);
        if (performance != null) {
            lines.add(performance);
        }
        return lines;
    }

    private static Component descriptionText(Module module, AttributeConfig cfg) {
        if (module == null || cfg == null || cfg.name == null || cfg.name.isBlank()) {
            return null;
        }

        boolean explicitDescription = cfg.description != null && !cfg.description.isBlank();
        String descriptionKey = explicitDescription ? cfg.description.trim() : cfg.name + DESCRIPTION_SUFFIX;
        Component translated = module.translateText(descriptionKey);
        if (explicitDescription) {
            return translated.getString().equals(descriptionKey) && !hasTranslation(module, descriptionKey)
                ? Component.literal(descriptionKey)
                : translated;
        }

        return hasTranslation(module, descriptionKey) ? translated : null;
    }

    private static boolean hasTranslation(Module module, String key) {
        if (module != null && module.dynamicTranslations != null
            && module.dynamicTranslations.containsKey(key)) {
            return true;
        }
        return !Component.translatable(key).getString().equals(key);
    }

    private static Component performanceImpactText(AttributeConfig cfg) {
        String impact = normalizePerformanceImpact(cfg == null ? null : cfg.performanceImpact);
        if (impact == null) {
            return null;
        }

        String key;
        ChatFormatting color;
        switch (impact) {
            case "low" -> {
                key = PERFORMANCE_LOW;
                color = ChatFormatting.GREEN;
            }
            case "medium" -> {
                key = PERFORMANCE_MEDIUM;
                color = ChatFormatting.YELLOW;
            }
            case "high" -> {
                key = PERFORMANCE_HIGH;
                color = ChatFormatting.RED;
            }
            default -> {
                return null;
            }
        }

        return Component.translatable(PERFORMANCE_IMPACT)
            .append(Component.literal(": "))
            .append(Component.translatable(key).withStyle(color));
    }

    private static String normalizePerformanceImpact(String performanceImpact) {
        if (performanceImpact == null || performanceImpact.isBlank()) {
            return null;
        }

        return switch (performanceImpact.trim().toLowerCase(Locale.ROOT)) {
            case "low", "低" -> "low";
            case "medium", "mid", "中" -> "medium";
            case "high", "高" -> "high";
            default -> null;
        };
    }

    static boolean shouldValidateBorder(String type) {
        return type.equals("int") || type.equals("float") || type.equals("string") || type.equals(
            "vec3");
    }

    static boolean isStrictInt(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isStrictFloat(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void drawBorder(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    static boolean isDefaultValue(AttributeConfig cfg) {
        if (cfg == null) {
            return true;
        }

        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);
        String value = cfg.value == null ? "" : cfg.value;
        String defaultValue = defaultValue(cfg);

        if (type.equals("int") || type.startsWith("int_range:")) {
            if (isStrictInt(value) && isStrictInt(defaultValue)) {
                return Integer.parseInt(value) == Integer.parseInt(defaultValue);
            }
        } else if (type.equals("float") || type.startsWith("float_range:")) {
            if (isStrictFloat(value) && isStrictFloat(defaultValue)) {
                return Float.compare(Float.parseFloat(value), Float.parseFloat(defaultValue)) == 0;
            }
        } else if (type.equals("vec3")) {
            float[] current = parseVec3(value);
            float[] defaults = parseVec3(defaultValue);
            return Float.compare(current[0], defaults[0]) == 0
                && Float.compare(current[1], defaults[1]) == 0
                && Float.compare(current[2], defaults[2]) == 0;
        }

        return Objects.equals(value, defaultValue);
    }

    static boolean hasNonDefaultValue(List<AttributeConfig> configs) {
        if (configs == null) {
            return false;
        }
        for (AttributeConfig cfg : configs) {
            if (cfg != null && !isDefaultValue(cfg)) {
                return true;
            }
        }
        return false;
    }

    static boolean resetAttributeToDefault(Module module, AttributeConfig cfg) {
        if (module == null || cfg == null) {
            return false;
        }
        return Pipeline.setAttributeValue(module, cfg, defaultValue(cfg));
    }

    static boolean resetAttributesToDefaults(Module module, List<AttributeConfig> configs) {
        boolean changed = false;
        if (module == null || configs == null) {
            return false;
        }
        for (AttributeConfig cfg : configs) {
            changed |= resetAttributeToDefault(module, cfg);
        }
        return Pipeline.reconcilePresetSelectors(module) || changed;
    }

    static void syncWidgetVisibility(List<AbstractWidget> widgets, boolean rowVisible) {
        for (AbstractWidget widget : widgets) {
            if (widget instanceof AttributeResetButton resetButton) {
                resetButton.syncVisibility(rowVisible);
                continue;
            }
            widget.visible = rowVisible;
            widget.active = rowVisible;
        }
    }

    static boolean isResetButton(AbstractWidget widget) {
        return widget instanceof AttributeResetButton;
    }

    static void layoutWidgets(List<AbstractWidget> widgets, int x, int y, int singleWidth,
        int tripleWidth, int gap) {
        AttributeResetButton resetButton = resetButton(widgets);
        List<AbstractWidget> valueWidgets = valueWidgets(widgets);
        int valueX = x;

        if (resetButton != null) {
            resetButton.setX(x);
            resetButton.setY(y);
            resetButton.setWidth(RESET_BUTTON_WIDTH);
            valueX += RESET_BUTTON_WIDTH + RESET_BUTTON_GAP;
        }

        if (valueWidgets.size() == 1) {
            AbstractWidget w = valueWidgets.get(0);
            w.setX(valueX);
            w.setY(y);
            w.setWidth(singleWidth);
            return;
        }

        if (valueWidgets.size() == 3) {
            for (int i = 0; i < 3; i++) {
                AbstractWidget cw = valueWidgets.get(i);
                cw.setX(valueX + i * (tripleWidth + gap));
                cw.setY(y);
                cw.setWidth(tripleWidth);
            }
        }
    }

    static int totalWidgetWidth(List<AbstractWidget> widgets, int singleWidth, int tripleWidth, int gap) {
        int valueWidth = singleWidth;
        if (valueWidgets(widgets).size() == 3) {
            valueWidth = (tripleWidth * 3) + (gap * 2);
        }
        return valueWidth + (resetButton(widgets) == null ? 0 : RESET_BUTTON_WIDTH + RESET_BUTTON_GAP);
    }

    private static String defaultValue(AttributeConfig cfg) {
        if (cfg == null) {
            return "";
        }
        if (cfg.defaultValue != null) {
            return cfg.defaultValue;
        }
        return cfg.value == null ? "" : cfg.value;
    }

    private static AttributeResetButton resetButton(List<AbstractWidget> widgets) {
        if (widgets == null) {
            return null;
        }
        for (AbstractWidget widget : widgets) {
            if (widget instanceof AttributeResetButton resetButton) {
                return resetButton;
            }
        }
        return null;
    }

    private static List<AbstractWidget> valueWidgets(List<AbstractWidget> widgets) {
        List<AbstractWidget> valueWidgets = new ArrayList<>();
        if (widgets == null) {
            return valueWidgets;
        }
        for (AbstractWidget widget : widgets) {
            if (widget instanceof AttributeResetButton) {
                continue;
            }
            valueWidgets.add(widget);
        }
        return valueWidgets;
    }

    private static void syncValueWidgetsFromConfig(List<AbstractWidget> widgets, AttributeConfig cfg,
        Module module) {
        if (widgets == null || cfg == null) {
            return;
        }

        for (AbstractWidget widget : widgets) {
            if (widget instanceof ConfigValueWidget valueWidget) {
                valueWidget.syncFromConfig();
            }
        }

        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);
        if (type.equals("bool")) {
            for (AbstractWidget widget : widgets) {
                if (widget instanceof Button button && !(widget instanceof AttributeResetButton)) {
                    boolean value = "render_pipeline.true".equalsIgnoreCase(cfg.value);
                    button.setMessage(module.translateText(value
                        ? "render_pipeline.true"
                        : "render_pipeline.false"));
                }
            }
        }

        List<EditBox> textFields = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            if (widget instanceof EditBox textField) {
                textFields.add(textField);
            }
        }

        if (textFields.isEmpty()) {
            return;
        }

        if (type.equals("vec3")) {
            float[] values = parseVec3(cfg.value);
            for (int i = 0; i < Math.min(3, textFields.size()); i++) {
                textFields.get(i).setValue(trimFloat(values[i]));
            }
            return;
        }

        for (EditBox textField : textFields) {
            textField.setValue(cfg.value == null ? "" : cfg.value);
        }
    }

    static List<AbstractWidget> buildWidgets(Module module, AttributeConfig cfg, Font textRenderer,
        int width,
        int vec3ComponentWidth) {
        return buildWidgets(module, cfg, textRenderer, width, vec3ComponentWidth, null);
    }

    static List<AbstractWidget> buildWidgets(Module module, AttributeConfig cfg, Font textRenderer,
        int width,
        int vec3ComponentWidth,
        Runnable afterValueChange) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);
        List<AbstractWidget> valueWidgets;

        if (type.startsWith("enum:")) {
            valueWidgets = List.of(buildEnumWidget(module, cfg, cfg.type.substring(5), width,
                afterValueChange));
        } else if (type.startsWith("int_range:")) {
            valueWidgets = List.of(buildIntRange(module, cfg, cfg.type.substring(10), width));
        } else if (type.startsWith("float_range:")) {
            valueWidgets = List.of(buildFloatRange(module, cfg, cfg.type.substring(12), width));
        } else {
            valueWidgets = switch (type) {
                case "bool" -> List.of(buildBoolWidget(module, cfg, width));
                case "int" -> List.of(buildIntWidget(module, cfg, textRenderer, width));
                case "float" -> List.of(buildFloatWidget(module, cfg, textRenderer, width));
                case "string" -> List.of(buildStringWidget(module, cfg, textRenderer, width));
                case "vec3" -> buildVec3Widget(module, cfg, textRenderer, vec3ComponentWidth);
                default -> List.of(buildStringWidget(module, cfg, textRenderer, width));
            };
        }

        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(new AttributeResetButton(module, cfg, valueWidgets, afterValueChange));
        widgets.addAll(valueWidgets);
        return widgets;
    }

    private static AbstractWidget buildBoolWidget(Module module, AttributeConfig cfg, int width) {
        boolean b = "render_pipeline.true".equalsIgnoreCase(cfg.value);
        return Button.builder(
            module.translateText(b ? "render_pipeline.true" : "render_pipeline.false"), btn -> {
                boolean nv = !"render_pipeline.true".equalsIgnoreCase(cfg.value);
                Pipeline.setAttributeValue(
                    module, cfg, nv ? "render_pipeline.true" : "render_pipeline.false");
                btn.setMessage(module.translateText(cfg.value));
            }).bounds(0, 0, width, 20).build();
    }

    private static AbstractWidget buildEnumWidget(Module module, AttributeConfig cfg, String raw, int width,
        Runnable afterValueChange) {
        String[] values = raw.isEmpty() ? new String[]{"<empty>"} : raw.split("-");
        return new EnumAttributeButton(module, cfg, values, width, afterValueChange);
    }

    private static AbstractWidget buildIntWidget(Module module, AttributeConfig cfg, Font textRenderer,
        int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(64);
        tf.setValue(cfg.value == null ? "" : cfg.value);
        tf.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        tf.setResponder(text -> {
            if (isStrictInt(text)) {
                Pipeline.setAttributeValue(module, cfg, text);
            }
        });
        return tf;
    }

    private static AbstractWidget buildFloatWidget(Module module, AttributeConfig cfg, Font textRenderer,
        int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(64);
        tf.setValue(cfg.value == null ? "" : cfg.value);
        tf.setFilter(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        tf.setResponder(text -> {
            if (isStrictFloat(text)) {
                Pipeline.setAttributeValue(module, cfg, text);
            }
        });
        return tf;
    }

    private static AbstractWidget buildStringWidget(Module module, AttributeConfig cfg, Font textRenderer,
        int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(128);
        tf.setValue(cfg.value == null ? "" : cfg.value);
        tf.setResponder(text -> Pipeline.setAttributeValue(module, cfg, text));
        return tf;
    }

    private static List<AbstractWidget> buildVec3Widget(Module module, AttributeConfig cfg,
        Font textRenderer,
        int componentWidth) {
        if (cfg.value == null || cfg.value.isEmpty()) {
            Pipeline.setAttributeValue(module, cfg, "0,0,0");
        }

        float[] v = parseVec3(cfg.value);
        EditBox x = vecField(textRenderer, v[0], componentWidth);
        EditBox y = vecField(textRenderer, v[1], componentWidth);
        EditBox z = vecField(textRenderer, v[2], componentWidth);

        Runnable syncIfValid = () -> {
            String sx = x.getValue();
            String sy = y.getValue();
            String sz = z.getValue();

            if (isStrictFloat(sx) && isStrictFloat(sy) && isStrictFloat(sz)) {
                Pipeline.setAttributeValue(module, cfg, sx + "," + sy + "," + sz);
            }
        };

        x.setResponder(s -> syncIfValid.run());
        y.setResponder(s -> syncIfValid.run());
        z.setResponder(s -> syncIfValid.run());

        syncIfValid.run();
        return List.of(x, y, z);
    }

    private static EditBox vecField(Font textRenderer, float v, int width) {
        EditBox tf = new EditBox(textRenderer, 0, 0, width, 20, Component.empty());
        tf.setMaxLength(32);
        tf.setValue(trimFloat(v));
        tf.setFilter(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        return tf;
    }

    private static AbstractWidget buildIntRange(Module module, AttributeConfig cfg, String raw, int width) {
        Range r = parseRange(raw);
        int start = (int) r.start;
        int end = (int) r.end;
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }

        int cur = start;
        if (isInt(cfg.value)) {
            cur = Integer.parseInt(cfg.value);
        } else {
            Pipeline.setAttributeValue(module, cfg, String.valueOf(start));
        }
        cur = Mth.clamp(cur, start, end);

        IntRangeSlider slider = new IntRangeSlider(0, 0, width, 20, start, end, cur, module, cfg);
        slider.updateMessage();
        return slider;
    }

    private static AbstractWidget buildFloatRange(Module module, AttributeConfig cfg, String raw, int width) {
        Range r = parseRange(raw);
        float start = (float) r.start;
        float end = (float) r.end;
        if (start > end) {
            float t = start;
            start = end;
            end = t;
        }

        float cur = start;
        if (isFloat(cfg.value)) {
            cur = Float.parseFloat(cfg.value);
        } else {
            Pipeline.setAttributeValue(module, cfg, formatTwoDecimals(start));
        }
        cur = Mth.clamp(cur, start, end);

        FloatRangeSlider slider = new FloatRangeSlider(0, 0, width, 20, start, end, cur, module, cfg);
        slider.updateMessage();
        return slider;
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFloat(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static float[] parseVec3(String s) {
        if (s == null || s.isEmpty()) {
            return new float[]{0, 0, 0};
        }

        String[] p = s.split("[,\\s]+");
        float x = p.length > 0 && isFloat(p[0]) ? Float.parseFloat(p[0]) : 0;
        float y = p.length > 1 && isFloat(p[1]) ? Float.parseFloat(p[1]) : 0;
        float z = p.length > 2 && isFloat(p[2]) ? Float.parseFloat(p[2]) : 0;
        return new float[]{x, y, z};
    }

    private static String formatTwoDecimals(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String trimFloat(float v) {
        String s = Float.toString(v);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static Range parseRange(String raw) {
        int dash = raw.lastIndexOf('-');
        if (dash <= 0) {
            return new Range(0, 1);
        }
        String a = raw.substring(0, dash);
        String b = raw.substring(dash + 1);
        double start = 0;
        double end = 1;
        try {
            start = Double.parseDouble(a);
            end = Double.parseDouble(b);
        } catch (Exception ignored) {
        }
        return new Range(start, end);
    }

    record TooltipFocus(int left, int top, int width, int height) {

        int right() {
            return left + width;
        }

        int bottom() {
            return top + height;
        }
    }

    private static class AttributeTooltipPositioner implements ClientTooltipPositioner {

        private final TooltipFocus focus;

        private AttributeTooltipPositioner(TooltipFocus focus) {
            this.focus = focus;
        }

        @Override
        public Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int width,
            int height) {
            int tooltipX = x + TOOLTIP_CURSOR_OFFSET_X;
            int maxX = Math.max(TOOLTIP_EDGE_MARGIN,
                screenWidth - width - TOOLTIP_EDGE_MARGIN);
            tooltipX = Mth.clamp(tooltipX, TOOLTIP_EDGE_MARGIN, maxX);

            int minY = TOOLTIP_EDGE_MARGIN;
            int maxY = Math.max(minY,
                screenHeight - height - TOOLTIP_EDGE_MARGIN - TOOLTIP_BOTTOM_PADDING);
            int belowY = focus.bottom() + TOOLTIP_VERTICAL_GAP;
            int aboveY = focus.top() - height - TOOLTIP_VERTICAL_GAP;
            boolean fitsBelow = belowY <= maxY;
            boolean fitsAbove = aboveY >= minY;

            int tooltipY;
            if (fitsBelow) {
                tooltipY = belowY;
            } else if (fitsAbove) {
                tooltipY = aboveY;
            } else {
                int spaceBelow = Math.max(0, maxY - belowY);
                int spaceAbove = Math.max(0, aboveY - minY);
                tooltipY = spaceBelow >= spaceAbove ? belowY : aboveY;
            }
            tooltipY = Mth.clamp(tooltipY, minY, maxY);

            return new Vector2i(tooltipX, tooltipY);
        }
    }

    private interface ConfigValueWidget {

        void syncFromConfig();
    }

    private static class ResetIconButton extends Button {

        private ResetIconButton(int x, int y, int width, int height, String tooltipKey,
            OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);

            int iconSize = Math.round(Math.min(getWidth(), getHeight()) * RESET_ICON_SIZE_RATIO);
            int iconX = getX() + (getWidth() - iconSize) / 2;
            int iconY = getY() + (getHeight() - iconSize) / 2;
            int color = active ? 0xFFFFFFFF : 0xFFA0A0A0;

            RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >> 24) & 0xFF) / 255f);
            context.blit(RenderType::guiTextured, RESET_TEX, iconX, iconY, 0, 0,
                iconSize, iconSize, RESET_TEXTURE_SIZE, RESET_TEXTURE_SIZE, RESET_TEXTURE_SIZE,
                RESET_TEXTURE_SIZE);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static class AttributeResetButton extends ResetIconButton {

        private final Module module;
        private final AttributeConfig cfg;
        private final List<AbstractWidget> valueWidgets;
        private final Runnable afterValueChange;

        private AttributeResetButton(Module module, AttributeConfig cfg,
            List<AbstractWidget> valueWidgets, Runnable afterValueChange) {
            super(0, 0, RESET_BUTTON_WIDTH, 20, RESET, button -> {
            });
            this.module = module;
            this.cfg = cfg;
            this.valueWidgets = valueWidgets;
            this.afterValueChange = afterValueChange;
            syncVisibility(false);
        }

        @Override
        public void onPress() {
            boolean changed = resetAttributeToDefault(module, cfg);
            syncValueWidgetsFromConfig(valueWidgets, cfg, module);
            syncVisibility(visible || active);
            if (changed && afterValueChange != null) {
                afterValueChange.run();
            }
        }

        private void syncVisibility(boolean rowVisible) {
            boolean show = rowVisible && !isDefaultValue(cfg);
            visible = show;
            active = show;
        }
    }

    private static class EnumAttributeButton extends Button implements ConfigValueWidget {

        private final Module module;
        private final AttributeConfig cfg;
        private final String[] values;
        private final Runnable afterValueChange;
        private int index;

        private EnumAttributeButton(Module module, AttributeConfig cfg, String[] values, int width,
            Runnable afterValueChange) {
            super(0, 0, width, 20, Component.empty(), button -> {
            }, DEFAULT_NARRATION);
            this.module = module;
            this.cfg = cfg;
            this.values = values;
            this.afterValueChange = afterValueChange;
            syncFromConfig();
        }

        @Override
        public void onPress() {
            int nextIndex = nextSelectableIndex();
            if (nextIndex < 0) {
                return;
            }
            boolean changed = Pipeline.setAttributeValue(module, cfg, values[nextIndex]);
            syncFromConfig();
            if (changed && afterValueChange != null) {
                afterValueChange.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            syncFromConfig();
            super.renderWidget(context, mouseX, mouseY, delta);
        }

        @Override
        public void syncFromConfig() {
            index = 0;
            if (cfg.value != null) {
                for (int i = 0; i < values.length; i++) {
                    if (Objects.equals(values[i], cfg.value)) {
                        index = i;
                        break;
                    }
                }
            } else if (values.length > 0) {
                Pipeline.setAttributeValue(module, cfg, values[0]);
            }
            setMessage(module.translateText(values[index]));
        }

        private int nextSelectableIndex() {
            if (values.length == 0) {
                return -1;
            }
            for (int offset = 1; offset <= values.length; offset++) {
                int candidate = (index + offset) % values.length;
                if (!Pipeline.isPresetCustomValue(cfg, values[candidate])) {
                    return candidate;
                }
            }
            return -1;
        }
    }

    private record Range(double start, double end) {

    }

    private static class IntRangeSlider extends AbstractSliderButton implements ConfigValueWidget {

        private final int start;
        private final int end;
        private final Module module;
        private final AttributeConfig cfg;

        public IntRangeSlider(int x, int y, int width, int height, int start, int end, int cur,
            Module module, AttributeConfig cfg) {
            super(x, y, width, height, Component.empty(),
                (cur - (double) start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.module = module;
            this.cfg = cfg;
            this.value = (cur - (double) start) / (double) (end - start);
        }

        private int current() {
            if (end == start) {
                return start;
            }
            return start + (int) Math.round(this.value * (end - start));
        }

        @Override
        public void syncFromConfig() {
            int current = isInt(cfg.value) ? Integer.parseInt(cfg.value) : start;
            current = Mth.clamp(current, start, end);
            this.value = end == start ? 0 : (current - (double) start) / (double) (end - start);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = current();
            setMessage(Component.translatable(Integer.toString(value)));
        }

        @Override
        protected void applyValue() {
            int v = Mth.clamp(current(), start, end);
            Pipeline.setAttributeValue(module, cfg, Integer.toString(v));
        }
    }

    private static class FloatRangeSlider extends AbstractSliderButton implements ConfigValueWidget {

        private final float start;
        private final float end;
        private final Module module;
        private final AttributeConfig cfg;

        public FloatRangeSlider(int x, int y, int width, int height, float start, float end,
            float cur,
            Module module, AttributeConfig cfg) {
            super(x, y, width, height, Component.empty(), (cur - start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.module = module;
            this.cfg = cfg;
            this.value = (cur - start) / (double) (end - start);
        }

        private float current() {
            if (end == start) {
                return start;
            }
            return (float) (start + this.value * (end - start));
        }

        @Override
        public void syncFromConfig() {
            float current = isFloat(cfg.value) ? Float.parseFloat(cfg.value) : start;
            current = Mth.clamp(current, start, end);
            this.value = end == start ? 0 : (current - start) / (double) (end - start);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(formatTwoDecimals(current())));
        }

        @Override
        protected void applyValue() {
            float v = Mth.clamp(current(), start, end);
            Pipeline.setAttributeValue(module, cfg, formatTwoDecimals(v));
        }
    }
}
