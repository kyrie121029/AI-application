package com.example.demo.service;

import org.springframework.stereotype.Component;

/**
 * 简单 Token 估算实现 —— 近似值，明确标注 estimated
 */
@Component
public class SimpleTokenEstimator implements TokenEstimator {

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjk++;
            } else {
                other++;
            }
        }
        // 中文约 1.5 token/字，英文约 4 字符/token —— 近似估算
        return (int) Math.ceil(cjk * 1.5 + other / 4.0);
    }
}
