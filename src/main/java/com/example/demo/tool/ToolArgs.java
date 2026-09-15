package com.example.demo.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数解析助手 —— 兼容模型传来的 Number / String / List 原始值。
 */
public final class ToolArgs {

    private ToolArgs() {}

    /** 解析 Long（兼容 Long 或数字字符串） */
    public static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数不是合法数字: " + value);
        }
    }

    /** 解析字符串 */
    public static String toString(Object value) {
        return value == null ? null : value.toString();
    }

    /** 解析 id 列表（兼容 List<Number>/List<String>） */
    public static List<Long> toLongList(Object value) {
        if (value == null) return null;
        List<Long> ids = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                ids.add(toLong(item));
            }
        }
        return ids;
    }
}
