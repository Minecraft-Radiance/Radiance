package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ShaderPackSettingsScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 160;
    private static final int VEC3_COMPONENT_WIDTH = 52;
    private static final int VEC3_GAP = 2;
    private static final int HEADER_HEIGHT = 32;
    private static final int GROUP_HEADER_HEIGHT = 28;
    private static final int GROUP_BOTTOM_GAP = 4;
    private static final int ROW_HEIGHT = 22;
    private static final int VIEWPORT_BOTTOM_PADDING = 24;
    private static final String TITLE = "shader_pack_settings_screen.title";
    private static final String NO_ATTRIBUTES = "module_attribute_screen.no_attributes";

    private final Screen parent;
    private final List<Group> groups = new ArrayList<>();
    private Module module;
    private ButtonWidget resetAllButton;
    private int scrollY = 0;
    private boolean scrollbarDragging = false;
    private int scrollbarDragOffsetY = 0;

    public ShaderPackSettingsScreen(Screen parent) {
        super(Text.translatable(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild(true);
    }

    private void rebuild(boolean reloadAttributes) {
        clearChildren();
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("Back"), button -> close())
                .dimensions(10, 6, 60, 20)
                .build());
        resetAllButton = addDrawableChild(
            AttributeWidgetUtil.buildGlobalResetButton(75, 6, this::resetAllAttributes));

        groups.clear();
        module = Pipeline.getMainRenderModule();
        List<AttributeConfig> list = reloadAttributes
            ? Pipeline.getMainRenderShaderPackAttributes()
            : currentShaderPackAttributes();
        if (module == null || list.isEmpty()) {
            syncResetAllButton();
            return;
        }

        Map<String, Group> groupedRows = new LinkedHashMap<>();
        for (AttributeConfig cfg : list) {
            List<ClickableWidget> ws = AttributeWidgetUtil.buildWidgets(module, cfg, textRenderer,
                WIDGET_WIDTH, VEC3_COMPONENT_WIDTH, () -> rebuild(false));
            for (ClickableWidget w : ws) {
                addDrawableChild(w);
            }
            String groupKey = AttributeWidgetUtil.groupKey(cfg);
            Group group = groupedRows.computeIfAbsent(groupKey,
                key -> new Group(key, new ArrayList<>()));
            group.rows().add(new Row(cfg, ws));
        }
        groups.addAll(groupedRows.values());
        syncResetAllButton();
    }

    private List<AttributeConfig> currentShaderPackAttributes() {
        List<AttributeConfig> attributes = new ArrayList<>();
        if (module == null || module.attributeConfigs == null) {
            return attributes;
        }
        for (AttributeConfig cfg : module.attributeConfigs) {
            if (Pipeline.isMainRenderShaderPackDynamicAttribute(module, cfg)) {
                attributes.add(cfg);
            }
        }
        return attributes;
    }

    @Override
    public void close() {
        if (module != null) {
            Pipeline.getModuleAttributes(module);
            Pipeline.savePipeline();
        }
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncResetAllButton();
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, Text.translatable(TITLE), 10, HEADER_HEIGHT + 8,
            0xFFEAEAEA);

        if (groups.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable(NO_ATTRIBUTES), 10, 60,
                0xFFB0B0B0);
            return;
        }

        scrollY = AttributeWidgetUtil.clampScroll(scrollY, viewportHeight(),
            visibleContentHeight());
        int y = listTopY() + scrollY;
        boolean hasVisibleRows = false;
        AttributeConfig hoveredConfig = null;
        AttributeWidgetUtil.TooltipFocus hoveredFocus = null;

        for (Group group : groups) {
            int visibleRowCount = visibleRowCount(group);
            if (visibleRowCount == 0) {
                setGroupVisible(group, false);
                continue;
            }
            hasVisibleRows = true;

            boolean groupHeaderVisible = y + GROUP_HEADER_HEIGHT >= listTopY()
                && y <= viewportBottomY();
            if (groupHeaderVisible) {
                AttributeWidgetUtil.drawGroupHeader(context, textRenderer,
                    module.translateText(group.key()), ROW_LEFT, y,
                    this.width - ROW_LEFT - ROW_RIGHT, GROUP_HEADER_HEIGHT);
            }
            y += GROUP_HEADER_HEIGHT;

            for (Row row : group.rows()) {
                boolean rowVisible = Pipeline.isAttributeVisible(module, row.cfg);
                if (!rowVisible) {
                    setRowVisible(row, false);
                    continue;
                }

                boolean visible = y >= listTopY() && y <= viewportBottomY();
                Text label = module.translateText(row.cfg.name);
                AttributeWidgetUtil.TooltipFocus rowFocus = AttributeWidgetUtil.tooltipFocus(
                    ROW_LEFT, y, this.width - ROW_RIGHT, y + ROW_HEIGHT);
                if (visible) {
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

                AttributeWidgetUtil.syncWidgetVisibility(row.widgets, visible);

                for (ClickableWidget w : row.widgets) {
                    if (visible && !AttributeWidgetUtil.isResetButton(w)
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
                        AttributeWidgetUtil.drawBorder(context, tf.getX(), tf.getY(), tf.getWidth(),
                            tf.getHeight(), c);
                    }
                }
                y += ROW_HEIGHT;
            }
            y += GROUP_BOTTOM_GAP;
        }

        if (!hasVisibleRows) {
            context.drawTextWithShadow(textRenderer, Text.translatable(NO_ATTRIBUTES), 10, 60,
                0xFFB0B0B0);
            return;
        }

        AttributeWidgetUtil.drawVerticalScrollbar(context, this.width, listTopY(),
            viewportHeight(), visibleContentHeight(), scrollY, scrollbarDragging, mouseX, mouseY);

        if (hoveredConfig != null) {
            AttributeWidgetUtil.drawAttributeTooltip(context, textRenderer, module, hoveredConfig,
                hoveredFocus, mouseX, mouseY);
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH,
            VEC3_COMPONENT_WIDTH, VEC3_GAP);
        int x = this.width - ROW_RIGHT - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH,
            VEC3_GAP);
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

    private int visibleRowCount(Group group) {
        int count = 0;
        for (Row row : group.rows()) {
            if (Pipeline.isAttributeVisible(module, row.cfg)) {
                count++;
            }
        }
        return count;
    }

    private int visibleContentHeight() {
        int contentHeight = 0;
        for (Group group : groups) {
            int groupVisibleRows = visibleRowCount(group);
            if (groupVisibleRows == 0) {
                continue;
            }
            contentHeight += GROUP_HEADER_HEIGHT + groupVisibleRows * ROW_HEIGHT + GROUP_BOTTOM_GAP;
        }
        return contentHeight;
    }

    private void setRowVisible(Row row, boolean visible) {
        AttributeWidgetUtil.syncWidgetVisibility(row.widgets, visible);
    }

    private void setGroupVisible(Group group, boolean visible) {
        for (Row row : group.rows()) {
            setRowVisible(row, visible);
        }
    }

    private void resetAllAttributes() {
        if (AttributeWidgetUtil.resetAttributesToDefaults(module, currentScreenAttributes())) {
            rebuild(false);
        }
        syncResetAllButton();
    }

    private void syncResetAllButton() {
        if (resetAllButton == null) {
            return;
        }
        boolean show = AttributeWidgetUtil.hasNonDefaultValue(currentScreenAttributes());
        resetAllButton.visible = show;
        resetAllButton.active = show;
    }

    private List<AttributeConfig> currentScreenAttributes() {
        List<AttributeConfig> configs = new ArrayList<>();
        for (Group group : groups) {
            for (Row row : group.rows()) {
                configs.add(row.cfg());
            }
        }
        return configs;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (groups.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        scrollY = AttributeWidgetUtil.clampScroll(scrollY + (int) (verticalAmount * 18),
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

    private record Group(String key, List<Row> rows) {
    }

    private record Row(AttributeConfig cfg, List<ClickableWidget> widgets) {
    }
}
