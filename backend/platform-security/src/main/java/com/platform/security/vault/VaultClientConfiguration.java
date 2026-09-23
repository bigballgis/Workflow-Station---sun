package com.platform.security.vault;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true")
class VaultClientConfiguration {

    @Bean
    VaultClientSettings vaultClientSettings(
            @Value("${vault.addr:}") String addr,
            @Value("${vault.namespace:}") String namespace,
            @Value("${vault.auth-mount:}") String authMount,
            @Value("${vault.role:}") String role,
            @Value("${vault.kv-mount:}") String kvMount,
            @Value("${vault.token-file:/var/run/secrets/kubernetes.io/serviceaccount/token}") String tokenFile,
            @Value("${vault.token:}") String staticToken,
            @Value("${vault.refresh-skew-seconds:30}") long refreshSkewSeconds) {
        return new VaultClientSettings(
                trimSlash(addr),
                trim(namespace),
                trim(authMount),
                trim(role),
                trim(kvMount),
                trim(tokenFile),
                trim(staticToken),
                refreshSkewSeconds <= 0 ? 30L : refreshSkewSeconds);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
