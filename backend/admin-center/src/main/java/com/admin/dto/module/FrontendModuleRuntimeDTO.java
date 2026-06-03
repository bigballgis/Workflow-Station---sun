package com.admin.dto.module;

import com.admin.entity.module.FrontendModuleRegistry;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Runtime-safe DTO returned to host apps (user-portal).
 * Excludes internal fields like id, tenantId, createdBy, timestamps.
 */
@Data
@Builder
public class FrontendModuleRuntimeDTO {

    private String moduleCode;
    private String displayName;
    private String routePath;
    private String icon;
    private Integer orderNo;
    private String remoteEntryUrl;
    private String exposedModule;
    private List<String> requiredPermissions;
    private List<String> tenantScope;
    private String version;

    public static FrontendModuleRuntimeDTO from(FrontendModuleRegistry entity) {
        return FrontendModuleRuntimeDTO.builder()
                .moduleCode(entity.getModuleCode())
                .displayName(entity.getDisplayName())
                .routePath(entity.getRoutePath())
                .icon(entity.getIcon())
                .orderNo(entity.getOrderNo())
                .remoteEntryUrl(entity.getRemoteEntryUrl())
                .exposedModule(entity.getExposedModule())
                .requiredPermissions(entity.getRequiredPermissions())
                .tenantScope(entity.getTenantScope())
                .version(entity.getVersion())
                .build();
    }
}
