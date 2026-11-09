package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reassignment of a pool task to a member of its pool with Portal access (the actor themselves = take over): by the
 * BU Approver or a Leader of the BU + Role, or by any pool member once the holder lost Portal access (§7.3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskReassignClaimRequest {

    @NotBlank(message = "Target user is required")
    private String targetUserId;
}
