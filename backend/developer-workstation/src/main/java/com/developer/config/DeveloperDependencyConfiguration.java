package com.developer.config;

import com.platform.common.config.TechnicalDebtRemediationConfiguration;
import com.platform.common.config.ValidationConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Developer Workstation Dependency Configuration
 * 
 * Imports platform-common configurations for dependency injection
 * of validation, error handling, and security components.
 * 
 * **Validates: Requirements 4.2**
 * 
 * @author Developer Workstation Team
 * @version 1.0
 */
@Configuration
@Import({
    TechnicalDebtRemediationConfiguration.class,
    ValidationConfiguration.class
})
public class DeveloperDependencyConfiguration {
    // Configuration is handled by imported classes
}