package com.radiance.client.gui;

import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
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
            w.setWidth(singleWidth);
            return;
        }

        if (widgets.size() == 3) {
            for (int i = 0; i < 3; i++) {
                ClickableWidget cw = widgets.get(i);
                cw.setX(x + i * (tripleWidth + gap));
                cw.setY(y);
                cw.setWidth(tripleWidth);
            }
        }
    }

    static int totalWidgetWidth(List<ClickableWidget> widgets, int singleWidth, int tripleWidth, int gap) {
        if (widgets.size() == 3) {
            return (tripleWidth * 3) + (gap * 2);
        }
        return singleWidth;
    }

    static List<ClickableWidget> buildWidgets(AttributeConfig cfg, TextRenderer textRenderer,
        int width,
        int vec3ComponentWidth) {
        return buildWidgets(cfg, textRenderer, width, vec3ComponentWidth, () -> {});
    }

    static List<ClickableWidget> buildWidgets(AttributeConfig cfg, TextRenderer textRenderer,
        int width,
        int vec3ComponentWidth, Runnable onChanged) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return List.of(buildEnumWidget(cfg, cfg.type.substring(5), width, onChanged));
        }

        if (type.startsWith("int_range:")) {
            return List.of(buildIntRange(cfg, cfg.type.substring(10), width));
        }

        if (type.startsWith("float_range:")) {
            return List.of(buildFloatRange(cfg, cfg.type.substring(12), width));
        }

        return switch (type) {
            case "bool" -> List.of(buildBoolWidget(cfg, width, onChanged));
            case "int" -> List.of(buildIntWidget(cfg, textRenderer, width, onChanged));
            case "float" -> List.of(buildFloatWidget(cfg, textRenderer, width, onChanged));
            case "string" -> List.of(buildStringWidget(cfg, textRenderer, width, onChanged));
            case "vec3" -> buildVec3Widget(cfg, textRenderer, vec3ComponentWidth, onChanged);
            default -> List.of(buildStringWidget(cfg, textRenderer, width, onChanged));
        };
    }

    private static ClickableWidget buildBoolWidget(AttributeConfig cfg, int width,
        Runnable onChanged) {
        boolean b = "render_pipeline.true".equalsIgnoreCase(cfg.value);
        return ButtonWidget.builder(
            Text.translatable(b ? "render_pipeline.true" : "render_pipeline.false"), btn -> {
                boolean nv = !"render_pipeline.true".equalsIgnoreCase(cfg.value);
                cfg.value = nv ? "render_pipeline.true" : "render_pipeline.false";
                btn.setMessage(Text.translatable(cfg.value));
                onChanged.run();
            }).dimensions(0, 0, width, 20).build();
    }

    private static ClickableWidget buildEnumWidget(AttributeConfig cfg, String raw, int width,
        Runnable onChanged) {
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
            cfg.value = values[index[0]];
            btn.setMessage(Text.translatable(cfg.value));
            onChanged.run();
        }).dimensions(0, 0, width, 20).build();
    }

    private static ClickableWidget buildIntWidget(AttributeConfig cfg, TextRenderer textRenderer,
        int width, Runnable onChanged) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictInt(text)) {
                cfg.value = text;
                onChanged.run();
            }
        });
        return tf;
    }

    private static ClickableWidget buildFloatWidget(AttributeConfig cfg, TextRenderer textRenderer,
        int width, Runnable onChanged) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictFloat(text)) {
                cfg.value = text;
                onChanged.run();
            }
        });
        return tf;
    }

    private static ClickableWidget buildStringWidget(AttributeConfig cfg,
        TextRenderer textRenderer,
        int width, Runnable onChanged) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(128);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setChangedListener(text -> {
            cfg.value = text;
            onChanged.run();
        });
        return tf;
    }

    private static List<ClickableWidget> buildVec3Widget(AttributeConfig cfg,
        TextRenderer textRenderer,
        int componentWidth, Runnable onChanged) {
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
                cfg.value = sx + "," + sy + "," + sz;
                onChanged.run();
            }
        };

        x.setChangedListener(s -> syncIfValid.run());
        y.setChangedListener(s -> syncIfValid.run());
        z.setChangedListener(s -> syncIfValid.run());

        // Normalize initial value without firing onChanged
        String sx0 = x.getText(), sy0 = y.getText(), sz0 = z.getText();
        if (isStrictFloat(sx0) && isStrictFloat(sy0) && isStrictFloat(sz0)) {
            cfg.value = sx0 + "," + sy0 + "," + sz0;
        }
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

    private static ClickableWidget buildIntRange(AttributeConfig cfg, String raw, int width) {
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
            cfg.value = String.valueOf(start);
        }
        cur = MathHelper.clamp(cur, start, end);

        IntRangeSlider slider = new IntRangeSlider(0, 0, width, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private static ClickableWidget buildFloatRange(AttributeConfig cfg, String raw, int width) {
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
            cfg.value = formatTwoDecimals(start);
        }
        cur = MathHelper.clamp(cur, start, end);

        FloatRangeSlider slider = new FloatRangeSlider(0, 0, width, 20, start, end, cur, cfg);
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

    private record Range(double start, double end) {

    }

    private static class IntRangeSlider extends SliderWidget {

        private final int start;
        private final int end;
        private final AttributeConfig cfg;

        public IntRangeSlider(int x, int y, int width, int height, int start, int end, int cur,
            AttributeConfig cfg) {
            super(x, y, width, height, Text.empty(),
                (cur - (double) start) / (double) (end - start));
            this.start = start;
            this.end = end;
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
        protected void updateMessage() {
            setMessage(Text.translatable(Integer.toString(current())));
        }

        @Override
        protected void applyValue() {
            int v = MathHelper.clamp(current(), start, end);
            cfg.value = Integer.toString(v);
        }
    }

    private static class FloatRangeSlider extends SliderWidget {

        private final float start;
        private final float end;
        private final AttributeConfig cfg;

        public FloatRangeSlider(int x, int y, int width, int height, float start, float end,
            float cur,
            AttributeConfig cfg) {
            super(x, y, width, height, Text.empty(), (cur - start) / (double) (end - start));
            this.start = start;
            this.end = end;
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
        protected void updateMessage() {
            setMessage(Text.translatable(formatTwoDecimals(current())));
        }

        @Override
        protected void applyValue() {
            float v = MathHelper.clamp(current(), start, end);
            cfg.value = formatTwoDecimals(v);
        }
    }
}
