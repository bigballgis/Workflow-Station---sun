package com.developer.dto;

import com.developer.entity.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for version history display in frontend.
 * Maps Version entity fields to frontend-expected field names.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionResponse {
    private Long id;
    private String versionNumber;
    private String changeLog;
    private String createdBy;
    private Instant createdAt;

    public static VersionResponse from(Version version) {
        return VersionResponse.builder()
                .id(version.getId())
                .versionNumber(version.getVersionNumber())
                .changeLog(version.getChangeLog())
                .createdBy(version.getPublishedBy())
                .createdAt(version.getPublishedAt())
                .build();
    }
}
