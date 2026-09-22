package com.platform.security.vault;

/**
 * Read-only HashiCorp Vault KV v2 client used by admin-center.
 * Kubernetes JWT login (or optional static {@code VAULT_TOKEN}); never exposes the client token.
 */
public interface VaultSecretClient {

    /**
     * Returns {@code data.data.password} for the KV v2 secret path.
     *
     * @throws VaultSecretNotFoundException if Vault returns 404
     * @throws VaultSecretUnavailableException if Vault is unconfigured, login fails, or 5xx
     */
    String readPassword(String secretPath);
}
