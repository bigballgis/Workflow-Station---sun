package com.admin.adapter.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResult {
    private boolean success;
    private String runtimeRevision;
    private String errorMessage;
    private String errorCode;

    public static PublishResult success(String revision) {
        return PublishResult.builder()
                .success(true)
                .runtimeRevision(revision)
                .build();
    }

    public static PublishResult failure(String errorCode, String errorMessage) {
        return PublishResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
