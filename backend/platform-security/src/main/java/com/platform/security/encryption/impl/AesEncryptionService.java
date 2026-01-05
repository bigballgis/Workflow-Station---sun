package com.platform.security.encryption.impl;

import com.platform.security.encryption.EncryptionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service implementation.
 * Validates: Requirements 13.1
 */
@Slf4j
@Service
public class AesEncryptionService implements EncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "ENC:";
    
    @Value("${encryption.key:default-256-bit-key-for-dev-only!}")
    private String encryptionKey;
    
    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @PostConstruct
    public void init() {
        // Ensure key is 32 bytes (256 bits)
        byte[] keyBytes = new byte[32];
        byte[] providedKey = encryptionKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(providedKey, 0, keyBytes, 0, Math.min(providedKey.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }
    
    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        try {
            byte[] encrypted = encrypt(plainText.getBytes(StandardCharsets.UTF_8));
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    @Override
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        if (!isEncrypted(encryptedText)) {
            return encryptedText;
        }
        
        try {
            String base64Data = encryptedText.substring(ENCRYPTED_PREFIX.length());
            byte[] encrypted = Base64.getDecoder().decode(base64Data);
            byte[] decrypted = decrypt(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    @Override
    public byte[] encrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encrypted = cipher.doFinal(data);
            
            // Prepend IV to encrypted data
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            
            return result;
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    @Override
    public byte[] decrypt(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length <= GCM_IV_LENGTH) {
            return encryptedData;
        }
        
        try {
            // Extract IV from the beginning
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);
            
            // Extract encrypted data
            byte[] encrypted = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    @Override
    public boolean isEncrypted(String text) {
        return text != null && text.startsWith(ENCRYPTED_PREFIX);
    }
}
