package com.example.demo.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一返回类 —— 所有接口都套用这个格式返回给前端
 * <p>
 * 格式：
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": ...
 * }
 *
 * @param <T> data 字段的具体类型
 */
public class ApiResponse<T> {

    @Schema(description = "状态码，200 表示成功", example = "200")
    private int code;

    @Schema(description = "提示信息", example = "success")
    private String message;

    @Schema(description = "实际返回的数据")
    private T data;

    // ==================== 构造方法（私有，通过静态方法创建） ====================

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 快捷静态工厂方法 ====================

    /** 成功（带数据） */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /** 成功（不带数据） */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null);
    }

    /** 失败 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ==================== Getter / Setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
