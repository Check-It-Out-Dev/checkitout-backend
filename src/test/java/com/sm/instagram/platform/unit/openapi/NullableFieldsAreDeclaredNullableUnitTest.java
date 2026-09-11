package com.sm.instagram.platform.unit.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A property the application sends as null is declared nullable in the published document.
 *
 * <p>The serializer is configured {@code NON_NULL}, so a null property is normally absent from the
 * body and {@code "type": "string"} is honest. {@code @JsonInclude(ALWAYS)} opts one back in:
 * {@code UserDtoOut.profilePicture} carries it deliberately, so a client clearing its avatar sees
 * the field go to null rather than keeping the old value. That made {@code GET /users/me} answer
 * {@code "profilePicture": null} against a document promising a string, and Schemathesis called it
 * a schema violation.
 *
 * <p>So {@code ALWAYS} is exactly the declaration "this one reaches the client as null", and this
 * reads the committed document to check every one of them says so.
 * {@link com.sm.instagram.platform.config.NullablePropertyCustomizer} is what puts it there;
 * without this, the next {@code ALWAYS} property would be added and the document would quietly
 * disagree with the server again.
 */
@DisplayName("A property serialised as null is declared nullable in the document")
class NullableFieldsAreDeclaredNullableUnitTest {

    private static final String BASE_PACKAGE = "com.sm.instagram.platform";
    private static final Path SPEC = Path.of("docs", "openapi", "openapi.json");

    private static JsonNode spec;

    @BeforeAll
    static void readSpec() throws IOException {
        assertThat(Files.exists(SPEC))
                .as("the generated OpenAPI document is committed at %s", SPEC)
                .isTrue();
        spec = new ObjectMapper().readTree(Files.readString(SPEC));
    }

    @Test
    @DisplayName("every @JsonInclude(ALWAYS) property carries \"null\" among its types")
    void alwaysIncludedPropertiesAreNullable() {
        List<String> wrong = new ArrayList<>();

        for (Field field : alwaysIncludedFields()) {
            JsonNode property = spec.path("components").path("schemas")
                    .path(field.getDeclaringClass().getSimpleName())
                    .path("properties").path(field.getName());
            String where = field.getDeclaringClass().getSimpleName() + "." + field.getName();
            if (property.isMissingNode()) {
                wrong.add(where + " is not in the document at all");
            } else if (!declaresNull(property.path("type"))) {
                wrong.add(where + " is declared " + property.path("type"));
            }
        }

        assertThat(wrong)
                .describedAs("@JsonInclude(ALWAYS) means the property reaches the client as null; "
                        + "the document must say type: [..., \"null\"]. Regenerate it with "
                        + "OpenApiSpecGeneratorTest after adding one")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan finds the property this rule was written for")
    void theScanReachesTheKnownCase() {
        assertThat(alwaysIncludedFields())
                .extracting(field -> field.getDeclaringClass().getSimpleName() + "." + field.getName())
                .contains("UserDtoOut.profilePicture");
    }

    /** OpenAPI 3.1 spells a nullable property as a type array containing {@code "null"}. */
    private static boolean declaresNull(JsonNode type) {
        if (!type.isArray()) {
            return false;
        }
        for (JsonNode entry : type) {
            if ("null".equals(entry.asText())) {
                return true;
            }
        }
        return false;
    }

    /** Every property that has opted back in to being serialised when null. */
    private static List<Field> alwaysIncludedFields() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(
                            org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                        return true; // DTOs are not components; the type filter alone decides
                    }
                };
        // Filtering on the annotation reads class metadata; the alternative — every class in the
        // package — loads the whole application to inspect a handful of DTOs.
        scanner.addIncludeFilter(new AnnotationTypeFilter(JsonInclude.class));

        List<Field> fields = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type;
            try {
                type = Class.forName(candidate.getBeanClassName());
            } catch (Throwable unloadable) {
                continue; // a class whose dependencies are absent cannot carry a DTO field
            }
            for (Field field : type.getDeclaredFields()) {
                JsonInclude onField = field.getAnnotation(JsonInclude.class);
                if (onField != null && onField.value() == JsonInclude.Include.ALWAYS) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
