package com.developer.config;

import com.developer.security.DatabasePermissionEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Spring Security configuration for custom permission evaluation.
 * Enables method-level security with database-backed permission checking.
 * 
 * Requirements: 3.1, 5.1, 5.2
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityPermissionConfig {
    
    /**
     * Configure method security expression handler with custom permission evaluator.
     * This enables @PreAuthorize and @PostAuthorize annotations to use our
     * database-backed permission checking system.
     * 
     * @param permissionEvaluator the custom permission evaluator
     * @return configured method security expression handler
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            DatabasePermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler expressionHandler = 
                new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(permissionEvaluator);
        return expressionHandler;
    }
}