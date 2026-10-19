package com.workflow.dto.response;

import java.util.Map;

/** Authoritative write scope from an active task's deployed BPMN and execution, not process-wide variables. */
public record TaskWriteContext(String taskId, String processInstanceId, String processDefinitionId,
                               String taskDefinitionKey, boolean multiInstance, Map<String, Object> currentItem) { }
