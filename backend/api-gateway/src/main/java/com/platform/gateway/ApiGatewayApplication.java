package com.platform.gateway;

import com.platform.security.config.JwtProperties;
import com.platform.security.service.impl.JwtTokenServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * API Gateway Application entry point.
 * Note: Excludes JPA components as Gateway uses WebFlux.
 * Only imports JWT token service from platform-security.
 */
@SpringBootApplication(
    scanBasePackages = {"com.platform.gateway", "com.platform.cache"},
    exclude = {
        DataSourceAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@Import({JwtProperties.class, JwtTokenServiceImpl.class})
public class ApiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
