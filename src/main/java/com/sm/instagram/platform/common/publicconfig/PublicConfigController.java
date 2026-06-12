package com.sm.instagram.platform.common.publicconfig;

import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous public-config endpoint exposing runtime feature flags to the frontend at bootstrap.
 *
 * <p>Reachable via {@code GET /api/public-config} (the {@code /api} prefix comes from the
 * global {@code server.servlet.context-path}). Permitted in {@code WebSecurityConfiguration}
 * and bypassed in {@code JwtAuthenticationFilter} so the landing page can call it before login.
 *
 * <p>Returns {@code Cache-Control: no-store} so toggles flip immediately on next page load
 * — no edge / browser caching.
 */
@Slf4j
@RestController
@RequestMapping("/public-config")
@RequiredArgsConstructor
public class PublicConfigController {

    private final AppPaymentsProperties appPaymentsProperties;

    @GetMapping
    public ResponseEntity<PublicConfigDto> getConfig() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PublicConfigDto(appPaymentsProperties.isEnabled()));
    }
}
