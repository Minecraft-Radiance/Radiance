package com.radiance.client.gui;

import static com.radiance.client.gui.compat.GuiCompat.*;

import com.radiance.client.gui.compat.PlatformScreen;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModuleAttributeScreen extends PlatformScreen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 160;
    private static final int VEC3_COMPONENT_WIDTH = 52;
    private static final int VEC3_GAP = 2;
    private static final int HEADER_HEIGHT = 32;
    private static final String NO_ATTRS_KEY = "module_attribute_screen.no_attributes";

    private final Object parent;
    private final Module module;
    private final List<Row> rows = new ArrayList<>();
    private int scrollY = 0;

    public ModuleAttributeScreen(Object parent, Module module) {
        super(module.translateText(module.name));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        Object backBtn = newButton(txt("Back"), btn -> close(), 60);
        screenAddChild(this, backBtn);
        setX(backBtn, 10);
        setY(backBtn, 6);

        rows.clear();
        List<AttributeConfig> list = module.attributeConfigs;
        if (list == null || list.isEmpty()) return;

        Object font = screenFont(this);
        for (AttributeConfig cfg : list) {
            if (Pipeline.isRayTracingShaderPackAttribute(module, cfg)) continue;
            List<Object> ws = AttributeWidgetUtil.buildWidgets(module, cfg, font,
                WIDGET_WIDTH, VEC3_COMPONENT_WIDTH);
            for (Object w : ws) screenAddChild(this, w);
            rows.add(new Row(cfg, ws));
        }
    }

    @Override
    public void close() {
        Pipeline.getModuleAttributes(module);
        Pipeline.rebuildModulePorts(module);
        Pipeline.savePipeline();
        setScreen(getMinecraft(), parent);
    }

    @Override
    protected void renderScreen(Object ctx, int mouseX, int mouseY, float delta) {
        Object font = screenFont(this);
        drawText(ctx, font, module.translateText(module.name), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA, true);

        if (rows.isEmpty()) {
            drawText(ctx, font, txt(NO_ATTRS_KEY), 10, 60, 0xFFB0B0B0, true);
            return;
        }

        int baseY = (HEADER_HEIGHT + 28) + scrollY;
        int rowH = 22;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = baseY + i * rowH;
            boolean visible = y >= (HEADER_HEIGHT + 18) && y <= (this.height - 24);

            if (visible) {
                drawText(ctx, font, module.translateText(row.cfg.name), ROW_LEFT, y + 6, 0xFFD0D0D0, true);
            }

            layoutRowWidgets(row, y);

            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
            boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

            for (Object widget : row.widgets) {
                setVisible(widget, visible);
                setActive(widget, visible);
                if (!doBorder) continue;

                boolean ok = true;
                if (type.equals("vec3")) {
                    if (isTextField(widget)) ok = AttributeWidgetUtil.isStrictFloat(tfGetText(widget));
                } else if (type.equals("int")) {
                    if (isTextField(widget)) ok = AttributeWidgetUtil.isStrictInt(tfGetText(widget));
                } else if (type.equals("float")) {
                    if (isTextField(widget)) ok = AttributeWidgetUtil.isStrictFloat(tfGetText(widget));
                }

                if (isTextField(widget)) {
                    int c = ok ? OK_BORDER : BAD_BORDER;
                    AttributeWidgetUtil.drawBorder(ctx, tfGetX(widget), tfGetY(widget), tfGetW(widget), tfGetH(widget), c);
                }
            }
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int ww = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH, VEC3_GAP);
        int x = this.width - ROW_RIGHT - ww;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH, VEC3_GAP);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int rowH = 22;
        int contentH = 60 + rows.size() * rowH + 20;
        int minScroll = Math.min(0, this.height - contentH);
        scrollY += (int) (vAmt * 10);
        if (scrollY > 0) scrollY = 0;
        if (scrollY < minScroll) scrollY = minScroll;
        return true;
    }

    private static class Row {
        final AttributeConfig cfg;
        final List<Object> widgets;
        Row(AttributeConfig cfg, List<Object> widgets) { this.cfg = cfg; this.widgets = widgets; }
    }
}
