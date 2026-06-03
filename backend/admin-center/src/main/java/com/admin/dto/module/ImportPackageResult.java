package com.admin.dto.module;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImportPackageResult {
    boolean success;
    String moduleCode;
    String version;
    Long registryId;
    String remoteEntryUrl;
    String error;
}
