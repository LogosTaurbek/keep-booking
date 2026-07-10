package com.keepbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.keepbooking.common.config.AppProperties;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableCaching
@EnableConfigurationProperties(AppProperties.class)
public class KeepBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeepBookingApplication.class, args);
    }
}
