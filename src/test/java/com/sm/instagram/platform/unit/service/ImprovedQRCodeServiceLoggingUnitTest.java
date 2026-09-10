package com.sm.instagram.platform.unit.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sm.instagram.platform.auth.service.ImprovedQRCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The enrolment path must not write a user's TOTP seed to the log.
 *
 * <p>It used to. {@code generateOtpAuthUrl} built
 * {@code otpauth://totp/CheckItOut:user@example.com?secret=SEED&...} and logged the whole thing at
 * DEBUG, which is a complete, non-expiring second factor and the address of the account it belongs
 * to, sitting in whatever collects the logs. CodeQL reported it as {@code java/sensitive-log}.
 *
 * <p>Asserting on the logger rather than on the method's return value is the point: the URL still
 * has to contain the seed, because that is what the user's authenticator app scans. What changed is
 * only what gets written down, and nothing but reading the appender can tell the two apart.
 */
@DisplayName("ImprovedQRCodeService — the seed never reaches the log")
class ImprovedQRCodeServiceLoggingUnitTest {

    private static final String SEED = "JBSWY3DPEHPK3PXP";
    private static final String EMAIL = "enrolling.user@example.com";

    private ImprovedQRCodeService service;
    private ch.qos.logback.classic.Logger serviceLogger;
    private ListAppender<ILoggingEvent> logs;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        service = new ImprovedQRCodeService();
        ReflectionTestUtils.setField(service, "issuer", "CheckItOut");
        ReflectionTestUtils.setField(service, "digits", 6);
        ReflectionTestUtils.setField(service, "period", 30);

        serviceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ImprovedQRCodeService.class);
        previousLevel = serviceLogger.getLevel();
        // DEBUG deliberately: the leak was at DEBUG, so a test that ran at INFO would pass on the
        // broken code as well.
        serviceLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
        serviceLogger.setLevel(previousLevel);
    }

    private String logged() {
        return logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("the returned URL carries the seed; the log carries neither it nor the email")
    void seedIsInTheUrlAndNotInTheLog() {
        String url = service.generateOtpAuthUrl(SEED, EMAIL);

        assertThat(url)
                .as("the authenticator app scans this, so the seed has to be in it")
                .contains("secret=" + SEED);

        String written = logged();
        assertThat(written).as("a line was still written; silence would pass this test for free").isNotEmpty();
        assertThat(written).as("the second factor").doesNotContain(SEED);
        assertThat(written).as("whose second factor").doesNotContain(EMAIL);
        assertThat(written).doesNotContain("secret=");
    }

    @Test
    @DisplayName("what is logged instead is the shape, which is what debugging actually needs")
    void logsTheShapeInstead() {
        service.generateOtpAuthUrl(SEED, EMAIL);

        assertThat(logged())
                .contains("issuer=CheckItOut")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    @DisplayName("a longer seed does not slip through on length")
    void longerSeedAlsoStaysOut() {
        String longSeed = "MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43UOV3HO7DMNRXXG5DBOJ5A";

        service.generateOtpAuthUrl(longSeed, EMAIL);

        assertThat(logged()).doesNotContain(longSeed);
    }
}
