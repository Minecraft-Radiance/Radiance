package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import net.minecraft.client.gui.Font;

public class ModuleNode {

    public final Module module;

    public int width = 220;
    public int headerH = 18;
    public int pad = 8;
    public int rowH = 16;

    public ModuleNode(Module module) {
        this.module = module;
    }

    public int rows() {
        int inNum = module.inputImageConfigs == null ? 0 : module.inputImageConfigs.size();
        int outNum = module.outputImageConfigs == null ? 0 : module.outputImageConfigs.size();
        return Math.max(inNum, outNum);
    }

    public int height() {
        return headerH + pad + rows() * rowH + pad;
    }

    public void updateWidth(Font textRenderer) {
        int maxInWidth = 0;
        if (module.inputImageConfigs != null) {
            for (var in : module.inputImageConfigs) {
                maxInWidth = Math.max(maxInWidth, textRenderer.width(in.name));
            }
        }

        int maxOutWidth = 0;
        if (module.outputImageConfigs != null) {
            for (var out : module.outputImageConfigs) {
                maxOutWidth = Math.max(maxOutWidth, textRenderer.width(out.name));
            }
        }

        int titleWidth = textRenderer.width(module.name) + 30;
        int contentWidth = maxInWidth + maxOutWidth + 60;

        width = Math.max(220, Math.max(titleWidth, contentWidth));
    }
}
