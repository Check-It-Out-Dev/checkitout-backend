package com.sm.instagram.platform.unit.security;

import com.sm.instagram.platform.common.security.geoip.GeoLocationCache;
import com.sm.instagram.platform.common.security.geoip.GeoLocationFacade;
import com.sm.instagram.platform.common.security.geoip.MaxMindDatabaseService;
import com.sm.instagram.platform.common.security.geoip.TravelPatternService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * A broad {@code catch (Exception e)} around something blocking takes InterruptedException with it,
 * and the interrupt flag goes too. The thread is then told nothing about having been asked to stop,
 * and carries on -- which surfaces as a shutdown that hangs rather than as anything that fails.
 *
 * <p>Eight sites were fixed to re-assert the flag (java:S2142). This proves the behaviour at the one
 * that can be reached without contorting anything: a GeoIP lookup waiting on a future that has not
 * completed, on a thread that has been interrupted. That is exactly the shape of all eight.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterruptRestorationUnitTest {

    private static final String IP = "203.0.113.7";

    @Mock
    private MaxMindDatabaseService databaseService;

    @Mock
    private GeoLocationCache cache;

    @Mock
    private TravelPatternService travelPatternService;

    @AfterEach
    void clearInterruptForTheNextTest() {
        // Reading it clears it. Leaving a set flag behind would make an unrelated test fail later, in
        // a way that would take a while to trace back to here.
        Thread.interrupted();
    }

    @Test
    @DisplayName("an interrupted lookup leaves the thread knowing it was interrupted")
    void restoresTheFlag() {
        GeoLocationFacade facade = new GeoLocationFacade(databaseService, cache, travelPatternService);
        // Never completes, so the get() below has to wait -- and a wait is what notices an interrupt.
        when(cache.getAsync(anyString())).thenReturn(new CompletableFuture<>());

        Thread.currentThread().interrupt();
        var location = facade.getLocation(IP, 5_000);

        assertThat(Thread.currentThread().isInterrupted())
                .as("the flag the catch block used to swallow")
                .isTrue();
        assertThat(location).as("the caller still gets an answer, as before").isNotNull();
    }

    @Test
    @DisplayName("an ordinary timeout is not an interrupt, and must not be reported as one")
    void aTimeoutDoesNotSetTheFlag() {
        GeoLocationFacade facade = new GeoLocationFacade(databaseService, cache, travelPatternService);
        when(cache.getAsync(anyString())).thenReturn(new CompletableFuture<>());

        // Not interrupted; the future simply does not finish inside the budget.
        var location = facade.getLocation(IP, 50);

        assertThat(Thread.currentThread().isInterrupted())
                .as("restoring the flag must be specific to InterruptedException")
                .isFalse();
        assertThat(location).isNotNull();
    }
}
