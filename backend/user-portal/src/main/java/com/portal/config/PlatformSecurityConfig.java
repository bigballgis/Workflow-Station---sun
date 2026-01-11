package com.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.platform.security.repository.RoleAssignmentRepository;

/**
 * 平台安全模块仓库配置
 * 只导入需要的RoleAssignmentRepository
 */
@Configuration
@EnableJpaRepositories(
    basePackageClasses = {RoleAssignmentRepository.class},
    includeFilters = {
        @org.springframework.context.annotation.ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = {RoleAssignmentRepository.class}
        )
    }
)
public class PlatformSecurityConfig {
}
