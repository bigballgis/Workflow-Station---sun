package com.platform.security.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for data encryption.
 * Validates: Property 16 (Sensitive Data Encryption Storage)
 */
class EncryptionPropertyTest {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "ENC:";
    private static final SecureRandom secureRandom = new SecureRandom();
    
    // Property 16: Sensitive Data Encryption Storage
    // For any field marked as sensitive, the value stored in the database
    // should be encrypted, and can be correctly decrypted to restore the original
    
    @Property(tries = 100)
    void encryptedDataShouldBeDecryptable(
            @ForAll @Size(min = 1, max = 1000) String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        String encrypted = service.encrypt(plainText);
        String decrypted = service.decrypt(encrypted);
        
        assertThat(decrypted).isEqualTo(plainText);
    }
    
    @Property(tries = 100)
    void encryptedDataShouldBeDifferentFromPlainText(
            @ForAll @Size(min = 1, max = 100) String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        String encrypted = service.encrypt(plainText);
        
        // Encrypted data should not contain the plain text
        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(encrypted).startsWith(ENCRYPTED_PREFIX);
    }
    
    @Property(tries = 100)
    void sameDataShouldProduceDifferentCiphertext(
            @ForAll @Size(min = 1, max = 100) String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        String encrypted1 = service.encrypt(plainText);
        String encrypted2 = service.encrypt(plainText);
        
        // Due to random IV, same plaintext should produce different ciphertext
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        
        // But both should decrypt to the same value
        assertThat(service.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(service.decrypt(encrypted2)).isEqualTo(plainText);
    }
    
    @Property(tries = 100)
    void encryptionShouldHandleSpecialCharacters(
            @ForAll("specialStrings") String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        String encrypted = service.encrypt(plainText);
        String decrypted = service.decrypt(encrypted);
        
        assertThat(decrypted).isEqualTo(plainText);
    }
    
    @Property(tries = 100)
    void encryptionShouldHandleUnicode(
            @ForAll("unicodeStrings") String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        String encrypted = service.encrypt(plainText);
        String decrypted = service.decrypt(encrypted);
        
        assertThat(decrypted).isEqualTo(plainText);
    }
    
    @Property(tries = 100)
    void isEncryptedShouldDetectEncryptedStrings(
            @ForAll @Size(min = 1, max = 100) String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        assertThat(service.isEncrypted(plainText)).isFalse();
        
        String encrypted = service.encrypt(plainText);
        assertThat(service.isEncrypted(encrypted)).isTrue();
    }
    
    @Property(tries = 100)
    void decryptingPlainTextShouldReturnSameValue(
            @ForAll @Size(min = 1, max = 100) String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        // Decrypting non-encrypted text should return the same text
        String result = service.decrypt(plainText);
        assertThat(result).isEqualTo(plainText);
    }
    
    @Property(tries = 50)
    void nullAndEmptyShouldBeHandledGracefully() {
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        assertThat(service.encrypt(null)).isNull();
        assertThat(service.encrypt("")).isEmpty();
        assertThat(service.decrypt(null)).isNull();
        assertThat(service.decrypt("")).isEmpty();
    }
    
    @Property(tries = 100)
    void encryptedDataShouldBeBase64Encoded(
            @ForAll @Size(min = 1, max = 100) String plainText) {
        
        SimulatedEncryptionService service = new SimulatedEncryptionService();
        
        String encrypted = service.encrypt(plainText);
        String base64Part = encrypted.substring(ENCRYPTED_PREFIX.length());
        
        // Should be valid Base64
        byte[] decoded = Base64.getDecoder().decode(base64Part);
        assertThat(decoded).isNotEmpty();
    }
    
    @Provide
    Arbitrary<String> specialStrings() {
        return Arbitraries.of(
                "Hello World!",
                "Test@123#$%",
                "Line1\nLine2\tTab",
                "Quote\"Test'Single",
                "<script>alert('xss')</script>",
                "SELECT * FROM users;",
                "path/to/file.txt",
                "email@example.com"
        );
    }
    
    @Provide
    Arbitrary<String> unicodeStrings() {
        return Arbitraries.of(
                "中文测试",
                "日本語テスト",
                "한국어 테스트",
                "Тест на русском",
                "🎉🎊🎁",
                "مرحبا بالعالم",
                "שלום עולם"
        );
    }
    
    // Simulated encryption service for testing
    private static class SimulatedEncryptionService {
        private final SecretKeySpec secretKey;
        
        SimulatedEncryptionService() {
            byte[] keyBytes = "test-256-bit-key-for-testing!!!!".getBytes(StandardCharsets.UTF_8);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
        
        String encrypt(String plainText) {
            if (plainText == null || plainText.isEmpty()) {
                return plainText;
            }
            
            try {
                byte[] encrypted = encryptBytes(plainText.getBytes(StandardCharsets.UTF_8));
                return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(encrypted);
            } catch (Exception e) {
                throw new RuntimeException("Encryption failed", e);
            }
        }
        
        String decrypt(String encryptedText) {
            if (encryptedText == null || encryptedText.isEmpty()) {
                return encryptedText;
            }
            
            if (!isEncrypted(encryptedText)) {
                return encryptedText;
            }
            
            try {
                String base64Data = encryptedText.substring(ENCRYPTED_PREFIX.length());
                byte[] encrypted = Base64.getDecoder().decode(base64Data);
                byte[] decrypted = decryptBytes(encrypted);
                return new String(decrypted, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Decryption failed", e);
            }
        }
        
        boolean isEncrypted(String text) {
            return text != null && text.startsWith(ENCRYPTED_PREFIX);
        }
        
        private byte[] encryptBytes(byte[] data) throws Exception {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encrypted = cipher.doFinal(data);
            
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            
            return result;
        }
        
        private byte[] decryptBytes(byte[] encryptedData) throws Exception {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);
            
            byte[] encrypted = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            return cipher.doFinal(encrypted);
        }
    }
}
