package com.example.demo.config;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器 —— 每个请求进来时执行一次
 * <p>
 * 从 Header 中提取 Token → 校验 → 把用户信息放入 Spring Security 上下文
 * 这样 Controller 里就能拿到"当前是谁在请求"
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 从 Header 里拿 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 没带 Token → 放行（让 Spring Security 自己的规则决定能不能访问）
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 提取 Token（去掉 "Bearer " 前缀）
        String token = authHeader.substring(7);

        // 3. 校验 Token
        if (!jwtUtil.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. 从 Token 中取 userId，查数据库拿到 User 对象
        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 5. 把用户信息放入 SecurityContext（后续 Controller 就能用了）
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}