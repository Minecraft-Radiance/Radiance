package com.radiance.client.pipeline.config;

import java.util.List;
import java.util.Map;

public class AttributeConfig {

    public String type;
    public String name;
    public String value;
    public transient String defaultValue;
    public String description;
    public String group;
    public String performanceImpact;
    public List<VisibilityCondition> visibleWhen;
    public Map<String, Map<String, String>> linkedValues;
    public PresetConfig preset;

    public static class VisibilityCondition {
        public String attribute;
        public String module;
        public List<String> values;
    }

    public static class PresetConfig {
        public String customValue;
        public Map<String, Map<String, String>> profiles;
    }
}
