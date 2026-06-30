package com.developer.dto;

import com.developer.enums.EmailMonitorActionType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Create/update payload for an inbound email monitor rule (design-time).
 */
@Data
public class EmailMonitorRuleRequest {

    @NotBlank
    private String name;

    private Boolean enabled = true;

    /** UID of the inbound OAuth connection this rule watches. */
    @NotBlank
    private String connectionUid;

    private String processDefinitionKey;

    private String startEventId;

    private String folderLabel = "INBOX";

    private String filterFrom;

    private String filterSubject;

    private EmailMonitorActionType actionType = EmailMonitorActionType.START_PROCESS;

    private Long targetFormId;

    private String targetBindingId;

    private String systemInitiatorUserId;

    private Map<String, Object> extractionRules;

    private Map<String, Object> correlation;

    private Integer pollIntervalSeconds = 60;

    private Boolean reviewOnMissing = true;
}
