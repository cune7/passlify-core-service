package com.passlify.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} tasks (reservation expiry sweep) and {@code @Async} (email delivery). */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
