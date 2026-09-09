package com.sm.instagram.platform.auth.sandbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * On the public sandbox Alloy scrapes {@code /actuator/prometheus} from inside the compose network and ships
 * it to Grafana Cloud. The main chain requires ADMIN for everything under {@code /actuator/**} except health;
 * this chain, first in order and matching that one path, lets the scrape through. It is never reachable from
 * outside: the frontend's nginx answers 404 for every actuator path but health, and the backend port is not
 * published (docs/ci/SANDBOX.md §5 in the frontend repository).
 */
@Configuration
@ConditionalOnProperty(prefix = "checkitout.sandbox", name = "enabled", havingValue = "true")
public class SandboxActuatorSecurity {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain sandboxPrometheusChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/prometheus")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
