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

    /**
     * {@code true} when KV v2 {@code data.data} contains a non-blank string for {@code field}.
     * Missing secret (404) is {@code false}.
     */
    boolean dataFieldExists(String secretPath, String field);

    /**
     * Returns the string at KV v2 {@code data.data[field]}.
     *
     * @throws VaultSecretNotFoundException if the secret or field is missing
     */
    String readDataField(String secretPath, String field);

    /**
     * GET-merge-POST: writes {@code data.data[field]} without dropping other keys at the path.
     */
    void upsertDataField(String secretPath, String field, String value);
}
