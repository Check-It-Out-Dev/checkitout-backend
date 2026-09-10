package com.sm.instagram.platform.unit.grpc;

import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.threeten.bp.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gRPC artifacts on this classpath must be one release train.
 *
 * <p>They stopped being one on 2026-09-09: a dependency update moved {@code grpc-netty-shaded} from
 * 1.59.0 to 1.75.0 on its own while four siblings were hand-pinned to 1.59.0. Everything compiled,
 * all 10,358 unit tests passed, the application started, and the classpath was still broken --
 * because the two halves only meet when a real transport starts. {@code NettyClientHandler}'s static
 * initialiser then calls {@code GrpcUtil.getFlag}, which 1.59 does not declare, and the resulting
 * {@link NoSuchMethodError} is thrown inside the channel's synchronisation context. gRPC calls that
 * a panic and poisons the channel for good, so every later call on it returns
 * {@code INTERNAL: Panic! This is a bug!} -- which reads like an unreachable server and is not one.
 * A whole night of Firebase scenarios failed as "external service unreachable" because of it.
 *
 * <p>The enforcer's {@code requireSameVersions} rule catches the versions drifting apart in the
 * declarations. This catches the thing that actually matters, which is whether the classes link,
 * and would also catch a mismatch arriving shaded or relocated rather than declared.
 *
 * <p>Nothing is started and nothing is reached. The endpoint is port 1, which is reserved and never
 * bound, so the connection is refused as fast as the loopback can say so -- but only after
 * {@code NettyClientTransport.start()} has run, which is the moment under test. UNAVAILABLE is the
 * pass, and asserting it is also what keeps this test honest: an endpoint that silently fell back to
 * the real {@code firestore.googleapis.com} would answer UNAUTHENTICATED instead, and fail here.
 */
class GrpcTransportLinkageUnitTest {

    /** Reserved, never bound. The refusal is instant, which keeps this test off the network entirely. */
    private static final String NOTHING_LISTENS_HERE = "127.0.0.1:1";

    @Test
    @DisplayName("starting a gRPC transport links: a dead port answers UNAVAILABLE, not NoSuchMethodError")
    void grpcTransportStartsWithoutLinkageError() throws Exception {
        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setProjectId("grpc-linkage-probe")
                .setCredentials(NoCredentials.getInstance())
                .setHost(NOTHING_LISTENS_HERE)
                .setChannelProvider(InstantiatingGrpcChannelProvider.newBuilder()
                        .setEndpoint(NOTHING_LISTENS_HERE)
                        .build())
                // gax retries UNAVAILABLE by default, and a refused port is retried as fast as it is
                // refused. Two attempts is enough to have started a transport; a default total timeout
                // would spend a minute proving the same thing.
                .setRetrySettings(RetrySettings.newBuilder()
                        .setMaxAttempts(2)
                        .setInitialRpcTimeout(Duration.ofSeconds(2))
                        .setMaxRpcTimeout(Duration.ofSeconds(2))
                        .setTotalTimeout(Duration.ofSeconds(5))
                        .build())
                .build();

        Throwable failure = null;
        try (Firestore firestore = options.getService()) {
            try {
                firestore.collection("probe").document("probe").get().get(15, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failure = e.getCause() != null ? e.getCause() : e;
            } catch (TimeoutException e) {
                // A channel that panicked on a linkage error stops answering rather than answering
                // badly, so this path is not "healthy, still retrying" -- it is the same defect wearing
                // a different coat. Say so here, because the NoSuchMethodError itself is only visible
                // in the io.grpc log lines printed above this failure.
                throw new AssertionError("a refused connection on " + NOTHING_LISTENS_HERE
                        + " did not answer within 15s; a gRPC channel poisoned by a version mismatch"
                        + " behaves exactly like this -- check the io.grpc SEVERE lines above", e);
            }
        }

        assertThat(failure)
                .as("a refused connection must surface as a failure, not as success")
                .isNotNull();

        List<Throwable> chain = chainOf(failure);

        assertThat(chain)
                .as("the causal chain was: %s", describe(chain))
                .noneMatch(t -> t instanceof LinkageError)
                .noneMatch(t -> String.valueOf(t.getMessage()).contains("Panic! This is a bug!"));

        assertThat(describe(chain))
                .as("port 1 refuses; anything else means the endpoint was not the one configured")
                .contains("UNAVAILABLE");
    }

    private static List<Throwable> chainOf(Throwable t) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable c = t; c != null && !chain.contains(c); c = c.getCause()) {
            chain.add(c);
            Collections.addAll(chain, c.getSuppressed());
        }
        return chain;
    }

    private static String describe(List<Throwable> chain) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c : chain) {
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
