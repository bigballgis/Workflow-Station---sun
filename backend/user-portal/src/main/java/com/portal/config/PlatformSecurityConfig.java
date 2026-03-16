package com.portal.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.platform.security.repository.RoleAssignmentRepository;

/**
 * 平台安全模块仓库配置
 * 导入RoleAssignmentRepository（com.platform.security.repository包）
 * 注意：com.portal.repository下的Repository由UserPortalApplication的@EnableJpaRepositories扫描
 */
@Configuration
@EnableJpaRepositories(
    basePackageClasses = {RoleAssignmentRepository.class}
)
@EntityScan(basePackages = {"com.platform.security.entity"})
public class PlatformSecurityConfig {
}
