package com.developer.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
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
@EntityScan(basePackages = {"com.platform.security.entity"})
public class PlatformSecurityConfig {
}
