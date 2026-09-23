package com.platform.security.vault;

/**
 * HashiCorp Vault KV v2 client used by admin-center.
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

    /**
     * {@code true} when KV v2 GET for the path returns 200.
     *
     * @throws VaultSecretUnavailableException if Vault is unconfigured, login fails, or non-404 error
     */
    boolean secretExists(String secretPath);

    /**
     * Writes {@code data.password} at the KV v2 path (create or new version).
     *
     * @throws VaultSecretNotFoundException if the path is invalid
     * @throws VaultSecretUnavailableException if Vault is unconfigured, login fails, or write fails
     */
    void writePassword(String secretPath, String password);
}
