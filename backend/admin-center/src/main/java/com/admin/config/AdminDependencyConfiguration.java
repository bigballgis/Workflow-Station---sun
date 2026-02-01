package com.admin.config;

import com.platform.common.config.TechnicalDebtRemediationConfiguration;
import com.platform.common.config.ValidationConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Admin Center Dependency Configuration
 * 
 * Imports platform-common configurations for dependency injection
 * of validation, error handling, and security components.
 * 
 * **Validates: Requirements 4.2**
 * 
 * @author Admin Center Team
 * @version 1.0
 */
@Configuration
@Import({
    TechnicalDebtRemediationConfiguration.class,
    ValidationConfiguration.class
})
public class AdminDependencyConfiguration {
    // Configuration is handled by imported classes
}