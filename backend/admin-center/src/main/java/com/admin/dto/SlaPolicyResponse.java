package com.admin.dto;

import com.admin.entity.SlaPolicyHistory;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** Result of a lead time change: the saved policy plus whether its recalculation job was started. */
@Data
@Builder
public class SlaPolicyResponse {
    private String functionUnitCode;
    private Integer leadTimeDays;
    private Integer version;
    private String updatedBy;
    private Instant updatedAt;
    private SlaPolicyHistory.DispatchStatus dispatchStatus;
    private String recalcJobId;
    private String dispatchError;
}
