package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ModuleAttributeScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 160;
    private static final int VEC3_COMPONENT_WIDTH = 52;
    private static final int VEC3_GAP = 2;

    private static final int HEADER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 22;
    private static final int VIEWPORT_BOTTOM_PADDING = 24;
    private static final String MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES = "module_attribute_screen.no_attributes";

    private final Screen parent;
    private final Module module;

    private final List<Row> rows = new ArrayList<>();
    private ButtonWidget resetAllButton;

    private int scrollY = 0;
    private boolean scrollbarDragging = false;
    private int scrollbarDragOffsetY = 0;

    public ModuleAttributeScreen(Screen parent, Module module) {
        super(module.translateText(module.name));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("Back"), button -> close())
                .dimensions(10, 6, 60, 20)
                .build());
        resetAllButton = addDrawableChild(
            AttributeWidgetUtil.buildGlobalResetButton(75, 6, this::resetAllAttributes));

        rows.clear();

        List<AttributeConfig> list = module.attributeConfigs;
        if (list == null || list.isEmpty()) {
            syncResetAllButton();
            return;
        }

        for (AttributeConfig cfg : list) {
            if (Pipeline.isMainRenderShaderPackAttribute(module, cfg)) {
                continue;
            }
            List<ClickableWidget> ws = AttributeWidgetUtil.buildWidgets(module, cfg, textRenderer,
                WIDGET_WIDTH, VEC3_COMPONENT_WIDTH, this::rebuild);
            Row row = new Row(cfg, ws);
            setRowVisible(row, Pipeline.isAttributeVisible(module, cfg));
            for (ClickableWidget w : ws) {
                addDrawableChild(w);
            }
            rows.add(row);
        }
        syncResetAllButton();
    }

    @Override
    public void close() {
        Pipeline.getModuleAttributes(module);
        Pipeline.savePipeline();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncResetAllButton();
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, module.translateText(module.name), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA);

        int visibleRowCount = visibleRowCount();

        if (visibleRowCount == 0) {
            for (Row row : rows) {
                setRowVisible(row, false);
            }
            context.drawTextWithShadow(textRenderer,
                Text.translatable(MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES), 10, 60, 0xFFB0B0B0);
            return;
        }

        scrollY = AttributeWidgetUtil.clampScroll(scrollY, viewportHeight(),
            visibleContentHeight());
        int baseY = listTopY() + scrollY;
        int visibleIndex = 0;
        AttributeConfig hoveredConfig = null;
        AttributeWidgetUtil.TooltipFocus hoveredFocus = null;

        for (Row row : rows) {
            boolean rowVisible = Pipeline.isAttributeVisible(module, row.cfg);
            if (!rowVisible) {
                setRowVisible(row, false);
                continue;
            }

            int y = baseY + visibleIndex * ROW_HEIGHT;
            visibleIndex++;

            boolean withinViewport = y >= listTopY() && y <= viewportBottomY();
            Text label = module.translateText(row.cfg.name);
            AttributeWidgetUtil.TooltipFocus rowFocus = AttributeWidgetUtil.tooltipFocus(ROW_LEFT,
                y, this.width - ROW_RIGHT, y + ROW_HEIGHT);
            if (withinViewport) {
                context.drawTextWithShadow(textRenderer, label, ROW_LEFT, y + 6, 0xFFD0D0D0);
                if (mouseX >= ROW_LEFT && mouseX <= ROW_LEFT + textRenderer.getWidth(label)
                    && mouseY >= y + 4 && mouseY <= y + 18) {
                    hoveredConfig = row.cfg;
                    hoveredFocus = rowFocus;
                }
            }

            layoutRowWidgets(row, y);

            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
            boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

            AttributeWidgetUtil.syncWidgetVisibility(row.widgets, withinViewport);

            for (ClickableWidget w : row.widgets) {
                if (withinViewport && !AttributeWidgetUtil.isResetButton(w)
                    && w.isMouseOver(mouseX, mouseY)) {
                    hoveredConfig = row.cfg;
                    hoveredFocus = rowFocus;
                }

                if (!doBorder) {
                    continue;
                }

                boolean ok = true;

                if (type.equals("vec3")) {
                    if (w instanceof TextFieldWidget tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getText());
                    }
                } else if (type.equals("int")) {
                    if (w instanceof TextFieldWidget tf) {
                        ok = AttributeWidgetUtil.isStrictInt(tf.getText());
                    }
                } else if (type.equals("float")) {
                    if (w instanceof TextFieldWidget tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getText());
                    }
                }

                if (w instanceof TextFieldWidget tf) {
                    int c = ok ? OK_BORDER : BAD_BORDER;
                    AttributeWidgetUtil.drawBorder(context, tf.getX(), tf.getY(), tf.getWidth(), tf.getHeight(), c);
                }
            }
        }

        AttributeWidgetUtil.drawVerticalScrollbar(context, this.width, listTopY(),
            viewportHeight(), visibleContentHeight(), scrollY, scrollbarDragging, mouseX, mouseY);

        if (hoveredConfig != null) {
            AttributeWidgetUtil.drawAttributeTooltip(context, textRenderer, module, hoveredConfig,
                hoveredFocus, mouseX, mouseY);
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH,
            VEC3_GAP);
        int x = this.width - ROW_RIGHT - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH, VEC3_GAP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollY = AttributeWidgetUtil.clampScroll(scrollY + (int) (verticalAmount * 10),
            viewportHeight(), visibleContentHeight());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && AttributeWidgetUtil.isVerticalScrollbarMouseOver(mouseX, mouseY,
            this.width, listTopY(), viewportHeight(), visibleContentHeight())) {
            scrollbarDragging = true;
            scrollbarDragOffsetY = AttributeWidgetUtil.verticalScrollbarDragOffsetY(mouseY,
                listTopY(), viewportHeight(), visibleContentHeight(), scrollY);
            scrollY = AttributeWidgetUtil.scrollFromVerticalScrollbarDrag(mouseY,
                scrollbarDragOffsetY, listTopY(), viewportHeight(), visibleContentHeight());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX,
        double deltaY) {
        if (scrollbarDragging && button == 0) {
            scrollY = AttributeWidgetUtil.scrollFromVerticalScrollbarDrag(mouseY,
                scrollbarDragOffsetY, listTopY(), viewportHeight(), visibleContentHeight());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int listTopY() {
        return HEADER_HEIGHT + 28;
    }

    private int viewportBottomY() {
        return this.height - VIEWPORT_BOTTOM_PADDING;
    }

    private int viewportHeight() {
        return Math.max(0, viewportBottomY() - listTopY());
    }

    private int visibleRowCount() {
        int visibleRowCount = 0;
        for (Row row : rows) {
            if (Pipeline.isAttributeVisible(module, row.cfg)) {
                visibleRowCount++;
            }
        }
        return visibleRowCount;
    }

    private int visibleContentHeight() {
        return visibleRowCount() * ROW_HEIGHT;
    }

    private void setRowVisible(Row row, boolean visible) {
        AttributeWidgetUtil.syncWidgetVisibility(row.widgets, visible);
    }

    private void resetAllAttributes() {
        List<AttributeConfig> configs = new ArrayList<>();
        for (Row row : rows) {
            configs.add(row.cfg);
        }
        if (AttributeWidgetUtil.resetAttributesToDefaults(module, configs)) {
            rebuild();
        }
        syncResetAllButton();
    }

    private void syncResetAllButton() {
        if (resetAllButton == null) {
            return;
        }
        List<AttributeConfig> configs = new ArrayList<>();
        for (Row row : rows) {
            configs.add(row.cfg);
        }
        boolean show = AttributeWidgetUtil.hasNonDefaultValue(configs);
        resetAllButton.visible = show;
        resetAllButton.active = show;
    }

    private static class Row {

        private final AttributeConfig cfg;
        private final List<ClickableWidget> widgets;

        private Row(AttributeConfig cfg, List<ClickableWidget> widgets) {
            this.cfg = cfg;
            this.widgets = widgets;
        }
    }
}
