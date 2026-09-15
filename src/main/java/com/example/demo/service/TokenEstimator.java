package com.example.demo.service;

/**
 * Token 估算器 —— 估算文本的 token 数
 * <p>
 * 用于上下文窗口的 Token 预算计算。
 * 当前是近似估算（中文约 1.5 token/字，英文约 4 字符/token），
 * 不应宣称为模型的精确 Token 计数。
 */
public interface TokenEstimator {

    /** 估算一段文本的 token 数 */
    int estimateTokens(String text);
}
