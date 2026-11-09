package com.portal.config;

import com.platform.security.util.ServiceTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Inbound {@code X-Service-Token} check for endpoints only platform services call. */
@Configuration
public class ServiceTokenConfig {

    @Bean
    public ServiceTokenVerifier serviceTokenVerifier(@Value("${service.internal-token}") String serviceToken) {
        return new ServiceTokenVerifier(serviceToken);
    }
}
