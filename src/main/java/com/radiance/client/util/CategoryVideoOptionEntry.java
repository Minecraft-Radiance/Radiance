package com.radiance.client.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.text.Text;

public class CategoryVideoOptionEntry extends OptionListWidget.WidgetEntry {

    private final Text text;
    private final int textWidth;
    private final MinecraftClient client;
    private final OptionListWidget parent;

    public CategoryVideoOptionEntry(Text text, OptionListWidget parent) {
        super(ImmutableMap.of());
        this.client = MinecraftClient.getInstance();
        this.parent = parent;
        this.text = text;
        this.textWidth = this.client.textRenderer.getWidth(this.text);
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth,
        int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        context.drawTextWithShadow(this.client.textRenderer, this.text,
            x + entryWidth / 2 - this.textWidth / 2, y + entryHeight - 10, 0xFFFFFF);
    }

    @Override
    public List<? extends Element> children() {
        return ImmutableList.of();
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return ImmutableList.of();
    }
}
