package com.platform.common.mail;

import com.platform.common.util.StringUtils;

import java.util.Map;

/**
 * Shared helpers for Email Connection export/import/sync payloads.
 */
public final class EmailConnectionPortability {

    private EmailConnectionPortability() {
    }

    /**
     * Read environment-variable key for mailbox password. Ciphertext keys from old packages
     * are ignored — operators must bind a VAULT variable.
     */
    public static String readPasswordEnvKey(Map<String, Object> connectionData) {
        if (connectionData == null) {
            return null;
        }
        Object key = connectionData.get("passwordEnvKey");
        if (key instanceof String text && StringUtils.isNotBlank(text)) {
            return text.trim();
        }
        return null;
    }

    /**
     * @deprecated mailbox passwords are no longer imported from ZIP ciphertext
     */
    public static String readEncryptedCredential(Map<String, Object> connectionData) {
        return null;
    }
}
