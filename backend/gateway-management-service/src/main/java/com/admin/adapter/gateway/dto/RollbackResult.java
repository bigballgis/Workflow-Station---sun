package com.admin.adapter.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResult {
    private boolean success;
    private String runtimeRevision;
    private String errorMessage;
    private String errorCode;

    public static RollbackResult success(String revision) {
        return RollbackResult.builder()
                .success(true)
                .runtimeRevision(revision)
                .build();
    }

    public static RollbackResult failure(String errorCode, String errorMessage) {
        return RollbackResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
