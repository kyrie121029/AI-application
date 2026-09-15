package com.example.demo.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tool 元数据 —— 名称、说明、参数描述与必需参数。
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, String> paramTypes,
        Map<String, String> paramDescriptions,
        Set<String> requiredParams
) {
    public ToolDefinition {
        paramTypes = paramTypes == null ? Map.of() : paramTypes;
        paramDescriptions = paramDescriptions == null ? Map.of() : paramDescriptions;
        requiredParams = requiredParams == null ? Set.of() : requiredParams;
    }

    public static Builder builder(String name, String description) {
        return new Builder(name, description);
    }

    /** 支持的 JSON Schema 类型 */
    public static final String STRING = "string";
    public static final String INTEGER = "integer";
    public static final String ARRAY_INTEGER = "array_integer";

    public static final class Builder {
        private final String name;
        private final String description;
        private final Map<String, String> paramTypes = new LinkedHashMap<>();
        private final Map<String, String> paramDescriptions = new LinkedHashMap<>();
        private final Set<String> requiredParams = new java.util.LinkedHashSet<>();

        private Builder(String name, String description) {
            this.name = name;
            this.description = description;
        }

        /** @param type STRING / INTEGER / ARRAY_INTEGER */
        public Builder param(String paramName, String type, String paramDescription, boolean required) {
            paramTypes.put(paramName, type);
            paramDescriptions.put(paramName, paramDescription);
            if (required) requiredParams.add(paramName);
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, paramTypes, paramDescriptions, requiredParams);
        }
    }
}
