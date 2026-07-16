package com.radiance.client.pipeline;

import com.radiance.client.pipeline.config.AttributeConfig;
import com.radiance.client.pipeline.config.ImageConfig;
import com.radiance.client.pipeline.config.SignalGroupConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//#if MC>=260200
//$$ import net.minecraft.network.chat.Component;
//#else
import net.minecraft.text.Text;
//#endif

public class Module {

    public String name;
    public List<ImageConfig> inputImageConfigs;
    public List<ImageConfig> outputImageConfigs;
    public List<AttributeConfig> attributeConfigs;
    public List<AttributeConfig> staticAttributeConfigs;
    public String signalGroupAttribute;
    public Map<String, SignalGroupConfig> signalGroups = new HashMap<>();
    public Map<String, String> dynamicTranslations = new HashMap<>();
    public String dynamicAttributeStoragePath;

    private List<ImageConfig> baseInputImageConfigs;
    private List<ImageConfig> baseOutputImageConfigs;
    private List<AttributeConfig> baseAttributeConfigs;

    // for GUI
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
            copy.add(cloned);
        }
        return copy;
    }

    public void initializeStaticPorts() {
        if (baseInputImageConfigs == null) {
            baseInputImageConfigs = copyImageConfigs(inputImageConfigs);
        }
        if (baseOutputImageConfigs == null) {
            baseOutputImageConfigs = copyImageConfigs(outputImageConfigs);
        }
        if (baseAttributeConfigs == null) {
            baseAttributeConfigs = copyAttributeConfigs(attributeConfigs);
        }
        if (usesSignalGroups()) {
            rebuildExpandedPorts();
            rebuildExpandedAttributes();
        } else {
            assignOwners(inputImageConfigs);
            assignOwners(outputImageConfigs);
        }
    }

    public void rebuildExpandedPorts() {
        List<ImageConfig> expandedInputs = new ArrayList<>();
        List<ImageConfig> expandedOutputs = new ArrayList<>();

        List<String> groupIds = parseSignalGroupIds();
        Map<String, Integer> groupOccurrences = new HashMap<>();
        for (String groupId : groupIds) {
            int occurrence = groupOccurrences.getOrDefault(groupId, 0);
            groupOccurrences.put(groupId, occurrence + 1);

            if ("0".equals(groupId)) {
                appendImageConfigs(expandedInputs, baseInputImageConfigs, occurrence == 0 ? null : occurrence - 1);
                appendImageConfigs(expandedOutputs, baseOutputImageConfigs, occurrence == 0 ? null : occurrence - 1);
                continue;
            }

            SignalGroupConfig group = signalGroups == null ? null : signalGroups.get(groupId);
            if (group == null) {
                continue;
            }
            appendImageConfigs(expandedInputs, group.inputImageConfigs, occurrence == 0 ? null : occurrence);
            appendImageConfigs(expandedOutputs, group.outputImageConfigs, occurrence == 0 ? null : occurrence);
        }

        inputImageConfigs = expandedInputs;
        outputImageConfigs = expandedOutputs;
        assignOwners(inputImageConfigs);
        assignOwners(outputImageConfigs);
    }

    public void rebuildExpandedAttributes() {
        rebuildExpandedAttributes(null);
    }

    public void rebuildExpandedAttributes(Map<String, String> preservedValues) {
        if (!usesSignalGroups()) {
            attributeConfigs = copyAttributeConfigs(baseAttributeConfigs);
            return;
        }

        Map<String, String> currentValues = new HashMap<>();
        if (attributeConfigs != null) {
            for (AttributeConfig attributeConfig : attributeConfigs) {
                if (attributeConfig != null && attributeConfig.name != null) {
                    currentValues.put(attributeConfig.name, attributeConfig.value);
                }
            }
        }
        if (preservedValues != null) {
            currentValues.putAll(preservedValues);
        }

        List<AttributeConfig> expandedAttributes = new ArrayList<>();
        List<String> groupIds = parseSignalGroupIds();
        Map<String, Integer> groupOccurrences = new HashMap<>();
        for (String groupId : groupIds) {
            int occurrence = groupOccurrences.getOrDefault(groupId, 0);
            groupOccurrences.put(groupId, occurrence + 1);

            if ("0".equals(groupId)) {
                appendAttributeConfigs(expandedAttributes, baseAttributeConfigs);
                continue;
            }

            SignalGroupConfig group = signalGroups == null ? null : signalGroups.get(groupId);
            if (group != null) {
                appendAttributeConfigs(expandedAttributes, group.attributeConfigs);
            }
        }

        for (AttributeConfig attributeConfig : expandedAttributes) {
            if (attributeConfig != null && attributeConfig.name != null && currentValues.containsKey(attributeConfig.name)) {
                attributeConfig.value = currentValues.get(attributeConfig.name);
            }
        }

        attributeConfigs = expandedAttributes;
    }

    public boolean usesSignalGroups() {
        return signalGroupAttribute != null && !signalGroupAttribute.isEmpty();
    }

    private List<String> parseSignalGroupIds() {
        String value = getAttributeValue(signalGroupAttribute);
        if (value == null || value.isBlank()) {
            value = "0";
        }

        List<String> ids = new ArrayList<>();
        for (String part : value.split("[,\\s]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        if (ids.isEmpty()) {
            ids.add("0");
        }
        return ids;
    }

    private String getAttributeValue(String attributeName) {
        if (attributeName == null || attributeConfigs == null) {
            return null;
        }
        for (AttributeConfig attributeConfig : attributeConfigs) {
            if (attributeConfig != null && attributeName.equals(attributeConfig.name)) {
                return attributeConfig.value;
            }
        }
        return null;
    }

    private void appendImageConfigs(List<ImageConfig> target, List<ImageConfig> source, Integer suffix) {
        if (source == null) {
            return;
        }
        for (ImageConfig imageConfig : source) {
            if (imageConfig == null) {
                continue;
            }
            ImageConfig copy = copyImageConfig(imageConfig);
            if (suffix != null) {
                copy.name = copy.name + "_" + suffix;
            }
            copy.owner = this;
            target.add(copy);
        }
    }

    private static List<ImageConfig> copyImageConfigs(List<ImageConfig> source) {
        List<ImageConfig> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (ImageConfig imageConfig : source) {
            if (imageConfig != null) {
                copy.add(copyImageConfig(imageConfig));
            }
        }
        return copy;
    }

    private static ImageConfig copyImageConfig(ImageConfig source) {
        ImageConfig copy = new ImageConfig();
        copy.name = source.name;
        copy.format = source.format;
        copy.finalOutput = source.finalOutput;
        return copy;
    }

    private static void appendAttributeConfigs(List<AttributeConfig> target, List<AttributeConfig> source) {
        if (source == null) {
            return;
        }
        target.addAll(copyAttributeConfigs(source));
    }

    private void assignOwners(List<ImageConfig> configs) {
        if (configs == null) {
            return;
        }
        for (ImageConfig imageConfig : configs) {
            if (imageConfig != null) {
                imageConfig.owner = this;
            }
        }
    }

    //#if MC>=260200
    //$$ public Component translateText(String key) {
    //$$     if (key == null || key.isEmpty()) return Component.empty();
    //$$     String translated = dynamicTranslations.get(key);
    //$$     return translated != null ? Component.literal(translated) : Component.translatable(key);
    //$$ }
    //#else
    public Text translateText(String key) {
        if (key == null || key.isEmpty()) return Text.empty();
        String translated = dynamicTranslations.get(key);
        return translated != null ? Text.literal(translated) : Text.translatable(key);
    }
    //#endif

    public ImageConfig getInputImageConfig(String name) {
        ImageConfig imageConfig = findInputImageConfig(name);
        if (imageConfig != null) {
            return imageConfig;
        }
        throw new RuntimeException("No such image config: " + name);
    }

    public ImageConfig findInputImageConfig(String name) {
        if (inputImageConfigs == null) {
            return null;
        }
        for (ImageConfig imageConfig : inputImageConfigs) {
            if (imageConfig != null && imageConfig.name.equals(name)) {
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
        if (outputImageConfigs == null) {
            return null;
        }
        for (ImageConfig imageConfig : outputImageConfigs) {
            if (imageConfig != null && imageConfig.name.equals(name)) {
                return imageConfig;
            }
        }
        return null;
    }
}
