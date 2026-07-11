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
    private Storage storage = new Storage();
    private RateLimit rateLimit = new RateLimit();
    private Firebase firebase = new Firebase();

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

    @Data
    public static class Storage {
        /** Endpoint used by the backend to talk to the S3-compatible service (e.g. MinIO container on the docker network). */
        private String endpoint;
        /** Base URL used to build publicly-reachable object links (may differ from {@code endpoint}, e.g. localhost vs container name). */
        private String publicUrl;
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        /** General limit applied to all endpoints. */
        private int generalLimit = 100;
        private long generalWindowMs = 60_000;
        /** Stricter limit for /api/v1/auth/** to slow down brute-force/credential-stuffing attempts. */
        private int authLimit = 10;
        private long authWindowMs = 60_000;
    }

    @Data
    public static class Firebase {
        /** Off by default — most environments (local dev, CI) don't have a service account key. */
        private boolean enabled = false;
        private String credentialsPath;
    }
}
