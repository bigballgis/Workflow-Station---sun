package com.admin.dto;

import com.admin.enums.EnvironmentValueKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class EnvironmentVariableRequest {

    @NotBlank
    @Size(max = 100)
    private String varKey;

    @NotNull
    private EnvironmentValueKind valueKind;

    @NotBlank
    @Size(max = 150)
    private String displayName;

    @Size(max = 500)
    private String description;

    private String defaultValue;

    private String currentValue;

    @Size(max = 512)
    private String vaultSecretPath;

    /** Write-only. Never persisted or returned; used to create the Vault KV secret. */
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 512)
    private String vaultPassword;
}
