package com.platform.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.ArrayList;

/**
 * Security Configuration
 * 
 * Externalized security settings including authentication, authorization,
 * and security policies
 * 
 * @author Platform Team
 * @version 1.0
 */
public class SecurityConfig {
    
    private boolean enableInputValidation = true;
    private boolean enableSqlInjectionDetection = true;
    private boolean enableXssProtection = true;
    private boolean enableCsrfProtection = true;
    
    @NotNull
    private List<String> allowedOrigins = new ArrayList<>();
    
    @NotNull
    private List<String> allowedHeaders = new ArrayList<>();
    
    @NotNull
    private List<String> allowedMethods = new ArrayList<>();
    
    @Min(value = 4, message = "Minimum password length must be at least 4")
    @Max(value = 256, message = "Maximum password length cannot exceed 256")
    private int passwordMinLength = 8;
    
    @Min(value = 8, message = "Maximum password length must be at least 8")
    @Max(value = 512, message = "Maximum password length cannot exceed 512")
    private int passwordMaxLength = 128;
    
    private boolean passwordRequireUppercase = true;
    private boolean passwordRequireLowercase = true;
    private boolean passwordRequireDigit = true;
    private boolean passwordRequireSpecial = true;
    
    @Min(value = 0, message = "Password history count cannot be negative")
    @Max(value = 50, message = "Password history count cannot exceed 50")
    private int passwordHistoryCount = 5;
    
    @Min(value = 1, message = "Password expiry days must be at least 1")
    @Max(value = 3650, message = "Password expiry days cannot exceed 10 years")
    private int passwordExpiryDays = 90;
    
    @Min(value = 1, message = "Max failed attempts must be at least 1")
    @Max(value = 100, message = "Max failed attempts cannot exceed 100")
    private int maxFailedAttempts = 5;
    
    @Min(value = 1, message = "Lockout duration must be at least 1 minute")
    @Max(value = 1440, message = "Lockout duration cannot exceed 24 hours")
    private int lockoutDurationMinutes = 30;
    
    @Min(value = 1, message = "Session timeout must be at least 1 minute")
    @Max(value = 1440, message = "Session timeout cannot exceed 24 hours")
    private int sessionTimeoutMinutes = 30;
    
    @Min(value = 1, message = "Max concurrent sessions must be at least 1")
    @Max(value = 100, message = "Max concurrent sessions cannot exceed 100")
    private int maxConcurrentSessions = 3;
    
    private boolean enableSecurityAuditLogging = true;
    private boolean enableSecurityEventNotification = true;
    
    @NotNull
    private String jwtSecretKey = "default-jwt-secret-key-change-in-production";
    
    @Min(value = 300, message = "JWT expiration must be at least 5 minutes")
    @Max(value = 86400, message = "JWT expiration cannot exceed 24 hours")
    private int jwtExpirationSeconds = 3600;
    
    // Configuration encryption settings
    private boolean enableConfigurationEncryption = true;
    private boolean enableConfigurationAuditLogging = true;
    private boolean enableCredentialCaching = true;
    
    @Min(value = 1, message = "Credential cache TTL must be at least 1 minute")
    @Max(value = 1440, message = "Credential cache TTL cannot exceed 24 hours")
    private int credentialCacheTtlMinutes = 60;
    
    @NotNull
    private String configurationEncryptionAlgorithm = "AES/GCM/NoPadding";
    
    private boolean maskSensitiveConfigurationInLogs = true;
    private boolean validateCredentialsAtStartup = true;
    
    // Getters and Setters
    public boolean isEnableInputValidation() {
        return enableInputValidation;
    }
    
    public void setEnableInputValidation(boolean enableInputValidation) {
        this.enableInputValidation = enableInputValidation;
    }
    
    public boolean isEnableSqlInjectionDetection() {
        return enableSqlInjectionDetection;
    }
    
    public void setEnableSqlInjectionDetection(boolean enableSqlInjectionDetection) {
        this.enableSqlInjectionDetection = enableSqlInjectionDetection;
    }
    
    public boolean isEnableXssProtection() {
        return enableXssProtection;
    }
    
    public void setEnableXssProtection(boolean enableXssProtection) {
        this.enableXssProtection = enableXssProtection;
    }
    
    public boolean isEnableCsrfProtection() {
        return enableCsrfProtection;
    }
    
    public void setEnableCsrfProtection(boolean enableCsrfProtection) {
        this.enableCsrfProtection = enableCsrfProtection;
    }
    
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }
    
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
    
    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }
    
    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }
    
    public List<String> getAllowedMethods() {
        return allowedMethods;
    }
    
    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }
    
    public int getPasswordMinLength() {
        return passwordMinLength;
    }
    
    public void setPasswordMinLength(int passwordMinLength) {
        this.passwordMinLength = passwordMinLength;
    }
    
    public int getPasswordMaxLength() {
        return passwordMaxLength;
    }
    
    public void setPasswordMaxLength(int passwordMaxLength) {
        this.passwordMaxLength = passwordMaxLength;
    }
    
    public boolean isPasswordRequireUppercase() {
        return passwordRequireUppercase;
    }
    
    public void setPasswordRequireUppercase(boolean passwordRequireUppercase) {
        this.passwordRequireUppercase = passwordRequireUppercase;
    }
    
    public boolean isPasswordRequireLowercase() {
        return passwordRequireLowercase;
    }
    
    public void setPasswordRequireLowercase(boolean passwordRequireLowercase) {
        this.passwordRequireLowercase = passwordRequireLowercase;
    }
    
    public boolean isPasswordRequireDigit() {
        return passwordRequireDigit;
    }
    
    public void setPasswordRequireDigit(boolean passwordRequireDigit) {
        this.passwordRequireDigit = passwordRequireDigit;
    }
    
    public boolean isPasswordRequireSpecial() {
        return passwordRequireSpecial;
    }
    
    public void setPasswordRequireSpecial(boolean passwordRequireSpecial) {
        this.passwordRequireSpecial = passwordRequireSpecial;
    }
    
    public int getPasswordHistoryCount() {
        return passwordHistoryCount;
    }
    
    public void setPasswordHistoryCount(int passwordHistoryCount) {
        this.passwordHistoryCount = passwordHistoryCount;
    }
    
    public int getPasswordExpiryDays() {
        return passwordExpiryDays;
    }
    
    public void setPasswordExpiryDays(int passwordExpiryDays) {
        this.passwordExpiryDays = passwordExpiryDays;
    }
    
    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }
    
    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }
    
    public int getLockoutDurationMinutes() {
        return lockoutDurationMinutes;
    }
    
    public void setLockoutDurationMinutes(int lockoutDurationMinutes) {
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }
    
    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }
    
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }
    
    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }
    
    public void setMaxConcurrentSessions(int maxConcurrentSessions) {
        this.maxConcurrentSessions = maxConcurrentSessions;
    }
    
    public boolean isEnableSecurityAuditLogging() {
        return enableSecurityAuditLogging;
    }
    
    public void setEnableSecurityAuditLogging(boolean enableSecurityAuditLogging) {
        this.enableSecurityAuditLogging = enableSecurityAuditLogging;
    }
    
    public boolean isEnableSecurityEventNotification() {
        return enableSecurityEventNotification;
    }
    
    public void setEnableSecurityEventNotification(boolean enableSecurityEventNotification) {
        this.enableSecurityEventNotification = enableSecurityEventNotification;
    }
    
    public String getJwtSecretKey() {
        return jwtSecretKey;
    }
    
    public void setJwtSecretKey(String jwtSecretKey) {
        this.jwtSecretKey = jwtSecretKey;
    }
    
    public int getJwtExpirationSeconds() {
        return jwtExpirationSeconds;
    }
    
    public void setJwtExpirationSeconds(int jwtExpirationSeconds) {
        this.jwtExpirationSeconds = jwtExpirationSeconds;
    }
    
    public boolean isEnableConfigurationEncryption() {
        return enableConfigurationEncryption;
    }
    
    public void setEnableConfigurationEncryption(boolean enableConfigurationEncryption) {
        this.enableConfigurationEncryption = enableConfigurationEncryption;
    }
    
    public boolean isEnableConfigurationAuditLogging() {
        return enableConfigurationAuditLogging;
    }
    
    public void setEnableConfigurationAuditLogging(boolean enableConfigurationAuditLogging) {
        this.enableConfigurationAuditLogging = enableConfigurationAuditLogging;
    }
    
    public boolean isEnableCredentialCaching() {
        return enableCredentialCaching;
    }
    
    public void setEnableCredentialCaching(boolean enableCredentialCaching) {
        this.enableCredentialCaching = enableCredentialCaching;
    }
    
    public int getCredentialCacheTtlMinutes() {
        return credentialCacheTtlMinutes;
    }
    
    public void setCredentialCacheTtlMinutes(int credentialCacheTtlMinutes) {
        this.credentialCacheTtlMinutes = credentialCacheTtlMinutes;
    }
    
    public String getConfigurationEncryptionAlgorithm() {
        return configurationEncryptionAlgorithm;
    }
    
    public void setConfigurationEncryptionAlgorithm(String configurationEncryptionAlgorithm) {
        this.configurationEncryptionAlgorithm = configurationEncryptionAlgorithm;
    }
    
    public boolean isMaskSensitiveConfigurationInLogs() {
        return maskSensitiveConfigurationInLogs;
    }
    
    public void setMaskSensitiveConfigurationInLogs(boolean maskSensitiveConfigurationInLogs) {
        this.maskSensitiveConfigurationInLogs = maskSensitiveConfigurationInLogs;
    }
    
    public boolean isValidateCredentialsAtStartup() {
        return validateCredentialsAtStartup;
    }
    
    public void setValidateCredentialsAtStartup(boolean validateCredentialsAtStartup) {
        this.validateCredentialsAtStartup = validateCredentialsAtStartup;
    }
}