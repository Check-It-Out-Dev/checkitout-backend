package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.config.LocalDatabaseInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The local database bootstrap builds eleven DDL statements by concatenating a database, role or
 * schema name into them. It has to: PostgreSQL does not take an identifier as a bound parameter, so
 * there is no {@code DROP USER ?} to write instead.
 *
 * <p>What there is instead is a refusal. The names come from configuration and this component only
 * exists under the dev and no-redis profiles, so the realistic failure is a typo in a .env rather
 * than an attack -- but a typo containing a semicolon would be executed just the same, and the guard
 * runs before the enabled flag is even read, so a misconfigured name cannot reach a Statement even
 * when initialization is switched off.
 */
class LocalDatabaseIdentifierGuardUnitTest {

    private static LocalDatabaseInitializer initializerWith(String database, String user, String superUser) {
        LocalDatabaseInitializer initializer = new LocalDatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "appDatabase", database);
        ReflectionTestUtils.setField(initializer, "appUser", user);
        ReflectionTestUtils.setField(initializer, "superUser", superUser);
        // Off, so a valid configuration returns before opening any connection. The guard runs first
        // either way, which is the point.
        ReflectionTestUtils.setField(initializer, "initEnabled", false);
        return initializer;
    }

    @Test
    @DisplayName("a name carrying a statement terminator never reaches a Statement")
    void refusesInjectedIdentifier() {
        LocalDatabaseInitializer initializer =
                initializerWith("checkitout_local_db; DROP TABLE users", "checkitout_app_local", "postgres");

        assertThatThrownBy(initializer::initializeDatabase)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.database")
                .hasMessageContaining("plain SQL identifier");
    }

    @Test
    @DisplayName("each of the three names is checked, not just the first")
    void checksEveryName() {
        assertThatThrownBy(initializerWith("ok_db", "1_starts_with_a_digit", "postgres")::initializeDatabase)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.username");

        assertThatThrownBy(initializerWith("ok_db", "ok_user", "super user")::initializeDatabase)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgres.superuser.username");
    }

    @Test
    @DisplayName("null is refused rather than concatenated as the word null")
    void refusesNull() {
        assertThatThrownBy(initializerWith(null, "ok_user", "postgres")::initializeDatabase)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the names this project actually uses pass, and nothing is opened when disabled")
    void acceptsTheRealNames() {
        assertThatCode(initializerWith("checkitout_local_db", "checkitout_app_local", "postgres")::initializeDatabase)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("63 characters is Postgres's own limit, and 64 is not an identifier")
    void refusesOverlongIdentifier() {
        String sixtyThree = "a".repeat(63);
        String sixtyFour = "a".repeat(64);
        assertThat(sixtyThree).hasSize(63);

        assertThatCode(initializerWith(sixtyThree, "ok_user", "postgres")::initializeDatabase)
                .doesNotThrowAnyException();
        assertThatThrownBy(initializerWith(sixtyFour, "ok_user", "postgres")::initializeDatabase)
                .isInstanceOf(IllegalStateException.class);
    }
}
