package com.developer.service.impl;

import com.developer.entity.UploadedFile;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.UploadedFileRepository;
import com.developer.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed file storage service implementation.
 */
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final UploadedFileRepository uploadedFileRepository;

    @Override
    @Transactional
    public UploadedFile store(String storedName, String originalName, String contentType, long fileSize, byte[] content) {
        UploadedFile file = UploadedFile.builder()
                .storedName(storedName)
                .originalName(originalName)
                .contentType(contentType)
                .fileSize(fileSize)
                .content(content)
                .build();
        return uploadedFileRepository.save(file);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadedFile getByStoredName(String storedName) {
        return uploadedFileRepository.findByStoredName(storedName)
                .orElseThrow(() -> new ResourceNotFoundException("UploadedFile", storedName));
    }

    @Override
    @Transactional
    public void deleteByStoredName(String storedName) {
        UploadedFile file = getByStoredName(storedName);
        uploadedFileRepository.delete(file);
    }
}
