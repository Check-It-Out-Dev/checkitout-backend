package com.sm.instagram.platform.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application disables Spring's CSRF tokens, and what makes that safe is one cookie attribute.
 *
 * <p>"Stateless API, therefore no CSRF" is not the argument here, because this application
 * authenticates with cookies: the browser attaches them whether the request came from our page or
 * from somebody else's. The argument is {@code SameSite=Strict} - a cross-site request carries no
 * credential at all, so there is nothing to forge with. {@code WebSecurityConfiguration} says so
 * beside the line that disables the tokens.
 *
 * <p>An argument that lives only in a comment is one refactor away from being false. The realistic
 * way for it to break is not malice, it is a redirect: somebody fixing an OAuth flow that "loses
 * the session" by relaxing the session cookie instead of the handshake cookie. Nothing would fail.
 * The application would work perfectly and be open to CSRF.
 *
 * <p>So this reads the sources and holds the policy. Two exemptions, both named, both checked:
 *
 * <ul>
 *   <li>{@code OAuthCallbackService} writes the three handshake cookies inline as Lax. It has to:
 *       SameSite=Strict is not sent on a top-level cross-site navigation either, which is exactly
 *       what the redirect back from the provider is. They live 120 seconds and carry a one-time
 *       token and its HMAC rather than a session. The test asserts that file writes exactly three,
 *       so a fourth cookie appearing there fails.</li>
 *   <li>{@code TokenExchangeService} chooses Lax once, to CLEAR those same three cookies. The test
 *       checks the cookie names next to the choice rather than trusting the file.</li>
 * </ul>
 *
 * <p>It reads declarations rather than responses, which is a real limit and is the point: the
 * declaration is what a person edits. A cookie set some entirely different way would slip past,
 * which is what the assertion on the count is for.
 */
@DisplayName("Cookie policy — SameSite=Strict is what stands in for CSRF tokens")
class CookieSameSitePolicyUnitTest {

    private static final Path MAIN = Path.of("src/main/java/com/sm/instagram/platform");

    /** A `Set-Cookie` value built by String.format: the whole format string. */
    private static final Pattern COOKIE_FORMAT =
            Pattern.compile("\"([A-Za-z_%][^\"]*?=\\s*[^\"]*?(?:Path=/|Max-Age=)[^\"]*?)\"");

    /** The OAuth handshake, the only cookies allowed to be Lax. */
    private static final List<String> HANDSHAKE = List.of("oauth_token", "oauth_sig", "oauth_meta");

    /** The one file that writes them, and writes nothing else. */
    private static final String HANDSHAKE_FILE = "OAuthCallbackService.java";

    private record Declaration(String file, int line, String format) {
        String sameSite() {
            Matcher m = Pattern.compile("SameSite=([A-Za-z%]+)").matcher(format);
            return m.find() ? m.group(1) : null;
        }

        @Override
        public String toString() {
            return file + ":" + line + "  " + format;
        }
    }

    private static List<Declaration> cookieDeclarations() throws IOException {
        List<Declaration> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!line.contains("SameSite") && !line.toLowerCase(Locale.ROOT).contains("set-cookie")) {
                        continue;
                    }
                    Matcher m = COOKIE_FORMAT.matcher(line);
                    while (m.find()) {
                        found.add(new Declaration(file.getFileName().toString(), i + 1, m.group(1)));
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("every cookie this codebase writes declares SameSite")
    void everyCookieDeclaresSameSite() throws IOException {
        List<Declaration> silent = cookieDeclarations().stream().filter(d -> d.sameSite() == null).toList();

        assertThat(silent)
                .as("a cookie with no SameSite falls back to the browser's default, which is not a policy")
                .isEmpty();
    }

    @Test
    @DisplayName("the only cookies written as Lax are the three in the OAuth handshake file")
    void onlyTheHandshakeFileWritesLax() throws IOException {
        List<Declaration> declarations = cookieDeclarations();

        assertThat(declarations).as("the sources were read at all").hasSizeGreaterThan(8);

        List<Declaration> laxOutsideTheHandshake = declarations.stream()
                .filter(d -> "Lax".equals(d.sameSite()))
                .filter(d -> !HANDSHAKE_FILE.equals(d.file()))
                .toList();

        assertThat(laxOutsideTheHandshake)
                .as("CSRF tokens are disabled in WebSecurityConfiguration on the strength of "
                        + "SameSite=Strict. A Lax cookie written anywhere but the OAuth handshake "
                        + "breaks that argument, and nothing else would fail.")
                .isEmpty();
    }

    @Test
    @DisplayName("the handshake file writes exactly the three short-lived OAuth cookies")
    void theHandshakeFileWritesOnlyItsThree() throws IOException {
        Path file = MAIN.resolve("auth/service/" + HANDSHAKE_FILE);
        String source = Files.readString(file, StandardCharsets.UTF_8);

        List<Declaration> here = cookieDeclarations().stream()
                .filter(d -> HANDSHAKE_FILE.equals(d.file()))
                .toList();

        assertThat(here)
                .as("a fourth cookie written here would inherit the Lax exemption without anyone "
                        + "deciding that it should")
                .hasSize(3);
        assertThat(here).allSatisfy(d -> assertThat(d.format()).contains("Max-Age=120"));
        assertThat(HANDSHAKE).allSatisfy(name -> assertThat(source).contains(name));
    }

    @Test
    @DisplayName("every sameSite variable reads Strict, except the one that clears the handshake")
    void everyChosenValueIsStrictExceptTheClearingOfTheHandshake() throws IOException {
        // The setters build the header with SameSite=%s and choose the value a few lines above, so
        // the format string alone cannot say what was chosen. This reads the choice, and for a Lax
        // choice looks at the cookie names written just below it rather than trusting the file.
        Pattern chosen = Pattern.compile("String\\s+sameSite\\s*=\\s*\"([A-Za-z]+)\"");
        List<String> offenders = new ArrayList<>();
        int seen = 0;

        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = chosen.matcher(source);
                while (m.find()) {
                    seen++;
                    if ("Strict".equals(m.group(1))) {
                        continue;
                    }
                    String below = source.substring(m.end(), Math.min(source.length(), m.end() + 1200));
                    boolean clearsTheHandshake = HANDSHAKE.stream().allMatch(below::contains)
                            && below.contains("Max-Age=0");
                    if (!clearsTheHandshake) {
                        offenders.add(file.getFileName() + " chose " + m.group(1));
                    }
                }
            }
        }

        assertThat(seen).as("the setters were found").isGreaterThan(4);
        assertThat(offenders)
                .as("a sameSite variable that is not Strict, and is not the one clearing the three "
                        + "OAuth handshake cookies, is a session cookie that can now be sent "
                        + "cross-site")
                .isEmpty();
    }
}
