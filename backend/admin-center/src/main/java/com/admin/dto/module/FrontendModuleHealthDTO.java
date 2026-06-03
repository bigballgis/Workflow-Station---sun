package com.admin.dto.module;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FrontendModuleHealthDTO {

    private Long id;
    private Long moduleRegistryId;
    private String status;
    private String detail;
    private Instant checkedAt;

    public static FrontendModuleHealthDTO from(
            com.admin.entity.module.FrontendModuleHealthLog entity) {
        return FrontendModuleHealthDTO.builder()
                .id(entity.getId())
                .moduleRegistryId(entity.getModuleRegistryId())
                .status(entity.getStatus())
                .detail(entity.getDetail())
                .checkedAt(entity.getCheckedAt())
                .build();
    }
}
