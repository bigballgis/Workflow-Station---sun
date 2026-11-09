package com.admin.bi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DataViewAssignmentRequest {

    @NotBlank(message = "Dashboard ID is required")
    private String dashboardId;

    @NotBlank(message = "Target function unit is required")
    private String functionUnitCode;

    /** Unique within the function unit; resolved in the unit's active release. */
    @NotBlank(message = "Table View is required")
    private String viewName;

    private Boolean enabled;
}
