package com.sm.instagram.platform.config;

import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitInterceptor;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * Declares 429 on every operation the rate limiter can refuse.
 *
 * <p>{@code RateLimitInterceptor} answers 429 for any handler carrying an enabled {@code @RateLimit},
 * on the method or on its class -- 237 of the 272 published operations, most of them through a
 * class-level annotation -- and the document declared 429 on one. The fuzz tier saw the gap as 67
 * "undocumented HTTP status code" findings a night, across /auth, /twofactor, /step-up, the admin
 * cascade deletes and most create and update endpoints. Until Schemathesis 4.28 they were labelled
 * "API accepted schema-violating request" instead, which is why the count looked like it jumped
 * when only the label moved.
 *
 * <p>This is an {@code OperationCustomizer} rather than one more {@code OpenApiCustomizer} because
 * the annotation lives on the handler, and only an operation customiser is given the handler. It
 * asks the interceptor's own lookup, so the operations declared here are by construction the ones
 * that can be refused. An operation that already declares 429 keeps what it has.
 */
@Configuration
public class RateLimitResponsesCustomizer {

    static final String TOO_MANY_REQUESTS = "429";

    @Bean
    public OperationCustomizer documentRateLimitRefusals() {
        return RateLimitResponsesCustomizer::declareRefusal;
    }

    static Operation declareRefusal(Operation operation, HandlerMethod handlerMethod) {
        RateLimit rateLimit = RateLimitInterceptor.getRateLimitConfig(handlerMethod);
        if (rateLimit == null || !rateLimit.enabled()) {
            return operation;
        }
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        responses.computeIfAbsent(TOO_MANY_REQUESTS, key -> new ApiResponse()
                .description("Rate limit exceeded: too many requests in the window this operation allows. "
                        + "The body carries retry_after, in seconds, next to the usual error fields.")
                .addHeaderObject("Retry-After", new Header()
                        .description("Seconds until the window resets. Sent when that is more than zero.")
                        .schema(new IntegerSchema()))
                .content(ErrorEnvelopeResponsesCustomizer.envelopeContent()));
        return operation;
    }
}
