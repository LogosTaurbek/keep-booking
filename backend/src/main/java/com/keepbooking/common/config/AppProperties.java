package com.keepbooking.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Tokens tokens = new Tokens();
    private Booking booking = new Booking();

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpirationMs;
        private long refreshTokenExpirationMs;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("*");
    }

    @Data
    public static class Tokens {
        private long emailVerificationExpirationMs;
        private long passwordResetExpirationMs;
    }

    @Data
    public static class Booking {
        private long pendingTimeoutMs;
    }
}
