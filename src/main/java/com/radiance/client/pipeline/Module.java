package com.radiance.client.pipeline;

import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import java.util.List;

public class Module {

    public String name;
    public List<ImageConfig> inputImageConfigs;
    public List<ImageConfig> outputImageConfigs;
    public List<AttributeConfig> attributeConfigs;

    // for GUI
    public double x, y;

    @Override
    public String toString() {
        return "name: " + name
            + ", inputImageConfigs: " + inputImageConfigs
            + ", outputImageConfigs: " + outputImageConfigs
            + ", attributeConfigs: " + attributeConfigs;
    }

    public ImageConfig getInputImageConfig(String name) {
        ImageConfig imageConfig = findInputImageConfig(name);
        if (imageConfig != null) {
            return imageConfig;
        }
        throw new RuntimeException("No such image config: " + name);
    }

    public ImageConfig findInputImageConfig(String name) {
        if (inputImageConfigs == null || name == null) {
            return null;
        }
        for (ImageConfig imageConfig : inputImageConfigs) {
            if (imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        return null;
    }

    public ImageConfig getOutputImageConfig(String name) {
        ImageConfig imageConfig = findOutputImageConfig(name);
        if (imageConfig != null) {
            return imageConfig;
        }
        throw new RuntimeException("No such image config: " + name);
    }

    public ImageConfig findOutputImageConfig(String name) {
        if (outputImageConfigs == null || name == null) {
            return null;
        }
        for (ImageConfig imageConfig : outputImageConfigs) {
            if (imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        return null;
    }
}
