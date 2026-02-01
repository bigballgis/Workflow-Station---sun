package com.platform.common.config;

import com.platform.common.config.security.ConfigurationAuditLogger;
import com.platform.common.config.security.ConfigurationEncryptionService;
import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.resource.ConnectionPoolManager;
import com.platform.common.resource.ResourceManager;
import com.platform.common.security.AuthenticationSecurityManager;
import com.platform.common.security.AuthorizationSecurityManager;
import com.platform.common.security.SecurityAuditLogger;
import com.platform.common.security.SecurityIntegrationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Technical Debt Remediation Configuration
 * 
 * Configures Spring dependency injection for all components created during
 * technical debt remediation including validation, error handling, security,
 * and resource management components.
 * 
 * **Validates: Requirements 4.2**
 * 
 * @author Platform Team
 * @version 1.0
 */
@Configuration
public class TechnicalDebtRemediationConfiguration {
    
    /**
     * Configure global exception handler for consistent error handling
     * across all modules
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "platformGlobalExceptionHandler")
    public GlobalExceptionHandler platformGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    
    /**
     * Configure security audit logger for security event tracking
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityAuditLogger securityAuditLogger(SecurityConfig securityConfig) {
        return new SecurityAuditLogger(securityConfig);
    }
    
    /**
     * Configure authentication security manager
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationSecurityManager authenticationSecurityManager(
            SecurityConfig securityConfig,
            ConfigurationAuditLogger auditLogger) {
        return new AuthenticationSecurityManager(securityConfig, auditLogger);
    }
    
    /**
     * Configure authorization security manager
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationSecurityManager authorizationSecurityManager(
            SecurityConfig securityConfig,
            ConfigurationAuditLogger auditLogger) {
        return new AuthorizationSecurityManager(securityConfig, auditLogger);
    }
    
    /**
     * Configure security integration service that coordinates all security components
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityIntegrationService securityIntegrationService(
            AuthenticationSecurityManager authenticationManager,
            AuthorizationSecurityManager authorizationManager,
            SecurityConfig securityConfig,
            ConfigurationAuditLogger auditLogger,
            SecurityAuditLogger securityAuditLogger) {
        return new SecurityIntegrationService(authenticationManager, authorizationManager, 
                                            securityConfig, auditLogger, securityAuditLogger);
    }
    
    /**
     * Configure connection pool manager for resource management
     */
    @Bean
    @ConditionalOnMissingBean
    public ConnectionPoolManager connectionPoolManager(ConfigurationManager configurationManager,
                                                     DataSource dataSource) {
        return new ConnectionPoolManager(configurationManager, dataSource);
    }
    
    /**
     * Configure resource manager with connection pool management
     */
    @Bean
    @ConditionalOnMissingBean
    public ResourceManager resourceManager(DataSource dataSource,
                                         ConfigurationManager configurationManager) {
        return new ResourceManager(dataSource, configurationManager);
    }
}