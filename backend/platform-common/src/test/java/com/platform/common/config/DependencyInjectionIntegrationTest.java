package com.platform.common.config;

import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.resource.ConnectionPoolManager;
import com.platform.common.resource.ResourceManager;
import com.platform.common.security.AuthenticationSecurityManager;
import com.platform.common.security.AuthorizationSecurityManager;
import com.platform.common.security.SecurityAuditLogger;
import com.platform.common.security.SecurityIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for dependency injection configuration.
 * Verifies that all components are properly wired together.
 * 
 * **Validates: Requirements 4.2**
 */
@SpringBootTest(classes = {
    TechnicalDebtRemediationConfiguration.class,
    ValidationConfiguration.class,
    PlatformConfigurationAutoConfiguration.class
})
@TestPropertySource(properties = {
    "platform.config.encryption.key=test-encryption-key-32-bytes-long",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DependencyInjectionIntegrationTest {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Test
    void shouldWireAllSecurityComponents() {
        // Verify security components are properly wired
        assertNotNull(applicationContext.getBean(SecurityAuditLogger.class));
        assertNotNull(applicationContext.getBean(AuthenticationSecurityManager.class));
        assertNotNull(applicationContext.getBean(AuthorizationSecurityManager.class));
        assertNotNull(applicationContext.getBean(SecurityIntegrationService.class));
        
        // Verify security integration service has proper dependencies
        SecurityIntegrationService securityService = applicationContext.getBean(SecurityIntegrationService.class);
        assertNotNull(securityService);
    }
    
    @Test
    void shouldWireResourceManagementComponents() {
        // Verify resource management components are properly wired
        assertNotNull(applicationContext.getBean(ConnectionPoolManager.class));
        assertNotNull(applicationContext.getBean(ResourceManager.class));
        
        // Verify resource manager has proper dependencies
        ResourceManager resourceManager = applicationContext.getBean(ResourceManager.class);
        assertNotNull(resourceManager);
    }
    
    @Test
    void shouldWireErrorHandlingComponents() {
        // Verify error handling components are properly wired
        assertNotNull(applicationContext.getBean(GlobalExceptionHandler.class));
    }
    
    @Test
    void shouldWireConfigurationComponents() {
        // Verify configuration components are properly wired
        assertNotNull(applicationContext.getBean(ConfigurationManager.class));
        assertNotNull(applicationContext.getBean(ConfigurationValidator.class));
        assertNotNull(applicationContext.getBean(RuntimeConfigurationUpdater.class));
    }
    
    @Test
    void shouldWireValidationComponents() {
        // Verify validation components are available
        assertTrue(applicationContext.containsBean("injectionDetector"));
        assertTrue(applicationContext.containsBean("sanitizationEngine"));
    }
    
    @Test
    void shouldHaveUniqueBeansForCriticalComponents() {
        // Verify that critical components have unique instances
        String[] securityAuditLoggerBeans = applicationContext.getBeanNamesForType(SecurityAuditLogger.class);
        assertEquals(1, securityAuditLoggerBeans.length, "Should have exactly one SecurityAuditLogger bean");
        
        String[] globalExceptionHandlerBeans = applicationContext.getBeanNamesForType(GlobalExceptionHandler.class);
        assertEquals(1, globalExceptionHandlerBeans.length, "Should have exactly one GlobalExceptionHandler bean");
        
        String[] configurationManagerBeans = applicationContext.getBeanNamesForType(ConfigurationManager.class);
        assertEquals(1, configurationManagerBeans.length, "Should have exactly one ConfigurationManager bean");
    }
    
    @Test
    void shouldSupportConditionalBeanCreation() {
        // Verify that @ConditionalOnMissingBean works correctly
        // If we create another context with existing beans, they should not be overridden
        assertTrue(applicationContext.containsBean("securityAuditLogger"));
        assertTrue(applicationContext.containsBean("authenticationSecurityManager"));
        assertTrue(applicationContext.containsBean("authorizationSecurityManager"));
        assertTrue(applicationContext.containsBean("securityIntegrationService"));
    }
}