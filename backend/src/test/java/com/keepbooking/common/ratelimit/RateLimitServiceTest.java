package com.keepbooking.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * The actual atomicity guarantee (INCR + PEXPIRE in one Lua script) can't be verified by
 * a unit test — that's exactly why it's a script instead of two round-trips. This only
 * verifies the limit-comparison contract: tryConsume returns true while current <= limit.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void stubScriptResult(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(result);
    }

    @Test
    void tryConsumeReturnsTrueWhenCountIsWithinLimit() {
        stubScriptResult(1L);

        assertThat(rateLimitService.tryConsume("ratelimit:general:1.2.3.4", 10, 60_000)).isTrue();
    }

    @Test
    void tryConsumeReturnsTrueWhenCountEqualsLimit() {
        stubScriptResult(10L);

        assertThat(rateLimitService.tryConsume("ratelimit:general:1.2.3.4", 10, 60_000)).isTrue();
    }

    @Test
    void tryConsumeReturnsFalseWhenCountExceedsLimit() {
        stubScriptResult(11L);

        assertThat(rateLimitService.tryConsume("ratelimit:general:1.2.3.4", 10, 60_000)).isFalse();
    }

    @Test
    void tryConsumeFailsClosedWhenRedisReturnsNull() {
        stubScriptResult(null);

        assertThat(rateLimitService.tryConsume("ratelimit:general:1.2.3.4", 10, 60_000)).isFalse();
    }
}
