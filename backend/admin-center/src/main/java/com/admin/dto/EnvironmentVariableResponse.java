package com.admin.dto;

import com.admin.entity.EnvironmentVariable;
import com.admin.enums.EnvironmentValueKind;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
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
                .build();
    }
}
