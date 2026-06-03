package com.admin.dto.module;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MfeManifest {
    @NotBlank
    private String moduleCode;
    @NotBlank
    private String version;
    private String displayName;
    @NotBlank
    private String hostApp;
    @NotBlank
    private String routePath;
    private String icon;
    @Builder.Default
    private int orderNo = 100;
    @Builder.Default
    private String exposedModule = "./App";
    @Builder.Default
    private List<String> requiredPermissions = new ArrayList<>();
    @Builder.Default
    private List<String> tenantScope = new ArrayList<>();
}
