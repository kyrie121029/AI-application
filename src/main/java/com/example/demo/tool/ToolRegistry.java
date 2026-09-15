package com.example.demo.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 注册表 —— 收集全部 Tool Bean，按名称索引。
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> toolList) {
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : toolList) {
            map.put(tool.definition().name(), tool);
        }
        this.tools = map;
    }

    public Tool byName(String name) {
        return tools.get(name);
    }

    public Map<String, ToolDefinition> allDefinitions() {
        Map<String, ToolDefinition> defs = new LinkedHashMap<>();
        tools.values().forEach(t -> defs.put(t.definition().name(), t.definition()));
        return defs;
    }
}
