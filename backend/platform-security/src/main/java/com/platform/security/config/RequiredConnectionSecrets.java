package com.platform.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fails startup, naming the variable, when the database or Redis password is not configured
 * (SPRING_DATASOURCE_PASSWORD / SPRING_REDIS_PASSWORD).
 *
 * <p>Spring Boot binds both through {@code @ConfigurationProperties}, which keeps an unresolvable
 * {@code ${SPRING_..._PASSWORD}} as literal text: Redis connects lazily and the database only fails
 * later with an authentication error that never mentions the variable. {@code @Value} resolution is
 * strict, so resolving the two keys here turns a missing variable into
 * "Could not resolve placeholder 'SPRING_..._PASSWORD'" at startup. An explicitly empty value (e.g. a
 * Redis without a password in tests) is a configured value and passes.
 */
@Component
public class RequiredConnectionSecrets {

    public RequiredConnectionSecrets(@Value("${spring.datasource.password}") String datasourcePassword,
                                     @Value("${spring.data.redis.password}") String redisPassword) {
        // Resolution itself is the check; the values are consumed by Spring Boot's own properties.
    }
}
