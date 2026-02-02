package com.workflow.config;

import com.platform.common.config.PlatformCommonConfiguration;
import com.platform.common.config.ValidationConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Workflow Engine Dependency Configuration
 * 
 * Imports platform-common configurations for dependency injection
 * of validation, error handling, and security components.
 * 
 * **Validates: Requirements 4.2**
 * 
 * @author Workflow Engine Team
 * @version 1.0
 */
@Configuration
@Import({
    PlatformCommonConfiguration.class,
    ValidationConfiguration.class
})
public class WorkflowDependencyConfiguration {
    // Configuration is handled by imported classes
}