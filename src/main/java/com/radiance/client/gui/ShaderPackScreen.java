package com.radiance.client.gui;

import com.radiance.client.pipeline.Pipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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
        super(Component.translatable(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries.clear();
        entries.addAll(Pipeline.getAvailableShaderPacks());

        int listHeight = this.height - LIST_TOP - FOOTER_HEIGHT;
        shaderPackList = addRenderableWidget(new ShaderPackListWidget(this.minecraft, this.width, listHeight, LIST_TOP, ROW_HEIGHT));

        int footerY = this.height - 28;
        int footerX = (this.width - (FOOTER_BUTTON_WIDTH * 2 + FOOTER_BUTTON_GAP)) / 2;

        addRenderableWidget(Button.builder(Component.translatable(BACK), button -> onClose())
            .bounds(footerX, footerY, FOOTER_BUTTON_WIDTH, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable(SHADER_SETTINGS),
                button -> Minecraft.getInstance().setScreen(new ShaderPackSettingsScreen(this)))
            .bounds(footerX + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP, footerY, FOOTER_BUTTON_WIDTH, 20)
            .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, Component.translatable(TITLE), this.width / 2, 16, 0xFFFFFF);

        Component hoveredTooltip = shaderPackList == null ? null : shaderPackList.getHoveredTooltip();
        if (hoveredTooltip != null) {
            context.renderTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }

        if (entries.isEmpty()) {
            context.drawCenteredString(font, Component.translatable(EMPTY), this.width / 2, LIST_TOP + 10, 0xB0B0B0);
        }
    }

    private Component buildLabel(Pipeline.ShaderPackChoice choice) {
        Component label = parseLegacyFormattedText(choice.displayName());
        if (Pipeline.isShaderPackActive(choice)) {
            label = Component.literal("> ").append(label);
        }
        return label;
    }

    private Component parseLegacyFormattedText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current == ChatFormatting.PREFIX_CODE && i + 1 < raw.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(i + 1));
                if (formatting != null) {
                    if (segment.length() > 0) {
                        result.append(Component.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }

                    if (formatting == ChatFormatting.RESET) {
                        style = Style.EMPTY;
                    } else if (formatting.isColor()) {
                        style = style.applyLegacyFormat(formatting);
                    } else {
                        style = style.applyFormat(formatting);
                    }
                    i++;
                    continue;
                }
            }
            segment.append(current);
        }

        if (segment.length() > 0) {
            result.append(Component.literal(segment.toString()).setStyle(style));
        }

        return result;
    }

    class ShaderPackListWidget extends ObjectSelectionList<ShaderPackListWidget.ShaderPackEntry> {
        ShaderPackListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
            this.centerListVertically = false;

            for (Pipeline.ShaderPackChoice choice : entries) {
                ShaderPackEntry entry = new ShaderPackEntry(choice);
                this.addEntry(entry);
                if (Pipeline.isShaderPackActive(choice)) {
                    this.setSelected(entry);
                }
            }

            if (this.getSelected() != null) {
                this.centerScrollOn(this.getSelected());
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
        public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener focused) {
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

        private Component getHoveredTooltip() {
            ShaderPackEntry entry = this.getHovered();
            return entry == null ? null : entry.getUnavailableReason();
        }

        class ShaderPackEntry extends ObjectSelectionList.Entry<ShaderPackEntry> {
            private final Pipeline.ShaderPackChoice choice;

            ShaderPackEntry(Pipeline.ShaderPackChoice choice) {
                this.choice = choice;
            }

            @Override
            public void render(GuiGraphics context,
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
                context.drawCenteredString(
                    ShaderPackScreen.this.font,
                    ShaderPackScreen.this.buildLabel(this.choice),
                    ShaderPackListWidget.this.width / 2,
                    y + entryHeight / 2 - 9 / 2,
                    textColor
                );
            }

            private Component getUnavailableReason() {
                String translationKey = Pipeline.getShaderPackUnavailabilityReasonTranslationKey(this.choice);
                return translationKey == null ? null : Component.translatable(translationKey);
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
            public Component getNarration() {
                return Component.translatable("narrator.select", ShaderPackScreen.this.buildLabel(this.choice));
            }
        }
    }
}
