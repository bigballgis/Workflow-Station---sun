package com.admin.ap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activepieces 集成配置类。
 */
@Configuration
@EnableConfigurationProperties(ActivepiecesProperties.class)
public class ApConfig {
}
