package com.ismile.core.otp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuration class for Redis integration.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a StringRedisTemplate bean, which is a convenient high-level abstraction
     * for Redis interactions that focuses on the common case of String data.
     *
     * @param connectionFactory The Redis connection factory provided by Spring Boot auto-configuration.
     * @return A configured instance of StringRedisTemplate.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
