package com.keepbooking.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keepbooking.booking.dto.BookingDto;
import com.keepbooking.booking.model.BookingStatus;

/**
 * This is the fast-path cache only — the actual duplicate-prevention guarantee is the
 * UNIQUE constraint on bookings.idempotency_key in the DB, so a cache miss/corruption
 * here must degrade gracefully (empty/no-op), never throw.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    private static final Long USER_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "key-abc-123";

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private BookingDto bookingDto() {
        return BookingDto.builder().id(1L).status(BookingStatus.PENDING)
                .bookingDate(LocalDate.now()).timeFrom(LocalTime.of(19, 0)).timeTo(LocalTime.of(21, 0)).build();
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:booking:" + USER_ID + ":" + IDEMPOTENCY_KEY)).thenReturn(null);

        assertThat(idempotencyService.get(USER_ID, IDEMPOTENCY_KEY)).isEmpty();
    }

    @Test
    void getReturnsDeserializedDtoOnCacheHit() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        idempotencyService = new IdempotencyService(redisTemplate, mapper);
        BookingDto dto = bookingDto();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:booking:" + USER_ID + ":" + IDEMPOTENCY_KEY))
                .thenReturn(mapper.writeValueAsString(dto));

        var result = idempotencyService.get(USER_ID, IDEMPOTENCY_KEY);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void getReturnsEmptyWhenCachedValueIsCorruptedJson() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:booking:" + USER_ID + ":" + IDEMPOTENCY_KEY))
                .thenReturn("{not valid json");

        assertThat(idempotencyService.get(USER_ID, IDEMPOTENCY_KEY)).isEmpty();
    }

    @Test
    void putStoresSerializedDtoWithTwentyFourHourTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyService.put(USER_ID, IDEMPOTENCY_KEY, bookingDto());

        verify(valueOperations).set(eq("idempotency:booking:" + USER_ID + ":" + IDEMPOTENCY_KEY),
                any(String.class), eq(Duration.ofHours(24)));
    }

    @Test
    void putDoesNotThrowWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        idempotencyService = new IdempotencyService(redisTemplate, failingMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonGenerationException("boom", (com.fasterxml.jackson.core.JsonGenerator) null));

        idempotencyService.put(USER_ID, IDEMPOTENCY_KEY, bookingDto());

        verify(valueOperations, org.mockito.Mockito.never()).set(any(), any(), any(Duration.class));
    }
}
