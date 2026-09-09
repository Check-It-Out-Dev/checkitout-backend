package com.sm.instagram.platform.auth.sandbox;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * On the public sandbox the {@code /test/**} helpers (legal, registry, account status, ensure-user, e-mail
 * readers) are closed: they answer 404 before any controller sees the request. Two doors stay open:
 * {@code POST /test/auth/mock-session} (persona-checked by {@link SandboxPersonaPolicy} in the controller) and
 * {@code POST /test/auth/clear-session} (sign-out). Registered first in the chain so nothing runs ahead of it.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "checkitout.sandbox", name = "enabled", havingValue = "true")
public class SandboxGuardFilter extends OncePerRequestFilter {

    static final String TEST_PREFIX = "/test/";
    static final Set<String> OPEN_DOORS = Set.of("/test/auth/mock-session", "/test/auth/clear-session");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathWithinApplication(request);
        if (!path.startsWith(TEST_PREFIX) || (OPEN_DOORS.contains(path) && "POST".equalsIgnoreCase(request.getMethod()))) {
            chain.doFilter(request, response);
            return;
        }
        log.info("[SANDBOX] Closed test helper {} {}", request.getMethod(), path);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":404,\"error\":\"Not Found\",\"path\":\"" + request.getRequestURI() + "\"}");
    }

    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
