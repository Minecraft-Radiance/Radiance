package com.radiance.client.gui.compat;

import com.radiance.client.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.List;

//#if MC>=260200
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.Font;
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//$$ import net.minecraft.client.gui.components.ObjectSelectionList;
//$$ import net.minecraft.client.gui.components.events.GuiEventListener;
//$$ import net.minecraft.network.chat.Component;
//#else
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
//#endif

/**
 * Version-neutral bridge for the shader pack selection list widget.
 */
//#if MC>=260200
//$$ public abstract class ShaderPackListBridge extends ObjectSelectionList<ShaderPackListBridge.Entry> {
//$$
//$$     private final List<Pipeline.ShaderPackChoice> entries;
//$$
//$$     protected ShaderPackListBridge(Object client, int width, int height, int y, int itemHeight,
//$$                                    List<Pipeline.ShaderPackChoice> entries) {
//$$         super((Minecraft) client, width, height, y, itemHeight);
//$$         this.entries = entries;
//$$         this.centerListVertically = false;
//$$         for (Pipeline.ShaderPackChoice choice : entries) {
//$$             Entry entry = new Entry(choice);
//$$             addEntry(entry);
//$$             if (Pipeline.isShaderPackActive(choice)) setSelected(entry);
//$$         }
//$$         if (getSelected() != null) centerScrollOn(getSelected());
//$$     }
//$$
//$$     @Override
//$$     public int getRowWidth() { return Math.min(340, width - 20); }
//$$
//$$     @Override
//$$     public void setSelected(Entry entry) {
//$$         if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) return;
//$$         super.setSelected(entry);
//$$     }
//$$
//$$     @Override
//$$     public void setFocused(GuiEventListener focused) {
//$$         if (focused instanceof Entry entry && !Pipeline.isShaderPackSelectable(entry.choice)) return;
//$$         super.setFocused(focused);
//$$     }
//$$
//$$     @Override
//$$     public boolean mouseClicked(double mx, double my, int button) {
//$$         Entry entry = getEntryAtPosition(mx, my);
//$$         if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) return true;
//$$         return super.mouseClicked(mx, my, button);
//$$     }
//$$
//$$     protected Object getHoveredTooltip() {
//$$         Entry entry = getHovered();
//$$         return entry == null ? null : entry.getUnavailableReason();
//$$     }
//$$
//$$     protected Object buildLabel(Pipeline.ShaderPackChoice choice) {
//$$         return buildLabelImpl(choice);
//$$     }
//$$
//$$     protected abstract Object buildLabelImpl(Pipeline.ShaderPackChoice choice);
//$$
//$$     public static class Entry extends ObjectSelectionList.Entry<Entry> {
//$$         public final Pipeline.ShaderPackChoice choice;
//$$         public Entry(Pipeline.ShaderPackChoice choice) { this.choice = choice; }
//$$
//$$         @Override
//$$         public void render(GuiGraphicsExtractor ctx, int index, int y, int x,
//$$                            int entryWidth, int entryHeight, int mx, int my,
//$$                            boolean hovered, float tickDelta) {
//$$             int textColor = Pipeline.isShaderPackSelectable(choice) ? 0xFFFFFF : 0x9A9A9A;
//$$             Font font = ((Minecraft) GuiCompat.getMinecraft()).font;
//$$             Object label = ShaderPackListBridge.this.buildLabel(choice);
//$$             GuiCompat.drawCenteredText(ctx, font, label,
//$$                 ShaderPackListBridge.this.width / 2,
//$$                 y + entryHeight / 2 - 9 / 2, textColor);
//$$         }
//$$
//$$         private Object getUnavailableReason() {
//$$             String key = Pipeline.getShaderPackUnavailabilityReasonTranslationKey(choice);
//$$             return key == null ? null : Component.translatable(key);
//$$         }
//$$
//$$         @Override
//$$         public boolean mouseClicked(double mx, double my, int button) {
//$$             if (!Pipeline.isShaderPackSelectable(choice)) return true;
//$$             ShaderPackListBridge.this.setSelected(this);
//$$             Pipeline.setShaderPack(choice, false);
//$$             return super.mouseClicked(mx, my, button);
//$$         }
//$$
//$$         @Override
//$$         public Component getNarration() {
//$$             return Component.translatable("narrator.select", (Component) ShaderPackListBridge.this.buildLabel(choice));
//$$         }
//$$     }
//$$ }
//#else
public abstract class ShaderPackListBridge extends AlwaysSelectedEntryListWidget<ShaderPackListBridge.Entry> {

    private final List<Pipeline.ShaderPackChoice> entries;

    protected ShaderPackListBridge(Object client, int width, int height, int y, int itemHeight,
                                   List<Pipeline.ShaderPackChoice> entries) {
        super((MinecraftClient) client, width, height, y, itemHeight);
        this.entries = entries;
        this.centerListVertically = false;
        for (Pipeline.ShaderPackChoice choice : entries) {
            Entry entry = new Entry(choice);
            addEntry(entry);
            if (Pipeline.isShaderPackActive(choice)) setSelected(entry);
        }
        if (getSelectedOrNull() != null) centerScrollOn(getSelectedOrNull());
    }

    @Override
    public int getRowWidth() { return Math.min(340, width - 20); }

    @Override
    public void setSelected(Entry entry) {
        if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) return;
        super.setSelected(entry);
    }

    @Override
    public void setFocused(net.minecraft.client.gui.Element focused) {
        if (focused instanceof Entry entry && !Pipeline.isShaderPackSelectable(entry.choice)) return;
        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Entry entry = getEntryAtPosition(mx, my);
        if (entry != null && !Pipeline.isShaderPackSelectable(entry.choice)) return true;
        return super.mouseClicked(mx, my, button);
    }

    protected Object getHoveredTooltip() {
        Entry entry = getHoveredEntry();
        return entry == null ? null : entry.getUnavailableReason();
    }

    protected Object buildLabel(Pipeline.ShaderPackChoice choice) {
        return buildLabelImpl(choice);
    }

    protected abstract Object buildLabelImpl(Pipeline.ShaderPackChoice choice);

    public static class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        public final Pipeline.ShaderPackChoice choice;
        public Entry(Pipeline.ShaderPackChoice choice) { this.choice = choice; }

        @Override
        public void render(DrawContext ctx, int index, int y, int x,
                           int entryWidth, int entryHeight, int mx, int my,
                           boolean hovered, float tickDelta) {
            int textColor = Pipeline.isShaderPackSelectable(choice) ? 0xFFFFFF : 0x9A9A9A;
            Object label = ((ShaderPackListBridge) getListWidget()).buildLabel(choice);
            ctx.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer, (Text) label,
                ((ShaderPackListBridge) getListWidget()).width / 2,
                y + entryHeight / 2 - 9 / 2, textColor);
        }

        private Object getUnavailableReason() {
            String key = Pipeline.getShaderPackUnavailabilityReasonTranslationKey(choice);
            return key == null ? null : Text.translatable(key);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!Pipeline.isShaderPackSelectable(choice)) return true;
            ((ShaderPackListBridge) getListWidget()).setSelected(this);
            Pipeline.setShaderPack(choice, false);
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public Text getNarration() {
            return Text.translatable("narrator.select", (Text) ((ShaderPackListBridge) getListWidget()).buildLabel(choice));
        }
    }
}
//#endif
