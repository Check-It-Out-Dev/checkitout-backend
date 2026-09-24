package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.config.ErrorEnvelopeResponsesCustomizer;
import com.sm.instagram.platform.config.RateLimitResponsesCustomizer;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The document must declare 429 exactly where {@code RateLimitInterceptor} can send it: an enabled
 * {@code @RateLimit} on the handler method, or on its class when the method has none.
 */
class RateLimitResponsesCustomizerUnitTest {

    private final OperationCustomizer customizer = new RateLimitResponsesCustomizer().documentRateLimitRefusals();

    static class Unlimited {
        public void open() {
        }

        @RateLimit(profile = RateLimitProfile.AUTH)
        public void limited() {
        }

        @RateLimit(enabled = false)
        public void switchedOff() {
        }
    }

    @RateLimit(profile = RateLimitProfile.STANDARD)
    static class LimitedClass {
        public void inherits() {
        }

        @RateLimit(enabled = false)
        public void optsOut() {
        }
    }

    private static HandlerMethod handler(Object bean, String method) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(method));
    }

    private ApiResponses customize(Object bean, String method) throws NoSuchMethodException {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OK")));
        return customizer.customize(operation, handler(bean, method)).getResponses();
    }

    @Test
    @DisplayName("a rate-limited method declares 429 with the error envelope and Retry-After")
    void methodAnnotationDeclaresRefusal() throws Exception {
        ApiResponse refusal = customize(new Unlimited(), "limited").get("429");

        assertThat(refusal).isNotNull();
        assertThat(refusal.getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/" + ErrorEnvelopeResponsesCustomizer.SCHEMA_NAME);
        assertThat(refusal.getHeaders()).containsKey("Retry-After");
    }

    @Test
    @DisplayName("a method on a rate-limited class inherits the refusal")
    void classAnnotationDeclaresRefusal() throws Exception {
        assertThat(customize(new LimitedClass(), "inherits")).containsKey("429");
    }

    @Test
    @DisplayName("no annotation, or a disabled one, declares nothing")
    void unlimitedOperationsAreLeftAlone() throws Exception {
        assertThat(customize(new Unlimited(), "open")).containsOnlyKeys("200");
        assertThat(customize(new Unlimited(), "switchedOff")).containsOnlyKeys("200");
        // the method-level annotation wins over the class, as it does in the interceptor
        assertThat(customize(new LimitedClass(), "optsOut")).containsOnlyKeys("200");
    }

    @Test
    @DisplayName("a 429 declared by hand is kept")
    void handWrittenRefusalIsKept() throws Exception {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("429", new ApiResponse().description("Rate limit exceeded")));

        ApiResponses responses = customizer.customize(operation, handler(new Unlimited(), "limited")).getResponses();

        assertThat(responses.get("429").getDescription()).isEqualTo("Rate limit exceeded");
    }
}
