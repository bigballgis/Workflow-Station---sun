package com.portal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PortalInternalApiProperties.class)
public class PortalInternalApiConfiguration {
}
