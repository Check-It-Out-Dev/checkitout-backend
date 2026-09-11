package com.sm.instagram.platform.unit.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published document may only promise {@code date-time} where the value actually carries one.
 *
 * <p>OpenAPI's {@code date-time} is RFC 3339, which requires a UTC offset. A {@link
 * java.time.LocalDateTime} has none to give, and Jackson writes it without one, so every field
 * springdoc labelled {@code date-time} by default was a promise the server could not keep --
 * 74 of them. Schemathesis found three in one night run before anyone read the document closely.
 *
 * <p>This reads the committed spec rather than booting the application, so it costs nothing and
 * runs in the unit tier. {@code OpenApiSpecGeneratorTest} is what keeps the committed spec in step
 * with the running server; between the two, a field that gains the wrong format is caught before
 * the frontend regenerates a client from it.
 */
@DisplayName("The published document promises date-time only where there is an offset")
class OpenApiDateTimeFormatUnitTest {

    private static final Path SPEC = Path.of("docs", "openapi", "openapi.json");

    /**
     * Every property backed by {@link java.time.Instant}, which serialises with a {@code Z} and is
     * therefore a real RFC 3339 date-time. Adding an Instant field to a DTO means adding it here;
     * that is the point -- the alternative is a list nobody maintains and a format nobody checks.
     */
    private static final Set<String> OFFSET_BACKED = Set.of(
            "confirmedAt", "createdAt", "expiresAt", "snapshotTime", "updatedAt", "uploadTime");

    private static JsonNode spec;

    @BeforeAll
    static void readSpec() throws IOException {
        assertThat(Files.exists(SPEC))
                .as("the generated OpenAPI document is committed at %s", SPEC)
                .isTrue();
        spec = new ObjectMapper().readTree(Files.readString(SPEC));
    }

    @Test
    @DisplayName("no local date-time field claims to be one")
    void onlyOffsetBackedFieldsClaimDateTime() {
        Set<String> claiming = new TreeSet<>(namesWithFormat(spec, "date-time"));

        assertThat(claiming)
                .as("a field here serialises without a UTC offset, so `format: date-time` is a "
                        + "promise the server cannot keep; see LocalDateTimeSchemaConfig")
                .isSubsetOf(OFFSET_BACKED);
    }

    @Test
    @DisplayName("and the local ones are still strings, with an example that shows the shape")
    void localDateTimeFieldsAreDescribedStrings() {
        List<JsonNode> local = new ArrayList<>();
        collect(spec, "createdTime", local);
        collect(spec, "lastUpdateTime", local);

        assertThat(local).as("the document describes these fields at all").isNotEmpty();
        for (JsonNode node : local) {
            assertThat(node.path("type").asText()).isEqualTo("string");
            assertThat(node.has("format"))
                    .as("a LocalDateTime carries no offset, so it carries no format either")
                    .isFalse();
        }
    }

    /** Property names anywhere in the document whose schema declares the given format. */
    private static Set<String> namesWithFormat(JsonNode node, String format) {
        Set<String> found = new TreeSet<>();
        walkProperties(node, (name, schema) -> {
            if (format.equals(schema.path("format").asText(null))) {
                found.add(name);
            }
        });
        return found;
    }

    private static void collect(JsonNode node, String property, List<JsonNode> into) {
        walkProperties(node, (name, schema) -> {
            if (property.equals(name)) {
                into.add(schema);
            }
        });
    }

    private interface PropertyVisitor {
        void visit(String name, JsonNode schema);
    }

    private static void walkProperties(JsonNode node, PropertyVisitor visitor) {
        if (node.isObject()) {
            JsonNode properties = node.get("properties");
            if (properties != null && properties.isObject()) {
                properties.fieldNames().forEachRemaining(
                        name -> visitor.visit(name, properties.get(name)));
            }
            node.fields().forEachRemaining(e -> walkProperties(e.getValue(), visitor));
        } else if (node.isArray()) {
            node.forEach(child -> walkProperties(child, visitor));
        }
    }
}
