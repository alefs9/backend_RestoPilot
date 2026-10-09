package com.restopilot.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {
    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of("America/Lima"));
    }

    @Bean
    public org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
