package com.workflow.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Admin Center's notice that holders of these BU + Role pools changed (platform access model WP8b-2). Ids and codes
 * both come: deployed BPMN names a pool by code (current designer) or by id (older deployments).
 */
public record ClaimPoolResyncRequest(@NotEmpty @Size(max = 200) List<@Valid Pool> pools) {

    public record Pool(@Size(max = 64) String businessUnitId,
                       @NotBlank @Size(max = 64) String businessUnitCode,
                       @Size(max = 64) String roleId,
                       @NotBlank @Size(max = 64) String roleCode) {
    }
}
