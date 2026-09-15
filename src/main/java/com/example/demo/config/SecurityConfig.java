package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

/**
 * Spring Security 核心配置
 * <p>
 * 规则：
 *   1. /api/auth/**  —— 无需登录（注册/登录接口）
 *   2. /h2-console/** —— 无需登录（H2 控制台）
 *   3. /api/tasks/**  —— 需要登录
 *   4. 无状态会话（JWT），不用 Cookie/Session
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（API 服务用 JWT，不需要 CSRF 保护）
            .csrf(csrf -> csrf.disable())

            // 无状态会话：不创建 Session，每次请求都带 Token
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL 权限规则
            .authorizeHttpRequests(auth -> auth
                // SSE 异步分发放行：ASYNC dispatch 时 SecurityContext 不传播，
                // 否则 AuthorizationFilter 在流结束后抛 AccessDenied（响应已提交 → 连接被异常关闭）
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers("/api/auth/**").permitAll()        // 注册/登录：放行
                .requestMatchers("/h2-console/**").permitAll()      // H2 控制台：放行
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**").permitAll() // Swagger：放行
                .requestMatchers(HttpMethod.GET, "/hello").permitAll() // hello 测试接口：放行
                .requestMatchers("/actuator/health").permitAll()      // 健康检查：放行（Docker healthcheck 用）
                .anyRequest().authenticated()                        // 其余请求：需登录
            )

            // 把 JWT 过滤器插在 Spring Security 的认证过滤器之前
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // 统一 401/403 返回格式为 ApiResponse
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        // 让 H2 控制台能正常显示（H2 用了 frame，Spring Security 默认禁止）
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt：自动加盐的单向哈希，不可逆
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}