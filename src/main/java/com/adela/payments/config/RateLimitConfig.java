package com.adela.payments.config;

import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;

public class RateLimitConfig {

    public static BucketConfiguration defaultConfig() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(60).refillGreedy(60, Duration.ofMinutes(1)))
                .build();
    }
}
