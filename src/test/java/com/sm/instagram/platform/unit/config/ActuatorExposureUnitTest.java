package com.sm.instagram.platform.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No profile may expose an actuator endpoint that hands out the process's insides.
 *
 * <p>Three did. {@code application-monitoring.yml} listed env, configprops, beans, threaddump and
 * heapdump on the web, with {@code cors.allowed-origins: "*"} beside them;
 * {@code application-actuator.yml} listed env, loggers, threaddump, heapdump and conditions; and
 * {@code application-prod-standalone.yml}, which the code treats as a real deployment profile,
 * listed env with {@code show-details: always}. Semgrep's
 * {@code spring-actuator-dangerous-endpoints-enabled-yaml} reported fourteen of them.
 *
 * <p>{@code heapdump} is the one that matters most: it returns the entire JVM heap as a file, which
 * is every credential, token and session the process is holding. {@code env} and {@code configprops}
 * print the configuration around it, {@code beans} and {@code conditions} the shape of the
 * application, and {@code threaddump} what it is doing. None of them is read by anything in this
 * repository — Alloy scrapes {@code prometheus}, the Kubernetes probes use the health groups.
 *
 * <p>Two layers already stood in front of them, the security chain and nginx's 404 for everything
 * under {@code /actuator/} except health. This is the third, and the only one that fails in review
 * rather than in production: a configuration file cannot list the endpoint at all.
 */
@DisplayName("actuator exposure · no profile offers the process's insides")
class ActuatorExposureUnitTest {

    /**
     * Endpoints that must never appear in a web exposure list.
     *
     * <p>`loggers` is here because it is a WRITE endpoint: a POST changes a running application's
     * log levels, which is enough to turn on DEBUG somewhere that prints more than it should.
     */
    private static final Set<String> NEVER_ON_THE_WEB = Set.of(
            "heapdump", "threaddump", "env", "configprops", "beans", "conditions", "loggers", "*");

    private static final Path RESOURCES = Paths.get("src", "main", "resources");

    static Stream<Path> profileFiles() throws IOException {
        try (var files = Files.list(RESOURCES)) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith("application"))
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object dig(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }

    /** `include` is written either as a comma-separated string or as a YAML list, in this repo both. */
    private static List<String> exposureList(Object include) {
        if (include == null) {
            return List.of();
        }
        if (include instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).toList();
        }
        return Arrays.stream(String.valueOf(include).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileFiles")
    @DisplayName("web exposure lists nothing that dumps the process")
    void exposesNothingDangerous(Path file) throws IOException {
        List<String> offending = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            for (Object document : new Yaml().loadAll(in)) {
                Object include = dig(document, "management", "endpoints", "web", "exposure", "include");
                for (String endpoint : exposureList(include)) {
                    if (NEVER_ON_THE_WEB.contains(endpoint.toLowerCase(Locale.ROOT))) {
                        offending.add(endpoint);
                    }
                }
            }
        }

        assertThat(offending)
                .as("%s exposes %s over HTTP; heapdump alone returns the whole JVM heap",
                        file.getFileName(), offending)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profileFiles")
    @DisplayName("the actuator has no CORS allow-list of its own")
    void actuatorHasNoCorsWildcard(Path file) throws IOException {
        List<String> offending = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file)) {
            for (Object document : new Yaml().loadAll(in)) {
                Object origins = dig(document, "management", "endpoints", "web", "cors", "allowed-origins");
                if (origins != null) {
                    offending.add(String.valueOf(origins));
                }
            }
        }

        // Prometheus and Alloy scrape server to server. A CORS header on the actuator only ever
        // invites a browser somewhere else to read it on a visitor's behalf.
        assertThat(offending)
                .as("%s gives the actuator a CORS allow-list: %s", file.getFileName(), offending)
                .isEmpty();
    }

    @Test
    @DisplayName("the test reads real files, and enough of them to mean something")
    void readsTheProfilesItClaimsTo() throws IOException {
        List<Path> files = profileFiles().toList();

        assertThat(files)
                .as("no application*.yml found — the test would pass on an empty list")
                .hasSizeGreaterThan(8);
        assertThat(files.stream().map(p -> p.getFileName().toString()))
                .contains("application.yml", "application-prod.yml", "application-monitoring.yml");
    }
}
