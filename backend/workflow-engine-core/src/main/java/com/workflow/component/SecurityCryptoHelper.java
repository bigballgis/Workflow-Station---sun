package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全加密协作类
 *
 * 从 {@link SecurityManagerComponent} 拆分而来，负责敏感数据的 AES-GCM 加解密、
 * 密码 SHA-256 哈希与校验。纯结构搬迁，行为与原实现逐字一致。
 *
 * <p>加密密钥从门面读取（{@code encryptionKey}），不持有可变状态。
 */
@Slf4j
@Component
public class SecurityCryptoHelper {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * 加密敏感数据
     */
    public String encryptData(String plainText, String encryptionKey) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            SecretKey secretKey = getEncryptionKey(encryptionKey);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 将IV和加密数据组合
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            log.error("数据加密失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("ENCRYPTION_FAILED", "Data encryption failed");
        }
    }

    /**
     * 解密敏感数据
     */
    public String decryptData(String encryptedText, String encryptionKey) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[combined.length - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);

            SecretKey secretKey = getEncryptionKey(encryptionKey);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);

            return new String(decryptedData, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("数据解密失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("DECRYPTION_FAILED", "Data decryption failed");
        }
    }

    /**
     * 哈希密码
     */
    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            log.error("密码哈希失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("HASH_FAILED", "Password hashing failed");
        }
    }

    /**
     * 验证密码
     */
    public boolean verifyPassword(String password, String hashedPassword) {
        String hash = hashPassword(password);
        return hash.equals(hashedPassword);
    }

    /**
     * 获取加密密钥
     */
    private SecretKey getEncryptionKey(String encryptionKey) {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32]; // AES-256需要32字节密钥
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(key, "AES");
    }
}
