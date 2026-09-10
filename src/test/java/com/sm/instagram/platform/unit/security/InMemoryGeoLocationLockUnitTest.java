package com.sm.instagram.platform.unit.security;

import com.sm.instagram.platform.common.security.geoip.InMemoryGeoLocationCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory side of the {@code GeoLocationCache} lock, which guards the MaxMind database update
 * so two instances do not download and rewrite it at once.
 *
 * <p>It used to be a {@link java.util.concurrent.locks.ReentrantLock} and had two defects that only
 * showed up as behaviour, never as a failure. The TTL "safety net" scheduled its unlock on an
 * executor thread and asked {@code isHeldByCurrentThread()} there, which is false by construction,
 * so it could not fire once. And the TTL was handed to {@code tryLock(timeout, unit)} as an
 * acquisition timeout, so the second instance did not take the caller's "another instance is
 * updating" branch -- it sat and waited ten minutes for a lock it was supposed to decline.
 *
 * <p>What it is now is what the Redis implementation of the same interface has always been:
 * {@code SET key NX EX ttl}. These four tests are that contract.
 */
class InMemoryGeoLocationLockUnitTest {

    private static final String KEY = "database_update";

    private InMemoryGeoLocationCache cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryGeoLocationCache();
    }

    @Test
    @DisplayName("a second caller is declined rather than made to wait")
    void secondCallerIsDeclinedImmediately() throws Exception {
        assertThat(cache.tryLock(KEY, 600)).isTrue();

        long startedAt = System.nanoTime();
        assertThat(cache.tryLock(KEY, 600)).isFalse();
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        // The old code would have blocked here for the full 600 seconds. A generous bound still
        // fails loudly if anyone reintroduces a waiting acquire.
        assertThat(waitedMillis).as("declining must not block").isLessThan(1_000);
    }

    @Test
    @DisplayName("released by whichever thread gets there, the way deleting a Redis key is")
    void releasedFromAnotherThread() throws Exception {
        assertThat(cache.tryLock(KEY, 600)).isTrue();

        Thread other = new Thread(() -> cache.releaseLock(KEY));
        other.start();
        other.join(5_000);

        assertThat(cache.tryLock(KEY, 600))
                .as("a release from another thread must actually free the lock")
                .isTrue();
    }

    @Test
    @DisplayName("a holder that dies without releasing does not block the next run forever")
    void lapsesOnItsOwn() throws Exception {
        // Zero seconds: the claim is already spent when the next caller looks at it.
        assertThat(cache.tryLock(KEY, 0)).isTrue();
        assertThat(cache.tryLock(KEY, 600))
                .as("an expired claim is a free lock")
                .isTrue();
    }

    @Test
    @DisplayName("two threads racing for it, exactly one wins")
    void exactlyOneWinnerUnderContention() throws Exception {
        int racers = 16;
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        AtomicBoolean[] won = new AtomicBoolean[racers];
        Thread[] threads = new Thread[racers];

        for (int i = 0; i < racers; i++) {
            won[i] = new AtomicBoolean();
            final int index = i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                won[index].set(cache.tryLock(KEY, 600));
            });
            threads[i].start();
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        for (Thread t : threads) {
            t.join(5_000);
        }

        long winners = java.util.Arrays.stream(won).filter(AtomicBoolean::get).count();
        assertThat(winners).as("a lock that lets two callers in is not a lock").isEqualTo(1);
    }
}
