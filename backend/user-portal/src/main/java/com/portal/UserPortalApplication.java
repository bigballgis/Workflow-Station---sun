package com.portal;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.platform.security.resolver.TargetResolverFactory;
import com.platform.security.service.impl.UserRoleServiceImpl;

/**
 * 用户门户应用程序入口
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.portal", "com.platform.common", "com.platform.security.exception", "com.platform.messaging", "com.platform.security.filter", "com.platform.security.config"},
    basePackageClasses = {TargetResolverFactory.class, UserRoleServiceImpl.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.repository\\..*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.service\\.impl\\.(?!UserRoleServiceImpl|JwtTokenServiceImpl).*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.config\\.(?!JwtProperties).*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.platform\\.security\\.controller\\..*")
    }
)
@EnableJpaRepositories(basePackages = {"com.portal.repository"})
@EntityScan(basePackages = {"com.portal.entity"})
public class UserPortalApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(UserPortalApplication.class, args);
    }
}
