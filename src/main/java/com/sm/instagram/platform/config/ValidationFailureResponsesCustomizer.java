package com.sm.instagram.platform.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the 400 that any operation taking input can actually return.
 *
 * <p>Sibling of {@link AuthFailureResponsesCustomizer}, and it exists for the same reason: the
 * document disagreed with the server about what an endpoint can answer. Bean validation rejects a
 * body the constraints refuse, Spring rejects a path variable that will not parse, and the handlers
 * in {@code common.exceptions} turn both into 400 — but almost no operation declared one, so every
 * such answer read as an undocumented status to anything checking the contract.
 *
 * <p>It became visible the moment it was fixed somewhere else. Closing two consent endpoints that
 * answered 500 to input the boundary should have refused moved those cases from "server error" to
 * "undocumented status code", and the fuzz tier's tracked budget went over. The debt did not
 * appear; it moved into the light.
 *
 * <p>Scoped to operations that take something: a request body or at least one parameter. An
 * operation that accepts nothing has nothing to reject, and declaring a 400 there would be the same
 * kind of untruth in the other direction. An operation that already documents 400 keeps what it has,
 * including its schema and description.
 */
@Configuration
public class ValidationFailureResponsesCustomizer {

    public static final String DESCRIPTION =
            "The request was rejected before it reached the handler: a field the constraints refuse, "
                    + "a parameter that will not parse, or a body that is not the JSON it claims to be.";

    @Bean
    public OpenApiCustomizer documentValidationFailures() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) ->
                    item.readOperationsMap().forEach((method, operation) -> {
                        if (!takesInput(operation)) {
                            return;
                        }
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                            return;
                        }
                        responses.computeIfAbsent("400", key -> new ApiResponse()
                                .description(DESCRIPTION)
                                .content(ErrorEnvelopeResponsesCustomizer.envelopeContent()));
                    }));
        };
    }

    /** True when the operation accepts a body or any parameter, and so has something to reject. */
    public static boolean takesInput(Operation operation) {
        if (operation == null) {
            return false;
        }
        if (operation.getRequestBody() != null) {
            return true;
        }
        return operation.getParameters() != null && !operation.getParameters().isEmpty();
    }
}
