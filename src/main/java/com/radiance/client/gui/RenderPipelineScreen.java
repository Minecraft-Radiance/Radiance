package com.radiance.client.gui;

import com.radiance.Radiance;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.ModuleEntry;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.Presets;
import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RenderPipelineScreen extends Screen {

    private static final ResourceLocation GEAR_TEX = ResourceLocation.fromNamespaceAndPath(Radiance.MOD_ID,
        "textures/gui/render_pipeline/gear.png");

    private static final Map<String, Integer> FORMAT_COLORS = Map.of("R8G8B8A8_SRGB", 0xFF4EA5FF,
        "R8G8B8A8_UNORM", 0xFF62E36A, "R16G16_SFLOAT", 0xFFFF6BD6, "R16_SFLOAT", 0xFF6BE6FF,
        "R16G16B16A16_SFLOAT", 0xFFFFB84E);

    private static final int HEADER_HEIGHT = 32;
    private static final int PRESET_GROUP_HEADER_HEIGHT = 28;
    private static final int PRESET_GROUP_BOTTOM_GAP = 18;
    private static final int PRESET_ROW_HEIGHT = 22;
    private static final int PRESET_VIEWPORT_BOTTOM_PADDING = 24;
    private static final float GLOBAL_SCALE = 0.75f;
    private static final String RENDER_PIPELINE_SCREEN_BACK = "render_pipeline_screen.back";
    private static final String RENDER_PIPELINE_SCREEN_RELOAD = "render_pipeline_screen.reload";
    private static final String RENDER_PIPELINE_SCREEN_ADD_MODULE = "render_pipeline_screen.add_module";
    private static final String RENDER_PIPELINE_SCREEN_SHADER_PACK = "render_pipeline_screen.shader_pack";
    private static final String RENDER_PIPELINE_SCREEN_BACK_HINT = "render_pipeline_screen.back_hint";
    private static final String RENDER_PIPELINE_SCREEN_REBUILDING = "render_pipeline_screen.rebuilding";
    private final Screen parent;
    private final List<ModuleNode> nodes = new ArrayList<>();
    private final List<ModuleConnection> moduleConnections = new ArrayList<>();
    private ModuleNode draggedNode = null;
    private double lastMouseX, lastMouseY;
    private ModuleSelector activeSelector = null;
    private ImageConfig localFinalOutput = null;
    private ImageConfig pendingPort = null;
    private boolean isPendingOutput = false;
    private boolean isPanning = false;

    private Mode mode = Mode.PIPELINE;
    private final List<PresetEntry> presets = new ArrayList<>();
    private PresetEntry activePreset = null;
    private PresetSelector activePresetSelector = null;
    private final List<PresetModuleBlock> presetBlocks = new ArrayList<>();
    private final List<AbstractWidget> presetWidgets = new ArrayList<>();
    private int presetScrollY = 0;
    private boolean presetScrollbarDragging = false;
    private int presetScrollbarDragOffsetY = 0;

    private Button modeToggleBtn;
    private Button secondaryBtn;
    private Button resetAllBtn;
    private CompletableFuture<Void> rebuildFuture = null;
    private boolean rebuildQueued = false;

    private static final String RENDER_PIPELINE_PRESET_NAME = "render_pipeline.preset.name";
    private static final String RENDER_PIPELINE_MODE_NAME = "render_pipeline.mode.name";

    public RenderPipelineScreen(Screen parent) {
        super(Component.literal("Render Pipeline"));

        this.parent = parent;

        registerDefaultPresets();

        Pipeline.PipelineMode pipelineMode = Pipeline.getPipelineMode();
        this.mode = pipelineMode == Pipeline.PipelineMode.PRESET ? Mode.PRESET : Mode.PIPELINE;

        String presetName = Pipeline.processPresetName(Pipeline.getActivePreset());
        if (presetName != null) {
            for (PresetEntry e : presets) {
                if (e != null && e.name() != null && e.name().equals(presetName)) {
                    this.activePreset = e;
                    break;
                }
            }
        }

        if (this.mode == Mode.PRESET && this.activePreset == null && !this.presets.isEmpty()) {
            this.activePreset = this.presets.getFirst();
        }
    }

    private int getFormatColor(String format) {
        Integer c = FORMAT_COLORS.get(format);
        if (c == null) {
            throw new RuntimeException("No color for image format: " + format);
        }
        return c;
    }

    @Override
    protected void init() {
        rebuildUI();
    }

    private void rebuildUI() {
        clearWidgets();
        activeSelector = null;
        activePresetSelector = null;
        presetWidgets.clear();
        presetScrollbarDragging = false;

        if (mode == Mode.PRESET && activePreset == null && !presets.isEmpty()) {
            activePreset = presets.getFirst();
        }

        if (mode == Mode.PIPELINE) {
            refreshPipeline();
        }

        int backX = 10;
        int backW = 60;
        int toggleX = backX + backW + 5;
        int toggleW = 110;
        int shaderPackX = toggleX + toggleW + 5;
        int shaderPackW = 110;
        int secondaryX = shaderPackX + shaderPackW + 5;
        int secondaryW = 150;

        addRenderableWidget(
            Button.builder(Component.translatable(RENDER_PIPELINE_SCREEN_BACK), button -> onClose())
                .bounds(backX, 6, backW, 20).build());

        modeToggleBtn = addRenderableWidget(Button.builder(
            Component.translatable(RENDER_PIPELINE_MODE_NAME)
                .append(Component.literal(": ").append(Component.translatable(mode.key))), button -> {
                if (mode == Mode.PIPELINE) {
                    if (activePreset == null && !presets.isEmpty()) {
                        activePreset = presets.getFirst();
                    }
                    Pipeline.switchToPresetMode(
                        activePreset != null ? activePreset.name() : "Default", false);
                    mode = Mode.PRESET;
                } else {
                    Pipeline.switchToPipelineMode(false);
                    mode = Mode.PIPELINE;
                }
                rebuildUI();
            }).bounds(toggleX, 6, toggleW, 20).build());

        addRenderableWidget(Button.builder(Component.translatable(RENDER_PIPELINE_SCREEN_SHADER_PACK),
                button -> Minecraft.getInstance().setScreen(new ShaderPackScreen(this)))
            .bounds(shaderPackX, 6, shaderPackW, 20)
            .build());

        if (mode == Mode.PIPELINE) {
            secondaryBtn = addRenderableWidget(
                Button.builder(Component.translatable(RENDER_PIPELINE_SCREEN_ADD_MODULE),
                    button -> {
                        Map<String, ModuleEntry> entries = Pipeline.INSTANCE.getModuleEntries();
                        if (entries != null && !entries.isEmpty()) {
                            activeSelector = new ModuleSelector(secondaryX, HEADER_HEIGHT + 4,
                                entries);
                        }
                    }).bounds(secondaryX, 6, secondaryW, 20).build());
        } else {
            Component activePresetText = activePreset != null
                ? Component.translatable(activePreset.name())
                : Component.literal("N/A");
            secondaryBtn = addRenderableWidget(
                Button.builder(Component.translatable(RENDER_PIPELINE_PRESET_NAME)
                    .append(Component.literal(": "))
                    .append(activePresetText), button -> {
                    if (!presets.isEmpty()) {
                        activePresetSelector = new PresetSelector(secondaryX, HEADER_HEIGHT + 4,
                            presets);
                    }
                }).bounds(secondaryX, 6, secondaryW, 20).build());
        }

        Button reloadBtn = addRenderableWidget(
            Button.builder(Component.translatable(RENDER_PIPELINE_SCREEN_RELOAD), button -> {
                if (mode == Mode.PIPELINE) {
                    refreshPipeline();
                } else {
                    applyActivePreset();
                }
            }).bounds(secondaryX + secondaryW + 5, 6, 100, 20).build());

        reloadBtn.active = true;
        secondaryBtn.visible = true;
        secondaryBtn.active = true;
        resetAllBtn = addRenderableWidget(AttributeWidgetUtil.buildGlobalResetButton(
            secondaryX + secondaryW + 110, 6, this::resetAllPipelineAttributes));

        if (mode == Mode.PRESET) {
            presetScrollY = 0;
            if (activePreset == null && !presets.isEmpty()) {
                activePreset = presets.get(0);
            }
            applyActivePreset();
        }

        syncResetAllButton();
    }

    public void refreshPipeline() {
        nodes.clear();
        for (Module module : Pipeline.INSTANCE.getModules()) {
            nodes.add(new ModuleNode(module));
        }

        moduleConnections.clear();
        var globalConnections = Pipeline.INSTANCE.getModuleConnections();
        globalConnections.forEach((src, list) -> list.forEach(
            dst -> moduleConnections.add(new ModuleConnection(src, dst))));
        for (ModuleNode node : nodes) {
            for (ImageConfig out : node.module.outputImageConfigs) {
                if (out.finalOutput) {
                    localFinalOutput = out;
                    break;
                }
            }
        }
    }

    public void syncToPipeline() {
        Pipeline.clear();
        for (ModuleNode node : nodes) {
            Pipeline.addModule(node.module);
        }

        for (ModuleConnection moduleConnection : moduleConnections) {
            Pipeline.connect(moduleConnection.src, moduleConnection.dst);
        }

        Pipeline.savePipeline();
        refreshPipeline();
    }

    @Override
    public void onClose() {
        if (isRebuilding()) {
            return;
        }

        if (mode == Mode.PIPELINE) {
            syncToPipeline();
        } else {
            syncPresetToPipeline();
        }
        rebuildQueued = true;
    }

    @Override
    public void tick() {
        super.tick();

        if (rebuildQueued || rebuildFuture == null || !rebuildFuture.isDone()) {
            return;
        }

        if (Pipeline.isNativeRebuildActive()) {
            return;
        }

        rebuildFuture.join();
        rebuildFuture = null;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (isRebuilding()) {
            this.renderBackground(context, mouseX, mouseY, delta);
            renderRebuildOverlay(context);
            if (rebuildQueued) {
                rebuildQueued = false;
                rebuildFuture = CompletableFuture.runAsync(Pipeline::build);
            }
            return;
        }

        for (ModuleNode node : nodes) {
            node.updateWidth(font);
        }
        syncResetAllButton();

        this.renderBackground(context, mouseX, mouseY, delta);

        context.pose().pushPose();
        context.pose().scale(GLOBAL_SCALE, GLOBAL_SCALE, 1f);

        int scaledMouseX = (int) (mouseX / GLOBAL_SCALE);
        int scaledMouseY = (int) (mouseY / GLOBAL_SCALE);

        for (Renderable drawable : this.renderables) {
            drawable.render(context, scaledMouseX, scaledMouseY, delta);
        }

        context.drawString(font,
            Component.translatable(RENDER_PIPELINE_SCREEN_BACK_HINT), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA);

        HoveredPresetAttribute hoveredPresetAttribute = null;
        if (mode == Mode.PIPELINE) {
            for (ModuleNode node : nodes) {
                drawModuleNode(context, node);
            }

            drawConnections(context);

            if (activeSelector != null) {
                context.pose().pushPose();
                context.pose().translate(0, 0, 200);
                activeSelector.render(context, scaledMouseX, scaledMouseY);
                context.pose().popPose();
            }
        } else {
            hoveredPresetAttribute = renderPresetMode(context, scaledMouseX, scaledMouseY);
            if (activePresetSelector != null) {
                context.pose().pushPose();
                context.pose().translate(0, 0, 200);
                activePresetSelector.render(context, scaledMouseX, scaledMouseY);
                context.pose().popPose();
                hoveredPresetAttribute = null;
            }
        }

        context.pose().popPose();

        if (hoveredPresetAttribute != null) {
            AttributeWidgetUtil.drawAttributeTooltip(context, font,
                hoveredPresetAttribute.module(), hoveredPresetAttribute.cfg(),
                hoveredPresetAttribute.focus(), mouseX, mouseY);
        }
    }

    private int scaledW() {
        return (int) (this.width / GLOBAL_SCALE);
    }

    private int scaledH() {
        return (int) (this.height / GLOBAL_SCALE);
    }

    private HoveredPresetAttribute renderPresetMode(GuiGraphics ctx, int mouseX, int mouseY) {
        int sw = scaledW();
        int x0 = 10;
        int y0 = presetListTopY();
        int contentW = sw - 20;

        presetScrollY = AttributeWidgetUtil.clampScroll(presetScrollY, presetViewportHeight(),
            presetContentHeight());
        int y = y0 + presetScrollY;
        int rowH = PRESET_ROW_HEIGHT;
        HoveredPresetAttribute hoveredAttribute = null;

        for (PresetModuleBlock block : presetBlocks) {
            int visibleRowCount = countVisibleRows(block);
            if (visibleRowCount == 0) {
                clearBlockVisibility(block);
                continue;
            }
            int titleY = y;

            boolean titleVisible = titleY + PRESET_GROUP_HEADER_HEIGHT >= presetListTopY()
                && titleY <= presetViewportBottomY();
            if (titleVisible) {
                AttributeWidgetUtil.drawGroupHeader(ctx, font,
                    block.module.translateText(block.module.name), x0, titleY, contentW,
                    PRESET_GROUP_HEADER_HEIGHT);
            }

            y += PRESET_GROUP_HEADER_HEIGHT;

            int visibleIndex = 0;
            for (PresetRow row : block.rows) {
                boolean rowVisible = Pipeline.isAttributeVisible(block.module, row.cfg);
                if (!rowVisible) {
                    setPresetRowVisible(row, false);
                    continue;
                }

                int ry = y + visibleIndex * rowH;
                visibleIndex++;
                boolean visible = ry >= presetListTopY() && ry <= presetViewportBottomY();
                AttributeWidgetUtil.TooltipFocus rowFocus = scaledPresetRowTooltipFocus(x0,
                    contentW, ry);

                if (visible) {
                    Component label = block.module.translateText(row.cfg.name);
                    ctx.drawString(font, label, x0 + 10, ry + 6, 0xFFD0D0D0);
                    if (mouseX >= x0 + 10 && mouseX <= x0 + 10 + font.width(label)
                        && mouseY >= ry + 4 && mouseY <= ry + 18) {
                        hoveredAttribute = new HoveredPresetAttribute(block.module, row.cfg,
                            rowFocus);
                    }
                }

                layoutPresetRowWidgets(row, x0 + contentW - 10, ry);

                String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
                boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

                AttributeWidgetUtil.syncWidgetVisibility(row.widgets, visible);

                for (AbstractWidget w : row.widgets) {
                    if (visible && !AttributeWidgetUtil.isResetButton(w)
                        && w.isMouseOver(mouseX, mouseY)) {
                        hoveredAttribute = new HoveredPresetAttribute(block.module, row.cfg,
                            rowFocus);
                    }

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

                    if (!(w instanceof EditBox)) {
                        continue;
                    }

                    int bx = w.getX() - 1;
                    int by = w.getY() - 1;
                    int bw = w.getWidth() + 2;
                    int bh = w.getHeight() + 2;
                    AttributeWidgetUtil.drawBorder(ctx, bx, by, bw, bh,
                        ok ? 0xFF34D058 : 0xFFE5534B);
                }
            }

            y += visibleRowCount * rowH + PRESET_GROUP_BOTTOM_GAP;
        }

        AttributeWidgetUtil.drawVerticalScrollbar(ctx, sw, presetListTopY(),
            presetViewportHeight(), presetContentHeight(), presetScrollY,
            presetScrollbarDragging, mouseX, mouseY);
        return hoveredAttribute;
    }

    private AttributeWidgetUtil.TooltipFocus scaledPresetRowTooltipFocus(int x0, int contentW,
        int y) {
        return AttributeWidgetUtil.tooltipFocus(
            Math.round((x0 + 10) * GLOBAL_SCALE),
            Math.round(y * GLOBAL_SCALE),
            Math.round((x0 + contentW - 10) * GLOBAL_SCALE),
            Math.round((y + PRESET_ROW_HEIGHT) * GLOBAL_SCALE));
    }

    private void renderRebuildOverlay(GuiGraphics context) {
        context.fill(0, 0, this.width, this.height, 0xE0000000);

        int popupWidth = 160;
        int popupHeight = 30;
        int x0 = (this.width - popupWidth) / 2;
        int y0 = (this.height - popupHeight) / 2;

        context.fill(x0, y0, x0 + popupWidth, y0 + popupHeight, 0xFF000000);
        context.fill(x0, y0, x0 + popupWidth, y0 + 1, 0xFFFFFFFF);
        context.fill(x0, y0 + popupHeight - 1, x0 + popupWidth, y0 + popupHeight, 0xFFFFFFFF);
        context.fill(x0, y0, x0 + 1, y0 + popupHeight, 0xFFFFFFFF);
        context.fill(x0 + popupWidth - 1, y0, x0 + popupWidth, y0 + popupHeight, 0xFFFFFFFF);

        context.drawCenteredString(font,
            Component.translatable(RENDER_PIPELINE_SCREEN_REBUILDING),
            this.width / 2, y0 + 9, 0xFFEAEAEA);
    }

    private boolean isRebuilding() {
        return rebuildQueued || rebuildFuture != null;
    }

    private int countVisibleRows(PresetModuleBlock block) {
        int count = 0;
        for (PresetRow row : block.rows) {
            if (Pipeline.isAttributeVisible(block.module, row.cfg)) {
                count++;
            }
        }
        return count;
    }

    private void clearBlockVisibility(PresetModuleBlock block) {
        for (PresetRow row : block.rows) {
            setPresetRowVisible(row, false);
        }
    }

    private void setPresetRowVisible(PresetRow row, boolean visible) {
        AttributeWidgetUtil.syncWidgetVisibility(row.widgets, visible);
    }

    private void layoutPresetRowWidgets(PresetRow row, int rightEdge, int y) {
        int singleWidth = 200;
        int tripleWidth = 64;
        int gap = 4;
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, singleWidth, tripleWidth, gap);
        int x = rightEdge - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, singleWidth, tripleWidth, gap);
    }

    private int presetListTopY() {
        return HEADER_HEIGHT + 28;
    }

    private int presetViewportBottomY() {
        return scaledH() - PRESET_VIEWPORT_BOTTOM_PADDING;
    }

    private int presetViewportHeight() {
        return Math.max(0, presetViewportBottomY() - presetListTopY());
    }

    private int presetContentHeight() {
        int contentHeight = 0;
        for (PresetModuleBlock block : presetBlocks) {
            int visibleRowCount = countVisibleRows(block);
            if (visibleRowCount == 0) {
                continue;
            }
            contentHeight += PRESET_GROUP_HEADER_HEIGHT + (visibleRowCount * PRESET_ROW_HEIGHT)
                + PRESET_GROUP_BOTTOM_GAP;
        }
        return contentHeight;
    }

    private PortPos getPortPosition(ImageConfig config, boolean isOutput) {
        for (ModuleNode node : nodes) {
            if (node.module == config.owner) {
                int x = (int) node.module.x;
                int y = (int) node.module.y + HEADER_HEIGHT;
                var list =
                    isOutput ? node.module.outputImageConfigs : node.module.inputImageConfigs;
                int index = list.indexOf(config);

                if (index != -1) {
                    int ry = y + node.headerH + node.pad + index * node.rowH + 7;
                    int dotY = ry + 7;
                    int dotX = isOutput ? (x + node.width - 10) : (x + 10);
                    return new PortPos(dotX, dotY);
                }
            }
        }
        return null;
    }

    private void drawBezier(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        int segments = 32;
        float prevX = x1;
        float prevY = y1;
        float thickness = 1.2f;

        float ctrlOffset = Math.abs(x2 - x1) * 0.5f;

        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float invT = 1 - t;
            float b0 = invT * invT * invT;
            float b1 = 3 * invT * invT * t;
            float b2 = 3 * invT * t * t;
            float b3 = t * t * t;

            float cx = b0 * x1 + b1 * (x1 + ctrlOffset) + b2 * (x2 - ctrlOffset) + b3 * x2;
            float cy = b0 * y1 + b1 * y1 + b2 * y2 + b3 * y2;

            ((IDrawContextExt) (Object) ctx).radiance$drawOrientedQuad(RenderType.gui(),
                prevX, prevY, cx, cy, thickness, color);

            prevX = cx;
            prevY = cy;
        }
    }

    private void drawConnections(GuiGraphics context) {
        for (ModuleConnection link : moduleConnections) {
            PortPos p1 = getPortPosition(link.src, true);
            PortPos p2 = getPortPosition(link.dst, false);
            if (p1 != null && p2 != null) {
                drawBezier(context, p1.x, p1.y, p2.x, p2.y, getFormatColor(link.src.format));
            }
        }
        context.flush();
    }

    private void drawModuleNode(GuiGraphics context, ModuleNode moduleNode) {
        int x = (int) moduleNode.module.x;
        int y = (int) moduleNode.module.y + HEADER_HEIGHT;
        int w = moduleNode.width;
        int h = moduleNode.height();

        context.fill(x, y, x + w, y + h, 0xFF20242C);

        context.fill(x, y, x + w, y + moduleNode.headerH, 0xFF2B3240);

        context.drawString(font, moduleNode.module.translateText(moduleNode.module.name), x + 6,
            y + 5, 0xFFEAEAEA);

        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (moduleNode.headerH - btnSize) / 2;
        int gearX = deleteX - btnSize - 2;

        context.blit(GEAR_TEX, gearX, btnY, 0, 0, btnSize, btnSize, btnSize, btnSize);

        context.drawString(font, "×", deleteX + 3, btnY + 2, 0xFFFF5A5A);

        for (int i = 0; i < moduleNode.rows(); i++) {
            int ry = y + moduleNode.headerH + moduleNode.pad + i * moduleNode.rowH + 7;

            if (i < moduleNode.module.inputImageConfigs.size()) {
                ImageConfig in = moduleNode.module.inputImageConfigs.get(i);
                int dotX = x + 10;
                int dotY = ry + 7;

                int color = (in == pendingPort) ? 0xFFFFFF00 : getFormatColor(in.format);
                boolean isConnected = moduleConnections.stream().anyMatch(l -> l.dst == in);
                drawPortDot(context, dotX, dotY, color, isConnected, false);

                context.drawString(font, in.name, x + 18, ry + 2, 0xFFD0D0D0);
            }

            if (i < moduleNode.module.outputImageConfigs.size()) {
                ImageConfig out = moduleNode.module.outputImageConfigs.get(i);
                int dotX = x + w - 10;
                int dotY = ry + 7;

                int color = (out == pendingPort) ? 0xFFFFFF00 : getFormatColor(out.format);
                boolean isConnected = moduleConnections.stream().anyMatch(l -> l.src == out);
                drawPortDot(context, dotX, dotY, color, isConnected, out == localFinalOutput);

                int nameWidth = font.width(out.name);
                context.drawString(font, out.name, (dotX - 8) - nameWidth, ry + 2,
                    0xFFD0D0D0);
            }
        }
    }

    private void drawPortDot(GuiGraphics ctx, int cx, int cy, int color, boolean filled,
        boolean isFinal) {
        ctx.fill(cx - 4, cy - 4, cx + 5, cy + 5, isFinal ? 0xFF55FF55 : 0xFF000000);
        ctx.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
        if (!filled) {
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF20242C);
        }
    }

    private boolean testCycle(ImageConfig src, ImageConfig dst) {
        if (src.owner == dst.owner) {
            return true;
        }
        return hasPath(dst.owner, src.owner);
    }

    private boolean hasPath(Module start, Module target) {
        if (start == target) {
            return true;
        }
        for (ModuleConnection link : moduleConnections) {
            if (link.src.owner == start) {
                if (hasPath(link.dst.owner, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleLocalConnection(ImageConfig current, boolean isOutput) {
        if (pendingPort == null) {
            pendingPort = current;
            isPendingOutput = isOutput;
        } else {
            if (isPendingOutput != isOutput) {
                ImageConfig src = isPendingOutput ? pendingPort : current;
                ImageConfig dst = isPendingOutput ? current : pendingPort;

                if (!Objects.equals(src.format, dst.format)) {
                    pendingPort = null;
                    return;
                }

                if (src.owner == dst.owner) {
                    pendingPort = null;
                    return;
                }

                if (!testCycle(src, dst)) {
                    moduleConnections.removeIf(link -> link.dst == dst);
                    moduleConnections.add(new ModuleConnection(src, dst));
                }
            }
            pendingPort = null;
        }
    }

    private boolean isGearClicked(ModuleNode node, double mouseX, double mouseY) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width;
        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (node.headerH - btnSize) / 2;
        int gearX = deleteX - btnSize - 2;
        return mouseX >= gearX && mouseX <= gearX + btnSize && mouseY >= btnY
            && mouseY <= btnY + btnSize;
    }

    private boolean isDeleteClicked(ModuleNode node, double mouseX, double mouseY) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width;
        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (node.headerH - btnSize) / 2;
        return mouseX >= deleteX && mouseX <= deleteX + btnSize && mouseY >= btnY
            && mouseY <= btnY + btnSize;
    }

    private ImageConfig getClickedPort(ModuleNode node, double mouseX, double mouseY,
        boolean isOutput) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width;

        var configs = isOutput ? node.module.outputImageConfigs : node.module.inputImageConfigs;
        if (configs == null) {
            return null;
        }

        for (int i = 0; i < configs.size(); i++) {
            int ry = y + node.headerH + node.pad + i * node.rowH + 7;
            int dotY = ry + 7;
            int dotX = isOutput ? (x + w - 10) : (x + 10);

            if (Math.abs(mouseX - dotX) <= 8 && Math.abs(mouseY - dotY) <= 8) {
                return configs.get(i);
            }
        }
        return null;
    }

    private void deleteNode(ModuleNode node) {
        nodes.remove(node);
        moduleConnections.removeIf(
            link -> link.src.owner == node.module || link.dst.owner == node.module);

        if (draggedNode == node) {
            draggedNode = null;
        }
        if (pendingPort != null && pendingPort.owner == node.module) {
            pendingPort = null;
        }
        if (localFinalOutput != null && localFinalOutput.owner == node.module) {
            localFinalOutput = null;
        }

        activeSelector = null;
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isRebuilding()) {
            return true;
        }

        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        isPanning = false;
        draggedNode = null;

        if (mouseY < HEADER_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (mode == Mode.PRESET) {
            if (activePresetSelector != null) {
                if (activePresetSelector.onClick(mouseX, mouseY)) {
                    activePresetSelector = null;
                    return true;
                }
                activePresetSelector = null;
            }
            if (button == 0 && AttributeWidgetUtil.isVerticalScrollbarMouseOver(mouseX, mouseY,
                scaledW(), presetListTopY(), presetViewportHeight(), presetContentHeight())) {
                presetScrollbarDragging = true;
                presetScrollbarDragOffsetY = AttributeWidgetUtil.verticalScrollbarDragOffsetY(mouseY,
                    presetListTopY(), presetViewportHeight(), presetContentHeight(), presetScrollY);
                presetScrollY = AttributeWidgetUtil.scrollFromVerticalScrollbarDrag(mouseY,
                    presetScrollbarDragOffsetY, presetListTopY(), presetViewportHeight(),
                    presetContentHeight());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (ModuleNode node : nodes) {
            if (button == 0 && isDeleteClicked(node, mouseX, mouseY)) {
                deleteNode(node);
                return true;
            }

            if (button == 0 && isGearClicked(node, mouseX, mouseY)) {
                Minecraft.getInstance()
                    .setScreen(new ModuleAttributeScreen(this, node.module));
                return true;
            }
        }

        for (ModuleNode node : nodes) {
            ImageConfig clickedIn = getClickedPort(node, mouseX, mouseY, false);
            ImageConfig clickedOut = getClickedPort(node, mouseX, mouseY, true);
            ImageConfig current = (clickedIn != null) ? clickedIn : clickedOut;

            if (current != null) {
                if (button == 0) {
                    handleLocalConnection(current, clickedOut != null);
                    return true;
                } else if (button == 1) {
                    boolean isConnected = moduleConnections.stream()
                        .anyMatch(l -> l.src == current || l.dst == current);

                    if (isConnected) {
                        moduleConnections.removeIf(
                            link -> link.src == current || link.dst == current);
                    } else if (clickedOut != null) {
                        if (localFinalOutput != null && localFinalOutput != current) {
                            localFinalOutput.finalOutput = false;
                        }

                        localFinalOutput = (localFinalOutput == current) ? null : current;
                        current.finalOutput = (localFinalOutput == current);
                    }
                    return true;
                }
            }
        }

        if (activeSelector != null) {
            if (activeSelector.onClick(mouseX, mouseY)) {
                activeSelector = null;
                return true;
            }
            activeSelector = null;
        }

        for (int i = nodes.size() - 1; i >= 0; i--) {
            ModuleNode node = nodes.get(i);
            if (mouseX >= node.module.x && mouseX <= node.module.x + node.width && mouseY >= (
                node.module.y + HEADER_HEIGHT) && mouseY <= (node.module.y + HEADER_HEIGHT
                + node.height())) {
                if (button == 0) {
                    draggedNode = node;
                } else if (button == 1) {
                    isPanning = true;
                }
                return true;
            }
        }

        if (button == 1 || button == 0) {
            isPanning = true;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
        double verticalAmount) {
        if (isRebuilding()) {
            return true;
        }

        if (mode != Mode.PRESET) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        presetScrollY = AttributeWidgetUtil.clampScroll(presetScrollY + (int) (verticalAmount * 12),
            presetViewportHeight(), presetContentHeight());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX,
        double deltaY) {
        if (isRebuilding()) {
            return true;
        }

        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        if (mode == Mode.PRESET) {
            if (presetScrollbarDragging && button == 0) {
                presetScrollY = AttributeWidgetUtil.scrollFromVerticalScrollbarDrag(mouseY,
                    presetScrollbarDragOffsetY, presetListTopY(), presetViewportHeight(),
                    presetContentHeight());
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX / GLOBAL_SCALE,
                deltaY / GLOBAL_SCALE);
        }

        if (super.mouseDragged(mouseX, mouseY, button, deltaX / GLOBAL_SCALE,
            deltaY / GLOBAL_SCALE)) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        double dx = mouseX - lastMouseX;
        double dy = mouseY - lastMouseY;

        if (button == 0 && draggedNode != null) {
            draggedNode.module.x += dx;
            draggedNode.module.y += dy;
        } else if (isPanning && (button == 1 || button == 0)) {
            for (ModuleNode node : nodes) {
                node.module.x += dx;
                node.module.y += dy;
            }
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isRebuilding()) {
            return true;
        }

        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        if (button == 0 && presetScrollbarDragging) {
            presetScrollbarDragging = false;
            return true;
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        boolean handled = super.mouseReleased(mouseX, mouseY, button);

        draggedNode = null;
        isPanning = false;

        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isRebuilding()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (isRebuilding()) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }


    private record ModuleConnection(ImageConfig src, ImageConfig dst) {

    }

    private record PortPos(int x, int y) {

    }

    private record HoveredPresetAttribute(Module module, AttributeConfig cfg,
        AttributeWidgetUtil.TooltipFocus focus) {

    }

    private class ModuleSelector {

        private final int x, y, width;
        private final List<ModuleEntry> options;
        private final int itemHeight = 18;

        public ModuleSelector(int x, int y, Map<String, ModuleEntry> entries) {
            this.x = x;
            this.y = y;
            this.options = new ArrayList<>();
            for (ModuleEntry entry : entries.values()) {
                if (entry == null || entry.name == null) {
                    continue;
                }
                if (Pipeline.isModuleAvailable(entry.name)) {
                    this.options.add(entry);
                }
            }
            this.width = 120;
        }

        public void render(GuiGraphics ctx, int mouseX, int mouseY) {
            int currentY = y;
            ctx.fill(x - 1, y - 1, x + width + 1, y + (options.size() * itemHeight) + 1,
                0xFFFFFFFF);

            for (ModuleEntry entry : options) {
                boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY
                    && mouseY <= currentY + itemHeight;
                ctx.fill(x, currentY, x + width, currentY + itemHeight,
                    hovered ? 0xFF444444 : 0xFF222222);
                ctx.drawString(font, Component.translatable(entry.name), x + 5,
                    currentY + 5, 0xFFE0E0E0);
                currentY += itemHeight;
            }
        }

        public boolean onClick(double mouseX, double mouseY) {
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + (options.size()
                * itemHeight)) {
                return false;
            }
            int index = (int) ((mouseY - y) / itemHeight);
            if (index >= 0 && index < options.size()) {
                ModuleEntry selected = options.get(index);
                if (!Pipeline.isModuleAvailable(selected.name)) {
                    return true;
                }
                Module module = selected.loadModule();
                module.x = 100;
                module.y = 100;
                nodes.add(new ModuleNode(module));
                return true;
            }
            return false;
        }
    }

    private enum Mode {
        PIPELINE("render_pipeline.mode.pipeline"), PRESET("render_pipeline.mode.preset");

        public final String key;

        Mode(String key) {
            this.key = key;
        }
    }

    private record PresetEntry(String name) {

    }

    private void registerDefaultPresets() {
        this.presets.clear();
        if (Pipeline.isPresetAvailable(Presets.RT_DLSSRR.key)) {
            this.presets.add(new PresetEntry(Presets.RT_DLSSRR.key));
        }
        if (Pipeline.isPresetAvailable(Presets.RT_NRD_DLSS.key)) {
            this.presets.add(new PresetEntry(Presets.RT_NRD_DLSS.key));
        }
        if (Pipeline.isPresetAvailable(Presets.RT_NRD_FSR.key)) {
            this.presets.add(new PresetEntry(Presets.RT_NRD_FSR.key));
        }
        if (Pipeline.isPresetAvailable(Presets.RT_NRD_XESS.key)) {
            this.presets.add(new PresetEntry(Presets.RT_NRD_XESS.key));
        }
    }

    private void applyActivePreset() {
        presetBlocks.clear();
        for (AbstractWidget w : presetWidgets) {
            this.children().remove(w);
            this.renderables.remove(w);
        }
        presetWidgets.clear();

        if (activePreset == null) {
            return;
        }

        Pipeline.switchToPresetMode(activePreset.name(), false);

        List<Module> modules = new ArrayList<>(Pipeline.INSTANCE.getModules());

        for (Module m : modules) {
            PresetModuleBlock block = new PresetModuleBlock(m);
            presetBlocks.add(block);
            if (m.attributeConfigs == null || m.attributeConfigs.isEmpty()) {
                continue;
            }
            for (AttributeConfig cfg : m.attributeConfigs) {
                if (Pipeline.isMainRenderShaderPackAttribute(m, cfg)) {
                    continue;
                }
                List<AbstractWidget> ws = buildPresetWidgets(m, cfg);
                PresetRow row = new PresetRow(cfg, ws);
                setPresetRowVisible(row, Pipeline.isAttributeVisible(m, cfg));
                for (AbstractWidget w : ws) {
                    presetWidgets.add(addRenderableWidget(w));
                }
                block.rows.add(row);
            }
        }

        if (secondaryBtn != null && mode == Mode.PRESET) {
            Component activePresetText = activePreset != null
                ? Component.translatable(activePreset.name())
                : Component.literal("N/A");
            secondaryBtn.setMessage(Component.translatable(RENDER_PIPELINE_PRESET_NAME)
                .append(Component.literal(": "))
                .append(activePresetText));
        }
    }

    private void syncPresetToPipeline() {
        Pipeline.savePipeline();
    }

    private List<AbstractWidget> buildPresetWidgets(Module module, AttributeConfig cfg) {
        return AttributeWidgetUtil.buildWidgets(module, cfg, font, 200, 64,
            this::applyActivePreset);
    }

    private void resetAllPipelineAttributes() {
        boolean changed = false;
        if (mode == Mode.PRESET && !presetBlocks.isEmpty()) {
            for (PresetModuleBlock block : presetBlocks) {
                List<AttributeConfig> attributes = new ArrayList<>();
                for (PresetRow row : block.rows) {
                    attributes.add(row.cfg);
                }
                changed |= AttributeWidgetUtil.resetAttributesToDefaults(block.module, attributes);
            }
        } else {
            for (ModuleNode node : nodes) {
                if (node == null || node.module == null || node.module.attributeConfigs == null) {
                    continue;
                }
                List<AttributeConfig> attributes = new ArrayList<>();
                for (AttributeConfig cfg : node.module.attributeConfigs) {
                    if (!Pipeline.isMainRenderShaderPackAttribute(node.module, cfg)) {
                        attributes.add(cfg);
                    }
                }
                changed |= AttributeWidgetUtil.resetAttributesToDefaults(node.module, attributes);
            }
        }

        if (changed) {
            if (mode == Mode.PRESET) {
                applyActivePreset();
            } else {
                refreshPipeline();
            }
        }
        syncResetAllButton();
    }

    private void syncResetAllButton() {
        if (resetAllBtn == null) {
            return;
        }
        boolean show = AttributeWidgetUtil.hasNonDefaultValue(currentPipelineSettingAttributes());
        resetAllBtn.visible = show;
        resetAllBtn.active = show;
    }

    private List<AttributeConfig> currentPipelineSettingAttributes() {
        List<AttributeConfig> attributes = new ArrayList<>();
        if (mode == Mode.PRESET && !presetBlocks.isEmpty()) {
            for (PresetModuleBlock block : presetBlocks) {
                for (PresetRow row : block.rows) {
                    attributes.add(row.cfg);
                }
            }
            return attributes;
        }

        for (ModuleNode node : nodes) {
            if (node == null || node.module == null || node.module.attributeConfigs == null) {
                continue;
            }
            for (AttributeConfig cfg : node.module.attributeConfigs) {
                if (Pipeline.isMainRenderShaderPackAttribute(node.module, cfg)) {
                    continue;
                }
                attributes.add(cfg);
            }
        }
        return attributes;
    }

    private class PresetSelector {

        private final int x, y, width;
        private final List<PresetEntry> options;
        private final int itemHeight = 18;

        public PresetSelector(int x, int y, List<PresetEntry> presets) {
            this.x = x;
            this.y = y;
            this.options = new ArrayList<>(presets);
            this.width = 140;
        }

        public void render(GuiGraphics ctx, int mouseX, int mouseY) {
            int currentY = y;
            ctx.fill(x - 1, y - 1, x + width + 1, y + (options.size() * itemHeight) + 1,
                0xFFFFFFFF);

            for (PresetEntry entry : options) {
                boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY
                    && mouseY <= currentY + itemHeight;
                ctx.fill(x, currentY, x + width, currentY + itemHeight,
                    hovered ? 0xFF444444 : 0xFF222222);
                ctx.drawString(font, Component.translatable(entry.name()), x + 5,
                    currentY + 5, 0xFFE0E0E0);
                currentY += itemHeight;
            }
        }

        public boolean onClick(double mouseX, double mouseY) {
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + (options.size()
                * itemHeight)) {
                return false;
            }
            int index = (int) ((mouseY - y) / itemHeight);
            if (index >= 0 && index < options.size()) {
                activePreset = options.get(index);
                presetScrollY = 0;
                applyActivePreset();
                return true;
            }
            return false;
        }
    }

    private static class PresetModuleBlock {

        private final Module module;
        private final List<PresetRow> rows = new ArrayList<>();

        private PresetModuleBlock(Module module) {
            this.module = module;
        }
    }

    private record PresetRow(AttributeConfig cfg, List<AbstractWidget> widgets) {

    }
}
