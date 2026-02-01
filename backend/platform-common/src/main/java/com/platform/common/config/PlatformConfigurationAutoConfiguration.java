package com.platform.common.config;

import com.platform.common.config.impl.ConfigurationManagerImpl;
import com.platform.common.config.security.ConfigurationAuditLogger;
import com.platform.common.config.security.ConfigurationEncryptionService;
import com.platform.common.config.security.SecureCredentialManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.validation.Validator;

/**
 * Platform Configuration Auto Configuration
 * 
 * Enables configuration properties and provides configuration manager bean
 * with validation and runtime update support
 * 
 * @author Platform Team
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(ApplicationConfiguration.class)
public class PlatformConfigurationAutoConfiguration {
    
    @Bean
    public ConfigurationValidator configurationValidator(Validator validator) {
        return new ConfigurationValidator(validator);
    }
    
    @Bean
    public RuntimeConfigurationUpdater runtimeConfigurationUpdater() {
        return new RuntimeConfigurationUpdater();
    }
    
    @Bean
    public ConfigurationEncryptionService configurationEncryptionService(Environment environment) {
        String encryptionKey = environment.getProperty("platform.config.encryption.key", 
                                                      "default-config-encryption-key-32bytes");
        return new ConfigurationEncryptionService(encryptionKey);
    }
    
    @Bean
    public ConfigurationAuditLogger configurationAuditLogger(ConfigurationEncryptionService encryptionService) {
        return new ConfigurationAuditLogger(encryptionService);
    }
    
    @Bean
    public SecureCredentialManager secureCredentialManager(Environment environment,
                                                         ConfigurationEncryptionService encryptionService,
                                                         ConfigurationAuditLogger auditLogger) {
        return new SecureCredentialManager(environment, encryptionService, auditLogger);
    }
    
    @Bean
    public ConfigurationManager configurationManager(Environment environment, 
                                                   Validator validator,
                                                   ApplicationConfiguration applicationConfiguration,
                                                   ConfigurationValidator configurationValidator,
                                                   RuntimeConfigurationUpdater runtimeUpdater,
                                                   ConfigurationEncryptionService encryptionService,
                                                   ConfigurationAuditLogger auditLogger,
                                                   SecureCredentialManager credentialManager) {
        return new ConfigurationManagerImpl(environment, validator, applicationConfiguration, 
                                          configurationValidator, runtimeUpdater, encryptionService,
                                          auditLogger, credentialManager);
    }
}