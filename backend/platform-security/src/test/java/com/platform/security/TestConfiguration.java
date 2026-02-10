package com.platform.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test configuration for platform-security module tests.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.platform.security.entity")
@EnableJpaRepositories(basePackages = "com.platform.security.repository")
public class TestConfiguration {
}
