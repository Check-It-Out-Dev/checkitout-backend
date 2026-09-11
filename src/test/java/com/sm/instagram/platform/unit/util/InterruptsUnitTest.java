package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.Interrupts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The re-interrupt contract used to be twenty copies of an if-statement inside twenty broad catch
 * blocks, none of them exercised by a test. The question is one method now, the answer is acted on
 * at the catch block, and these are the cases that matter.
 */
@DisplayName("Interrupts Unit Tests")
class InterruptsUnitTest {

    @BeforeEach
    @AfterEach
    void clearInterruptFlag() {
        // Thread.interrupted() reads AND clears, so no case can leak its flag into the next one --
        // nor out of this class into the rest of the suite, which shares the JUnit thread.
        Thread.interrupted();
    }

    @Test
    @DisplayName("says yes to an InterruptedException")
    void recognisesInterruptedException() {
        assertThat(Interrupts.isInterrupt(new InterruptedException("shutting down"))).isTrue();
    }

    @Test
    @DisplayName("says yes to an InterruptedIOException, the other way the JDK spells it")
    void recognisesInterruptedIoException() {
        assertThat(Interrupts.isInterrupt(new InterruptedIOException("socket read"))).isTrue();
    }

    @Test
    @DisplayName("says no to an ordinary failure")
    void rejectsOrdinaryFailure() {
        assertThat(Interrupts.isInterrupt(new IOException("connection reset"))).isFalse();
        assertThat(Interrupts.isInterrupt(new IllegalStateException("bad state"))).isFalse();
        assertThat(Interrupts.isInterrupt(new TimeoutException("30s"))).isFalse();
    }

    @Test
    @DisplayName("says no when another thread's interrupt arrives wrapped")
    void rejectsWrappedInterrupt() {
        // future.get() reports a worker's interrupt as ExecutionException. That thread was
        // interrupted; this one was not. Setting this thread's flag would abort unrelated work
        // later, at a point with no connection to the failure that caused it.
        ExecutionException wrapped = new ExecutionException(new InterruptedException("worker"));

        assertThat(Interrupts.isInterrupt(wrapped)).isFalse();
    }

    @Test
    @DisplayName("tolerates null")
    void toleratesNull() {
        assertThatCode(() -> assertThat(Interrupts.isInterrupt(null)).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the call-site idiom restores the flag, and only for a real interrupt")
    void callSiteIdiomRestoresTheFlag() {
        // The shape every one of the twenty catch blocks now has, exercised once here so that the
        // two halves of the contract -- the predicate and the act -- are tested together.
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        handleAsCatchBlocksDo(new IOException("connection reset"));
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        handleAsCatchBlocksDo(new InterruptedException("shutting down"));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private void handleAsCatchBlocksDo(Exception e) {
        if (Interrupts.isInterrupt(e)) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("is a utility class: final, with one private constructor")
    void isAUtilityClass() throws Exception {
        assertThat(Modifier.isFinal(Interrupts.class.getModifiers())).isTrue();

        Constructor<?>[] constructors = Interrupts.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(Modifier.isPrivate(constructors[0].getModifiers())).isTrue();

        constructors[0].setAccessible(true);
        assertThatCode(constructors[0]::newInstance).doesNotThrowAnyException();
    }
}
