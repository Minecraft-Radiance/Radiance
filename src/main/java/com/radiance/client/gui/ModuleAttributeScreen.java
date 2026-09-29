package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModuleAttributeScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 160;
    private static final int VEC3_COMPONENT_WIDTH = 52;
    private static final int VEC3_GAP = 2;

    private static final int HEADER_HEIGHT = 32;
    private static final String MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES = "module_attribute_screen.no_attributes";

    private final Screen parent;
    private final Module module;

    private final List<Row> rows = new ArrayList<>();

    private int scrollY = 0;

    public ModuleAttributeScreen(Screen parent, Module module) {
        super(module.translateText(module.name));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        addRenderableWidget(
            Button.builder(Component.translatable("Back"), button -> onClose())
                .bounds(10, 6, 60, 20)
                .build());

        rows.clear();

        List<AttributeConfig> list = module.attributeConfigs;
        if (list == null || list.isEmpty()) {
            return;
        }

        for (AttributeConfig cfg : list) {
            if (Pipeline.isMainRenderShaderPackAttribute(module, cfg)) {
                continue;
            }
            List<AbstractWidget> ws = AttributeWidgetUtil.buildWidgets(module, cfg, font, WIDGET_WIDTH,
                VEC3_COMPONENT_WIDTH);
            for (AbstractWidget w : ws) {
                addRenderableWidget(w);
            }
            rows.add(new Row(cfg, ws));
        }
    }

    @Override
    public void onClose() {
        Pipeline.getModuleAttributes(module);
        Pipeline.savePipeline();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawString(font, module.translateText(module.name), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA);

        if (rows.isEmpty()) {
            context.drawString(font, MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES, 10, 60, 0xFFB0B0B0);
            return;
        }

        int baseY = (HEADER_HEIGHT + 28) + scrollY;
        int rowH = 22;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = baseY + i * rowH;

            boolean visible = y >= (HEADER_HEIGHT + 18) && y <= (this.height - 24);
            if (visible) {
                context.drawString(font, module.translateText(row.cfg.name), ROW_LEFT, y + 6,
                    0xFFD0D0D0);
            }

            layoutRowWidgets(row, y);

            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
            boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

            for (AbstractWidget w : row.widgets) {
                w.visible = visible;
                w.active = visible;

                if (!doBorder) {
                    continue;
                }

                boolean ok = true;

                if (type.equals("vec3")) {
                    if (w instanceof EditBox tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getValue());
                    }
                } else if (type.equals("int")) {
                    if (w instanceof EditBox tf) {
                        ok = AttributeWidgetUtil.isStrictInt(tf.getValue());
                    }
                } else if (type.equals("float")) {
                    if (w instanceof EditBox tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getValue());
                    }
                }

                if (w instanceof EditBox tf) {
                    int c = ok ? OK_BORDER : BAD_BORDER;
                    AttributeWidgetUtil.drawBorder(context, tf.getX(), tf.getY(), tf.getWidth(), tf.getHeight(), c);
                }
            }
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH,
            VEC3_GAP);
        int x = this.width - ROW_RIGHT - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH, VEC3_GAP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        int rowH = 22;
        int contentH = 60 + rows.size() * rowH + 20;
        int minScroll = Math.min(0, this.height - contentH);

        scrollY += (int) (verticalAmount * 10);
        if (scrollY > 0) {
            scrollY = 0;
        }
        if (scrollY < minScroll) {
            scrollY = minScroll;
        }
        return true;
    }

    private static class Row {

        private final AttributeConfig cfg;
        private final List<AbstractWidget> widgets;

        private Row(AttributeConfig cfg, List<AbstractWidget> widgets) {
            this.cfg = cfg;
            this.widgets = widgets;
        }
    }
}
