package com.admin.bi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Creates several dashboard bindings for one Data View in a single transaction. */
@Data
@NoArgsConstructor
public class DataViewAssignmentBatchRequest {

    @NotEmpty(message = "At least one Dashboard ID is required")
    private List<@NotBlank(message = "Dashboard ID is required") String> dashboardIds;

    @NotBlank(message = "Target function unit is required")
    private String functionUnitCode;

    /** Unique within the function unit; resolved in the unit's active release. */
    @NotBlank(message = "Table View is required")
    private String viewName;

    private Boolean enabled;
}
