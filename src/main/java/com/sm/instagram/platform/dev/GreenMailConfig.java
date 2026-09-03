package com.sm.instagram.platform.dev;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * GreenMail in-memory SMTP server.
 *
 * <p>Boots a process-local SMTP listener (port 3025 by default, overridable
 * with {@code greenmail.smtp.port} so two local stacks can coexist) that
 * captures every outgoing email the application produces. Tests (Cucumber E2E + FE
 * Playwright integration) retrieve and assert on captured messages by
 * subject, body, recipient. Production code sees an ordinary
 * {@code spring.mail} target — it does not know GreenMail exists.
 *
 * <p>Active in:
 * <ul>
 *   <li><b>{@code e2e}</b> — the BE Cucumber E2E suite. Captures auto-startup
 *       behavior validated by {@code Cucumber*EmailFlow*Test} fixtures.</li>
 *   <li><b>{@code dev}</b> — local development server for FE Playwright
 *       integration tests. Step-up email-code flows, password-reset codes,
 *       and verification links can be exercised end-to-end without
 *       standing up an external SMTP or burning real provider credits.
 *       The greenfield FE points {@code spring.mail.*} at this server via
 *       {@code application-dev.yml}.</li>
 * </ul>
 *
 * <p>Not loaded in {@code prod} / {@code prod-standalone} / {@code test} —
 * the exclusions are spelled out in the expression below rather than implied
 * by the absence of those names, so a combined activation such as
 * {@code prod,dev} cannot bind an in-memory SMTP server in production.
 * The JAR ships
 * in the prod artifact (~1.6 MB of GreenMail + JavaMail), but no thread
 * starts and no port is bound when the profile doesn't match.
 *
 * <p>Originally lived in {@code src/test/java} as the e2e-only fixture
 * (moved to {@code src/main} on 2026-05-12 to unblock FE integration
 * tests on the dev profile — see task #215).
 */
@Slf4j
@Configuration
@Profile("(e2e | dev) & !prod & !prod-standalone & !test")
public class GreenMailConfig {

    @Getter
    private GreenMail greenMail;

    /** Matches spring.mail.port; overridable so a second local stack can bind elsewhere. */
    @Value("${greenmail.smtp.port:${spring.mail.port:3025}}")
    private int smtpPort;

    @Bean
    public GreenMail greenMail() {
        ServerSetup smtp = new ServerSetup(smtpPort, "localhost", ServerSetup.PROTOCOL_SMTP);
        smtp.setServerStartupTimeout(5000);

        greenMail = new GreenMail(smtp);
        greenMail.withConfiguration(
                GreenMailConfiguration.aConfig()
                        .withUser("test", "test")
        );
        greenMail.start();

        log.info("[dev/e2e] GreenMail SMTP started on localhost:{}", smtpPort);
        return greenMail;
    }

    @PreDestroy
    public void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
            log.info("[dev/e2e] GreenMail SMTP stopped");
        }
    }
}
