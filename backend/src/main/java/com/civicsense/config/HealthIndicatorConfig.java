package com.civicsense.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.civicsense.health.InfrastructureHealthService;

@Configuration
public class HealthIndicatorConfig {

    @Bean("postgis")
    HealthIndicator postgisHealthIndicator(InfrastructureHealthService service) {
        return () -> {
            try {
                return service.postgisWorks()
                        ? Health.up().withDetail("spatialQuery", "ST_Intersects OK").build()
                        : Health.down().withDetail("spatialQuery", "unexpected result").build();
            } catch (Exception ex) {
                return Health.down(ex).build();
            }
        };
    }

    @Bean("objectStorage")
    HealthIndicator objectStorageHealthIndicator(InfrastructureHealthService service) {
        return () -> {
            try {
                return service.objectStorageWorks()
                        ? Health.up().withDetail("check", "HeadBucket OK").build()
                        : Health.down().withDetail("check", "unexpected result").build();
            } catch (Exception ex) {
                return Health.down(ex).build();
            }
        };
    }
}
