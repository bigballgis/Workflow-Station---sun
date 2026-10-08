package com.platform.security.vault;

/**
 * Admin-center Vault connection settings. Tokens are never logged.
 */
public record VaultClientSettings(
        String addr,
        String namespace,
        String authMount,
        String role,
        String kvMount,
        String tokenFile,
        String staticToken,
        long refreshSkewSeconds) {
}
