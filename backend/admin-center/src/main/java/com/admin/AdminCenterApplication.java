package com.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.platform.security.resolver.TargetResolverFactory;
import com.platform.security.service.impl.UserRoleServiceImpl;

/**
 * 管理员中心应用程序入口
 * 低代码工作流平台核心管理模块
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.admin"},
    basePackageClasses = {TargetResolverFactory.class, UserRoleServiceImpl.class},
    excludeFilters = {
        // 排除platform-security中的repository，由单独的配置类处理
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.repository\\..*"),
        // 排除platform-security中不需要的服务（它们依赖platform-security自己的repository和config）
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.service\\.impl\\.(?!UserRoleServiceImpl).*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.config\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.controller\\..*")
    }
)
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {"com.admin.repository"})
@EntityScan(basePackages = {"com.admin.entity", "com.platform.security.entity"})
@EnableCaching
@EnableAsync
@EnableScheduling
public class AdminCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminCenterApplication.class, args);
    }
}
