package com.sm.instagram.platform.unit.grpc;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.NoCredentials;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.KeyManagementServiceSettings;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceSettings;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.threeten.bp.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The rest of the Google Cloud stack has to link too, and only a started transport proves it.
 *
 * <p>{@link GrpcTransportLinkageUnitTest} covers Firestore, which is the path the 2026-09-09
 * incident took. It is not the only path. KMS decrypts every stored Instagram token,
 * reCAPTCHA Enterprise gates registration, and Cloud Storage holds the uploads and the GeoIP
 * database; each one builds its client from a different half of the same dependency train
 * (gax + gRPC for the first two, google-http-client for the third).
 *
 * <p>Those artifacts were hand-pinned one by one until 2026-09-11, twenty of them below what
 * something else on the classpath had been compiled against. The enforcer's
 * {@code requireUpperBoundDeps} rule now refuses that arrangement and {@code libraries-bom}
 * supplies one version for the set, but a declared version is not the same claim as "the bytes
 * fit together": a mismatch can still arrive shaded, relocated, or through a BOM that manages
 * one artifact and not its sibling. Compilation will not notice, and neither will 10,399 unit
 * tests, because these classes only meet when a client actually opens a connection.
 *
 * <p>So each test builds a real client the way production does, points it at a port nothing
 * listens on, and asserts the failure is a refused connection. A {@link LinkageError} anywhere in
 * the causal chain is the failure this exists to catch. Nothing is started and nothing is
 * reached: port 1 is reserved and never bound, so the loopback refuses immediately and these
 * tests stay off the network entirely.
 */
class GoogleCloudClientLinkageUnitTest {

    /** Reserved, never bound. The refusal is instant, which keeps these tests off the network. */
    private static final String NOTHING_LISTENS_HERE = "127.0.0.1:1";

    /**
     * Two attempts is enough to have started a transport; the defaults spend a minute proving the
     * same thing. gax applies retry settings per method, so the gRPC clients below take this
     * through {@code applyToAllUnaryMethods} rather than as one builder call.
     */
    private static RetrySettings fastFail() {
        return RetrySettings.newBuilder()
                .setMaxAttempts(2)
                .setInitialRpcTimeout(Duration.ofSeconds(2))
                .setMaxRpcTimeout(Duration.ofSeconds(2))
                .setTotalTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    @DisplayName("KMS links: a dead port answers UNAVAILABLE, not a LinkageError")
    void kmsClientStartsWithoutLinkageError() throws Exception {
        KeyManagementServiceSettings.Builder builder = KeyManagementServiceSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setEndpoint(NOTHING_LISTENS_HERE);
        builder.applyToAllUnaryMethods(m -> {
            m.setRetrySettings(fastFail());
            return null;
        });
        KeyManagementServiceSettings settings = builder.build();

        Throwable failure;
        try (KeyManagementServiceClient kms = KeyManagementServiceClient.create(settings)) {
            failure = catchThrowable(() ->
                    kms.getKeyRing("projects/linkage-probe/locations/global/keyRings/probe"));
        }

        assertNoLinkageError(failure, "KMS", "UNAVAILABLE");
    }

    @Test
    @DisplayName("reCAPTCHA Enterprise links: a dead port answers UNAVAILABLE, not a LinkageError")
    void recaptchaClientStartsWithoutLinkageError() throws Exception {
        RecaptchaEnterpriseServiceSettings.Builder builder =
                RecaptchaEnterpriseServiceSettings.newBuilder()
                        .setCredentialsProvider(NoCredentialsProvider.create())
                        .setEndpoint(NOTHING_LISTENS_HERE);
        builder.applyToAllUnaryMethods(m -> {
            m.setRetrySettings(fastFail());
            return null;
        });
        RecaptchaEnterpriseServiceSettings settings = builder.build();

        Throwable failure;
        try (RecaptchaEnterpriseServiceClient recaptcha =
                     RecaptchaEnterpriseServiceClient.create(settings)) {
            failure = catchThrowable(() -> recaptcha.listKeys("projects/linkage-probe"));
        }

        assertNoLinkageError(failure, "reCAPTCHA Enterprise", "UNAVAILABLE");
    }

    /**
     * Storage is the one that does NOT go through gRPC: it is built on google-http-client, whose
     * version moved with the same BOM. A broken classpath there looks identical from the outside
     * and has to be caught the same way.
     */
    @Test
    @DisplayName("Cloud Storage links: a dead port refuses the connection, not a LinkageError")
    void storageClientStartsWithoutLinkageError() {
        Storage storage = StorageOptions.newBuilder()
                .setProjectId("linkage-probe")
                .setCredentials(NoCredentials.getInstance())
                .setHost("http://" + NOTHING_LISTENS_HERE)
                .setRetrySettings(fastFail())
                .build()
                .getService();

        Throwable failure = catchThrowable(() -> storage.get("linkage-probe-bucket"));

        assertNoLinkageError(failure, "Cloud Storage", "ConnectException");
    }

    /**
     * The assertion all three share. A {@link LinkageError} in the chain means the classes on this
     * path do not fit together, which is the whole point. The {@code transportSignature} keeps the
     * test honest the other way: it is what a refused connection on port 1 actually looks like, so a
     * client that ignored the endpoint and reached the real Google API would answer with an
     * authentication error instead and fail here rather than pass for the wrong reason.
     */
    private static void assertNoLinkageError(Throwable failure, String what, String transportSignature) {
        assertThat(failure)
                .as("%s: a refused connection on %s must surface as a failure, not as success",
                        what, NOTHING_LISTENS_HERE)
                .isNotNull();

        List<Throwable> chain = chainOf(failure);
        String described = describe(chain);

        assertThat(chain)
                .as("%s: the causal chain was: %s", what, described)
                .noneMatch(t -> t instanceof LinkageError)
                .noneMatch(t -> String.valueOf(t.getMessage()).contains("Panic! This is a bug!"));

        assertThat(described)
                .as("%s: port 1 refuses; anything else means the endpoint was not the one configured",
                        what)
                .contains(transportSignature);
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
