package com.sm.instagram.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private List<String> allowedOrigins = List.of();
    /**
     * Origin patterns (wildcards allowed, e.g. {@code https://localhost:*}),
     * checked in addition to the exact list above.
     *
     * <p>Empty everywhere except the {@code dev-lite} simulator, where the
     * frontend port is whatever the wizard settled on: it moves services to
     * spare ports on a clash and lets you pin your own, so an enumerated list
     * turns a routine port change into a 403 "Invalid CORS request" at sign-in.
     * Production keeps exact origins — a pattern there would widen the trust
     * boundary rather than describe a developer's own machine.
     */
    private List<String> allowedOriginPatterns = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
    private List<String> allowedHeaders = List.of("*");
    private boolean allowCredentials = true;
    private long maxAge = 86400L;
}
