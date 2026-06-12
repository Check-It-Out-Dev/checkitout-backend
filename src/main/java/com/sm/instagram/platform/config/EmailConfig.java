package com.sm.instagram.platform.config;

import com.sm.instagram.platform.common.email.config.EmailConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(EmailConfigurationProperties.class)
public class EmailConfig {
}
