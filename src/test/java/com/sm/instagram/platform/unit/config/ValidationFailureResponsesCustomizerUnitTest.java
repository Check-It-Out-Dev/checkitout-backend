package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.config.ErrorEnvelopeResponsesCustomizer;
import com.sm.instagram.platform.config.ValidationFailureResponsesCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 400 is declared where the server can actually produce one, and nowhere else.
 *
 * <p>The customiser exists because closing two 500s turned them into undocumented 400s and the fuzz
 * tier's budget went over — the debt did not appear, it moved into the light. These tests are what
 * keep the declaration honest in both directions: an operation that takes input can reject it, and
 * an operation that takes nothing has nothing to reject.
 */
@DisplayName("Operations that take input declare the 400 they can return")
class ValidationFailureResponsesCustomizerUnitTest {

    private final ValidationFailureResponsesCustomizer customizer =
            new ValidationFailureResponsesCustomizer();

    private static Operation operationWith(ApiResponses responses) {
        Operation operation = new Operation();
        operation.setResponses(responses);
        return operation;
    }

    private static ApiResponses ok() {
        return new ApiResponses().addApiResponse("200", new ApiResponse().description("fine"));
    }

    private OpenAPI customise(String path, PathItem.HttpMethod method, Operation operation) {
        PathItem item = new PathItem();
        item.operation(method, operation);
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem(path, item));
        customizer.documentValidationFailures().customise(openApi);
        return openApi;
    }

    @Test
    @DisplayName("an operation with a request body gains 400")
    void bodyGainsBadRequest() {
        Operation operation = operationWith(ok());
        operation.setRequestBody(new RequestBody());

        customise("/legal/consent/prepare", PathItem.HttpMethod.POST, operation);

        assertThat(operation.getResponses()).containsKey("400");
        assertThat(operation.getResponses().get("400").getDescription())
                .isEqualTo(ValidationFailureResponsesCustomizer.DESCRIPTION);
        // Described where it is created, rather than relying on ErrorEnvelopeResponsesCustomizer to
        // come along afterwards: springdoc applies these beans in alphabetical order, so it does not.
        assertThat(operation.getResponses().get("400").getContent().values().iterator().next()
                .getSchema().get$ref())
                .isEqualTo("#/components/schemas/" + ErrorEnvelopeResponsesCustomizer.SCHEMA_NAME);
    }

    @Test
    @DisplayName("an operation with only a parameter gains 400 too — it can still fail to parse")
    void parameterGainsBadRequest() {
        Operation operation = operationWith(ok());
        operation.setParameters(List.of(new Parameter().name("page").in("query")));

        customise("/address/paged", PathItem.HttpMethod.GET, operation);

        assertThat(operation.getResponses()).containsKey("400");
    }

    @Test
    @DisplayName("an operation that accepts nothing does not")
    void inputlessOperationIsLeftAlone() {
        Operation operation = operationWith(ok());

        customise("/auth/health", PathItem.HttpMethod.GET, operation);

        assertThat(operation.getResponses()).containsOnlyKeys("200");
    }

    @Test
    @DisplayName("an operation that already documents 400 keeps its own")
    void existingBadRequestIsKept() {
        ApiResponses responses = ok();
        responses.addApiResponse("400", new ApiResponse().description("Invalid input data"));
        Operation operation = operationWith(responses);
        operation.setRequestBody(new RequestBody());

        customise("/users", PathItem.HttpMethod.POST, operation);

        assertThat(operation.getResponses().get("400").getDescription()).isEqualTo("Invalid input data");
    }

    @Test
    @DisplayName("takesInput is the whole rule, and it says no to nothing")
    void takesInputIsExplicit() {
        assertThat(ValidationFailureResponsesCustomizer.takesInput(null)).isFalse();
        assertThat(ValidationFailureResponsesCustomizer.takesInput(new Operation())).isFalse();
        assertThat(ValidationFailureResponsesCustomizer.takesInput(
                new Operation().parameters(List.of()))).isFalse();
    }
}
