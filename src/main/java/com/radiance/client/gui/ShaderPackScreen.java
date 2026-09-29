package com.radiance.client.gui;

import com.radiance.client.pipeline.Pipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class ShaderPackScreen extends Screen {

    private static final int LIST_TOP = 36;
    private static final int ROW_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 32;
    private static final int FOOTER_BUTTON_WIDTH = 120;
    private static final int FOOTER_BUTTON_GAP = 8;

    private static final String TITLE = "shader_pack_screen.title";
    private static final String BACK = "render_pipeline_screen.back";
    private static final String SHADER_SETTINGS = "shader_pack_screen.shader_settings";
    private static final String EMPTY = "shader_pack_screen.empty";

    private final Screen parent;
    private final List<Pipeline.ShaderPackChoice> entries = new ArrayList<>();
    private ShaderPackListWidget shaderPackList;

    public ShaderPackScreen(Screen parent) {
        super(Text.translatable(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries.clear();
        entries.addAll(Pipeline.getAvailableShaderPacks());

        int listHeight = this.height - LIST_TOP - FOOTER_HEIGHT;
        shaderPackList = addDrawableChild(new ShaderPackListWidget(this.client, this.width, listHeight, LIST_TOP, ROW_HEIGHT));

        int footerY = this.height - 28;
        int footerX = (this.width - (FOOTER_BUTTON_WIDTH * 2 + FOOTER_BUTTON_GAP)) / 2;

        addDrawableChild(ButtonWidget.builder(Text.translatable(BACK), button -> close())
            .dimensions(footerX, footerY, FOOTER_BUTTON_WIDTH, 20)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.translatable(SHADER_SETTINGS),
                button -> MinecraftClient.getInstance().setScreen(new ShaderPackSettingsScreen(this)))
            .dimensions(footerX + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP, footerY, FOOTER_BUTTON_WIDTH, 20)
            .build());
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(TITLE), this.width / 2, 16, 0xFFFFFF);

        Text hoveredTooltip = shaderPackList == null ? null : shaderPackList.getHoveredTooltip();
        if (hoveredTooltip != null) {
            context.drawTooltip(this.textRenderer, hoveredTooltip, mouseX, mouseY);
        }

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(EMPTY), this.width / 2, LIST_TOP + 10, 0xB0B0B0);
        }
    }

    private Text buildLabel(Pipeline.ShaderPackChoice choice) {
        Text label = parseLegacyFormattedText(choice.displayName());
        if (Pipeline.isShaderPackActive(choice)) {
            label = Text.literal("> ").append(label);
        }
        return label;
    }

    private Text parseLegacyFormattedText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Text.empty();
        }

        MutableText result = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current == Formatting.FORMATTING_CODE_PREFIX && i + 1 < raw.length()) {
                Formatting formatting = Formatting.byCode(raw.charAt(i + 1));
                if (formatting != null) {
                    if (segment.length() > 0) {
                        result.append(Text.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }

                    if (formatting == Formatting.RESET) {
                        style = Style.EMPTY;
                    } else if (formatting.isColor()) {
                        style = style.withExclusiveFormatting(formatting);
                    } else {
                        style = style.withFormatting(formatting);
                    }
                    i++;
                    continue;
                }
            }
            segment.append(current);
        }

        if (segment.length() > 0) {
            result.append(Text.literal(segment.toString()).setStyle(style));
        }

        return result;
    }

    class ShaderPackListWidget extends AlwaysSelectedEntryListWidget<ShaderPackListWidget.ShaderPackEntry> {
        ShaderPackListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, height, itemHeight);
            this.centerListVertically = false;

            for (Pipeline.ShaderPackChoice choice : entries) {
                ShaderPackEntry entry = new ShaderPackEntry(choice);
                this.addEntry(entry);
                if (Pipeline.isShaderPackActive(choice)) {
                    this.setSelected(entry);
                }
            }

            if (this.getSelectedOrNull() != null) {
                this.centerScrollOn(this.getSelectedOrNull());
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(340, ShaderPackScreen.this.width - 20);
        }

        @Override
        public void setSelected(ShaderPackEntry entry) {
            if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) {
                return;
            }
            super.setSelected(entry);
        }

        @Override
        public void setFocused(net.minecraft.client.gui.Element focused) {
            if (focused instanceof ShaderPackEntry entry && !Pipeline.isShaderPackSelectable(entry.choice)) {
                return;
            }
            super.setFocused(focused);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ShaderPackEntry entry = this.getEntryAtPosition(mouseX, mouseY);
            if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private Text getHoveredTooltip() {
            ShaderPackEntry entry = this.getHoveredEntry();
            return entry == null ? null : entry.getUnavailableReason();
        }

        class ShaderPackEntry extends AlwaysSelectedEntryListWidget.Entry<ShaderPackEntry> {
            private final Pipeline.ShaderPackChoice choice;

            ShaderPackEntry(Pipeline.ShaderPackChoice choice) {
                this.choice = choice;
            }

            @Override
            public void render(DrawContext context,
                               int index,
                               int y,
                               int x,
                               int entryWidth,
                               int entryHeight,
                               int mouseX,
                               int mouseY,
                               boolean hovered,
                               float tickDelta) {
                int textColor = Pipeline.isShaderPackSelectable(this.choice) ? 0xFFFFFF : 0x9A9A9A;
                context.drawCenteredTextWithShadow(
                    ShaderPackScreen.this.textRenderer,
                    ShaderPackScreen.this.buildLabel(this.choice),
                    ShaderPackListWidget.this.width / 2,
                    y + entryHeight / 2 - 9 / 2,
                    textColor
                );
            }

            private Text getUnavailableReason() {
                String translationKey = Pipeline.getShaderPackUnavailabilityReasonTranslationKey(this.choice);
                return translationKey == null ? null : Text.translatable(translationKey);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!Pipeline.isShaderPackSelectable(this.choice)) {
                    return true;
                }
                ShaderPackListWidget.this.setSelected(this);
                if (Pipeline.setShaderPack(this.choice, false)) {
                    Pipeline.savePipeline();
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public Text getNarration() {
                return Text.translatable("narrator.select", ShaderPackScreen.this.buildLabel(this.choice));
            }
        }
    }
}
