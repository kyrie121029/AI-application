package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT Token", example = "eyJhbGciOiJIUzM4NCJ9...")
    private String token;

    @Schema(description = "用户名", example = "alice")
    private String username;

    public LoginResponse() {}

    public LoginResponse(String token, String username) {
        this.token = token;
        this.username = username;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
