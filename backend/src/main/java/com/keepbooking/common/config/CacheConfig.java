package com.keepbooking.common.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

@Configuration
public class CacheConfig {

    // Map pins and search result pages go stale far faster than reference data (which keeps the
    // global 5 min TTL from application.yml) - tz2.txt §19 calls for a short TTL specifically for
    // map/search results. Restaurant cards (single-restaurant lookups) keep the global "medium"
    // TTL since they're explicitly invalidated on the mutations that matter (moderation, rating
    // recalculation) rather than relying on expiry alone.
    private static final Duration SHORT_CACHE_TTL = Duration.ofSeconds(30);

    @Bean
    public RedisCacheManagerBuilderCustomizer shortTtlCacheCustomizer() {
        RedisCacheConfiguration shortTtl = RedisCacheConfiguration.defaultCacheConfig().entryTtl(SHORT_CACHE_TTL);
        return builder -> builder
                .withCacheConfiguration("mapRestaurants", shortTtl)
                .withCacheConfiguration("restaurantSearch", shortTtl);
    }
}
