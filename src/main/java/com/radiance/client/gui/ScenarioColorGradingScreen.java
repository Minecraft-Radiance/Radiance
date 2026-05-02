package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.ModuleEntry;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class ScenarioColorGradingScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int STATUS_OK = 0xFF34D058;
    private static final int STATUS_BAD = 0xFFE5534B;
    private static final int HEADER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_LEFT = 12;
    private static final int ROW_RIGHT = 12;
    private static final int LABEL_MIN_WIDTH = 170;
    private static final int WIDGET_GAP = 4;
    private static final int TOGGLE_WIDTH = 90;
    private static final int SMALL_FIELD_WIDTH = 72;
    private static final String TONE_MAPPING_MODULE_NAME = "render_pipeline.module.tone_mapping.name";
    private static final List<String> AUTHORING_ATTRIBUTE_KEYS = List.of(
        "render_pipeline.module.tone_mapping.attribute.gain",
        "render_pipeline.module.tone_mapping.attribute.contrast",
        "render_pipeline.module.tone_mapping.attribute.saturation",
        "render_pipeline.module.tone_mapping.attribute.temperature",
        "render_pipeline.module.tone_mapping.attribute.tint",
        "render_pipeline.module.tone_mapping.attribute.gamma"
    );

    private final Screen parent;
    private final List<AttributeConfig> toneMappingConfigs = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Module toneMappingModule;

    private int scrollY = 0;
    private TextFieldWidget scenarioNameField;
    private TextFieldWidget priorityField;
    private TextFieldWidget timeStartField;
    private TextFieldWidget timeEndField;
    private Row timeRow;
    private boolean saveWorld = true;
    private boolean saveTime = false;
    private boolean saveWeather = false;
    private boolean saveBiome = false;
    private boolean saveSubmersion = false;
    private boolean saveIndoor = false;
    private boolean saveCave = false;
    private String lastPreviewSignature = null;
    private String activeScenarioName = "Default";
    private Text statusText = Text.empty();
    private int statusColor = 0xFFB0B0B0;
    private long statusUntilMs = 0L;

    public ScenarioColorGradingScreen(Screen parent) {
        super(Text.translatable("scenario_color_grading_screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        rows.clear();
        scrollY = 0;

        addDrawableChild(ButtonWidget.builder(Text.translatable("render_pipeline_screen.back"),
            button -> close()).dimensions(10, 6, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("scenario_color_grading_screen.save"),
            button -> saveScenario()).dimensions(75, 6, 120, 20).build());

        RendererProxy.setScenarioGradingIsolation(true);
        toneMappingConfigs.clear();
        toneMappingConfigs.addAll(loadToneMappingAttributeConfigs());
        refreshActiveScenarioState();

        scenarioNameField = addTextField(controlFieldWidth(), 64, null);
        priorityField = addTextField(SMALL_FIELD_WIDTH, 2, s -> s.isEmpty() || s.matches("\\d{0,2}"));
        priorityField.setText("0");

        timeStartField = addTextField(SMALL_FIELD_WIDTH, 5, s -> s.isEmpty() || s.matches("\\d{0,5}"));
        timeStartField.setSuggestion("0");
        timeEndField = addTextField(SMALL_FIELD_WIDTH, 5, s -> s.isEmpty() || s.matches("\\d{0,5}"));
        timeEndField.setSuggestion("24000");

        rows.add(new Row(Text.translatable("scenario_color_grading_screen.name"),
            List.of(scenarioNameField), RowValidation.NONE));
        rows.add(new Row(Text.translatable("scenario_color_grading_screen.priority"),
            List.of(priorityField), RowValidation.PRIORITY));
        rows.add(new Row(Text.translatable("scenario_color_grading_screen.match.world"),
            List.of(addToggleButton(() -> saveWorld = !saveWorld, () -> saveWorld)), RowValidation.NONE));

        ButtonWidget timeToggle = addToggleButton(() -> {
            saveTime = !saveTime;
            updateTimeFieldState();
        }, () -> saveTime);
        timeRow = new Row(Text.translatable("scenario_color_grading_screen.match.time"),
            List.of(timeStartField, timeEndField, timeToggle), RowValidation.TIME_RANGE);
        rows.add(timeRow);

        rows.add(new Row(Text.translatable("scenario_color_grading_screen.match.weather"),
            List.of(addToggleButton(() -> saveWeather = !saveWeather, () -> saveWeather)), RowValidation.NONE));
        rows.add(new Row(Text.translatable("scenario_color_grading_screen.match.biome"),
            List.of(addToggleButton(() -> saveBiome = !saveBiome, () -> saveBiome)), RowValidation.NONE));
        rows.add(new Row(Text.translatable("scenario_color_grading_screen.match.submersion"),
            List.of(addToggleButton(() -> saveSubmersion = !saveSubmersion, () -> saveSubmersion)), RowValidation.NONE));
        rows.add(new Row(Text.translatable("scenario_color_grading_screen.match.indoor"),
            List.of(addToggleButton(() -> saveIndoor = !saveIndoor, () -> saveIndoor)), RowValidation.NONE));
        rows.add(new Row(Text.translatable("scenario_color_grading_screen.match.cave"),
            List.of(addToggleButton(() -> saveCave = !saveCave, () -> saveCave)), RowValidation.NONE));
        rows.add(Row.section(Text.translatable(TONE_MAPPING_MODULE_NAME)));

        for (AttributeConfig cfg : toneMappingConfigs) {
            List<ClickableWidget> widgets = AttributeWidgetUtil.buildWidgets(toneMappingModule, cfg, textRenderer,
                attributeWidgetWidth(), vec3ComponentWidth());
            for (ClickableWidget widget : widgets) {
                addDrawableChild(widget);
            }
            rows.add(new Row(Text.translatable(cfg.name), widgets, validationForType(cfg.type)));
        }

        updateTimeFieldState();
    }

    @Override
    public void tick() {
        super.tick();
        if (Util.getMeasuringTimeMs() > statusUntilMs) {
            statusText = Text.empty();
        }
        if (toneMappingConfigs.isEmpty()) {
            if (lastPreviewSignature != null) {
                RendererProxy.clearPreviewScenarioColorGrading();
                lastPreviewSignature = null;
            }
            return;
        }

        String signature = buildPreviewSignature();
        if (!signature.equals(lastPreviewSignature)) {
            RendererProxy.applyPreviewScenarioColorGrading(collectAttributePairs());
            lastPreviewSignature = signature;
        }
    }

    @Override
    public void close() {
        RendererProxy.clearPreviewScenarioColorGrading();
        RendererProxy.setScenarioGradingIsolation(false);
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layoutRows();
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, Text.translatable("scenario_color_grading_screen.title"), 10,
            HEADER_HEIGHT + 8, 0xFFEAEAEA);
        context.drawTextWithShadow(textRenderer,
            Text.translatable("scenario_color_grading_screen.current", activeScenarioName),
            10, HEADER_HEIGHT + 20, 0xFFB8D8FF);
        if (!statusText.getString().isEmpty()) {
            context.drawTextWithShadow(textRenderer, statusText, 205, 12, statusColor);
        }

        for (Row row : rows) {
            if (!row.visible) {
                continue;
            }

            int textColor = row.section ? 0xFFEAEAEA : 0xFFD0D0D0;
            context.drawTextWithShadow(textRenderer, row.label, ROW_LEFT, row.y + 6, textColor);

            if (row == timeRow) {
                drawTimeSeparator(context, row.y);
            }

            drawValidationBorders(context, row);
        }

        if (toneMappingConfigs.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.translatable("module_attribute_screen.no_attributes"),
                ROW_LEFT, contentTop() + 10 * ROW_HEIGHT + scrollY, 0xFFB0B0B0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int contentHeight = contentTop() + rows.size() * ROW_HEIGHT + 20;
        int minScroll = Math.min(0, this.height - contentHeight);

        scrollY += (int) (verticalAmount * 10);
        if (scrollY > 0) {
            scrollY = 0;
        }
        if (scrollY < minScroll) {
            scrollY = minScroll;
        }
        return true;
    }

    private void layoutRows() {
        int baseY = contentTop() + scrollY;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.y = baseY + i * ROW_HEIGHT;
            row.visible = row.y >= (HEADER_HEIGHT + 18) && row.y <= (this.height - 24);
            layoutRowWidgets(row);
            for (ClickableWidget widget : row.widgets) {
                widget.visible = row.visible;
                widget.active = row.visible && isWidgetEnabled(widget);
            }
        }
    }

    private void layoutRowWidgets(Row row) {
        if (row.widgets.isEmpty()) {
            return;
        }

        int totalWidth = rowWidgetsWidth(row.widgets);
        int startX = Math.max(this.width - ROW_RIGHT - totalWidth, ROW_LEFT + labelWidthBudget());
        int x = startX;
        for (ClickableWidget widget : row.widgets) {
            widget.setX(x);
            widget.setY(row.y);
            x += widget.getWidth() + WIDGET_GAP;
        }
    }

    private int rowWidgetsWidth(List<ClickableWidget> widgets) {
        int totalWidth = 0;
        for (int i = 0; i < widgets.size(); i++) {
            totalWidth += widgets.get(i).getWidth();
            if (i + 1 < widgets.size()) {
                totalWidth += WIDGET_GAP;
            }
        }
        return totalWidth;
    }

    private int labelWidthBudget() {
        return Math.min(Math.max(LABEL_MIN_WIDTH, this.width / 4), 220);
    }

    private int attributeWidgetWidth() {
        int availableWidth = this.width - ROW_LEFT - ROW_RIGHT - labelWidthBudget() - 12;
        return Math.max(180, Math.min(availableWidth, 360));
    }

    private int vec3ComponentWidth() {
        int totalWidth = attributeWidgetWidth();
        return Math.max(52, (totalWidth - (WIDGET_GAP * 2)) / 3);
    }

    private int controlFieldWidth() {
        return Math.max(180, Math.min(attributeWidgetWidth(), 220));
    }

    private int contentTop() {
        return HEADER_HEIGHT + 40;
    }

    private boolean isWidgetEnabled(ClickableWidget widget) {
        if (widget == timeStartField || widget == timeEndField) {
            return saveTime;
        }
        return true;
    }

    private void drawTimeSeparator(DrawContext context, int y) {
        if (!timeStartField.visible || !timeEndField.visible) {
            return;
        }
        int separatorX = timeStartField.getX() + timeStartField.getWidth() + 2;
        int separatorColor = saveTime ? 0xFFB8B8B8 : 0xFF707070;
        context.drawTextWithShadow(textRenderer, Text.literal("~"), separatorX, y + 6, separatorColor);
    }

    private void drawValidationBorders(DrawContext context, Row row) {
        if (row.validation == RowValidation.NONE) {
            return;
        }

        for (ClickableWidget widget : row.widgets) {
            if (!(widget instanceof TextFieldWidget textFieldWidget)) {
                continue;
            }

            if (row.validation == RowValidation.TIME_RANGE && !saveTime) {
                continue;
            }

            boolean ok = switch (row.validation) {
                case INT -> AttributeWidgetUtil.isStrictInt(textFieldWidget.getText());
                case FLOAT -> AttributeWidgetUtil.isStrictFloat(textFieldWidget.getText());
                case VEC3 -> AttributeWidgetUtil.isStrictFloat(textFieldWidget.getText());
                case PRIORITY -> validatePriorityField(textFieldWidget.getText());
                case TIME_RANGE -> validateDayTickField(textFieldWidget.getText());
                case NONE -> true;
            };

            AttributeWidgetUtil.drawBorder(context, textFieldWidget.getX(), textFieldWidget.getY(),
                textFieldWidget.getWidth(), textFieldWidget.getHeight(), ok ? OK_BORDER : BAD_BORDER);
        }
    }

    private boolean validatePriorityField(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        if (!AttributeWidgetUtil.isStrictInt(text)) {
            return false;
        }
        int value = Integer.parseInt(text);
        return value >= 0 && value <= 10;
    }

    private boolean validateDayTickField(String text) {
        if (!AttributeWidgetUtil.isStrictInt(text)) {
            return false;
        }
        int value = Integer.parseInt(text);
        return value >= 0 && value <= 24000;
    }

    private void saveScenario() {
        String scenarioName = scenarioNameField.getText().trim();
        if (scenarioName.isEmpty()) {
            showStatusMessage("scenario_color_grading_screen.save_name_required", STATUS_BAD);
            return;
        }

        String priorityText = priorityField.getText().trim();
        if (!validatePriorityField(priorityText)) {
            showStatusMessage("scenario_color_grading_screen.save_priority_invalid", STATUS_BAD);
            return;
        }

        int priority = priorityText.isEmpty() ? 0 : Integer.parseInt(priorityText);

        int timeStart = -1;
        int timeEnd = -1;
        if (saveTime) {
            timeStart = parseDayTick(timeStartField);
            timeEnd = parseDayTick(timeEndField);
            if (timeStart < 0 || timeEnd < 0) {
                showStatusMessage("scenario_color_grading_screen.save_time_invalid", STATUS_BAD);
                return;
            }
        }

        boolean saved = RendererProxy.saveScenarioColorGrading(
            scenarioName,
            priority,
            saveWorld,
            saveTime,
            saveWeather,
            saveBiome,
            saveSubmersion,
            saveIndoor,
            saveCave,
            timeStart,
            timeEnd,
            collectAttributePairs());
        showStatusMessage(saved
            ? "scenario_color_grading_screen.save_success"
            : "scenario_color_grading_screen.save_failed", saved ? STATUS_OK : STATUS_BAD);
    }

    private int parseDayTick(TextFieldWidget field) {
        String text = field.getText().trim();
        if (!validateDayTickField(text)) {
            return -1;
        }
        return Integer.parseInt(text);
    }

    private void showStatusMessage(String translationKey, int color) {
        statusText = Text.translatable(translationKey);
        statusColor = color;
        statusUntilMs = Util.getMeasuringTimeMs() + 4000L;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(statusText, false);
        }
    }

    private void updateTimeFieldState() {
        if (timeStartField == null || timeEndField == null) {
            return;
        }
        timeStartField.setEditableColor(saveTime ? 0xFFEAEAEA : 0xFF7A7A7A);
        timeEndField.setEditableColor(saveTime ? 0xFFEAEAEA : 0xFF7A7A7A);
    }

    private TextFieldWidget addTextField(int width, int maxLength, java.util.function.Predicate<String> predicate) {
        TextFieldWidget textField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty()));
        textField.setMaxLength(maxLength);
        if (predicate != null) {
            textField.setTextPredicate(predicate);
        }
        return textField;
    }

    private ButtonWidget addToggleButton(Runnable toggleAction, ToggleValueSource valueSource) {
        return addDrawableChild(ButtonWidget.builder(toggleText(valueSource.enabled()), button -> {
            toggleAction.run();
            button.setMessage(toggleText(valueSource.enabled()));
        }).dimensions(0, 0, TOGGLE_WIDTH, 20).build());
    }

    private Text toggleText(boolean enabled) {
        return Text.translatable(enabled ? "render_pipeline.true" : "render_pipeline.false");
    }

    private RowValidation validationForType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "int" -> RowValidation.INT;
            case "float" -> RowValidation.FLOAT;
            case "vec3" -> RowValidation.VEC3;
            default -> RowValidation.NONE;
        };
    }

    private List<AttributeConfig> loadToneMappingAttributeConfigs() {
        List<AttributeConfig> moduleConfigs = List.of();
        for (Module module : Pipeline.INSTANCE.getModules()) {
            if (TONE_MAPPING_MODULE_NAME.equals(module.name)) {
                toneMappingModule = module;
                moduleConfigs = module.attributeConfigs;
                break;
            }
        }

        if (moduleConfigs.isEmpty()) {
            Map<String, ModuleEntry> moduleEntries = Pipeline.INSTANCE.getModuleEntries();
            ModuleEntry moduleEntry = moduleEntries.get(TONE_MAPPING_MODULE_NAME);
            if (moduleEntry != null) {
                Module module = moduleEntry.loadModule();
                toneMappingModule = module;
                moduleConfigs = module.attributeConfigs;
            }
        }

        return cloneAttributeConfigs(moduleConfigs);
    }

    private List<AttributeConfig> cloneAttributeConfigs(List<AttributeConfig> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        Map<String, AttributeConfig> sourceByName = new HashMap<>();
        for (AttributeConfig attributeConfig : source) {
            if (attributeConfig == null) {
                continue;
            }
            sourceByName.put(attributeConfig.name, attributeConfig);
        }

        List<AttributeConfig> cloned = new ArrayList<>(AUTHORING_ATTRIBUTE_KEYS.size());
        for (String attributeKey : AUTHORING_ATTRIBUTE_KEYS) {
            AttributeConfig attributeConfig = sourceByName.get(attributeKey);
            if (attributeConfig == null) {
                continue;
            }
            AttributeConfig copy = new AttributeConfig();
            copy.type = attributeConfig.type;
            copy.name = attributeConfig.name;
            copy.value = attributeConfig.value;
            cloned.add(copy);
        }
        return cloned;
    }

    private void refreshActiveScenarioState() {
        activeScenarioName = RendererProxy.getActiveScenarioColorGradingName();
        if (activeScenarioName == null || activeScenarioName.isBlank()) {
            activeScenarioName = "Default";
        }

        String[] activeValues = RendererProxy.getActiveScenarioColorGradingValues(collectAttributeKeys());
        if (activeValues == null || activeValues.length == 0) {
            return;
        }

        Map<String, String> valueByKey = new HashMap<>();
        for (int i = 0; i + 1 < activeValues.length; i += 2) {
            valueByKey.put(activeValues[i], activeValues[i + 1]);
        }
        for (AttributeConfig config : toneMappingConfigs) {
            if (config == null || config.name == null) {
                continue;
            }
            String value = valueByKey.get(config.name);
            if (value != null) {
                config.value = value;
            }
        }
    }

    private String[] collectAttributeKeys() {
        String[] keys = new String[toneMappingConfigs.size()];
        for (int i = 0; i < toneMappingConfigs.size(); i++) {
            AttributeConfig config = toneMappingConfigs.get(i);
            keys[i] = config == null || config.name == null ? "" : config.name;
        }
        return keys;
    }

    private String[] collectAttributePairs() {
        if (toneMappingConfigs.isEmpty()) {
            return new String[0];
        }

        List<String> pairs = new ArrayList<>(toneMappingConfigs.size() * 2);
        for (AttributeConfig config : toneMappingConfigs) {
            if (config == null || config.name == null) {
                continue;
            }
            pairs.add(config.name);
            pairs.add(config.value == null ? "" : config.value);
        }
        return pairs.toArray(String[]::new);
    }

    private String buildPreviewSignature() {
        return String.join("\n", collectAttributePairs());
    }

    private interface ToggleValueSource {

        boolean enabled();
    }

    private enum RowValidation {
        NONE,
        INT,
        FLOAT,
        VEC3,
        PRIORITY,
        TIME_RANGE
    }

    private static class Row {

        private final Text label;
        private final List<ClickableWidget> widgets;
        private final RowValidation validation;
        private final boolean section;
        private int y;
        private boolean visible;

        private Row(Text label, List<ClickableWidget> widgets, RowValidation validation) {
            this(label, widgets, validation, false);
        }

        private Row(Text label, List<ClickableWidget> widgets, RowValidation validation, boolean section) {
            this.label = label;
            this.widgets = widgets;
            this.validation = validation;
            this.section = section;
        }

        private static Row section(Text label) {
            return new Row(label, List.of(), RowValidation.NONE, true);
        }
    }
}
