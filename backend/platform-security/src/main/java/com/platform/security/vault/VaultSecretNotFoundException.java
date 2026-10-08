package com.platform.security.vault;

/**
 * Vault KV path is missing or the current version is deleted (HTTP 404).
 */
public class VaultSecretNotFoundException extends RuntimeException {

    public VaultSecretNotFoundException(String message) {
        super(message);
    }

    public VaultSecretNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
