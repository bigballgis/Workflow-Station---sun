package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AllocatePrimaryKeyRequest {

    @NotBlank
    private String fieldName;

    private Integer count;

    private String scopeKey;
}
