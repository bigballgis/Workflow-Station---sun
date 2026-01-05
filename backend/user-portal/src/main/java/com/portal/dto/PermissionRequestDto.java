package com.portal.dto;

import com.portal.enums.PermissionRequestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDto {
    private PermissionRequestType type;
    private List<String> permissions;
    private String reason;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
}
