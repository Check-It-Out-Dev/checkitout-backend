package com.sm.instagram.platform.unit.storage;

import com.sm.instagram.platform.storage.service.SignedUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A filename the caller chose must not be able to throw.
 *
 * <p>{@code POST /upload/signed-url} answered 507 Insufficient Storage -- a claim about the
 * server's disk -- to a 239-character filename. The name had no dot in it, the truncation branch
 * did {@code filename.substring(filename.lastIndexOf('.'))}, {@code lastIndexOf} returned -1, and
 * {@code String.substring(-1)} throws. The caller was told their quota was full because the server
 * could not shorten their filename.
 *
 * <p>The second way it could throw is here too, because it was never reachable by accident: an
 * "extension" long enough that there is nothing left to truncate to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Sanitising a filename")
class SanitizeFilenameUnitTest {

    /** Nothing here reaches a collaborator; the method is pure string work. */
    @InjectMocks
    private SignedUrlService service;

    private static final int MAX = 100;

    @ParameterizedTest(name = "{0} characters, no extension")
    @ValueSource(ints = {101, 239, 255})
    @DisplayName("a long name with no extension is shortened, not thrown at")
    void longNameWithoutExtension(int length) {
        String filename = "0".repeat(length);

        assertThatCode(() -> service.sanitizeFilename(filename)).doesNotThrowAnyException();
        assertThat(service.sanitizeFilename(filename)).hasSizeLessThanOrEqualTo(MAX);
    }

    @Test
    @DisplayName("a long name keeps the extension it has")
    void longNameKeepsItsExtension() {
        String sanitized = service.sanitizeFilename("0".repeat(250) + ".jpeg");

        assertThat(sanitized).endsWith(".jpeg").hasSizeLessThanOrEqualTo(MAX);
    }

    @Test
    @DisplayName("an extension longer than the whole budget does not underflow the truncation")
    void absurdExtensionDoesNotUnderflow() {
        String filename = "name." + "e".repeat(200);

        assertThatCode(() -> service.sanitizeFilename(filename)).doesNotThrowAnyException();
        assertThat(service.sanitizeFilename(filename)).hasSizeLessThanOrEqualTo(MAX);
    }

    @Test
    @DisplayName("a name that already fits is returned unchanged")
    void shortNameIsUntouched() {
        assertThat(service.sanitizeFilename("holiday-photo_2.jpg")).isEqualTo("holiday-photo_2.jpg");
    }

    @Test
    @DisplayName("path separators cannot survive")
    void pathSeparatorsAreRemoved() {
        assertThat(service.sanitizeFilename("../../etc/passwd"))
                .doesNotContain("/")
                .doesNotContain("\\");
    }

    @Test
    @DisplayName("a leading dot cannot survive either")
    void hiddenFilesAreNotCreated() {
        assertThat(service.sanitizeFilename(".bashrc")).doesNotStartWith(".");
    }
}
