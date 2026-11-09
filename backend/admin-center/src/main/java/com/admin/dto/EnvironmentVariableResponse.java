package com.admin.dto;

import com.admin.entity.EnvironmentVariable;
import com.admin.enums.EnvironmentValueKind;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class EnvironmentVariableResponse {

    private String id;
    private String varKey;
    private String deployEnv;
    private EnvironmentValueKind valueKind;
    private String displayName;
    private String description;
    private String defaultValue;
    private String currentValue;
    private String vaultSecretPath;
    /** VAULT only: whether a secret path is set; shown instead of the path to everyone but SYS_ADMIN (D-44). */
    private Boolean vaultConfigured;

    public static EnvironmentVariableResponse fromEntity(EnvironmentVariable entity) {
        return EnvironmentVariableResponse.builder()
                .id(entity.getId())
                .varKey(entity.getVarKey())
                .deployEnv(entity.getDeployEnv())
                .valueKind(entity.getValueKind())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .defaultValue(entity.getDefaultValue())
                .currentValue(entity.getCurrentValue())
                .vaultSecretPath(entity.getVaultSecretPath())
                .vaultConfigured(entity.getValueKind() == EnvironmentValueKind.VAULT
                        ? entity.getVaultSecretPath() != null && !entity.getVaultSecretPath().isBlank()
                        : null)
                .build();
    }

    /** A copy for a caller who may not see secrets: a VAULT entry keeps its key and "configured", nothing else. */
    public EnvironmentVariableResponse withoutSecrets() {
        if (valueKind != EnvironmentValueKind.VAULT) {
            return this;
        }
        return toBuilder().vaultSecretPath(null).currentValue(null).defaultValue(null).build();
    }
}
