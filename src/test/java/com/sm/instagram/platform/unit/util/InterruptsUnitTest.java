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
 * The re-interrupt contract used to be six copies of an if-statement inside six broad catch blocks,
 * none of them exercised by a test. It is one method now, and these are the cases that matter.
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
    @DisplayName("restores the flag for an InterruptedException")
    void restoresFlagForInterruptedException() {
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        boolean restored = Interrupts.preserveInterrupt(new InterruptedException("shutting down"));

        assertThat(restored).isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("restores the flag for an InterruptedIOException, the other way the JDK spells it")
    void restoresFlagForInterruptedIoException() {
        boolean restored = Interrupts.preserveInterrupt(new InterruptedIOException("socket read"));

        assertThat(restored).isTrue();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("leaves the flag alone for an ordinary failure")
    void leavesFlagAloneForOrdinaryFailure() {
        assertThat(Interrupts.preserveInterrupt(new IOException("connection reset"))).isFalse();
        assertThat(Interrupts.preserveInterrupt(new IllegalStateException("bad state"))).isFalse();
        assertThat(Interrupts.preserveInterrupt(new TimeoutException("30s"))).isFalse();

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("does NOT restore the flag when another thread's interrupt arrives wrapped")
    void doesNotRestoreFlagForWrappedInterrupt() {
        // future.get() reports a worker's interrupt as ExecutionException. That thread was
        // interrupted; this one was not. Setting this thread's flag would abort unrelated work
        // later, at a point with no connection to the failure that caused it.
        ExecutionException wrapped = new ExecutionException(new InterruptedException("worker"));

        assertThat(Interrupts.preserveInterrupt(wrapped)).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("tolerates null")
    void toleratesNull() {
        assertThatCode(() -> assertThat(Interrupts.preserveInterrupt(null)).isFalse())
                .doesNotThrowAnyException();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
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
