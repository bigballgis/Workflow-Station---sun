package com.admin.config;

import com.platform.security.util.SecurityContextUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(
                SecurityContextUtils.getCurrentUsername().orElse("system")
        );
    }
}
