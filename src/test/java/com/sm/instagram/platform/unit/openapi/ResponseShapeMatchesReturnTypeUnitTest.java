package com.sm.instagram.platform.unit.openapi;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An {@code @ApiResponse} that names a type replaces the one the method returns — so it has to agree.
 *
 * <p>{@code GET /address/search} returns {@code List<AddressDtoOut>} and its annotation said
 * {@code @Schema(implementation = AddressDtoOut.class)}. springdoc believes the annotation, so the
 * document promised an object, the server sent an array, and the fuzzer called it a schema
 * violation. Four sibling endpoints in the same controller had the same defect and no fuzzer had
 * reached them yet; the generated TypeScript client was wrong on all five.
 *
 * <p>This is a shape check, not a type check: whether the body is a list. A mismatch here is always
 * a defect — either the annotation is stale or the method changed under it — and it is invisible in
 * review because the two halves sit twenty lines apart.
 */
@DisplayName("A declared response shape matches the shape the method returns")
class ResponseShapeMatchesReturnTypeUnitTest {

    private static final String BASE_PACKAGE = "com.sm.instagram.platform";

    /** The default of {@code @Schema#implementation} — "the annotation names no type". */
    private static final Class<?> UNSET = Void.class;

    @Test
    @DisplayName("no controller declares an object where it returns a list, or the reverse")
    void declaredShapesAgreeWithReturnTypes() {
        List<String> mismatches = new ArrayList<>();

        for (Class<?> controller : restControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                Optional<Boolean> returnsList = returnsCollection(method);
                if (returnsList.isEmpty()) {
                    continue;
                }
                for (ApiResponse response : successResponses(method)) {
                    for (Content content : response.content()) {
                        describedShape(content).ifPresent(declaresList -> {
                            if (!declaresList.equals(returnsList.get())) {
                                mismatches.add("%s.%s (%s) declares %s but returns %s"
                                        .formatted(controller.getSimpleName(), method.getName(),
                                                response.responseCode(),
                                                declaresList ? "an array" : "an object",
                                                returnsList.get() ? "a collection" : "a single value"));
                            }
                        });
                    }
                }
            }
        }

        assertThat(mismatches)
                .describedAs("Use array = @ArraySchema(schema = @Schema(implementation = X.class)) "
                        + "for a method returning a collection, and a plain schema otherwise")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan actually reaches the controllers")
    void theScanFindsControllers() {
        assertThat(restControllers()).hasSizeGreaterThan(20);
    }

    private static List<Class<?>> restControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                controllers.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned but could not load " + candidate, e);
            }
        }
        return controllers;
    }

    /**
     * Both spellings at once: {@code getAnnotationsByType} unwraps the {@code @ApiResponses}
     * container for a repeatable annotation, so reading the container as well would double-count.
     */
    private static List<ApiResponse> successResponses(Method method) {
        return java.util.Arrays.stream(method.getAnnotationsByType(ApiResponse.class))
                .filter(response -> response.responseCode().startsWith("2"))
                .toList();
    }

    /**
     * True if the annotation describes an array, false if it describes an object, empty if it
     * names no type at all — which leaves springdoc reading the return type, and nothing to check.
     */
    private static Optional<Boolean> describedShape(Content content) {
        ArraySchema array = content.array();
        if (array.schema().implementation() != UNSET || array.arraySchema().implementation() != UNSET) {
            return Optional.of(true);
        }
        Schema schema = content.schema();
        if (schema.implementation() != UNSET) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    /**
     * True if the body is a collection or array, empty when the body's type is not knowable from
     * the signature — {@code ResponseEntity<?>}, a raw {@code Object}, or a
     * {@link MappingJacksonValue}, whose wire body is whatever it wraps and whose own shape never
     * reaches the client.
     */
    private static Optional<Boolean> returnsCollection(Method method) {
        Type type = method.getGenericReturnType();
        if (rawTypeOf(type) == ResponseEntity.class) {
            if (!(type instanceof ParameterizedType parameterized)) {
                return Optional.empty();
            }
            type = parameterized.getActualTypeArguments()[0];
            if (type instanceof WildcardType) {
                return Optional.empty();
            }
        }
        Class<?> raw = rawTypeOf(type);
        if (raw == null || raw == Object.class || raw == MappingJacksonValue.class) {
            return Optional.empty();
        }
        return Optional.of(Collection.class.isAssignableFrom(raw) || raw.isArray());
    }

    private static Class<?> rawTypeOf(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return rawTypeOf(parameterized.getRawType());
        }
        return null;
    }
}
