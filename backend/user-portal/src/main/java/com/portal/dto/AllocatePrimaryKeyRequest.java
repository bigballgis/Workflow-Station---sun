package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AllocatePrimaryKeyRequest {

    @NotNull
    private Long tableId;

    @NotBlank
    private String fieldName;

    private Integer count;

    private String scopeKey;

    /** Optional frozen batch control; ordinary single-row allocation keeps its existing contract. */
    private String batchControlId;
}
