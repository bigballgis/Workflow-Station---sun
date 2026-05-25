package com.developer.service;

import com.developer.entity.UploadedFile;

/**
 * 数据库存储文件服务。
 */
public interface FileStorageService {

    UploadedFile store(String storedName, String originalName, String contentType, long fileSize, byte[] content);

    UploadedFile getByStoredName(String storedName);

    void deleteByStoredName(String storedName);
}
