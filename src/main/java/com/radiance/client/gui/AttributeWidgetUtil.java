package com.radiance.client.gui;

import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

final class AttributeWidgetUtil {

    private AttributeWidgetUtil() {
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

    static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    static void layoutWidgets(List<ClickableWidget> widgets, int x, int y, int singleWidth,
        int tripleWidth, int gap) {
        if (widgets.size() == 1) {
            ClickableWidget w = widgets.get(0);
            w.setX(x);
            w.setY(y);
            if (w.getWidth() <= 0) {
                w.setWidth(singleWidth);
            }
            return;
        }

        if (widgets.size() == 3 && widgets.stream().allMatch(
            widget -> widget instanceof TextFieldWidget)) {
            for (int i = 0; i < 3; i++) {
                ClickableWidget cw = widgets.get(i);
                cw.setX(x + i * (tripleWidth + gap));
                cw.setY(y);
                cw.setWidth(tripleWidth);
            }
            return;
        }

        int cursorX = x;
        for (ClickableWidget widget : widgets) {
            int preferredWidth = widget.getWidth() > 0 ? widget.getWidth() : singleWidth;
            widget.setX(cursorX);
            widget.setY(y);
            widget.setWidth(preferredWidth);
            cursorX += preferredWidth + gap;
        }
    }

    static int totalWidgetWidth(List<ClickableWidget> widgets, int singleWidth, int tripleWidth, int gap) {
        if (widgets.size() == 3 && widgets.stream().allMatch(
            widget -> widget instanceof TextFieldWidget)) {
            return (tripleWidth * 3) + (gap * 2);
        }
        if (widgets.size() == 1) {
            ClickableWidget widget = widgets.get(0);
            return widget.getWidth() > 0 ? widget.getWidth() : singleWidth;
        }

        int total = 0;
        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            total += widget.getWidth() > 0 ? widget.getWidth() : singleWidth;
            if (i + 1 < widgets.size()) {
                total += gap;
            }
        }
        return total;
    }

    static List<ClickableWidget> buildWidgets(AttributeConfig cfg, Screen owner, TextRenderer textRenderer,
        int width,
        int vec3ComponentWidth) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return List.of(buildEnumWidget(cfg, cfg.type.substring(5), width));
        }

        if (type.startsWith("int_range:")) {
            return buildIntRange(cfg, owner, cfg.type.substring(10), width);
        }

        if (type.startsWith("float_range:")) {
            return buildFloatRange(cfg, owner, cfg.type.substring(12), width);
        }

        return switch (type) {
            case "bool" -> List.of(buildBoolWidget(cfg, width));
            case "int" -> List.of(buildIntWidget(cfg, textRenderer, width));
            case "float" -> List.of(buildFloatWidget(cfg, textRenderer, width));
            case "string" -> List.of(buildStringWidget(cfg, textRenderer, width));
            case "vec3" -> buildVec3Widget(cfg, textRenderer, vec3ComponentWidth);
            default -> List.of(buildStringWidget(cfg, textRenderer, width));
        };
    }

    private static ClickableWidget buildBoolWidget(AttributeConfig cfg, int width) {
        boolean b = "render_pipeline.true".equalsIgnoreCase(cfg.value);
        return ButtonWidget.builder(
            Text.translatable(b ? "render_pipeline.true" : "render_pipeline.false"), btn -> {
                boolean nv = !"render_pipeline.true".equalsIgnoreCase(cfg.value);
                setValue(cfg, nv ? "render_pipeline.true" : "render_pipeline.false");
                btn.setMessage(Text.translatable(cfg.value));
            }).dimensions(0, 0, width, 20).build();
    }

    private static ClickableWidget buildEnumWidget(AttributeConfig cfg, String raw, int width) {
        String[] values = raw.isEmpty() ? new String[]{"<empty>"} : raw.split("-");
        int idx = 0;
        if (cfg.value != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(cfg.value)) {
                    idx = i;
                    break;
                }
            }
        } else {
            cfg.value = values[0];
        }

        int[] index = new int[]{idx};
        return ButtonWidget.builder(Text.translatable(values[index[0]]), btn -> {
            index[0] = (index[0] + 1) % values.length;
            setValue(cfg, values[index[0]]);
            btn.setMessage(Text.translatable(cfg.value));
        }).dimensions(0, 0, width, 20).build();
    }

    private static ClickableWidget buildIntWidget(AttributeConfig cfg, TextRenderer textRenderer,
        int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictInt(text)) {
                setValue(cfg, text);
            }
        });
        return tf;
    }

    private static ClickableWidget buildFloatWidget(AttributeConfig cfg, TextRenderer textRenderer,
        int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictFloat(text)) {
                setValue(cfg, text);
            }
        });
        return tf;
    }

    private static ClickableWidget buildStringWidget(AttributeConfig cfg, TextRenderer textRenderer,
        int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(128);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setChangedListener(text -> setValue(cfg, text));
        return tf;
    }

    private static List<ClickableWidget> buildVec3Widget(AttributeConfig cfg,
        TextRenderer textRenderer,
        int componentWidth) {
        if (cfg.value == null || cfg.value.isEmpty()) {
            cfg.value = "0,0,0";
        }

        float[] v = parseVec3(cfg.value);
        TextFieldWidget x = vecField(textRenderer, v[0], componentWidth);
        TextFieldWidget y = vecField(textRenderer, v[1], componentWidth);
        TextFieldWidget z = vecField(textRenderer, v[2], componentWidth);

        Runnable syncIfValid = () -> {
            String sx = x.getText();
            String sy = y.getText();
            String sz = z.getText();

            if (isStrictFloat(sx) && isStrictFloat(sy) && isStrictFloat(sz)) {
                setValue(cfg, sx + "," + sy + "," + sz);
            }
        };

        x.setChangedListener(s -> syncIfValid.run());
        y.setChangedListener(s -> syncIfValid.run());
        z.setChangedListener(s -> syncIfValid.run());

        syncIfValid.run();
        return List.of(x, y, z);
    }

    private static TextFieldWidget vecField(TextRenderer textRenderer, float v, int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(32);
        tf.setText(trimFloat(v));
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        return tf;
    }

    private static List<ClickableWidget> buildIntRange(AttributeConfig cfg, Screen owner, String raw,
        int width) {
        Range r = parseRange(raw);
        int start = (int) r.start;
        int end = (int) r.end;
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        final int minInt = start;
        final int maxInt = end;

        int cur = minInt;
        if (isInt(cfg.value)) {
            cur = Integer.parseInt(cfg.value);
        } else {
            setValue(cfg, String.valueOf(minInt));
        }
        cur = MathHelper.clamp(cur, minInt, maxInt);

        int sliderWidth = Math.max(80, width - 24);
        IntRangeSlider slider = new IntRangeSlider(0, 0, sliderWidth, 20, minInt, maxInt, cur, cfg,
            cfg instanceof BoundAttributeConfig bound ? bound.defaultValue()
                : Integer.toString(minInt));
        slider.updateMessage();
        ButtonWidget editButton = ButtonWidget.builder(Text.literal("#"), button -> {
            if (owner == null) {
                return;
            }
            MinecraftClient.getInstance().setScreen(new NumericSliderInputScreen(owner, cfg.name,
                true, minInt, maxInt, cfg.value, cfg instanceof BoundAttributeConfig bound
                    ? bound.defaultValue()
                    : Integer.toString(minInt), value -> {
                    setValue(cfg, value);
                    slider.syncFromValueString(value);
                }));
        }).dimensions(0, 0, 20, 20).build();
        return List.of(slider, editButton);
    }

    private static List<ClickableWidget> buildFloatRange(AttributeConfig cfg, Screen owner,
        String raw, int width) {
        Range r = parseRange(raw);
        float start = (float) r.start;
        float end = (float) r.end;
        if (start > end) {
            float t = start;
            start = end;
            end = t;
        }
        final float minFloat = start;
        final float maxFloat = end;

        float cur = minFloat;
        if (isFloat(cfg.value)) {
            cur = Float.parseFloat(cfg.value);
        } else {
            setValue(cfg, formatTwoDecimals(minFloat));
        }
        cur = MathHelper.clamp(cur, minFloat, maxFloat);

        int sliderWidth = Math.max(80, width - 24);
        FloatRangeSlider slider = new FloatRangeSlider(0, 0, sliderWidth, 20, minFloat, maxFloat,
            cur, cfg, cfg instanceof BoundAttributeConfig bound ? bound.defaultValue()
            : formatTwoDecimals(minFloat));
        slider.updateMessage();
        ButtonWidget editButton = ButtonWidget.builder(Text.literal("#"), button -> {
            if (owner == null) {
                return;
            }
            MinecraftClient.getInstance().setScreen(new NumericSliderInputScreen(owner, cfg.name,
                false, minFloat, maxFloat, cfg.value, cfg instanceof BoundAttributeConfig bound
                    ? bound.defaultValue()
                    : formatTwoDecimals(minFloat), value -> {
                    setValue(cfg, value);
                    slider.syncFromValueString(value);
                }));
        }).dimensions(0, 0, 20, 20).build();
        return List.of(slider, editButton);
    }

    private static void setValue(AttributeConfig cfg, String value) {
        if (cfg instanceof BoundAttributeConfig bound) {
            bound.apply(value);
            return;
        }
        cfg.value = value;
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

    private record Range(double start, double end) {

    }

    private static class IntRangeSlider extends SliderWidget {

        private final int start;
        private final int end;
        private final AttributeConfig cfg;
        private final String defaultValue;

        public IntRangeSlider(int x, int y, int width, int height, int start, int end, int cur,
            AttributeConfig cfg, String defaultValue) {
            super(x, y, width, height, Text.empty(),
                (cur - (double) start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.cfg = cfg;
            this.defaultValue = defaultValue;
            this.value = (cur - (double) start) / (double) (end - start);
        }

        private int current() {
            if (end == start) {
                return start;
            }
            return start + (int) Math.round(this.value * (end - start));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(Integer.toString(current())));
        }

        @Override
        protected void applyValue() {
            int v = MathHelper.clamp(current(), start, end);
            setValue(cfg, Integer.toString(v));
        }

        public void syncFromValueString(String valueString) {
            if (!isInt(valueString)) {
                return;
            }
            int value = MathHelper.clamp(Integer.parseInt(valueString), start, end);
            this.value = (value - (double) start) / (double) (end - start);
            updateMessage();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && this.isMouseOver(mouseX, mouseY)) {
                syncFromValueString(defaultValue);
                setValue(cfg, Integer.toString(current()));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static class FloatRangeSlider extends SliderWidget {

        private final float start;
        private final float end;
        private final AttributeConfig cfg;
        private final String defaultValue;

        public FloatRangeSlider(int x, int y, int width, int height, float start, float end,
            float cur,
            AttributeConfig cfg, String defaultValue) {
            super(x, y, width, height, Text.empty(), (cur - start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.cfg = cfg;
            this.defaultValue = defaultValue;
            this.value = (cur - start) / (double) (end - start);
        }

        private float current() {
            if (end == start) {
                return start;
            }
            return (float) (start + this.value * (end - start));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(formatTwoDecimals(current())));
        }

        @Override
        protected void applyValue() {
            float v = MathHelper.clamp(current(), start, end);
            setValue(cfg, formatTwoDecimals(v));
        }

        public void syncFromValueString(String valueString) {
            if (!isFloat(valueString)) {
                return;
            }
            float value = MathHelper.clamp(Float.parseFloat(valueString), start, end);
            this.value = (value - start) / (double) (end - start);
            updateMessage();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && this.isMouseOver(mouseX, mouseY)) {
                syncFromValueString(defaultValue);
                setValue(cfg, formatTwoDecimals(current()));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
