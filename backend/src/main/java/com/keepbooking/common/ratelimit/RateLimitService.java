package com.keepbooking.common.ratelimit;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Fixed-window request counter backed by Redis. INCR + conditional PEXPIRE run atomically
 * in a single Lua script so concurrent requests can't race past the limit.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean tryConsume(String key, int limit, long windowMs) {
        Long current = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(windowMs));
        return current != null && current <= limit;
    }
}
