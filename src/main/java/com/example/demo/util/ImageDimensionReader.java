package com.example.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 图片尺寸读取 —— 用 ImageIO ImageReader 只读 header 获取宽高，不解码整图。
 * <p>
 * JPEG / PNG 有内置 reader；WEBP 等无内置 reader 时返回 null（不强制）。
 */
public final class ImageDimensionReader {

    private static final Logger log = LoggerFactory.getLogger(ImageDimensionReader.class);

    private ImageDimensionReader() {}

    /** 读取宽高，失败或格式不支持时返回 null */
    public static int[] read(InputStream inputStream) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(inputStream)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.warn("图片尺寸读取失败: {}", e.getMessage());
            return null;
        }
    }
}
