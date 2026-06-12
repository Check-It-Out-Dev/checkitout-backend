package com.sm.instagram.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Configuration for pageable endpoints to enforce maximum page size limits.
 * This prevents potential performance issues from excessive page size requests.
 * <p>
 * Default page size: 20 (maintains existing behavior)
 * Maximum page size: 100 (enforced limit)
 * <p>
 * This configuration applies to:
 * - All endpoints using Pageable parameters (automatically enforced by Spring)
 * - Custom endpoints creating PageRequest manually have been updated separately
 */
@Configuration
@Slf4j
public class PageableConfiguration implements WebMvcConfigurer, PageableHandlerMethodArgumentResolverCustomizer {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @PostConstruct
    public void init() {
        log.info("=============================================================================");
        log.info("PAGEABLE CONFIGURATION INITIALIZED");
        log.info("=============================================================================");
        log.info("Default page size: {}", DEFAULT_PAGE_SIZE);
        log.info("Maximum page size: {}", MAX_PAGE_SIZE);
        log.info("Any request exceeding {} items per page will be automatically limited", MAX_PAGE_SIZE);
        log.info("=============================================================================");
    }

    @Override
    public void customize(PageableHandlerMethodArgumentResolver resolver) {
        // Set default page size to 20
        resolver.setFallbackPageable(PageRequest.of(0, DEFAULT_PAGE_SIZE));

        // Set maximum page size to 100
        resolver.setMaxPageSize(MAX_PAGE_SIZE);

        log.debug("Configured PageableHandlerMethodArgumentResolver with max page size: {}", MAX_PAGE_SIZE);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();

        // Configure the resolver
        pageableResolver.setFallbackPageable(PageRequest.of(0, DEFAULT_PAGE_SIZE));
        pageableResolver.setMaxPageSize(MAX_PAGE_SIZE);

        // Add it to the resolvers list
        resolvers.add(pageableResolver);

        log.debug("Added custom PageableHandlerMethodArgumentResolver to WebMvc configuration");
    }
}

