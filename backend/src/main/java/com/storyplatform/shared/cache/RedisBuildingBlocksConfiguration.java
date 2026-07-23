package com.storyplatform.shared.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisBuildingBlocksProperties.class)
public class RedisBuildingBlocksConfiguration {

    @Bean
    RedisKeyFactory redisKeyFactory(
            RedisBuildingBlocksProperties properties
    ) {
        return new RedisKeyFactory(
                properties.applicationPrefix(),
                properties.keyVersion(),
                properties.environment()
        );
    }

    @Bean
    RedisTtlPolicy redisTtlPolicy(
            RedisBuildingBlocksProperties properties
    ) {
        return new RedisTtlPolicy(
                properties.minimumCacheTtl(),
                properties.maximumCacheTtl(),
                properties.ttlJitterRatio()
        );
    }

    @Bean
    RedisValueStore redisValueStore(StringRedisTemplate redis) {
        return new SpringRedisValueStore(redis);
    }

    @Bean
    RedisValueCodec redisValueCodec(ObjectMapper objectMapper) {
        return new RedisValueCodec(objectMapper);
    }

    @Bean
    RedisCacheTelemetry redisCacheTelemetry(MeterRegistry meterRegistry) {
        return new RedisCacheTelemetry(meterRegistry);
    }

    @Bean
    ResilientRedisCache resilientRedisCache(
            RedisValueStore store,
            RedisValueCodec codec,
            RedisTtlPolicy ttlPolicy,
            RedisCacheTelemetry telemetry
    ) {
        return new ResilientRedisCache(
                store,
                codec,
                ttlPolicy,
                telemetry
        );
    }
}
