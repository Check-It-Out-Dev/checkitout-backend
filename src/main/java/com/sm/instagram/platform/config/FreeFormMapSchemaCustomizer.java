package com.sm.instagram.platform.config;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A {@code Map<String, Object>} has values of any type, and the document should say so.
 *
 * <p>springdoc renders one as {@code {"type":"object","additionalProperties":{"type":"object"}}} —
 * which claims every value in the map is itself an object. The values are usually not:
 * {@code GET /auth/health} returns {@code {"status":"healthy","service":"auth","timestamp":17890…}},
 * two strings and a number, and a validator reads that as three violations. Four endpoints were
 * reported this way in one night run, and the count was the number Schemathesis happened to reach
 * rather than the number that were wrong.
 *
 * <p>The correction is {@code additionalProperties: true} — any type, which is what
 * {@code Map<String, Object>} means. Applied only to the exact shape springdoc emits for it: an
 * additionalProperties schema of bare {@code type: object} with no properties, no
 * additionalProperties of its own and no {@code $ref}. A {@code Map<String, SomeDto>} carries a
 * {@code $ref} and is left alone; a {@code Map<String, Map<String, X>>} carries its own
 * additionalProperties and is left alone too.
 *
 * <p>The better fix for the four endpoints is a typed response rather than a free-form map, which
 * would also stop the generated client saying {@code { [key: string]: object }}. That is an API
 * change per endpoint; this is the document telling the truth about the API as it is.
 */
@Configuration
public class FreeFormMapSchemaCustomizer {

    @Bean
    public OpenApiCustomizer describeFreeFormMaps() {
        return openApi -> {
            Set<Schema<?>> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                openApi.getComponents().getSchemas().values().forEach(schema -> visit(schema, seen));
            }
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(item ->
                        item.readOperations().forEach(operation -> {
                            if (operation.getRequestBody() != null) {
                                visitContent(operation.getRequestBody().getContent(), seen);
                            }
                            if (operation.getResponses() != null) {
                                operation.getResponses().values()
                                        .forEach(response -> visitContent(response.getContent(), seen));
                            }
                        }));
            }
        };
    }

    private void visitContent(Content content, Set<Schema<?>> seen) {
        if (content == null) {
            return;
        }
        content.values().stream().map(MediaType::getSchema).forEach(schema -> visit(schema, seen));
    }

    /** Walk a schema and everything under it, once each; schemas can be shared and cyclic. */
    private void visit(Schema<?> schema, Set<Schema<?>> seen) {
        if (schema == null || !seen.add(schema)) {
            return;
        }
        if (isBareObject(asSchema(schema.getAdditionalProperties()))) {
            schema.setAdditionalProperties(Boolean.TRUE);
        }
        visit(schema.getItems(), seen);
        visit(asSchema(schema.getAdditionalProperties()), seen);
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> visit(property, seen));
        }
        visitAll(schema.getAllOf(), seen);
        visitAll(schema.getAnyOf(), seen);
        visitAll(schema.getOneOf(), seen);
    }

    private void visitAll(java.util.List<Schema> schemas, Set<Schema<?>> seen) {
        if (schemas != null) {
            schemas.forEach(schema -> visit(schema, seen));
        }
    }

    private static Schema<?> asSchema(Object additionalProperties) {
        return additionalProperties instanceof Schema<?> schema ? schema : null;
    }

    /**
     * True for the exact shape {@code Map<String, Object>} produces: {@code type: object} and
     * nothing else that would make it a description of something.
     *
     * <p>The type has to be read from both places. Swagger's 3.0 classes keep it in {@code type};
     * the {@code JsonSchema} the 3.1 resolver produces keeps it in {@code types} and leaves
     * {@code type} null. Checking only the first fixed 35 of the 39 free-form maps in this document
     * and silently missed four, all of them properties of component schemas rather than response
     * bodies -- including {@code FileOperationResponse.data}, whose endpoint the fuzzer then
     * reported.
     */
    static boolean isBareObject(Schema<?> schema) {
        return schema != null
                && isObjectTyped(schema)
                && schema.get$ref() == null
                && schema.getProperties() == null
                && schema.getAdditionalProperties() == null
                && schema.getAllOf() == null
                && schema.getAnyOf() == null
                && schema.getOneOf() == null;
    }

    /** {@code type: "object"} however the resolver chose to spell it. */
    private static boolean isObjectTyped(Schema<?> schema) {
        Set<String> types = schema.getTypes();
        if (types != null && !types.isEmpty()) {
            return types.size() == 1 && types.contains("object");
        }
        return "object".equals(schema.getType());
    }
}
