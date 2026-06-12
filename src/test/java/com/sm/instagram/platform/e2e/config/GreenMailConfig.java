package com.sm.instagram.platform.e2e.config;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * GreenMail in-memory SMTP server for E2E email testing.
 *
 * <p>Starts an SMTP server on port 3025 that captures all outgoing emails.
 * Tests can then retrieve and assert on captured messages (subject, body, recipients).
 *
 * <p>Only active in the {@code e2e} profile. The application's {@code spring.mail}
 * properties are overridden in {@code application-e2e.yml} to point to this server.
 */
@Slf4j
@Configuration
@Profile("e2e")
public class GreenMailConfig {

    @Getter
    private GreenMail greenMail;

    @Bean
    public GreenMail greenMail() {
        ServerSetup smtp = new ServerSetup(3025, "localhost", ServerSetup.PROTOCOL_SMTP);
        smtp.setServerStartupTimeout(5000);

        greenMail = new GreenMail(smtp);
        greenMail.withConfiguration(
                GreenMailConfiguration.aConfig()
                        .withUser("test", "test")
        );
        greenMail.start();

        log.info("[E2E] GreenMail SMTP started on localhost:3025");
        return greenMail;
    }

    @PreDestroy
    public void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
            log.info("[E2E] GreenMail SMTP stopped");
        }
    }
}
