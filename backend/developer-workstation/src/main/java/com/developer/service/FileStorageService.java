package com.developer.service;

import com.developer.entity.UploadedFile;

/**
 * Database-backed file storage service.
 */
public interface FileStorageService {

    UploadedFile store(String storedName, String originalName, String contentType, long fileSize, byte[] content);

    UploadedFile getByStoredName(String storedName);

    void deleteByStoredName(String storedName);
}
