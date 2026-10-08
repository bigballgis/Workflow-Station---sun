package com.platform.security.vault;

/**
 * Vault address missing, Kubernetes login failed, network error, or HTTP 5xx.
 */
public class VaultSecretUnavailableException extends RuntimeException {

    public VaultSecretUnavailableException(String message) {
        super(message);
    }

    public VaultSecretUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
