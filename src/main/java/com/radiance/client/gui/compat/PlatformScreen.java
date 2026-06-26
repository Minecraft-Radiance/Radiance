package com.radiance.client.gui.compat;

//#if MC>=260200
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//$$ import net.minecraft.client.gui.screens.Screen;
//$$ import net.minecraft.network.chat.Component;
//#else
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
    //$$     this.renderMenuBackground(ctx);
    //$$     renderScreen(ctx, mouseX, mouseY, delta);
    //$$ }
    //#else
    @Override
    public final void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        renderScreen(ctx, mouseX, mouseY, delta);
    }
    //#endif
}
