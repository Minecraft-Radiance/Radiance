package com.radiance.client.pipeline;

import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

public class Module {

    public String name;
    public List<ImageConfig> inputImageConfigs;
    public List<ImageConfig> outputImageConfigs;
    public List<AttributeConfig> attributeConfigs;
    public List<AttributeConfig> staticAttributeConfigs;
    public Map<String, String> dynamicTranslations = new HashMap<>();
    public String dynamicAttributeStoragePath;

    public double x, y;

    @Override
    public String toString() {
        return "name: " + name
            + ", inputImageConfigs: " + inputImageConfigs
            + ", outputImageConfigs: " + outputImageConfigs
            + ", attributeConfigs: " + attributeConfigs;
    }

    public static List<AttributeConfig> copyAttributeConfigs(List<AttributeConfig> source) {
        List<AttributeConfig> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (AttributeConfig attributeConfig : source) {
            if (attributeConfig == null) {
                continue;
            }
            AttributeConfig cloned = new AttributeConfig();
            cloned.type = attributeConfig.type;
            cloned.name = attributeConfig.name;
            cloned.value = attributeConfig.value;
            cloned.defaultValue = attributeConfig.defaultValue != null
                ? attributeConfig.defaultValue
                : attributeConfig.value;
            cloned.description = attributeConfig.description;
            cloned.group = attributeConfig.group;
            cloned.performanceImpact = attributeConfig.performanceImpact;
            if (attributeConfig.linkedValues != null) {
                cloned.linkedValues = new HashMap<>();
                for (Map.Entry<String, Map<String, String>> entry : attributeConfig.linkedValues.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }
                    cloned.linkedValues.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
            }
            if (attributeConfig.preset != null) {
                cloned.preset = new AttributeConfig.PresetConfig();
                cloned.preset.customValue = attributeConfig.preset.customValue;
                if (attributeConfig.preset.profiles != null) {
                    cloned.preset.profiles = new HashMap<>();
                    for (Map.Entry<String, Map<String, String>> entry
                            : attributeConfig.preset.profiles.entrySet()) {
                        if (entry.getValue() == null) {
                            continue;
                        }
                        cloned.preset.profiles.put(entry.getKey(), new HashMap<>(entry.getValue()));
                    }
                }
            }
            if (attributeConfig.visibleWhen != null) {
                cloned.visibleWhen = new ArrayList<>();
                for (AttributeConfig.VisibilityCondition condition : attributeConfig.visibleWhen) {
                    if (condition == null) {
                        continue;
                    }
                    AttributeConfig.VisibilityCondition copiedCondition =
                            new AttributeConfig.VisibilityCondition();
                    copiedCondition.attribute = condition.attribute;
                    copiedCondition.module = condition.module;
                    if (condition.values != null) {
                        copiedCondition.values = new ArrayList<>(condition.values);
                    }
                    cloned.visibleWhen.add(copiedCondition);
                }
            }
            copy.add(cloned);
        }
        return copy;
    }

    public static void initializeAttributeDefaults(List<AttributeConfig> attributes) {
        if (attributes == null) {
            return;
        }
        for (AttributeConfig attributeConfig : attributes) {
            if (attributeConfig == null || attributeConfig.defaultValue != null) {
                continue;
            }
            attributeConfig.defaultValue = attributeConfig.value;
        }
    }

    public Component translateText(String key) {
        if (key == null || key.isEmpty()) {
            return Component.empty();
        }
        String translated = dynamicTranslations.get(key);
        return translated != null ? Component.literal(translated) : Component.translatable(key);
    }

    public ImageConfig getInputImageConfig(String name) {
        for (ImageConfig imageConfig : inputImageConfigs) {
            if (imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        throw new RuntimeException("No such image config: " + name);
    }

    public ImageConfig getOutputImageConfig(String name) {
        for (ImageConfig imageConfig : outputImageConfigs) {
            if (imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        throw new RuntimeException("No such image config: " + name);
    }
}
