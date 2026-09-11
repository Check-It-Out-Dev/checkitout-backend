package com.sm.instagram.platform.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A property the application sends as null is documented as nullable.
 *
 * <p>The serializer is configured {@code NON_NULL}, so a null property is normally absent from the
 * body and {@code "type": "string"} is the truth. {@code @JsonInclude(ALWAYS)} on a property opts
 * back in — {@code UserDtoOut.profilePicture} carries it deliberately, so that a client clearing
 * its avatar sees the field go to null instead of keeping the old value — and that made
 * {@code GET /users/me} answer {@code "profilePicture": null} against a document promising a
 * string. Schemathesis called it a schema violation and it was one.
 *
 * <p>Written here rather than as {@code @Schema(nullable = true)} on the field because that
 * annotation does not survive: springdoc 2.8.6 resolves the property in 3.0 mode and serialises the
 * document in 3.1, so {@code nullable} is set and then dropped, and {@code types} is ignored on the
 * way in. The description from the same annotation lands, which is what makes the loss quiet. This
 * hook runs after resolution with the field's own annotations in hand, so it sets the type set the
 * emitted document keeps.
 *
 * <p>{@link com.sm.instagram.platform.unit.openapi.NullableFieldsAreDeclaredNullableUnitTest} reads
 * the committed document and checks the same rule from the other end, so a new {@code ALWAYS}
 * property cannot be added without the document following.
 */
@Configuration
public class NullablePropertyCustomizer {

    private static final String NULL_TYPE = "null";

    @Bean
    public PropertyCustomizer documentAlwaysIncludedPropertiesAsNullable() {
        return (property, type) -> {
            if (property != null && isAlwaysIncluded(type)) {
                markNullable(property);
            }
            return property;
        };
    }

    private static boolean isAlwaysIncluded(AnnotatedType type) {
        if (type == null || type.getCtxAnnotations() == null) {
            return false;
        }
        for (Annotation annotation : type.getCtxAnnotations()) {
            if (annotation instanceof JsonInclude include
                    && include.value() == JsonInclude.Include.ALWAYS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add {@code "null"} beside the type the resolver already found, leaving a {@code $ref} or a
     * composed schema alone — neither states a type here, and a union would have to be written
     * around it rather than inside it.
     */
    private static void markNullable(Schema<?> property) {
        Set<String> types = property.getTypes() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(property.getTypes());
        if (property.getType() != null) {
            types.add(property.getType());
        }
        if (types.isEmpty()) {
            return;
        }
        types.add(NULL_TYPE);
        property.setTypes(types);
    }
}
