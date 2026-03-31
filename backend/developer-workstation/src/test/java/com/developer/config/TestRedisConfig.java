package com.developer.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Provides mock Redis beans for the test profile.
 * Prevents ApplicationContext startup failures when no Redis instance is available.
 */
@Configuration
@Profile("test")
public class TestRedisConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class, Mockito.RETURNS_DEEP_STUBS);
    }
}
