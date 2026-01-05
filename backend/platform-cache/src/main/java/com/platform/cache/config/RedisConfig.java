package com.platform.cache.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis configuration for cache service.
 */
@Configuration
public class RedisConfig {
    
    private static final String INVALIDATION_CHANNEL = "cache:invalidation";
    
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(INVALIDATION_CHANNEL));
        return container;
    }
    
    @Bean
    public MessageListenerAdapter listenerAdapter(CacheInvalidationListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }
    
    @Bean
    public CacheInvalidationListener cacheInvalidationListener(StringRedisTemplate redisTemplate) {
        return new CacheInvalidationListener(redisTemplate);
    }
    
    /**
     * Listener for cache invalidation messages.
     */
    public static class CacheInvalidationListener {
        
        private final StringRedisTemplate redisTemplate;
        
        public CacheInvalidationListener(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }
        
        public void onMessage(String message, String channel) {
            // Handle cache invalidation by pattern
            if (message != null && !message.isBlank()) {
                var keys = redisTemplate.keys(message);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            }
        }
    }
}
