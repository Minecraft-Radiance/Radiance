package com.radiance.client.gui;

import static com.radiance.client.gui.compat.GuiCompat.*;

import com.radiance.client.gui.compat.PlatformScreen;
import com.radiance.client.gui.compat.ShaderPackListBridge;
import com.radiance.client.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.List;

public class ShaderPackScreen extends PlatformScreen {

    private static final int LIST_TOP = 36;
    private static final int ROW_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 32;
    private static final int FOOTER_BUTTON_WIDTH = 120;
    private static final int FOOTER_BUTTON_GAP = 8;

    private static final String TITLE = "shader_pack_screen.title";
    private static final String BACK = "render_pipeline_screen.back";
    private static final String SHADER_SETTINGS = "shader_pack_screen.shader_settings";
    private static final String EMPTY = "shader_pack_screen.empty";

    private final Object parent;
    private final List<Pipeline.ShaderPackChoice> entries = new ArrayList<>();
    private ShaderPackListBridge shaderPackList;

    public ShaderPackScreen(Object parent) {
        super(txt(TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries.clear();
        entries.addAll(Pipeline.getAvailableShaderPacks());

        int listHeight = this.height - LIST_TOP - FOOTER_HEIGHT;
        shaderPackList = new ShaderPackListBridge(screenClient(this), this.width, listHeight,
            LIST_TOP, ROW_HEIGHT, entries) {
            @Override
            protected Object buildLabelImpl(Pipeline.ShaderPackChoice choice) {
                return ShaderPackScreen.this.buildLabel(choice);
            }
        };
        screenAddChild(this, shaderPackList);

        int footerY = this.height - 28;
        int footerX = (this.width - (FOOTER_BUTTON_WIDTH * 2 + FOOTER_BUTTON_GAP)) / 2;

        Object backBtn = newButton(txt(BACK), btn -> close(), FOOTER_BUTTON_WIDTH);
        screenAddChild(this, backBtn);
        setX(backBtn, footerX);
        setY(backBtn, footerY);

        Object settingsBtn = newButton(txt(SHADER_SETTINGS),
            btn -> setScreen(getMinecraft(), new ShaderPackSettingsScreen(this)),
            FOOTER_BUTTON_WIDTH);
        screenAddChild(this, settingsBtn);
        setX(settingsBtn, footerX + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP);
        setY(settingsBtn, footerY);
    }

    @Override
    public void close() {
        setScreen(getMinecraft(), parent);
    }

    @Override
    protected void renderScreen(Object ctx, int mouseX, int mouseY, float delta) {
        Object font = screenFont(this);
        drawCenteredText(ctx, font, txt(TITLE), this.width / 2, 16, 0xFFFFFF);

        Object hoveredTooltip = shaderPackList == null ? null : shaderPackList.getHoveredTooltip();
        if (hoveredTooltip != null) {
            drawTooltip(ctx, font, hoveredTooltip, mouseX, mouseY);
        }

        if (entries.isEmpty()) {
            drawCenteredText(ctx, font, txt(EMPTY), this.width / 2, LIST_TOP + 10, 0xB0B0B0);
        }
    }

    // --- Label formatting (uses only GuiCompat, no MC types) ---

    private Object buildLabel(Pipeline.ShaderPackChoice choice) {
        Object label = parseLegacyFormatting(choice.displayName());
        if (Pipeline.isShaderPackActive(choice)) {
            label = txtAppend(txtLit("> "), label);
        }
        return label;
    }
}
