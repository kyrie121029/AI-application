package com.example.demo.service;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * 文件存储抽象 —— FileService 只依赖此接口，不依赖具体实现。
 * <p>
 * 第一版为本地文件系统（LocalStorageService），后续可替换为 MinIO 等对象存储。
 * <p>
 * 契约：
 *   - storageKey 是系统内部生成的定位标识，实现方必须防止路径穿越；
 *   - store/load/delete 失败统一抛 FileStorageException。
 */
public interface StorageService {

    /**
     * 将内容流写入 storageKey 指定的位置。
     *
     * @param size 内容字节数（MinIO 流式上传需要，本地实现可忽略）
     */
    void store(String storageKey, InputStream content, long size);

    /** 读取 storageKey 指定的内容；不存在时抛 FileStorageException */
    Resource load(String storageKey);

    /** 删除 storageKey 指定的内容（幂等：不存在不报错） */
    void delete(String storageKey);
}
