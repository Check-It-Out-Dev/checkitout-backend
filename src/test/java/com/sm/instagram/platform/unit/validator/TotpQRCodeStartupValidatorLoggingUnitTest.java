package com.sm.instagram.platform.unit.validator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sm.instagram.platform.auth.service.ImprovedQRCodeService;
import com.sm.instagram.platform.auth.service.QRCodeGeneratorService;
import com.sm.instagram.platform.auth.validator.TotpQRCodeStartupValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * The startup validator prints eight results at INFO on every boot. None of them may carry a seed.
 *
 * <p>It used to log the generated {@code otpauth://} URL three times, and CodeQL reported two of
 * those as {@code java/sensitive-log}. The seed it runs on is a throwaway one generated for the
 * check, so nothing real was disclosed - but a validator is the most-copied code in a codebase,
 * and this one was teaching every reader that printing an enrolment URL is normal. Two of the three
 * were redundant with the result the method already returns and are gone; the third survives only
 * as a redacted string inside a result, because a format mismatch cannot be diagnosed without the
 * shape of the URL that failed.
 *
 * <p>So the invariant is not "the URL never appears" - it is "no seed ever appears, and every
 * {@code secret=} that reaches the log reads REDACTED". That is what these tests assert, against
 * the appender rather than against a return value, because the difference is invisible anywhere
 * else.
 */
@DisplayName("TotpQRCodeStartupValidator - the startup summary carries no seed")
class TotpQRCodeStartupValidatorLoggingUnitTest {

    /** 32 Base32 characters, the length the validator expects of a real seed. */
    private static final String SEED = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final String VALID_URL =
            "otpauth://totp/CheckItOut:validator@test.com?secret=" + SEED
                    + "&issuer=CheckItOut&algorithm=SHA1&digits=6&period=30";

    /** Every `secret=` in the log, with whatever value followed it. */
    private static final Pattern SECRET_PARAM = Pattern.compile("secret=([^&\\s,)]*)");

    private ImprovedQRCodeService improvedQRCodeService;
    private TotpQRCodeStartupValidator validator;
    private ch.qos.logback.classic.Logger validatorLogger;
    private ListAppender<ILoggingEvent> logs;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        improvedQRCodeService = mock(ImprovedQRCodeService.class);
        QRCodeGeneratorService qrCodeGeneratorService = mock(QRCodeGeneratorService.class);
        lenient().when(improvedQRCodeService.generateSecret()).thenReturn(SEED);
        lenient().when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString())).thenReturn(VALID_URL);

        validator = new TotpQRCodeStartupValidator(improvedQRCodeService, qrCodeGeneratorService);
        ReflectionTestUtils.setField(validator, "totpEnabled", true);
        ReflectionTestUtils.setField(validator, "issuer", "CheckItOut");

        validatorLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TotpQRCodeStartupValidator.class);
        previousLevel = validatorLogger.getLevel();
        // DEBUG deliberately: the two lines that leaked were DEBUG lines, so a test that ran at INFO
        // would have passed on the broken code too.
        validatorLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        validatorLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        validatorLogger.detachAppender(logs);
        validatorLogger.setLevel(previousLevel);
    }

    private String logged() {
        return logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("a well-formed URL validates, and the seed does not reach the log")
    void seedStaysOutOfTheStartupSummary() {
        validator.validateOnStartup();

        String written = logged();
        assertThat(written)
                .as("the summary always prints; silence would pass every assertion below for free")
                .contains("VALIDATION SUMMARY")
                .contains("OTP Auth URL Format");
        assertThat(written).as("the seed").doesNotContain(SEED);
    }

    @Test
    @DisplayName("every secret= that reaches the log reads REDACTED")
    void everySecretParameterIsRedacted() {
        validator.validateOnStartup();

        Matcher m = SECRET_PARAM.matcher(logged());
        int seen = 0;
        while (m.find()) {
            seen++;
            assertThat(m.group(1)).as("secret= occurrence %d", seen).isEqualTo("REDACTED");
        }
        assertThat(seen)
                .as("the URL is quoted into the success result, so at least one occurrence is expected")
                .isPositive();
    }

    @Test
    @DisplayName("a malformed URL takes the warning path, and that carries no seed either")
    void seedStaysOutOfTheMismatchWarning() {
        // No `otpauth://totp/` prefix in the parameter order the pattern wants, so OTPAUTH_PATTERN
        // fails and the method returns the branch that quotes the URL back.
        lenient().when(improvedQRCodeService.generateOtpAuthUrl(anyString(), anyString()))
                .thenReturn("otpauth://totp/CheckItOut:validator@test.com?issuer=CheckItOut&secret=" + SEED);

        validator.validateOnStartup();

        String written = logged();
        assertThat(written).as("the seed").doesNotContain(SEED);
        assertThat(written).contains("OTP Auth URL Format");
    }

    @Test
    @DisplayName("with TOTP disabled nothing is validated and nothing is printed")
    void disabledValidatorPrintsNothing() {
        ReflectionTestUtils.setField(validator, "totpEnabled", false);

        validator.validateOnStartup();

        assertThat(logged()).doesNotContain("VALIDATION SUMMARY").doesNotContain(SEED);
    }
}
