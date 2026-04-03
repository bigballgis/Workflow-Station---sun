package com.developer.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Test profile: stub {@link RedisConnectionFactory} so {@code platform-cache} {@code RedisConfig}
 * can create Redis templates without a real Redis server.
 */
@Configuration
@Profile("test")
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class, Mockito.RETURNS_DEEP_STUBS);
    }
}
