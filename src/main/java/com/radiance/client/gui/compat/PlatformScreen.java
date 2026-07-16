package com.radiance.client.gui.compat;

//#if MC>=260200
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//$$ import net.minecraft.client.gui.components.Renderable;
//$$ import net.minecraft.client.gui.components.events.GuiEventListener;
//$$ import net.minecraft.client.gui.narration.NarratableEntry;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.client.input.CharacterEvent;
//$$ import net.minecraft.client.input.KeyEvent;
//$$ import net.minecraft.client.input.MouseButtonEvent;
//$$ import net.minecraft.client.input.MouseButtonInfo;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.network.chat.Component;
//#else
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
//#endif

/**
 * Version-neutral base class for all Radiance GUI screens.
 * Subclasses override {@link #renderScreen} instead of the MC-specific render method.
 *
 * IMPORTANT: This is the ONLY compat file that subclasses Screen directly.
 * All other GUI files extend PlatformScreen and use GuiCompat for everything else.
 */
public abstract class PlatformScreen extends Screen {

    //#if MC>=260200
    //$$ protected PlatformScreen(Object title) {
    //$$     super((Component) title);
    //$$ }
    //#else
    protected PlatformScreen(Object title) {
        super((Text) title);
    }
    //#endif

    /**
     * Version-neutral render method. Subclasses override this instead of
     * render(DrawContext) or extractRenderState(GuiGraphicsExtractor).
     *
     * @param ctx    the drawing context (DrawContext or GuiGraphicsExtractor)
     * @param mouseX mouse X coordinate
     * @param mouseY mouse Y coordinate
     * @param delta  tick delta
     */
    protected abstract void renderScreen(Object ctx, int mouseX, int mouseY, float delta);

    //#if MC>=260200
    //$$ @Override
    //$$ public final void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
    //$$     renderScreen(ctx, mouseX, mouseY, delta);
    //$$ }
    //$$
    //$$ public <T extends GuiEventListener & Renderable & NarratableEntry> T compatAddRenderableWidget(T widget) {
    //$$     return this.addRenderableWidget(widget);
    //$$ }
    //$$
    //$$ public Minecraft compatMinecraft() {
    //$$     return this.minecraft;
    //$$ }
    //$$
    //$$ public void compatClearWidgets() {
    //$$     this.clearWidgets();
    //$$ }
    //$$
    //$$ public void close() {
    //$$     super.onClose();
    //$$ }
    //$$
    //$$ @Override
    //$$ public final void onClose() {
    //$$     this.close();
    //$$ }
    //$$
    //$$ private static MouseButtonEvent mouseButtonEvent(double mx, double my, int btn) {
    //$$     return new MouseButtonEvent(mx, my, new MouseButtonInfo(btn, 0));
    //$$ }
    //$$
    //$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //$$     return super.mouseClicked(mouseButtonEvent(mouseX, mouseY, button), false);
    //$$ }
    //$$
    //$$ @Override
    //$$ public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    //$$     return this.mouseClicked(event.x(), event.y(), event.button());
    //$$ }
    //$$
    //$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
    //$$     return super.mouseDragged(mouseButtonEvent(mouseX, mouseY, button), deltaX, deltaY);
    //$$ }
    //$$
    //$$ @Override
    //$$ public final boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
    //$$     return this.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY);
    //$$ }
    //$$
    //$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
    //$$     return super.mouseReleased(mouseButtonEvent(mouseX, mouseY, button));
    //$$ }
    //$$
    //$$ @Override
    //$$ public final boolean mouseReleased(MouseButtonEvent event) {
    //$$     return this.mouseReleased(event.x(), event.y(), event.button());
    //$$ }
    //$$
    //$$ public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //$$     return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    //$$ }
    //$$
    //$$ @Override
    //$$ public final boolean keyPressed(KeyEvent event) {
    //$$     return this.keyPressed(event.key(), event.scancode(), event.modifiers());
    //$$ }
    //$$
    //$$ public boolean charTyped(char chr, int modifiers) {
    //$$     return super.charTyped(new CharacterEvent(chr));
    //$$ }
    //$$
    //$$ @Override
    //$$ public final boolean charTyped(CharacterEvent event) {
    //$$     return this.charTyped((char) event.codepoint(), 0);
    //$$ }
    //#else
    @Override
    public final void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        renderScreen(ctx, mouseX, mouseY, delta);
    }

    public <T extends Element & Drawable & Selectable> T compatAddDrawableChild(T widget) {
        return this.addDrawableChild(widget);
    }

    public TextRenderer compatTextRenderer() {
        return this.textRenderer;
    }

    public MinecraftClient compatClient() {
        return this.client;
    }

    public void compatClearChildren() {
        this.clearChildren();
    }
    //#endif
}
