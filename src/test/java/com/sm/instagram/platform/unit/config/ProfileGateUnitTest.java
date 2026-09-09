package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.dev.DevLiteUploadController;
import com.sm.instagram.platform.dev.GreenMailConfig;
import com.sm.instagram.platform.storage.service.LocalUploadSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every test affordance and simulator bean is registered by a {@link Profile}
 * expression, and those expressions are the only thing standing between a
 * production deployment and endpoints that mint sessions, read mail or accept
 * unauthenticated uploads. Security by absence: the bean does not exist, so the
 * path 404s regardless of what the security config permits.
 *
 * <p>These tests read the annotation off the class and evaluate it the way
 * Spring does, so a careless edit to a profile string fails here instead of in
 * production. Booting a context per profile combination would be the slower and
 * less direct way to assert the same property.
 */
@DisplayName("Profile gates — no simulator affordance may activate under prod")
class ProfileGateUnitTest {

    /** Every class whose absence in production is a security property. */
    private static final Class<?>[] GATED = {
            GreenMailConfig.class,
            DevLiteUploadController.class,
            LocalUploadSink.class,
    };

    private static boolean active(Class<?> type, String... activeProfiles) {
        Profile annotation = type.getAnnotation(Profile.class);
        assertThat(annotation)
                .as("%s must carry a @Profile gate", type.getSimpleName())
                .isNotNull();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment.acceptsProfiles(Profiles.of(annotation.value()));
    }

    @ParameterizedTest(name = "prod: {0}")
    @ValueSource(strings = {"prod", "prod-standalone"})
    @DisplayName("nothing gated activates in a production profile")
    void nothingActivatesInProduction(String productionProfile) {
        for (Class<?> type : GATED) {
            assertThat(active(type, productionProfile))
                    .as("%s must not activate under %s", type.getSimpleName(), productionProfile)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a production profile wins even when a permissive one is also active")
    void productionVetoesAnyCombination() {
        // The realistic accident: SPRING_PROFILES_ACTIVE picks up a leftover
        // `dev` next to `prod`. An expression that merely omits `prod` would
        // happily activate here; ours excludes it explicitly.
        for (Class<?> type : GATED) {
            assertThat(active(type, "prod", "dev"))
                    .as("%s must not activate under prod,dev", type.getSimpleName())
                    .isFalse();
            assertThat(active(type, "prod", "e2e"))
                    .as("%s must not activate under prod,e2e", type.getSimpleName())
                    .isFalse();
            assertThat(active(type, "prod", "dev-lite"))
                    .as("%s must not activate under prod,dev-lite", type.getSimpleName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the simulator upload beans exist under dev-lite and e2e, and nowhere else")
    void devLiteUploadBeansActivateUnderDevLiteAndE2e() {
        // e2e joined dev-lite here on 2026-09-11: the end-to-end tier runs on a public runner with no
        // Google credential, and a signed URL against a real bucket answers 403 with 507 on the confirm.
        // The pair is asserted together on purpose - the sink mints the token and the controller accepts
        // the bytes, so one without the other is a 404 on every upload.
        for (Class<?> type : new Class<?>[] {DevLiteUploadController.class, LocalUploadSink.class}) {
            assertThat(active(type, "dev-lite")).as("%s under dev-lite", type.getSimpleName()).isTrue();
            assertThat(active(type, "e2e")).as("%s under e2e", type.getSimpleName()).isTrue();
            assertThat(active(type, "dev")).as("%s under plain dev", type.getSimpleName()).isFalse();
            assertThat(active(type, "test")).as("%s under test", type.getSimpleName()).isFalse();
            assertThat(active(type, "e2e", "prod")).as("%s never under prod", type.getSimpleName()).isFalse();
        }
    }

    @Test
    @DisplayName("GreenMail serves dev and e2e — dev-lite reaches it through the profile group")
    void greenMailStaysAvailableToTheSuitesThatNeedIt() {
        // Deliberately wider than the dev-lite gates: the plain `dev` profile
        // has used the in-memory mailbox since before dev-lite existed, and the
        // FE integration tier reads it there. `dev-lite` activates `dev` via
        // spring.profiles.group, so the simulator gets it too.
        assertThat(active(GreenMailConfig.class, "e2e")).isTrue();
        assertThat(active(GreenMailConfig.class, "dev")).isTrue();
        assertThat(active(GreenMailConfig.class, "dev-lite", "dev")).isTrue();
        assertThat(active(GreenMailConfig.class, "test")).isFalse();
    }
}
