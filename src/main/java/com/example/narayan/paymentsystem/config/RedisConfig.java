package com.example.narayan.paymentsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public JedisPool jedisPool() {
        System.out.println("🔗 Creating JedisPool for Redis at " + redisHost + ":" + redisPort);
        return new JedisPool(new JedisPoolConfig(), redisHost, redisPort);
    }
}