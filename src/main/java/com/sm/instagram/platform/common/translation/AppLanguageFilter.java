package com.sm.instagram.platform.common.translation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Filter to extract application language from X-App-Language header and set it in LocaleContextHolder.
 *
 * This filter reads the user's explicit language choice from the frontend (set via UI dropdown)
 * rather than relying on browser's Accept-Language header. This ensures consistent language
 * handling based on user preference.
 *
 * The X-App-Language header is sent by the frontend Angular app on every HTTP request.
 *
 * Order: After EarlyRequestLoggingFilter (HIGHEST_PRECEDENCE) and RequestCorrelationFilter (1),
 * but before most other processing to ensure locale is set for exception messages.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AppLanguageFilter extends OncePerRequestFilter {

    private static final String APP_LANGUAGE_HEADER = "X-App-Language";
    private static final Locale DEFAULT_LOCALE = new Locale("pl");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String langHeader = request.getHeader(APP_LANGUAGE_HEADER);
        Locale locale = parseLocale(langHeader);

        log.debug("AppLanguageFilter: X-App-Language='{}', resolved locale='{}'", langHeader, locale);

        LocaleContextHolder.setLocale(locale);

        try {
            filterChain.doFilter(request, response);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /**
     * Parse the language header value into a Locale.
     * Supports common language codes and defaults to Polish if not recognized.
     */
    private Locale parseLocale(String lang) {
        if (lang == null || lang.isBlank()) {
            return DEFAULT_LOCALE;
        }

        return switch (lang.toLowerCase().trim()) {
            case "en", "en-us", "en-gb", "en_us", "en_gb" -> Locale.ENGLISH;
            case "pl", "pl-pl", "pl_pl" -> new Locale("pl");
            default -> {
                log.debug("Unknown language code '{}', defaulting to Polish", lang);
                yield DEFAULT_LOCALE;
            }
        };
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Don't filter actuator endpoints to avoid noise
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/");
    }
}
