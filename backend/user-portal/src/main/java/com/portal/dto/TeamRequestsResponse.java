package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Team requests overview response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRequestsResponse {

    private long overallCount;
    private long runningCount;
    private long completedCount;
    private long withdrawnCount;

    private List<TeamRequestItem> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamRequestItem {
        private String id;
        /** 主表配置的人类可读单号(如 ATM-DC-PW-000018);主表未配置 Request ID 时为 null。 */
        private String requestId;
        private String processDefinitionName;
        private String businessKey;
        private String startUserName;
        private String status;
        private String currentNode;
        private String currentAssignee;
        private LocalDateTime startTime;
        private LocalDateTime completedAt;
    }
}
