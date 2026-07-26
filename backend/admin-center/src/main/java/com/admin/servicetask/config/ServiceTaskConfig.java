package com.admin.servicetask.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activepieces 集成配置类。
 */
@Configuration
@EnableConfigurationProperties(ServiceTaskProperties.class)
public class ServiceTaskConfig {
}
