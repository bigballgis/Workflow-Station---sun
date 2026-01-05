package com.platform.security.encryption;

/**
 * Service interface for data encryption.
 * Validates: Requirements 13.1
 */
public interface EncryptionService {
    
    /**
     * Encrypt a string value.
     * 
     * @param plainText Plain text to encrypt
     * @return Encrypted text (Base64 encoded)
     */
    String encrypt(String plainText);
    
    /**
     * Decrypt an encrypted string.
     * 
     * @param encryptedText Encrypted text (Base64 encoded)
     * @return Decrypted plain text
     */
    String decrypt(String encryptedText);
    
    /**
     * Encrypt bytes.
     * 
     * @param data Data to encrypt
     * @return Encrypted data
     */
    byte[] encrypt(byte[] data);
    
    /**
     * Decrypt bytes.
     * 
     * @param encryptedData Encrypted data
     * @return Decrypted data
     */
    byte[] decrypt(byte[] encryptedData);
    
    /**
     * Check if a string is encrypted.
     * 
     * @param text Text to check
     * @return true if the text appears to be encrypted
     */
    boolean isEncrypted(String text);
}
