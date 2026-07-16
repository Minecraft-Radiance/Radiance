package com.radiance.client.gui;

import static com.radiance.client.gui.compat.GuiCompat.*;

import com.radiance.Radiance;
import com.radiance.client.config.VRPerformanceConfig;
import com.radiance.client.gui.compat.PlatformScreen;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.ModuleEntry;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.Presets;
import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class RenderPipelineScreen extends PlatformScreen {

    private static final Object GEAR_TEX = id(Radiance.MOD_ID, "textures/gui/render_pipeline/gear.png");

    private static final int UNKNOWN_FORMAT_COLOR = 0xFF9CA3AF;
    private static final Map<String, Integer> FORMAT_COLORS = Map.of("R8G8B8A8_SRGB", 0xFF4EA5FF,
        "R8G8B8A8_UNORM", 0xFF62E36A, "R8_UNORM", 0xFF8FD14F, "R8_UINT", 0xFFA78BFA,
        "R16G16_SFLOAT", 0xFFFF6BD6, "R16_SFLOAT", 0xFF6BE6FF,
        "R16G16B16A16_SFLOAT", 0xFFFFB84E);

    private static final int HEADER_HEIGHT = 32;
    private static final float GLOBAL_SCALE = 0.75f;
    private static final String BACK_KEY = "render_pipeline_screen.back";
    private static final String RELOAD_KEY = "render_pipeline_screen.reload";
    private static final String ADD_MODULE_KEY = "render_pipeline_screen.add_module";
    private static final String SHADER_PACK_KEY = "render_pipeline_screen.shader_pack";
    private static final String VR_KEY = "render_pipeline_screen.vr";
    private static final String BACK_HINT_KEY = "render_pipeline_screen.back_hint";
    private static final String REBUILDING_KEY = "render_pipeline_screen.rebuilding";
    private static final String PRESET_NAME_KEY = "render_pipeline.preset.name";
    private static final String MODE_NAME_KEY = "render_pipeline.mode.name";

    private final Object parent;
    private final List<ModuleNode> nodes = new ArrayList<>();
    private final List<ModuleConnection> moduleConnections = new ArrayList<>();
    private ModuleNode draggedNode = null;
    private double lastMouseX, lastMouseY;
    private ModuleSelector activeSelector = null;
    private Object activeVRSettings = null;
    private ImageConfig localFinalOutput = null;
    private ImageConfig pendingPort = null;
    private boolean isPendingOutput = false;
    private boolean isPanning = false;

    private Mode mode = Mode.PIPELINE;
    private final List<PresetEntry> presets = new ArrayList<>();
    private PresetEntry activePreset = null;
    private PresetSelector activePresetSelector = null;
    private final List<PresetModuleBlock> presetBlocks = new ArrayList<>();
    private final List<Object> presetWidgets = new ArrayList<>();
    private int presetScrollY = 0;

    private Object modeToggleBtn;
    private Object secondaryBtn;
    private CompletableFuture<Void> rebuildFuture = null;
    private boolean rebuildQueued = false;

    public RenderPipelineScreen(Object parent) {
        super(txtLit("Render Pipeline"));
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
        return c != null ? c : UNKNOWN_FORMAT_COLOR;
    }

    @Override
    protected void init() {
        rebuildUI();
    }

    private void rebuildUI() {
        screenClearChildren(this);
        activeSelector = null;
        activePresetSelector = null;
        activeVRSettings = null;
        presetWidgets.clear();

        if (mode == Mode.PRESET && activePreset == null && !presets.isEmpty()) {
            activePreset = presets.getFirst();
        }
        if (mode == Mode.PIPELINE) {
            refreshPipeline();
        }

        int backX = 10, backW = 60;
        int toggleX = backX + backW + 5, toggleW = 110;
        int shaderPackX = toggleX + toggleW + 5, shaderPackW = 110;
        int vrX = shaderPackX + shaderPackW + 5, vrW = 54;
        int secondaryX = vrX + vrW + 5, secondaryW = 150;

        // Back button
        screenAddChild(this, newButton(txt(BACK_KEY), btn -> close(), backW));
        setX(screenChildren(this).getLast(), backX);
        setY(screenChildren(this).getLast(), 6);

        // Mode toggle
        modeToggleBtn = newButton(
            txtAppend(txt(MODE_NAME_KEY), txtAppend(txtLit(": "), txt(mode.key))),
            btn -> {
                if (mode == Mode.PIPELINE) {
                    if (activePreset == null && !presets.isEmpty()) activePreset = presets.getFirst();
                    Pipeline.switchToPresetMode(activePreset != null ? activePreset.name() : "Default", false);
                    mode = Mode.PRESET;
                } else {
                    Pipeline.switchToPipelineMode(false);
                    mode = Mode.PIPELINE;
                }
                rebuildUI();
            }, toggleW);
        screenAddChild(this, modeToggleBtn);
        setX(modeToggleBtn, toggleX);
        setY(modeToggleBtn, 6);

        // Shader pack
        screenAddChild(this, newButton(txt(SHADER_PACK_KEY),
            btn -> setScreen(getMinecraft(), new ShaderPackScreen(this)), shaderPackW));
        setX(screenChildren(this).getLast(), shaderPackX);
        setY(screenChildren(this).getLast(), 6);

        // VR
        screenAddChild(this, newButton(txt(VR_KEY), btn -> {
            activeSelector = null;
            activePresetSelector = null;
            activeVRSettings = (activeVRSettings == null) ? createVRSettings(vrX, HEADER_HEIGHT + 4) : null;
        }, vrW));
        setX(screenChildren(this).getLast(), vrX);
        setY(screenChildren(this).getLast(), 6);

        // Secondary button
        if (mode == Mode.PIPELINE) {
            secondaryBtn = newButton(txt(ADD_MODULE_KEY), btn -> {
                Map<String, ModuleEntry> entries = Pipeline.INSTANCE.getModuleEntries();
                if (entries != null && !entries.isEmpty()) {
                    activeVRSettings = null;
                    activeSelector = new ModuleSelector(secondaryX, HEADER_HEIGHT + 4, entries);
                }
            }, secondaryW);
        } else {
            Object presetText = activePreset != null ? txt(activePreset.name()) : txtLit("N/A");
            secondaryBtn = newButton(
                txtAppend(txt(PRESET_NAME_KEY), txtAppend(txtLit(": "), presetText)),
                btn -> {
                    if (!presets.isEmpty()) {
                        activeVRSettings = null;
                        activePresetSelector = new PresetSelector(secondaryX, HEADER_HEIGHT + 4, presets);
                    }
                }, secondaryW);
        }
        screenAddChild(this, secondaryBtn);
        setX(secondaryBtn, secondaryX);
        setY(secondaryBtn, 6);

        // Reload button
        Object reloadBtn = newButton(txt(RELOAD_KEY), btn -> {
            if (mode == Mode.PIPELINE) refreshPipeline();
            else applyActivePreset();
        }, 100);
        screenAddChild(this, reloadBtn);
        setX(reloadBtn, secondaryX + secondaryW + 5);
        setY(reloadBtn, 6);
        setActive(reloadBtn, true);
        setVisible(secondaryBtn, true);
        setActive(secondaryBtn, true);

        if (mode == Mode.PRESET) {
            presetScrollY = 0;
            if (activePreset == null && !presets.isEmpty()) activePreset = presets.get(0);
            applyActivePreset();
        }
    }

    /** Stub: VRSettingsPopup is not available in 26.2+ */
    private Object createVRSettings(int x, int y) { return null; }

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
    public void close() {
        if (isRebuilding()) return;
        if (mode == Mode.PIPELINE) syncToPipeline();
        else syncPresetToPipeline();
        rebuildQueued = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildQueued || rebuildFuture == null || !rebuildFuture.isDone()) return;
        if (Pipeline.isNativeRebuildActive()) return;
        rebuildFuture.join();
        rebuildFuture = null;
        setScreen(getMinecraft(), parent);
    }

    @Override
    protected void renderScreen(Object ctx, int mouseX, int mouseY, float delta) {
        if (isRebuilding()) {
            renderRebuildOverlay(ctx);
            if (rebuildQueued) {
                rebuildQueued = false;
                rebuildFuture = CompletableFuture.runAsync(Pipeline::build);
            }
            return;
        }

        for (ModuleNode node : nodes) {
            node.updateWidth(screenFont(this));
        }

        matricesPush(ctx);
        matricesScale(ctx, GLOBAL_SCALE, GLOBAL_SCALE, 1f);

        int smx = (int) (mouseX / GLOBAL_SCALE);
        int smy = (int) (mouseY / GLOBAL_SCALE);

        Object font = screenFont(this);
        drawText(ctx, font, txt(BACK_HINT_KEY), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA, true);

        if (mode == Mode.PIPELINE) {
            for (ModuleNode node : nodes) drawModuleNode(ctx, node);
            drawConnections(ctx);
            if (activeSelector != null) {
                matricesPush(ctx);
                matricesTranslate(ctx, 0, 0, 200);
                activeSelector.render(ctx, smx, smy);
                matricesPop(ctx);
            }
        } else {
            renderPresetMode(ctx);
            if (activePresetSelector != null) {
                matricesPush(ctx);
                matricesTranslate(ctx, 0, 0, 200);
                activePresetSelector.render(ctx, smx, smy);
                matricesPop(ctx);
            }
        }

        if (activeVRSettings != null) {
            matricesPush(ctx);
            matricesTranslate(ctx, 0, 0, 220);
            renderVRSettings(activeVRSettings, ctx, smx, smy);
            matricesPop(ctx);
        }

        matricesPop(ctx);
    }

    private void renderVRSettings(Object vs, Object ctx, int mx, int my) { /* stub */ }

    private int scaledW() {
        return (int) (this.width / GLOBAL_SCALE);
    }

    private int scaledH() {
        return (int) (this.height / GLOBAL_SCALE);
    }

    private void renderPresetMode(Object ctx) {
        int sw = scaledW(), sh = scaledH();
        int x0 = 10, y0 = HEADER_HEIGHT + 28;
        int contentW = sw - 20;
        int y = y0 + presetScrollY;
        int titleGap = 12, afterTitle = 8, rowH = 22;
        Object font = screenFont(this);

        for (PresetModuleBlock block : presetBlocks) {
            if (block.rows.isEmpty()) continue;
            int titleY = y;
            int tw = textWidth(font, block.module.translateText(block.module.name));
            int tx = x0 + (contentW - tw) / 2;

            boolean titleVisible = titleY >= (HEADER_HEIGHT + 18) && titleY <= (sh - 24);
            if (titleVisible) {
                drawText(ctx, font, block.module.translateText(block.module.name), tx, titleY, 0xFFEAEAEA, true);
            }

            y += titleGap + afterTitle;
            for (int i = 0; i < block.rows.size(); i++) {
                PresetRow row = block.rows.get(i);
                int ry = y + i * rowH;
                boolean visible = ry >= (HEADER_HEIGHT + 18) && ry <= (sh - 24);
                if (visible) {
                    drawText(ctx, font, block.module.translateText(row.cfg.name), x0 + 10, ry + 6, 0xFFD0D0D0, true);
                }
                layoutPresetRowWidgets(row, x0 + contentW - 10, ry);
                String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(java.util.Locale.ROOT);
                boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);
                for (Object widget : row.widgets) {
                    setVisible(widget, visible);
                    setActive(widget, visible);
                    if (!doBorder) continue;
                    boolean ok = true;
                    if (type.equals("vec3") && isTextField(widget)) ok = AttributeWidgetUtil.isStrictFloat(tfGetText(widget));
                    else if (type.equals("int") && isTextField(widget)) ok = AttributeWidgetUtil.isStrictInt(tfGetText(widget));
                    else if (type.equals("float") && isTextField(widget)) ok = AttributeWidgetUtil.isStrictFloat(tfGetText(widget));
                    AttributeWidgetUtil.drawBorder(ctx, getX(widget) - 1, getY(widget) - 1, getW(widget) + 2, getH(widget) + 2,
                        ok ? 0xFF34D058 : 0xFFE5534B);
                }
            }
            y += block.rows.size() * rowH + 18;
        }
    }

    private void renderRebuildOverlay(Object ctx) {
        fill(ctx, 0, 0, this.width, this.height, 0xE0000000);
        int pw = 160, ph = 30;
        int x0 = (this.width - pw) / 2, y0 = (this.height - ph) / 2;
        fill(ctx, x0, y0, x0 + pw, y0 + ph, 0xFF000000);
        fill(ctx, x0, y0, x0 + pw, y0 + 1, 0xFFFFFFFF);
        fill(ctx, x0, y0 + ph - 1, x0 + pw, y0 + ph, 0xFFFFFFFF);
        fill(ctx, x0, y0, x0 + 1, y0 + ph, 0xFFFFFFFF);
        fill(ctx, x0 + pw - 1, y0, x0 + pw, y0 + ph, 0xFFFFFFFF);
        drawCenteredText(ctx, screenFont(this), txt(REBUILDING_KEY), this.width / 2, y0 + 9, 0xFFEAEAEA);
    }

    private boolean isRebuilding() { return rebuildQueued || rebuildFuture != null; }

    private void layoutPresetRowWidgets(PresetRow row, int rightEdge, int y) {
        int singleWidth = 200;
        int tripleWidth = 64;
        int gap = 4;
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, singleWidth, tripleWidth, gap);
        int x = rightEdge - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, singleWidth, tripleWidth, gap);
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

    private void drawBezier(Object ctx, int x1, int y1, int x2, int y2, int color) {
        int segments = 32;
        float prevX = x1, prevY = y1;
        float thickness = 1.2f;
        float ctrlOffset = Math.abs(x2 - x1) * 0.5f;
        Object rl = getGuiRenderLayer();
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float invT = 1 - t;
            float b0 = invT * invT * invT;
            float b1 = 3 * invT * invT * t;
            float b2 = 3 * invT * t * t;
            float b3 = t * t * t;
            float cx = b0 * x1 + b1 * (x1 + ctrlOffset) + b2 * (x2 - ctrlOffset) + b3 * x2;
            float cy = b0 * y1 + b1 * y1 + b2 * y2 + b3 * y2;
            drawOrientedQuad(ctx, rl, prevX, prevY, cx, cy, thickness, color);
            prevX = cx; prevY = cy;
        }
    }

    private void drawConnections(Object ctx) {
        for (ModuleConnection link : moduleConnections) {
            PortPos p1 = getPortPosition(link.src, true);
            PortPos p2 = getPortPosition(link.dst, false);
            if (p1 != null && p2 != null) drawBezier(ctx, p1.x, p1.y, p2.x, p2.y, getFormatColor(link.src.format));
        }
    }

    private void drawModuleNode(Object ctx, ModuleNode node) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width, h = node.height();
        Object font = screenFont(this);

        fill(ctx, x, y, x + w, y + h, 0xFF20242C);
        fill(ctx, x, y, x + w, y + node.headerH, 0xFF2B3240);
        drawText(ctx, font, node.module.translateText(node.module.name), x + 6, y + 5, 0xFFEAEAEA, true);

        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (node.headerH - btnSize) / 2;
        int gearX = deleteX - btnSize - 2;

        drawTexture(ctx, GEAR_TEX, gearX, btnY, 0, 0, btnSize, btnSize, btnSize, btnSize);
        drawText(ctx, font, "×", deleteX + 3, btnY + 2, 0xFFFF5A5A, true);

        for (int i = 0; i < node.rows(); i++) {
            int ry = y + node.headerH + node.pad + i * node.rowH + 7;
            if (i < node.module.inputImageConfigs.size()) {
                ImageConfig in = node.module.inputImageConfigs.get(i);
                int dotX = x + 10, dotY = ry + 7;
                int color = (in == pendingPort) ? 0xFFFFFF00 : getFormatColor(in.format);
                boolean connected = moduleConnections.stream().anyMatch(l -> l.dst == in);
                drawPortDot(ctx, dotX, dotY, color, connected, false);
                drawText(ctx, font, in.name, x + 18, ry + 2, 0xFFD0D0D0, true);
            }
            if (i < node.module.outputImageConfigs.size()) {
                ImageConfig out = node.module.outputImageConfigs.get(i);
                int dotX = x + w - 10, dotY = ry + 7;
                int color = (out == pendingPort) ? 0xFFFFFF00 : getFormatColor(out.format);
                boolean connected = moduleConnections.stream().anyMatch(l -> l.src == out);
                drawPortDot(ctx, dotX, dotY, color, connected, out == localFinalOutput);
                int nameWidth = textWidth(font, out.name);
                drawText(ctx, font, out.name, (dotX - 8) - nameWidth, ry + 2, 0xFFD0D0D0, true);
            }
        }
    }

    private void drawPortDot(Object ctx, int cx, int cy, int color, boolean filled, boolean isFinal) {
        fill(ctx, cx - 4, cy - 4, cx + 5, cy + 5, isFinal ? 0xFF55FF55 : 0xFF000000);
        fill(ctx, cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        fill(ctx, cx - 2, cy - 2, cx + 3, cy + 3, color);
        if (!filled) fill(ctx, cx - 1, cy - 1, cx + 2, cy + 2, 0xFF20242C);
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

        if (activeVRSettings != null && mouseY >= HEADER_HEIGHT) {
            if (vrSettingsContains(activeVRSettings, mouseX, mouseY)) {
                return vrSettingsMouseClicked(activeVRSettings, mouseX, mouseY, button);
            }
            activeVRSettings = null;
        }

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
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (ModuleNode node : nodes) {
            if (button == 0 && isDeleteClicked(node, mouseX, mouseY)) {
                deleteNode(node);
                return true;
            }

            if (button == 0 && isGearClicked(node, mouseX, mouseY)) {
                setScreen(getMinecraft(), new ModuleAttributeScreen(this, node.module));
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

        int sh = scaledH();
        int y0 = HEADER_HEIGHT + 28;
        int rowH = 22;
        int contentH = y0 + presetBlocks.stream().filter(b -> !b.rows.isEmpty())
            .mapToInt(b -> 12 + 8 + (b.rows.size() * rowH) + 18).sum();
        int minScroll = Math.min(0, sh - contentH - 10);

        presetScrollY += (int) (verticalAmount * 12);
        if (presetScrollY > 0) {
            presetScrollY = 0;
        }
        if (presetScrollY < minScroll) {
            presetScrollY = minScroll;
        }
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

        if (activeVRSettings != null && vrSettingsMouseDragged(activeVRSettings, mouseX, mouseY, button,
            deltaX / GLOBAL_SCALE, deltaY / GLOBAL_SCALE)) {
            return true;
        }

        if (mode == Mode.PRESET) {
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

        if (activeVRSettings != null && vrSettingsMouseReleased(activeVRSettings, mouseX, mouseY, button)) {
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

    private class ModuleSelector {
        private final int x, y, width;
        private final List<ModuleEntry> options;
        private final int itemHeight = 18;

        public ModuleSelector(int x, int y, Map<String, ModuleEntry> entries) {
            this.x = x; this.y = y;
            this.options = new ArrayList<>();
            for (ModuleEntry entry : entries.values()) {
                if (entry == null || entry.name == null) continue;
                if (Pipeline.isModuleAvailable(entry.name)) this.options.add(entry);
            }
            this.width = 120;
        }

        public void render(Object ctx, int mx, int my) {
            int cy = y;
            Object font = screenFont(RenderPipelineScreen.this);
            fill(ctx, x - 1, y - 1, x + width + 1, y + (options.size() * itemHeight) + 1, 0xFFFFFFFF);
            for (ModuleEntry entry : options) {
                boolean hovered = mx >= x && mx <= x + width && my >= cy && my <= cy + itemHeight;
                fill(ctx, x, cy, x + width, cy + itemHeight, hovered ? 0xFF444444 : 0xFF222222);
                drawText(ctx, font, txt(entry.name), x + 5, cy + 5, 0xFFE0E0E0, true);
                cy += itemHeight;
            }
        }

        public boolean onClick(double mx, double my) {
            if (mx < x || mx > x + width || my < y || my > y + (options.size() * itemHeight)) return false;
            int index = (int) ((my - y) / itemHeight);
            if (index >= 0 && index < options.size()) {
                ModuleEntry selected = options.get(index);
                if (!Pipeline.isModuleAvailable(selected.name)) return true;
                Module module = selected.loadModule();
                module.x = 100; module.y = 100;
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
        for (Presets preset : Presets.values()) {
            if (Pipeline.isPresetAvailable(preset.key)) {
                this.presets.add(new PresetEntry(preset.key));
            }
        }
    }

    private void applyActivePreset() {
        presetBlocks.clear();
        for (Object w : presetWidgets) {
            try {
                List<?> children = screenChildren(this);
                children.remove(w);
                // renderables managed by PlatformScreen
            } catch (Exception ignored) {}
        }
        presetWidgets.clear();

        if (activePreset == null) return;
        Pipeline.switchToPresetMode(activePreset.name(), false);

        List<Module> modules = new ArrayList<>(Pipeline.INSTANCE.getModules());
        for (Module m : modules) {
            PresetModuleBlock block = new PresetModuleBlock(m);
            presetBlocks.add(block);
            if (m.attributeConfigs == null || m.attributeConfigs.isEmpty()) continue;
            for (AttributeConfig cfg : m.attributeConfigs) {
                if (Pipeline.isRayTracingShaderPackAttribute(m, cfg)) continue;
                List<Object> ws = buildPresetWidgets(m, cfg);
                for (Object w : ws) {
                    presetWidgets.add(screenAddChild(RenderPipelineScreen.this, w));
                }
                block.rows.add(new PresetRow(cfg, ws));
            }
        }

        if (secondaryBtn != null && mode == Mode.PRESET) {
            Object text = activePreset != null ? txt(activePreset.name()) : txtLit("N/A");
            setButtonMsg(secondaryBtn, txtAppend(txt(PRESET_NAME_KEY), txtAppend(txtLit(": "), text)));
        }
    }

    private List<Object> buildPresetWidgets(Module module, AttributeConfig cfg) {
        return AttributeWidgetUtil.buildWidgets(module, cfg, screenFont(this), 200, 64);
    }

    private void syncPresetToPipeline() {
        Pipeline.savePipeline();
    }

    // VRSettings stubs (SimpleOption is not available in 26.2+)
    private boolean vrSettingsContains(Object vs, double mx, double my) { return false; }
    private boolean vrSettingsMouseClicked(Object vs, double mx, double my, int btn) { return false; }
    private boolean vrSettingsMouseDragged(Object vs, double mx, double my, int btn, double dx, double dy) { return false; }
    private boolean vrSettingsMouseReleased(Object vs, double mx, double my, int btn) { return false; }

    private class PresetSelector {
        private final int x, y, width;
        private final List<PresetEntry> options;
        private final int itemHeight = 18;

        public PresetSelector(int x, int y, List<PresetEntry> presets) {
            this.x = x; this.y = y;
            this.options = new ArrayList<>(presets);
            this.width = 140;
        }

        public void render(Object ctx, int mx, int my) {
            int cy = y;
            Object font = screenFont(RenderPipelineScreen.this);
            fill(ctx, x - 1, y - 1, x + width + 1, y + (options.size() * itemHeight) + 1, 0xFFFFFFFF);
            for (PresetEntry entry : options) {
                boolean hovered = mx >= x && mx <= x + width && my >= cy && my <= cy + itemHeight;
                fill(ctx, x, cy, x + width, cy + itemHeight, hovered ? 0xFF444444 : 0xFF222222);
                drawText(ctx, font, txt(entry.name()), x + 5, cy + 5, 0xFFE0E0E0, true);
                cy += itemHeight;
            }
        }

        public boolean onClick(double mx, double my) {
            if (mx < x || mx > x + width || my < y || my > y + (options.size() * itemHeight)) return false;
            int index = (int) ((my - y) / itemHeight);
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

    private record PresetRow(AttributeConfig cfg, List<Object> widgets) {

    }
}
