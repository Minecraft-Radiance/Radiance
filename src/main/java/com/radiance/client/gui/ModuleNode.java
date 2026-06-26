package com.radiance.client.gui;

import com.radiance.client.gui.compat.GuiCompat;
import com.radiance.client.pipeline.Module;

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

    public void updateWidth(Object font) {
        int maxInWidth = 0;
        if (module.inputImageConfigs != null) {
            for (var in : module.inputImageConfigs) {
                maxInWidth = Math.max(maxInWidth, GuiCompat.textWidth(font, in.name));
            }
        }

        int maxOutWidth = 0;
        if (module.outputImageConfigs != null) {
            for (var out : module.outputImageConfigs) {
                maxOutWidth = Math.max(maxOutWidth, GuiCompat.textWidth(font, out.name));
            }
        }

        int titleWidth = GuiCompat.textWidth(font, module.name) + 30;
        int contentWidth = maxInWidth + maxOutWidth + 60;

        width = Math.max(220, Math.max(titleWidth, contentWidth));
    }
}
