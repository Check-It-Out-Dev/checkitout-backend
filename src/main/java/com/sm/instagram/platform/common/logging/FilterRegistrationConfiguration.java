package com.sm.instagram.platform.common.logging;

import com.sm.instagram.platform.config.CorsProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuration to manually register filters and ensure proper ordering.
 */
@Configuration
public class FilterRegistrationConfiguration {

    @Bean
    public FilterRegistrationBean<EarlyRequestLoggingFilter> earlyRequestLoggingFilter(CorsProperties corsProperties) {
        FilterRegistrationBean<EarlyRequestLoggingFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new EarlyRequestLoggingFilter(corsProperties));
        registrationBean.addUrlPatterns("/*"); // Apply to all URLs
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Highest priority
        registrationBean.setName("earlyRequestLoggingFilter");

        return registrationBean;
    }
}
