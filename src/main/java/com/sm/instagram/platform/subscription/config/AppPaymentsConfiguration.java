package com.sm.instagram.platform.subscription.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AppPaymentsProperties} so it can be injected as a bean wherever
 * the paying infrastructure needs to be guarded.
 */
@Configuration
@EnableConfigurationProperties(AppPaymentsProperties.class)
public class AppPaymentsConfiguration {
}
