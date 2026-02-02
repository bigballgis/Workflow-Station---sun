package com.platform.common.config;

import com.platform.common.audit.AuditAspect;
import com.platform.common.audit.AuditContextProvider;
import com.platform.common.audit.AuditService;
import com.platform.common.audit.impl.DefaultAuditContextProvider;
import com.platform.common.audit.impl.DefaultAuditService;
import com.platform.common.config.security.ConfigurationAuditLogger;
import com.platform.common.config.security.ConfigurationEncryptionService;
import com.platform.common.resource.ConnectionPoolManager;
import com.platform.common.resource.ResourceManager;
import com.platform.common.security.EnhancedAuthenticationManager;
import com.platform.common.security.EnhancedAuthorizationManager;
import com.platform.common.security.SecurityAuditLogger;
import com.platform.common.security.SecurityIntegrationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Platform Common Configuration
 * 
 * Provides Spring Bean configuration for all platform-common components
 * including security, audit, resource management, and exception handling.
 * 
 * This configuration ensures proper dependency injection and avoids
 * Bean conflicts with application-specific configurations.
 * 
 * **Validates: Requirements 4.2**
 * 
 * @author Platform Team
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(ApplicationConfiguration.class)
public class PlatformCommonConfiguration {
    
    private final ApplicationConfiguration applicationConfiguration;
    
    public PlatformCommonConfiguration(ApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
        System.out.println("PlatformCommonConfiguration is being loaded!");
        System.out.println("ApplicationConfiguration injected: " + applicationConfiguration);
    }
    
    /**
     * Configure platform security config for platform security settings
     * This bean is created by extracting SecurityConfig from ApplicationConfiguration
     */
    @Bean("platformSecurityConfig")
    @ConditionalOnMissingBean(name = "platformSecurityConfig")
    public SecurityConfig platformSecurityConfig() {
        System.out.println("Creating platformSecurityConfig bean...");
        System.out.println("ApplicationConfiguration: " + applicationConfiguration);
        SecurityConfig securityConfig = applicationConfiguration.getSecurity();
        System.out.println("SecurityConfig from ApplicationConfiguration: " + securityConfig);
        if (securityConfig == null) {
            System.out.println("WARNING: SecurityConfig is null in ApplicationConfiguration! Creating default.");
            securityConfig = new SecurityConfig(); // Create default if null
        }
        return securityConfig;
    }
    
    /**
     * Configure security audit logger for security event tracking
     */
    @Bean("securityAuditLogger")
    @ConditionalOnMissingBean(SecurityAuditLogger.class)
    public SecurityAuditLogger securityAuditLogger() {
        return new SecurityAuditLogger(applicationConfiguration.getSecurity());
    }
    
    /**
     * Configure authentication security manager
     */
    @Bean
    @ConditionalOnMissingBean
    public EnhancedAuthenticationManager authenticationSecurityManager(
            ConfigurationAuditLogger auditLogger) {
        return new EnhancedAuthenticationManager(applicationConfiguration.getSecurity(), auditLogger);
    }
    
    /**
     * Configure authorization security manager
     */
    @Bean
    @ConditionalOnMissingBean
    public EnhancedAuthorizationManager authorizationSecurityManager(
            ConfigurationAuditLogger auditLogger) {
        return new EnhancedAuthorizationManager(applicationConfiguration.getSecurity(), auditLogger);
    }
    
    /**
     * Configure security integration service that coordinates all security components
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityIntegrationService securityIntegrationService(
            EnhancedAuthenticationManager authenticationManager,
            EnhancedAuthorizationManager authorizationManager,
            ConfigurationAuditLogger auditLogger,
            SecurityAuditLogger securityAuditLogger) {
        return new SecurityIntegrationService(authenticationManager, authorizationManager, 
                                            applicationConfiguration.getSecurity(), auditLogger, securityAuditLogger);
    }
    
    /**
     * Configure default audit service for audit logging
     */
    @Bean("platformAuditService")
    public AuditService platformAuditService() {
        System.out.println("Creating platformAuditService bean");
        return new DefaultAuditService();
    }
    
    /**
     * Configure default audit context provider for extracting audit context
     */
    @Bean("platformAuditContextProvider")
    public AuditContextProvider platformAuditContextProvider() {
        System.out.println("Creating platformAuditContextProvider bean");
        return new DefaultAuditContextProvider();
    }
    
    /**
     * Configure audit aspect for automatic audit logging via @Audited annotation
     */
    @Bean("platformAuditAspect")
    public AuditAspect platformAuditAspect(
            @Qualifier("platformAuditService") AuditService auditService,
            @Qualifier("platformAuditContextProvider") AuditContextProvider contextProvider) {
        System.out.println("Creating platformAuditAspect bean with auditService: " + auditService);
        return new AuditAspect(auditService, contextProvider);
    }
}