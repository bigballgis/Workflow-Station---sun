package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstanceInfo {
    private String id;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processDefinitionName;
    private String businessKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String startUserId;
    private String startUserName;
    private String currentNode;
    private String currentAssignee;
    private String candidateUsers;
    private Map<String, Object> variables;
}
