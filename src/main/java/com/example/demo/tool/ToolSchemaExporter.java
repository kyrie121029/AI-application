package com.example.demo.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolDefinition → OpenAI-compatible function/tool JSON Schema。
 * 参数类型来自 ToolDefinition.paramTypes（显式，不再按参数名猜测）。
 */
public final class ToolSchemaExporter {

    private ToolSchemaExporter() {}

    public static List<Map<String, Object>> toOpenaiTools(List<ToolDefinition> definitions) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition def : definitions) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", def.name());
            function.put("description", def.description());

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            for (String paramName : def.paramDescriptions().keySet()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                applyType(prop, def.paramTypes().getOrDefault(paramName, "string"));
                prop.put("description", def.paramDescriptions().get(paramName));
                properties.put(paramName, prop);
            }
            parameters.put("properties", properties);
            if (!def.requiredParams().isEmpty()) {
                parameters.put("required", new ArrayList<>(def.requiredParams()));
            }
            function.put("parameters", parameters);

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private static void applyType(Map<String, Object> prop, String type) {
        switch (type) {
            case ToolDefinition.INTEGER -> {
                prop.put("type", "integer");
            }
            case ToolDefinition.ARRAY_INTEGER -> {
                prop.put("type", "array");
                Map<String, Object> items = new LinkedHashMap<>();
                items.put("type", "integer");
                prop.put("items", items);
            }
            default -> prop.put("type", "string");
        }
    }
}
