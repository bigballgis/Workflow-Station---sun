package com.platform.common.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Configuration Encryption Service
 * 
 * Provides encryption and decryption capabilities for sensitive configuration data
 * including credentials, API keys, and other sensitive settings.
 * 
 * Uses AES-256-GCM encryption for strong security with authenticated encryption.
 * 
 * @author Platform Team
 * @version 1.0
 */
@Service
public class ConfigurationEncryptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationEncryptionService.class);
    
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "{encrypted}";
    
    // Patterns to identify sensitive configuration keys
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
        "(?i).*(password|secret|key|token|credential|auth|jwt|api[_-]?key|private[_-]?key).*"
    );
    
    private final String encryptionKey;
    private final SecureRandom secureRandom;
    
    public ConfigurationEncryptionService(@Value("${platform.config.encryption.key:default-config-encryption-key-32bytes}") String encryptionKey) {
        this.encryptionKey = encryptionKey;
        this.secureRandom = new SecureRandom();
        
        // Validate encryption key length
        if (encryptionKey.length() < 32) {
            logger.warn("Configuration encryption key length is less than 32 characters. " +
                       "Consider using a longer key for better security.");
        }
        
        logger.info("Configuration encryption service initialized");
    }
    
    /**
     * Encrypt sensitive configuration value
     * 
     * @param plainValue The plain text value to encrypt
     * @return Encrypted value with prefix, or original value if encryption fails
     */
    public String encryptValue(String plainValue) {
        if (plainValue == null || plainValue.isEmpty()) {
            return plainValue;
        }
        
        // Don't encrypt already encrypted values
        if (isEncrypted(plainValue)) {
            return plainValue;
        }
        
        try {
            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            String encryptedValue = Base64.getEncoder().encodeToString(combined);
            
            logger.debug("Configuration value encrypted successfully");
            return ENCRYPTED_PREFIX + encryptedValue;
            
        } catch (Exception e) {
            logger.error("Failed to encrypt configuration value", e);
            // Return original value if encryption fails to avoid breaking the application
            return plainValue;
        }
    }
    
    /**
     * Decrypt encrypted configuration value
     * 
     * @param encryptedValue The encrypted value to decrypt
     * @return Decrypted plain text value, or original value if not encrypted or decryption fails
     */
    public String decryptValue(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        
        // Return as-is if not encrypted
        if (!isEncrypted(encryptedValue)) {
            return encryptedValue;
        }
        
        try {
            // Remove encryption prefix
            String base64Data = encryptedValue.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Data);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];
            
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);
            
            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            logger.debug("Configuration value decrypted successfully");
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("Failed to decrypt configuration value", e);
            // Return encrypted value if decryption fails to avoid breaking the application
            return encryptedValue;
        }
    }
    
    /**
     * Check if a configuration key represents sensitive data
     * 
     * @param key Configuration key
     * @return true if the key is considered sensitive
     */
    public boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        
        return SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }
    
    /**
     * Check if a value is encrypted
     * 
     * @param value Configuration value
     * @return true if the value is encrypted
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }
    
    /**
     * Encrypt configuration value if the key is sensitive
     * 
     * @param key Configuration key
     * @param value Configuration value
     * @return Encrypted value if key is sensitive, otherwise original value
     */
    public String encryptIfSensitive(String key, String value) {
        if (isSensitiveKey(key)) {
            return encryptValue(value);
        }
        return value;
    }
    
    /**
     * Mask sensitive configuration value for logging
     * 
     * @param key Configuration key
     * @param value Configuration value
     * @return Masked value if key is sensitive, otherwise original value
     */
    public String maskSensitiveValue(String key, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        if (isSensitiveKey(key)) {
            if (value.length() <= 4) {
                return "****";
            } else {
                return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
            }
        }
        
        return value;
    }
    
    /**
     * Get the secret key for encryption/decryption
     * 
     * @return SecretKey for AES encryption
     */
    private SecretKey getSecretKey() {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32]; // AES-256 requires 32-byte key
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(key, "AES");
    }
}