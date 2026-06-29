package com.developer;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.platform.security.encryption.impl.AesEncryptionService;
import com.platform.security.resolver.TargetResolverFactory;
import com.platform.security.service.impl.UserRoleServiceImpl;
import com.platform.security.util.SecurityContextUtils;

import java.util.Optional;

/**
 * 开发者工作站应用程序入口
 */
@SpringBootApplication
@EnableConfigurationProperties(com.developer.config.SecurityConfigurationProperties.class)
@ComponentScan(
    basePackages = {"com.developer", "com.platform.common", "com.platform.cache", "com.platform.security.exception", "com.platform.security.filter", "com.platform.security.config"},
    basePackageClasses = {TargetResolverFactory.class, UserRoleServiceImpl.class, AesEncryptionService.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.repository\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.service\\.impl\\.(?!UserRoleServiceImpl|JwtTokenServiceImpl).*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.config\\.(?!JwtProperties).*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.controller\\..*")
    }
)
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableJpaRepositories(basePackages = {"com.developer.repository"})
@EntityScan(basePackages = {"com.developer.entity"})
public class DeveloperWorkstationApplication {
    
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(DeveloperWorkstationApplication.class, args);
    }
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(SecurityContextUtils.getCurrentUsername().orElse("system"));
    }
}
