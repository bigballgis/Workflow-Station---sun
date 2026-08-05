package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Bind an Email Monitor template to a BPMN Start Event with per-event filters.
 */
@Data
public class EmailMonitorStartEventBindRequest {

    @NotNull
    private Long templateRuleId;

    @NotBlank
    private String startEventId;

    @NotBlank
    private String processDefinitionKey;

    private String filterFrom;

    private String filterSubject;

    private Boolean enabled = true;
}
