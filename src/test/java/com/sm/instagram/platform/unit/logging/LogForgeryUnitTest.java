package com.sm.instagram.platform.unit.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * The production appender writes structured ECS rather than a {@code %msg%n} pattern, and this is
 * the reason why.
 *
 * <p>A pattern encoder writes the message verbatim. Any value that reaches a log call carrying a
 * newline therefore ends the record early and starts a new one, and the forged line is
 * indistinguishable from a real one — same file, same shape, no marker saying it was invented.
 * CodeQL counts 500 sinks in this codebase where a request-derived value is logged, which is the
 * scale that makes sanitising every call site the wrong answer: one missed site is the whole hole.
 *
 * <p>An encoder that emits JSON cannot produce a raw newline inside a value, so the attack stops
 * being possible rather than becoming unlikely. These tests hold that property still.
 */
class LogForgeryUnitTest {

  /** Encode one event exactly as the production appender would. */
  private String encode(String message) {
    LoggerContext context = new LoggerContext();
    // A context built by hand has no MDC adapter; the one the application runs with does. Set one
    // rather than borrowing the global context, so this test cannot disturb anything else.
    context.setMDCAdapter(new LogbackMDCAdapter());
    // The encoder reads its service name and version from the Spring Environment, which the boot
    // logging system puts into the logger context under this key. Supplying one here is what makes
    // the test encode exactly what production encodes.
    context.putObject(Environment.class.getName(), new MockEnvironment());
    context.start();
    StructuredLogEncoder encoder = new StructuredLogEncoder();
    encoder.setFormat("ecs");
    encoder.setContext(context);
    encoder.start();

    // Built through a real logger from the context, so the event carries the context the encoder
    // needs (MDC adapter, logger name, level) exactly as one produced by a log call would.
    Logger logger = context.getLogger("com.sm.instagram.platform.Forgery");
    LoggingEvent event = new LoggingEvent(Logger.FQCN, logger, Level.INFO, message, null, null);

    byte[] out = encoder.encode(event);
    encoder.stop();
    context.stop();
    return new String(out, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("a newline in a logged value cannot start a second record")
  void newlineCannotForgeARecord() {
    // The shape of a real line an attacker would want to invent.
    String hostile = "user logged out\nERROR [security] admin session granted to attacker";

    String encoded = encode(hostile);

    // One record: exactly one line, whatever the message contained.
    assertThat(encoded.strip().lines()).hasSize(1);
    // The newline survives as data, escaped, rather than as structure.
    assertThat(encoded).contains("\\n");
    assertThat(encoded.strip()).doesNotContain("\n");
    // And the forged text is still there to be read — the point is that it is a value, not a record.
    assertThat(encoded).contains("admin session granted to attacker");
  }

  @Test
  @DisplayName("a carriage return cannot start a second record either")
  void carriageReturnCannotForgeARecord() {
    String encoded = encode("ok\r\nERROR [security] forged");

    assertThat(encoded.strip().lines()).hasSize(1);
    assertThat(encoded.strip()).doesNotContain("\r");
  }

  @Test
  @DisplayName("a value that looks like JSON cannot break out of its field")
  void jsonInAValueStaysInsideItsField() {
    // If the message were concatenated rather than encoded, this would add a top-level field.
    String encoded = encode("plain\",\"log.level\":\"ERROR\",\"forged\":\"yes");

    assertThat(encoded.strip().lines()).hasSize(1);
    // The quotes are escaped, so the injected key never becomes a key.
    assertThat(encoded).contains("\\\"");
    // ECS writes the real level as log.level; the forged one must not have replaced it.
    assertThat(encoded).contains("\"log.level\":\"INFO\"");
  }
}
