package com.admin.dto.module;

import com.admin.entity.module.FrontendModuleVersion;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FrontendModuleVersionDTO {

    private Long id;
    private String version;
    private String remoteEntryUrl;
    private Boolean isActive;
    private String releaseNote;
    private String createdBy;
    private Instant createdAt;

    public static FrontendModuleVersionDTO from(FrontendModuleVersion entity) {
        return FrontendModuleVersionDTO.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .remoteEntryUrl(entity.getRemoteEntryUrl())
                .isActive(entity.getIsActive())
                .releaseNote(entity.getReleaseNote())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
