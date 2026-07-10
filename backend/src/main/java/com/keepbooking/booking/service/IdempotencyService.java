package com.keepbooking.booking.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepbooking.booking.dto.BookingDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-based fast path for Idempotency-Key request handling.
 * Not the final guarantee — {@code bookings.idempotency_key} is UNIQUE in the DB,
 * so a cache miss (e.g. Redis restart) still can't create duplicate bookings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:booking:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<BookingDto> get(Long userId, String idempotencyKey) {
        String raw = redisTemplate.opsForValue().get(redisKey(userId, idempotencyKey));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, BookingDto.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached idempotent response", e);
            return Optional.empty();
        }
    }

    public void put(Long userId, String idempotencyKey, BookingDto dto) {
        try {
            redisTemplate.opsForValue().set(redisKey(userId, idempotencyKey),
                    objectMapper.writeValueAsString(dto), TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache idempotent response", e);
        }
    }

    private String redisKey(Long userId, String idempotencyKey) {
        return KEY_PREFIX + userId + ":" + idempotencyKey;
    }
}
