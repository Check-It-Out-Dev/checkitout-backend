package com.sm.instagram.platform.subsume;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Clears the thread's {@link SecurityContextHolder} after every test, in every class.
 *
 * <p>Twelve unit-test classes install a security context — often a mock — and never clear it. The
 * unit tier runs in one JVM, so the next class inherits it: {@code
 * ConsentEnforcementFilterUnitTest.should_set_X_Consent_Required_header} set its authentication on
 * a leftover mocked context, the filter saw no authentication, and the test passed only when its
 * class ran before the leaking one. The governance round's random-order run found it on
 * 2026-09-14. A test that depends on the order it was written in is a defect the matrices cannot
 * see; this closes the shared static for all of them, registered through {@code
 * META-INF/services/org.junit.jupiter.api.extension.Extension} with autodetection on.
 */
public final class ClearSecurityContextExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SecurityContextHolder.clearContext();
    }
}
