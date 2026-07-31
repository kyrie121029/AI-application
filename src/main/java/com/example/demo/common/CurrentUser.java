package com.example.demo.common;

import java.lang.annotation.*;

/**
 * 自定义注解 —— 标记在 Controller 方法参数上，自动注入当前登录用户
 * <p>
 * 用法：
 *   public ApiResponse<Task> getTask(@PathVariable Long id, @CurrentUser User user) {
 *       // user 就是当前请求的用户，无需手动从 Token 中提取
 *   }
 */
@Target(ElementType.PARAMETER)          // 只能用在方法参数上
@Retention(RetentionPolicy.RUNTIME)     // 运行时保留
@Documented
public @interface CurrentUser {
}