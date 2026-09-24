package com.sm.instagram.platform.subsume;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Clears the two other thread-bound statics a test can leave behind: the request bound to
 * {@link RequestContextHolder} and the logging {@link MDC}.
 *
 * <p>Same defect as {@link ClearSecurityContextExtension}, found the same way, by coverage that
 * moved when nothing but the order moved. The runner image changed on 2026-09-23, surefire's
 * filesystem order changed with it, and the backend invariant reported four methods covered less:
 * {@code TwoFactorAuthService.getClientIp}/{@code getUserAgent} had only ever run their
 * request-present branch on a request some earlier {@code @WebMvcTest} left bound to the thread,
 * and {@code ConsentEnforcementFilter.write403Response} skipped generating a request id whenever
 * {@code RequestLoggingFilterUnitTest} had run just before it, because that filter puts
 * {@code correlationId} into the MDC by design and the test never took it out. Each class now
 * binds or clears what it needs itself, and this makes sure nothing reaches the next one.
 */
public final class ClearRequestStateExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }
}
