package com.keepbooking.common.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

@Configuration
public class CacheConfig {

    // Map pins carry a live "free tables now" flag, so they go stale far faster than reference
    // data (which keeps the global 5 min TTL from application.yml) - tz2.txt §19 calls for a
    // short TTL specifically for map/search results.
    private static final Duration MAP_CACHE_TTL = Duration.ofSeconds(30);

    @Bean
    public RedisCacheManagerBuilderCustomizer mapCacheTtlCustomizer() {
        return builder -> builder.withCacheConfiguration("mapRestaurants",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(MAP_CACHE_TTL));
    }
}
