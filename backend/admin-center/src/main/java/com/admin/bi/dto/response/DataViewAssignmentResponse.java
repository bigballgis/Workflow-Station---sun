package com.admin.bi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataViewAssignmentResponse {
    private String id;
    private String dashboardId;
    private String dashboardTitle;
    private String functionUnitCode;
    private String functionUnitName;
    private String tableName;
    private String tableDisplayName;
    private String tableType;
    private String viewName;
    private Boolean viewAvailable;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** List only: whether the caller may change it — its unit is of a team they lead, or SYS_ADMIN (D-15, AC-32). */
    private Boolean canEdit;
}
