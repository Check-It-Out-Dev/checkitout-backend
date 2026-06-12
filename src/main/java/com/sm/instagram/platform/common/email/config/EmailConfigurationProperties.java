package com.sm.instagram.platform.common.email.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.email")
public class EmailConfigurationProperties {

    @NotBlank
    @Email
    private String adminEmail;

    @NotBlank
    @Email
    private String fromEmail;

    private boolean enabled = true;

    private final Validation validation = new Validation();

    @Data
    public static class Validation {
        private boolean enabled = true;
        private boolean failFast = false;  // User preference: Degraded mode
        private boolean sendTestEmail = true;  // User preference: Send on every startup
        private int timeoutMs = 5000;
    }
}
