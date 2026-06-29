package com.developer.dto;

import com.developer.entity.EmailMonitorRule;
import com.developer.enums.EmailMonitorActionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class EmailMonitorRuleResponse {

    private Long id;
    private String ruleUid;
    private String name;
    private Boolean enabled;
    private String connectionUid;
    private String processDefinitionKey;
    private String startEventId;
    private String folderLabel;
    private String filterFrom;
    private String filterSubject;
    private EmailMonitorActionType actionType;
    private Long targetFormId;
    private String targetBindingId;
    private String systemInitiatorUserId;
    private Map<String, Object> extractionRules;
    private Map<String, Object> correlation;
    private Integer pollIntervalSeconds;
    private Boolean reviewOnMissing;
    private Instant lastSyncedAt;

    public static EmailMonitorRuleResponse fromEntity(EmailMonitorRule entity) {
        return EmailMonitorRuleResponse.builder()
                .id(entity.getId())
                .ruleUid(entity.getRuleUid())
                .name(entity.getName())
                .enabled(entity.getEnabled())
                .connectionUid(entity.getConnectionUid())
                .processDefinitionKey(entity.getProcessDefinitionKey())
                .startEventId(entity.getStartEventId())
                .folderLabel(entity.getFolderLabel())
                .filterFrom(entity.getFilterFrom())
                .filterSubject(entity.getFilterSubject())
                .actionType(entity.getActionType())
                .targetFormId(entity.getTargetFormId())
                .targetBindingId(entity.getTargetBindingId())
                .systemInitiatorUserId(entity.getSystemInitiatorUserId())
                .extractionRules(entity.getExtractionRules())
                .correlation(entity.getCorrelation())
                .pollIntervalSeconds(entity.getPollIntervalSeconds())
                .reviewOnMissing(entity.getReviewOnMissing())
                .lastSyncedAt(entity.getLastSyncedAt())
                .build();
    }
}
