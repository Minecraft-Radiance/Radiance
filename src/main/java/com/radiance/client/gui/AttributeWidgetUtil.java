package com.radiance.client.gui;

import static com.radiance.client.gui.compat.GuiCompat.*;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.List;
import java.util.Locale;

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

    static void drawBorder(Object ctx, int x, int y, int w, int h, int color) {
        fill(ctx, x, y, x + w, y + 1, color);
        fill(ctx, x, y + h - 1, x + w, y + h, color);
        fill(ctx, x, y, x + 1, y + h, color);
        fill(ctx, x + w - 1, y, x + w, y + h, color);
    }

    static void layoutWidgets(List<Object> widgets, int x, int y, int singleWidth, int tripleWidth, int gap) {
        if (widgets.size() == 1) {
            Object w = widgets.get(0);
            setX(w, x); setY(w, y); setW(w, singleWidth);
            return;
        }
        if (widgets.size() == 3) {
            for (int i = 0; i < 3; i++) {
                Object cw = widgets.get(i);
                setX(cw, x + i * (tripleWidth + gap));
                setY(cw, y);
                setW(cw, tripleWidth);
            }
        }
    }

    static int totalWidgetWidth(List<Object> widgets, int singleWidth, int tripleWidth, int gap) {
        return (widgets.size() == 3) ? (tripleWidth * 3) + (gap * 2) : singleWidth;
    }

    static List<Object> buildWidgets(Module module, AttributeConfig cfg, Object font,
        int width,
        int vec3ComponentWidth) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return List.of(buildEnumWidget(module, cfg, cfg.type.substring(5), width));
        }

        if (type.startsWith("int_range:")) {
            return List.of(buildIntRange(cfg, cfg.type.substring(10), width));
        }

        if (type.startsWith("float_range:")) {
            return List.of(buildFloatRange(cfg, cfg.type.substring(12), width));
        }

        return switch (type) {
            case "bool" -> List.of(buildBoolWidget(module, cfg, width));
            case "int" -> List.of(buildIntWidget(cfg, font, width));
            case "float" -> List.of(buildFloatWidget(cfg, font, width));
            case "string" -> List.of(buildStringWidget(cfg, font, width));
            case "vec3" -> buildVec3Widget(cfg, font, vec3ComponentWidth);
            default -> List.of(buildStringWidget(cfg, font, width));
        };
    }

    private static Object buildBoolWidget(Module module, AttributeConfig cfg, int width) {
        boolean b = "render_pipeline.true".equalsIgnoreCase(cfg.value);
        return newButton(module.translateText(b ? "render_pipeline.true" : "render_pipeline.false"), _btn -> {
            boolean nv = !"render_pipeline.true".equalsIgnoreCase(cfg.value);
            cfg.value = nv ? "render_pipeline.true" : "render_pipeline.false";
            setButtonMsg(_btn, module.translateText(cfg.value));
        }, width);
    }

    private static Object buildEnumWidget(Module module, AttributeConfig cfg, String raw, int width) {
        String[] values = raw.isEmpty() ? new String[]{"<empty>"} : raw.split("-");
        int idx = 0;
        if (cfg.value != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(cfg.value)) { idx = i; break; }
            }
        } else { cfg.value = values[0]; }
        int[] index = new int[]{idx};
        return newButton(module.translateText(values[index[0]]), _btn -> {
            index[0] = (index[0] + 1) % values.length;
            cfg.value = values[index[0]];
            setButtonMsg(_btn, module.translateText(cfg.value));
        }, width);
    }

    private static Object buildIntWidget(AttributeConfig cfg, Object font, int width) {
        Object tf = newTextField(font, width, emptyTxt());
        tfSetMaxLen(tf, 64);
        tfSetValue(tf, cfg.value == null ? "" : cfg.value);
        tfSetResponder(tf, text -> { if (isStrictInt(text)) cfg.value = text; });
        return tf;
    }

    private static Object buildFloatWidget(AttributeConfig cfg, Object font, int width) {
        Object tf = newTextField(font, width, emptyTxt());
        tfSetMaxLen(tf, 64);
        tfSetValue(tf, cfg.value == null ? "" : cfg.value);
        tfSetResponder(tf, text -> { if (isStrictFloat(text)) cfg.value = text; });
        return tf;
    }

    private static Object buildStringWidget(AttributeConfig cfg, Object font, int width) {
        Object tf = newTextField(font, width, emptyTxt());
        tfSetMaxLen(tf, 128);
        tfSetValue(tf, cfg.value == null ? "" : cfg.value);
        tfSetResponder(tf, text -> cfg.value = text);
        return tf;
    }

    private static List<Object> buildVec3Widget(AttributeConfig cfg,
        Object font,
        int componentWidth) {
        if (cfg.value == null || cfg.value.isEmpty()) cfg.value = "0,0,0";
        float[] v = parseVec3(cfg.value);
        Object x = vecField(font, v[0], componentWidth);
        Object y = vecField(font, v[1], componentWidth);
        Object z = vecField(font, v[2], componentWidth);
        Runnable syncIfValid = () -> {
            String sx = tfValue(x);
            String sy = tfValue(y);
            String sz = tfValue(z);

            if (isStrictFloat(sx) && isStrictFloat(sy) && isStrictFloat(sz)) {
                cfg.value = sx + "," + sy + "," + sz;
            }
        };

        tfSetResponder(x, s -> syncIfValid.run());
        tfSetResponder(y, s -> syncIfValid.run());
        tfSetResponder(z, s -> syncIfValid.run());

        syncIfValid.run();
        return List.of(x, y, z);
    }

    private static Object vecField(Object font, float v, int width) {
        Object tf = newTextField(font, width, emptyTxt());
        tfSetMaxLen(tf, 32);
        tfSetValue(tf, trimFloat(v));
        return tf;
    }

    private static Object buildIntRange(AttributeConfig cfg, String raw, int width) {
        Range r = parseRange(raw);
        int start = (int) r.start, end = (int) r.end;
        if (start > end) { int t = start; start = end; end = t; }
        int cur = start;
        if (isInt(cfg.value)) cur = Integer.parseInt(cfg.value);
        else cfg.value = String.valueOf(start);
        cur = clampI(cur, start, end);
        final int sliderStart = start;
        final int sliderEnd = end;
        return newSlider(width, normalizedRangeValue(cur, sliderStart, sliderEnd),
            value -> txt(Integer.toString(currentInt(value, sliderStart, sliderEnd))),
            value -> cfg.value = Integer.toString(clampI(currentInt(value, sliderStart, sliderEnd),
                sliderStart, sliderEnd)));
    }

    private static Object buildFloatRange(AttributeConfig cfg, String raw, int width) {
        Range r = parseRange(raw);
        float start = (float) r.start, end = (float) r.end;
        if (start > end) { float t = start; start = end; end = t; }
        float cur = start;
        if (isFloat(cfg.value)) cur = Float.parseFloat(cfg.value);
        else cfg.value = formatTwoDecimals(start);
        cur = clampF(cur, start, end);
        final float sliderStart = start;
        final float sliderEnd = end;
        return newSlider(width, normalizedRangeValue(cur, sliderStart, sliderEnd),
            value -> txt(formatTwoDecimals(currentFloat(value, sliderStart, sliderEnd))),
            value -> cfg.value = formatTwoDecimals(clampF(currentFloat(value, sliderStart, sliderEnd),
                sliderStart, sliderEnd)));
    }

    private static double normalizedRangeValue(double current, double start, double end) {
        return end == start ? 0 : (current - start) / (end - start);
    }

    private static int currentInt(double value, int start, int end) {
        return end == start ? start : start + (int) Math.round(value * (end - start));
    }

    private static float currentFloat(double value, float start, float end) {
        return end == start ? start : (float) (start + value * (end - start));
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

}
