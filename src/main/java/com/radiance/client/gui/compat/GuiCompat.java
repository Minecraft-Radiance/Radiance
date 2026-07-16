package com.radiance.client.gui.compat;

//#if MC>=260200
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.Font;
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//$$ import net.minecraft.client.gui.components.AbstractWidget;
//$$ import net.minecraft.client.gui.components.AbstractSliderButton;
//$$ import net.minecraft.client.gui.components.Button;
//$$ import net.minecraft.client.gui.components.EditBox;
//$$ import net.minecraft.client.gui.components.Renderable;
//$$ import net.minecraft.client.gui.components.events.GuiEventListener;
//$$ import net.minecraft.client.gui.narration.NarratableEntry;
//$$ import net.minecraft.client.input.MouseButtonEvent;
//$$ import net.minecraft.client.input.MouseButtonInfo;
//$$ import net.minecraft.client.renderer.RenderPipelines;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.network.chat.MutableComponent;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.util.Mth;
//#else
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
//#endif

/**
 * Central version-neutral bridge for all MC GUI APIs.
 * ALL preprocessor directives are confined to this file and PlatformScreen.
 * Other source files MUST NOT contain any //#if / //$$ directives.
 */
public final class GuiCompat {
    private GuiCompat() {}

    // ======================== Text / Component ========================

    //#if MC>=260200
    //$$ public static Object txt(String key)                { return Component.translatable(key); }
    //$$ public static Object emptyTxt()                     { return Component.empty(); }
    //$$ public static Object txtLit(String s)               { return Component.literal(s); }
    //$$ public static Object txtAppend(Object a, Object b)  { return ((Component) a).copy().append((Component) b); }
    //#else
    public static Object txt(String key)                { return Text.translatable(key); }
    public static Object emptyTxt()                     { return Text.empty(); }
    public static Object txtLit(String s)               { return Text.literal(s); }
    public static Object txtAppend(Object a, Object b)  { return ((Text) a).copy().append((Text) b); }
    //#endif

    // ======================== Math ========================

    //#if MC>=260200
    //$$ public static int   clampI(int v, int lo, int hi)       { return Mth.clamp(v, lo, hi); }
    //$$ public static float clampF(float v, float lo, float hi) { return Mth.clamp(v, lo, hi); }
    //$$ public static float lerpF(float d, float a, float b)    { return Mth.lerp(d, a, b); }
    //#else
    public static int   clampI(int v, int lo, int hi)       { return MathHelper.clamp(v, lo, hi); }
    public static float clampF(float v, float lo, float hi) { return MathHelper.clamp(v, lo, hi); }
    public static float lerpF(float d, float a, float b)    { return MathHelper.lerp(d, a, b); }
    //#endif

    // ======================== DrawContext / GuiGraphicsExtractor ========================

    //#if MC>=260200
    //$$ public static void fill(Object ctx, int x0, int y0, int x1, int y1, int color) {
    //$$     ((GuiGraphicsExtractor) ctx).fill(x0, y0, x1, y1, color);
    //$$ }
    //$$
    //$$ public static void drawText(Object ctx, Object font, Object text, int x, int y, int color, boolean shadow) {
    //$$     ((GuiGraphicsExtractor) ctx).text((Font) font, (Component) text, x, y, color, shadow);
    //$$ }
    //$$ public static void drawText(Object ctx, Object font, String text, int x, int y, int color, boolean shadow) {
    //$$     ((GuiGraphicsExtractor) ctx).text((Font) font, text, x, y, color, shadow);
    //$$ }
    //$$ public static void drawCenteredText(Object ctx, Object font, Object text, int x, int y, int color) {
    //$$     ((GuiGraphicsExtractor) ctx).centeredText((Font) font, (Component) text, x, y, color);
    //$$ }
    //$$ public static void drawCenteredText(Object ctx, Object font, String text, int x, int y, int color) {
    //$$     ((GuiGraphicsExtractor) ctx).centeredText((Font) font, text, x, y, color);
    //$$ }
    //$$
    //$$ public static void drawTexture(Object ctx, Object texId, int x, int y, float u, float v, int w, int h, int tw, int th) {
    //$$     ((GuiGraphicsExtractor) ctx).blit(RenderPipelines.GUI_TEXTURED, (Identifier) texId, x, y, u, v, w, h, tw, th);
    //$$ }
    //$$
    //$$ public static void drawTooltip(Object ctx, Object font, Object text, int mx, int my) {
    //$$     ((GuiGraphicsExtractor) ctx).setTooltipForNextFrame((Font) font, (Component) text, mx, my);
    //$$ }
    //#else
    public static void fill(Object ctx, int x0, int y0, int x1, int y1, int color) {
        ((DrawContext) ctx).fill(x0, y0, x1, y1, color);
    }

    public static void drawText(Object ctx, Object font, Object text, int x, int y, int color, boolean shadow) {
        if (shadow) ((DrawContext) ctx).drawTextWithShadow((TextRenderer) font, (Text) text, x, y, color);
        else ((DrawContext) ctx).drawText((TextRenderer) font, (Text) text, x, y, color, false);
    }
    public static void drawText(Object ctx, Object font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) ((DrawContext) ctx).drawTextWithShadow((TextRenderer) font, text, x, y, color);
        else ((DrawContext) ctx).drawText((TextRenderer) font, text, x, y, color, false);
    }
    public static void drawCenteredText(Object ctx, Object font, Object text, int x, int y, int color) {
        ((DrawContext) ctx).drawCenteredTextWithShadow((TextRenderer) font, (Text) text, x, y, color);
    }
    public static void drawCenteredText(Object ctx, Object font, String text, int x, int y, int color) {
        ((DrawContext) ctx).drawCenteredTextWithShadow((TextRenderer) font, text, x, y, color);
    }

    public static void drawTexture(Object ctx, Object texId, int x, int y, float u, float v, int w, int h, int tw, int th) {
        ((DrawContext) ctx).drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured,
            (Identifier) texId, x, y, u, v, w, h, tw, th);
    }

    public static void drawTooltip(Object ctx, Object font, Object text, int mx, int my) {
        ((DrawContext) ctx).drawTooltip((TextRenderer) font, (Text) text, mx, my);
    }
    //#endif

    // ======================== Matrix stack ========================

    //#if MC>=260200
    //$$ public static void matricesPush(Object ctx) {
    //$$     ((GuiGraphicsExtractor) ctx).pose().pushMatrix();
    //$$ }
    //$$ public static void matricesPop(Object ctx) {
    //$$     ((GuiGraphicsExtractor) ctx).pose().popMatrix();
    //$$ }
    //$$ public static void matricesTranslate(Object ctx, float x, float y, float z) {
    //$$     ((GuiGraphicsExtractor) ctx).pose().translate(x, y);
    //$$ }
    //$$ public static void matricesScale(Object ctx, float sx, float sy, float sz) {
    //$$     ((GuiGraphicsExtractor) ctx).pose().scale(sx, sy);
    //$$ }
    //#else
    public static void matricesPush(Object ctx)       { ((DrawContext) ctx).getMatrices().push(); }
    public static void matricesPop(Object ctx)        { ((DrawContext) ctx).getMatrices().pop(); }
    public static void matricesTranslate(Object ctx, float x, float y, float z) {
        ((DrawContext) ctx).getMatrices().translate(x, y, z);
    }
    public static void matricesScale(Object ctx, float sx, float sy, float sz) {
        ((DrawContext) ctx).getMatrices().scale(sx, sy, sz);
    }
    //#endif

    // ======================== Font ========================

    //#if MC>=260200
    //$$ public static int textWidth(Object font, String s)   { return ((Font) font).width(s); }
    //$$ public static int textWidth(Object font, Object txt) { return ((Font) font).width((Component) txt); }
    //#else
    public static int textWidth(Object font, String s)   { return ((TextRenderer) font).getWidth(s); }
    public static int textWidth(Object font, Object txt) { return ((TextRenderer) font).getWidth((Text) txt); }
    //#endif

    // ======================== Widget position/size/visibility ========================

    //#if MC>=260200
    //$$ public static void setX(Object w, int x)     { ((AbstractWidget) w).setX(x); }
    //$$ public static void setY(Object w, int y)     { ((AbstractWidget) w).setY(y); }
    //$$ public static void setW(Object w, int w_)    { ((AbstractWidget) w).setWidth(w_); }
    //$$ public static void setH(Object w, int h_)    { ((AbstractWidget) w).setHeight(h_); }
    //$$ public static int  getX(Object w)            { return ((AbstractWidget) w).getX(); }
    //$$ public static int  getY(Object w)            { return ((AbstractWidget) w).getY(); }
    //$$ public static int  getW(Object w)            { return ((AbstractWidget) w).getWidth(); }
    //$$ public static int  getH(Object w)            { return ((AbstractWidget) w).getHeight(); }
    //$$ public static void setVisible(Object w, boolean v) { ((AbstractWidget) w).visible = v; }
    //$$ public static void setActive(Object w, boolean a)  { ((AbstractWidget) w).active = a; }
    //#else
    public static void setX(Object w, int x)     { ((ClickableWidget) w).setX(x); }
    public static void setY(Object w, int y)     { ((ClickableWidget) w).setY(y); }
    public static void setW(Object w, int w_)    { ((ClickableWidget) w).setWidth(w_); }
    public static void setH(Object w, int h_)    { ((ClickableWidget) w).setHeight(h_); }
    public static int  getX(Object w)            { return ((ClickableWidget) w).getX(); }
    public static int  getY(Object w)            { return ((ClickableWidget) w).getY(); }
    public static int  getW(Object w)            { return ((ClickableWidget) w).getWidth(); }
    public static int  getH(Object w)            { return ((ClickableWidget) w).getHeight(); }
    public static void setVisible(Object w, boolean v) { ((ClickableWidget) w).visible = v; }
    public static void setActive(Object w, boolean a)  { ((ClickableWidget) w).active = a; }
    //#endif

    // ======================== Widget rendering ========================

    //#if MC>=260200
    //$$ public static void widgetRender(Object widget, Object ctx, int mx, int my, float delta) {
    //$$     ((AbstractWidget) widget).extractRenderState((GuiGraphicsExtractor) ctx, mx, my, delta);
    //$$ }
    //#else
    public static void widgetRender(Object widget, Object ctx, int mx, int my, float delta) {
        ((ClickableWidget) widget).render((DrawContext) ctx, mx, my, delta);
    }
    //#endif

    // ======================== Widget mouse events ========================

    //#if MC>=260200
    //$$ private static MouseButtonEvent mouseButtonEvent(double mx, double my, int btn) {
    //$$     return new MouseButtonEvent(mx, my, new MouseButtonInfo(btn, 0));
    //$$ }
    //$$
    //$$ public static boolean widgetMouseClicked(Object w, double mx, double my, int btn) {
    //$$     return ((AbstractWidget) w).mouseClicked(mouseButtonEvent(mx, my, btn), false);
    //$$ }
    //$$ public static boolean widgetMouseDragged(Object w, double mx, double my, int btn, double dx, double dy) {
    //$$     return ((AbstractWidget) w).mouseDragged(mouseButtonEvent(mx, my, btn), dx, dy);
    //$$ }
    //$$ public static boolean widgetMouseReleased(Object w, double mx, double my, int btn) {
    //$$     return ((AbstractWidget) w).mouseReleased(mouseButtonEvent(mx, my, btn));
    //$$ }
    //#else
    public static boolean widgetMouseClicked(Object w, double mx, double my, int btn) {
        return ((ClickableWidget) w).mouseClicked(mx, my, btn);
    }
    public static boolean widgetMouseDragged(Object w, double mx, double my, int btn, double dx, double dy) {
        return ((ClickableWidget) w).mouseDragged(mx, my, btn, dx, dy);
    }
    public static boolean widgetMouseReleased(Object w, double mx, double my, int btn) {
        return ((ClickableWidget) w).mouseReleased(mx, my, btn);
    }
    //#endif

    // ======================== Button ========================

    //#if MC>=260200
    //$$ public static Object newButton(Object msg, java.util.function.Consumer<Object> onClick, int w) {
    //$$     return Button.builder((Component) msg, btn -> onClick.accept(btn)).bounds(0, 0, w, 20).build();
    //$$ }
    //$$ public static void setButtonMsg(Object btn, Object msg) {
    //$$     ((Button) btn).setMessage((Component) msg);
    //$$ }
    //$$ @SuppressWarnings("unchecked")
    //$$ public static <T> T newButtonFull(Object msg, java.util.function.Consumer<Object> onClick,
    //$$         int x, int y, int w, int h) {
    //$$     return (T) Button.builder((Component) msg, btn -> onClick.accept(btn)).bounds(x, y, w, h).build();
    //$$ }
    //#else
    public static Object newButton(Object msg, java.util.function.Consumer<Object> onClick, int w) {
        return ButtonWidget.builder((Text) msg, btn -> onClick.accept(btn)).dimensions(0, 0, w, 20).build();
    }
    public static void setButtonMsg(Object btn, Object msg) {
        ((ButtonWidget) btn).setMessage((Text) msg);
    }
    @SuppressWarnings("unchecked")
    public static <T> T newButtonFull(Object msg, java.util.function.Consumer<Object> onClick,
            int x, int y, int w, int h) {
        return (T) ButtonWidget.builder((Text) msg, btn -> onClick.accept(btn)).dimensions(x, y, w, h).build();
    }
    //#endif

    // ======================== TextField / EditBox ========================

    //#if MC>=260200
    //$$ public static Object newTextField(Object font, int w, Object narration) {
    //$$     return new EditBox((Font) font, 0, 0, w, 20, (Component) narration);
    //$$ }
    //$$ public static String tfValue(Object tf)              { return ((EditBox) tf).getValue(); }
    //$$ public static void   tfSetValue(Object tf, String v) { ((EditBox) tf).setValue(v); }
    //$$ public static void   tfSetMaxLen(Object tf, int n)   { ((EditBox) tf).setMaxLength(n); }
    //$$ public static void   tfSetResponder(Object tf, java.util.function.Consumer<String> r) {
    //$$     ((EditBox) tf).setResponder(r);
    //$$ }
    //#else
    public static Object newTextField(Object font, int w, Object narration) {
        return new TextFieldWidget((TextRenderer) font, 0, 0, w, 20, (Text) narration);
    }
    public static String tfValue(Object tf)              { return ((TextFieldWidget) tf).getText(); }
    public static void   tfSetValue(Object tf, String v) { ((TextFieldWidget) tf).setText(v); }
    public static void   tfSetMaxLen(Object tf, int n)   { ((TextFieldWidget) tf).setMaxLength(n); }
    public static void   tfSetResponder(Object tf, java.util.function.Consumer<String> r) {
        ((TextFieldWidget) tf).setChangedListener(r::accept);
    }
    //#endif

    // ======================== TextField instanceof helpers ========================

    //#if MC>=260200
    //$$ public static boolean isTextField(Object w) { return w instanceof EditBox; }
    //$$ public static String  tfGetText(Object w)   { return ((EditBox) w).getValue(); }
    //$$ public static int     tfGetX(Object w)      { return ((EditBox) w).getX(); }
    //$$ public static int     tfGetY(Object w)      { return ((EditBox) w).getY(); }
    //$$ public static int     tfGetW(Object w)      { return ((EditBox) w).getWidth(); }
    //$$ public static int     tfGetH(Object w)      { return ((EditBox) w).getHeight(); }
    //#else
    public static boolean isTextField(Object w) { return w instanceof TextFieldWidget; }
    public static String  tfGetText(Object w)   { return ((TextFieldWidget) w).getText(); }
    public static int     tfGetX(Object w)      { return ((TextFieldWidget) w).getX(); }
    public static int     tfGetY(Object w)      { return ((TextFieldWidget) w).getY(); }
    public static int     tfGetW(Object w)      { return ((TextFieldWidget) w).getWidth(); }
    public static int     tfGetH(Object w)      { return ((TextFieldWidget) w).getHeight(); }
    //#endif

    // ======================== Slider ========================

    //#if MC>=260200
    //$$ public static void sliderSetMsg(Object s, Object msg) {
    //$$     ((AbstractSliderButton) s).setMessage((Component) msg);
    //$$ }
    //#else
    public static void sliderSetMsg(Object s, Object msg) {
        ((SliderWidget) s).setMessage((Text) msg);
    }
    //#endif

    //#if MC>=260200
    //$$ public static Object newSlider(int width, double value,
    //$$         java.util.function.DoubleFunction<Object> mf, java.util.function.DoubleConsumer onV) {
    //$$     return new CompatSlider(0, 0, width, 20, value, mf, onV);
    //$$ }
    //$$
    //$$ private static class CompatSlider extends AbstractSliderButton {
    //$$     private final java.util.function.DoubleFunction<Object> mf;
    //$$     private final java.util.function.DoubleConsumer onV;
    //$$     CompatSlider(int x, int y, int w, int h, double val,
    //$$             java.util.function.DoubleFunction<Object> mf, java.util.function.DoubleConsumer onV) {
    //$$         super(x, y, w, h, (Component) mf.apply(val), val);
    //$$         this.mf = mf; this.onV = onV;
    //$$     }
    //$$     @Override protected void updateMessage() { setMessage((Component) mf.apply(value)); }
    //$$     @Override protected void applyValue()    { onV.accept(value); }
    //$$ }
    //#else
    public static Object newSlider(int width, double value,
            java.util.function.DoubleFunction<Object> mf, java.util.function.DoubleConsumer onV) {
        return new CompatSlider(0, 0, width, 20, value, mf, onV);
    }

    private static class CompatSlider extends SliderWidget {
        private final java.util.function.DoubleFunction<Object> mf;
        private final java.util.function.DoubleConsumer onV;
        CompatSlider(int x, int y, int w, int h, double val,
                java.util.function.DoubleFunction<Object> mf, java.util.function.DoubleConsumer onV) {
            super(x, y, w, h, (Text) mf.apply(val), val);
            this.mf = mf; this.onV = onV;
        }
        @Override protected void updateMessage() { setMessage((Text) mf.apply(value)); }
        @Override protected void applyValue()    { onV.accept(value); }
    }
    //#endif

    // ======================== Screen helpers ========================

    //#if MC>=260200
    //$$ @SuppressWarnings("unchecked")
    //$$ public static <T> T screenAddChild(Object screen, Object widget) {
    //$$     return (T) ((PlatformScreen) screen).compatAddRenderableWidget((GuiEventListener & Renderable & NarratableEntry) widget);
    //$$ }
    //$$ public static Object screenFont(Object screen)       { return ((Screen) screen).getFont(); }
    //$$ public static Object screenClient(Object screen)     { return ((PlatformScreen) screen).compatMinecraft(); }
    //$$ public static void   screenClearChildren(Object scr) { ((PlatformScreen) scr).compatClearWidgets(); }
    //$$ public static java.util.List<?> screenChildren(Object screen) { return ((Screen) screen).children(); }
    //#else
    @SuppressWarnings("unchecked")
    public static <T> T screenAddChild(Object screen, Object widget) {
        return (T) ((PlatformScreen) screen).compatAddDrawableChild(
            (net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable) widget);
    }
    public static Object screenFont(Object screen)       { return ((PlatformScreen) screen).compatTextRenderer(); }
    public static Object screenClient(Object screen)     { return ((PlatformScreen) screen).compatClient(); }
    public static void   screenClearChildren(Object scr) { ((PlatformScreen) scr).compatClearChildren(); }
    public static java.util.List<?> screenChildren(Object screen) { return ((Screen) screen).children(); }
    //#endif

    // ======================== Minecraft client ========================

    //#if MC>=260200
    //$$ public static Object getMinecraft()                       { return Minecraft.getInstance(); }
    //$$ public static Object getClientFont()                      { return Minecraft.getInstance().font; }
    //$$ public static Object getClientPlayer(Object cl)           { return ((Minecraft) cl).player; }
    //$$ public static void   setScreen(Object cl, Object screen)  { ((Minecraft) cl).gui.setScreen((Screen) screen); }
//#else
    public static Object getMinecraft()                       { return MinecraftClient.getInstance(); }
    public static Object getClientFont()                      { return MinecraftClient.getInstance().textRenderer; }
    public static Object getClientPlayer(Object cl)           { return ((MinecraftClient) cl).player; }
    public static void   setScreen(Object cl, Object screen)  { ((MinecraftClient) cl).setScreen((Screen) screen); }
    //#endif

    // ======================== Identifier ========================

    //#if MC>=260200
    //$$ public static Object id(String namespace, String path) { return Identifier.fromNamespaceAndPath(namespace, path); }
    //#else
    public static Object id(String namespace, String path) { return Identifier.of(namespace, path); }
    //#endif

    // ======================== Custom draw (drawBezier via IDrawContextExt) ========================

    //#if MC>=260200
    //$$ // drawOrientedQuad not available in 26.2 yet; stub it out
    //$$ public static void drawOrientedQuad(Object ctx, Object renderLayer, float x1, float y1, float x2, float y2, float w, int color) {
    //$$     // TODO: implement for 26.2
    //$$ }
    //$$ public static Object getGuiRenderLayer() { return null; }
    //#else
    @SuppressWarnings("unchecked")
    public static void drawOrientedQuad(Object ctx, Object renderLayer, float x1, float y1, float x2, float y2, float w, int color) {
        try {
            com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt ext =
                (com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt) ctx;
            ext.radiance$drawOrientedQuad((net.minecraft.client.render.RenderLayer) renderLayer,
                x1, y1, x2, y2, w, color);
        } catch (Exception ignored) {}
    }
    public static Object getGuiRenderLayer() {
        return net.minecraft.client.render.RenderLayer.getGui();
    }
    //#endif

    // ======================== Legacy formatting (Formatting / ChatFormatting) ========================

    private static final char FORMATTING_PREFIX = '\u00a7';

    //#if MC>=260200
    //$$ public static Object parseLegacyFormatting(String raw) {
    //$$     if (raw == null || raw.isEmpty()) return Component.empty();
    //$$
    //$$     MutableComponent result = ((MutableComponent) Component.empty());
    //$$     Object style = net.minecraft.network.chat.Style.EMPTY;
    //$$     StringBuilder segment = new StringBuilder();
    //$$
    //$$     for (int i = 0; i < raw.length(); i++) {
    //$$         char c = raw.charAt(i);
    //$$         if (c == FORMATTING_PREFIX && i + 1 < raw.length()) {
    //$$             net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(raw.charAt(i + 1));
    //$$             if (fmt != null) {
    //$$                 if (segment.length() > 0) {
    //$$                     result.append(Component.literal(segment.toString()).setStyle((net.minecraft.network.chat.Style) style));
    //$$                     segment.setLength(0);
    //$$                 }
    //$$                 style = ((net.minecraft.network.chat.Style) style).applyLegacyFormat(fmt);
    //$$                 i++;
    //$$                 continue;
    //$$             }
    //$$         }
    //$$         segment.append(c);
    //$$     }
    //$$     if (segment.length() > 0) {
    //$$         result.append(Component.literal(segment.toString()).setStyle((net.minecraft.network.chat.Style) style));
    //$$     }
    //$$     return result;
    //$$ }
    //#else
    public static Object parseLegacyFormatting(String raw) {
        if (raw == null || raw.isEmpty()) return Text.empty();

        net.minecraft.text.MutableText result = Text.empty();
        net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY;
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == FORMATTING_PREFIX && i + 1 < raw.length()) {
                net.minecraft.util.Formatting fmt = net.minecraft.util.Formatting.byCode(raw.charAt(i + 1));
                if (fmt != null) {
                    if (segment.length() > 0) {
                        result.append(Text.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }
                    if (fmt == net.minecraft.util.Formatting.RESET) {
                        style = net.minecraft.text.Style.EMPTY;
                    } else if (fmt.isColor()) {
                        style = style.withExclusiveFormatting(fmt);
                    } else {
                        style = style.withFormatting(fmt);
                    }
                    i++;
                    continue;
                }
            }
            segment.append(c);
        }
        if (segment.length() > 0) {
            result.append(Text.literal(segment.toString()).setStyle(style));
        }
        return result;
    }
    //#endif
}
