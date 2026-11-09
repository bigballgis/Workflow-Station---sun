package com.developer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on method-level security ({@code @PreAuthorize}, {@code @Secured}). Workspace rights per function unit are
 * decided by {@code FunctionUnitWorkspaceAccessService}, not by a permission evaluator over role tables.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityPermissionConfig {
}
