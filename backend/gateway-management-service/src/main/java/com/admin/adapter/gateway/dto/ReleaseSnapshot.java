package com.admin.adapter.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseSnapshot {
    private Long releaseId;
    private String releaseNo;
    private String environmentCode;
    private List<Map<String, Object>> apiVersions;
    private List<Map<String, Object>> accessPolicies;
    private List<Map<String, Object>> trafficPolicies;
    private String snapshotHash;
}
