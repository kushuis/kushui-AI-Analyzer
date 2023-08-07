package com.kushui.springbootinit.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Data
@ConfigurationProperties(prefix = "spring.redis")
@Configuration
public class RedissonConfig {
    private Integer database;
    private String host;
    private Integer port;
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setDatabase(database)
                .setAddress("redis://" + host + ":" + port)
                .setPassword(password);
        RedissonClient redisson = Redisson.create();
        return redisson;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
//        config.setPassword(RedisPassword.of(password));
        config.setDatabase(database);
        return new LettuceConnectionFactory(config);
    }
    @Bean
    public StringRedisTemplate  redisTemplate() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        return redisTemplate;
    }


}