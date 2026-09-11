package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.common.logging.CustomErrorController;
import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.error.ErrorController;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /error} is a servlet forward target, and must not be in the published contract.
 *
 * <p>It was. springdoc publishes any {@code @RestController} it finds, so the document declared an
 * {@code /error} operation, the frontend generated a {@code CustomErrorControllerApi} that nothing
 * has ever called, and a reader of the contract would conclude the API has an endpoint that reports
 * errors on request. It does not. The status comes from the {@code jakarta.servlet.error.status_code}
 * attribute the container sets during a forward; a direct call arrives without it and is a 500 by
 * construction. Schemathesis tried all six methods and reported six server errors, none of which
 * was a defect in anything except the document.
 *
 * <p>Asserted on the annotation rather than on a generated document so that it costs no boot: the
 * document itself is checked by {@code OpenApiSpecGeneratorTest} and, across the repositories, by
 * the frontend's contract-check pipeline.
 */
@DisplayName("The servlet error forward target stays out of the published contract")
class ErrorControllerNotPublishedUnitTest {

    @Test
    @DisplayName("CustomErrorController is @Hidden")
    void errorControllerIsHidden() {
        assertThat(CustomErrorController.class.isAnnotationPresent(Hidden.class))
                .as("@Hidden on the class is what keeps /error out of the OpenAPI document")
                .isTrue();
    }

    @Test
    @DisplayName("it is still the container's error handler, so hiding it changed no behaviour")
    void errorControllerStillHandlesForwards() {
        assertThat(ErrorController.class).isAssignableFrom(CustomErrorController.class);

        Method handler = Arrays.stream(CustomErrorController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("handleError"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no handleError method"));
        assertThat(handler.getParameterCount()).isEqualTo(1);
    }
}
