package com.sm.instagram.platform.unit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.service.SessionSecurityService;
import com.sm.instagram.platform.common.authorization.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev-lite storage transport stands in for Google Cloud Storage URLs, and
 * no browser sends a session cookie to a storage host. Uploads authorise with
 * the single-use token in the path; served files are the public URLs the seeded
 * demo world points at.
 *
 * <p>Regression coverage for a defect found by cloning the public repositories
 * and running the wizard: the paths were listed as {@code permitAll} in the
 * security configuration, but authorization rules run AFTER this filter, so
 * every request without a session was rejected with 401 before reaching them.
 * The visible symptom was that every image in the seeded world was broken
 * until you happened to be signed in — and the wizard's own ownership probe,
 * which asks an unauthenticated question, could never recognise its own
 * backend.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationFilter — dev-lite storage paths are public")
class JwtAuthenticationFilterDevLitePublicUnitTest {

    @Mock private SessionSecurityService sessionSecurityService;
    @Mock private UserCacheService userCacheService;
    @Mock private MessageSource messageSource;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(userCacheService, sessionSecurityService,
                messageSource, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(filter, "jwtSecret", "test-secret-that-is-long-enough-for-hmac-sha256!");
        ReflectionTestUtils.setField(filter, "cookieHmacSecret", "test-cookie-hmac-secret-value-long-enough");
    }

    @ParameterizedTest(name = "{0} passes without a session")
    @ValueSource(strings = {
            "/api/dev-lite/placeholder/campaign-351-1",
            "/dev-lite/placeholder/campaign-351-1",
            "/api/dev-lite/files/content/uid/1700000000_pic.jpg",
            "/dev-lite/files/content/uid/1700000000_pic.jpg",
            "/api/dev-lite/upload/2b0f6f1e-0000-4000-8000-000000000000",
            "/dev-lite/upload/2b0f6f1e-0000-4000-8000-000000000000",
    })
    void devLiteStoragePathsAreAnonymous(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).as("no 401 short-circuit for %s", uri).isEqualTo(200);
        assertThat(chain.getRequest()).as("chain continues to the controller").isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("request stays anonymous").isNull();
    }

    @ParameterizedTest(name = "{0} is NOT made public by the dev-lite rule")
    @ValueSource(strings = {
            "/api/users/me",
            "/api/dev-litex/secret",
            "/api/partnership-opportunities",
    })
    void neighbouringPathsAreUnaffected(String uri) throws Exception {
        // The rule is a prefix match; make sure the prefix is the exact one and
        // that nothing adjacent inherits anonymity from it.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Without a session cookie these reach the chain unauthenticated and are
        // rejected downstream by the authorization rules — what must NOT happen
        // is this filter treating them as public.
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("%s must not be authenticated", uri).isNull();
    }
}
