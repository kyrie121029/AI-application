package com.example.demo.service;

import com.example.demo.config.StorageProperties;
import com.example.demo.exception.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件系统存储实现
 * <p>
 * 安全要点：每次 store/load/delete 都先 resolve + normalize，
 * 并验证最终路径仍在 storage.local.root-path 之下，防止 ../ 路径穿越。
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path rootPath;

    public LocalStorageService(StorageProperties props) {
        this.rootPath = Paths.get(props.getLocal().getRootPath()).toAbsolutePath().normalize();
        log.info("本地文件存储根目录: {}", rootPath);
    }

    @Override
    public void store(String storageKey, InputStream content, long size) {
        Path target = resolveAndVerify(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("文件写入失败: " + storageKey, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveAndVerify(storageKey);
        if (!Files.exists(target)) {
            throw new FileStorageException("存储文件缺失: " + storageKey);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveAndVerify(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new FileStorageException("文件删除失败: " + storageKey, e);
        }
    }

    /** resolve + normalize 后验证仍在 root 之下，防止 ../ 路径穿越 */
    private Path resolveAndVerify(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new FileStorageException("非法存储路径: 为空");
        }
        Path resolved = rootPath.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new FileStorageException("存储路径越界: " + storageKey);
        }
        return resolved;
    }
}
