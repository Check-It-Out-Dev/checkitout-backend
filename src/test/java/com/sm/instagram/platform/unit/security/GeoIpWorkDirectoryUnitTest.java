package com.sm.instagram.platform.unit.security;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Storage;
import com.sm.instagram.platform.common.security.GeoIpStorageService;
import com.sm.instagram.platform.common.security.geoip.MaxMindDatabaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The half-downloaded GeoIP database goes somewhere this application owns, not the shared system
 * temp directory (java:S5443).
 *
 * <p>The NIO calls create the file itself owner-only, so this was never the classic symlink race.
 * The directory is shared, though, and this particular file decides whether a login is treated as
 * impossible travel — a security input, not a cache — so another local account being able to watch
 * it appear, or swap it between the download and the read, is worth more care than the platform's
 * defaults. Asking for POSIX permissions instead is not an option: that call throws on Windows,
 * where this is developed.
 */
class GeoIpWorkDirectoryUnitTest {

    @Test
    @DisplayName("MaxMind works beside its own database file, and creates the directory")
    void maxMindWorksBesideTheDatabase(@TempDir Path tmp) {
        MaxMindDatabaseService service = new MaxMindDatabaseService();
        Path database = tmp.resolve("geoip").resolve("GeoLite2-City.mmdb");
        ReflectionTestUtils.setField(service, "databasePath", database.toString());

        Path work = (Path) ReflectionTestUtils.invokeMethod(service, "workDirectory");

        assertThat(work).isNotNull();
        assertThat(work.getParent()).isEqualTo(database.getParent());
        assertThat(Files.isDirectory(work)).as("created, not merely computed").isTrue();
        // Deliberately not asserting "outside java.io.tmpdir": JUnit's own @TempDir lives there, so
        // the check would fail on a correct implementation. What matters is that the location is
        // derived from the configured database path rather than from the platform default, and
        // asserting the parent says that exactly.
    }

    @Test
    @DisplayName("the storage service reads the same property, because it writes the same file")
    void storageServiceUsesTheSameDirectory(@TempDir Path tmp) {
        // Neither collaborator is touched: workDirectory() only reads a configured path.
        GeoIpStorageService service =
                new GeoIpStorageService(org.mockito.Mockito.mock(Storage.class), org.mockito.Mockito.mock(Firestore.class));
        Path database = tmp.resolve("geoip").resolve("GeoLite2-City.mmdb");
        ReflectionTestUtils.setField(service, "databasePath", database.toString());

        Path work = (Path) ReflectionTestUtils.invokeMethod(service, "workDirectory");

        assertThat(work).isNotNull();
        assertThat(work.getParent()).isEqualTo(database.getParent());
        assertThat(Files.isDirectory(work)).isTrue();
    }

    @Test
    @DisplayName("calling it twice is not an error, because a download can be retried")
    void idempotent(@TempDir Path tmp) {
        MaxMindDatabaseService service = new MaxMindDatabaseService();
        ReflectionTestUtils.setField(service, "databasePath", tmp.resolve("g").resolve("db.mmdb").toString());

        Path first = (Path) ReflectionTestUtils.invokeMethod(service, "workDirectory");
        Path second = (Path) ReflectionTestUtils.invokeMethod(service, "workDirectory");

        assertThat(first).isEqualTo(second);
    }
}
