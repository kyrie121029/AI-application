package com.example.demo.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 文本编码检测工具 —— 供文件校验（FileContentValidator）与 TXT 解析（TxtDocumentParser）共用，
 * 避免两套不同的编码判断规则。
 * <p>
 * 规则：严格解码（malformed 报错），优先 UTF-8，其次 GBK；均失败返回 null。
 */
public final class TextEncodingUtil {

    private static final Charset GBK = Charset.forName("GBK");

    private TextEncodingUtil() {}

    /** 检测字节数组的编码：UTF-8 优先，其次 GBK，无法判定返回 null */
    public static Charset detectCharset(byte[] bytes) {
        if (isDecodable(bytes, StandardCharsets.UTF_8)) return StandardCharsets.UTF_8;
        if (isDecodable(bytes, GBK)) return GBK;
        return null;
    }

    /** 严格解码（malformed/unmappable 报错，而不是静默替换） */
    public static boolean isDecodable(byte[] bytes, Charset charset) {
        try {
            charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
