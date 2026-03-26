package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModuleAttributeScreen extends Screen {

    private final Screen parent;

    public ModuleAttributeScreen(Screen parent, Module module) {
        super(Text.translatable(module.name));
        this.parent = parent;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
    }
}
