package com.admin.bi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * BI 模块配置类
 */
@Configuration
@EnableConfigurationProperties(BiProperties.class)
public class BiConfig {
}
