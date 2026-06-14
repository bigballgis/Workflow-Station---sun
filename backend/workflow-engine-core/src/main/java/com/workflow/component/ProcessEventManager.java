package com.workflow.component;

import com.workflow.dto.response.EventTriggerResult;
import com.workflow.dto.response.SubProcessInfo;
import com.workflow.exception.WorkflowBusinessException;

import lombok.extern.slf4j.Slf4j;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SubProcess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Process Event Manager
 * Handles process event triggering (message/signal/timer) and sub-process queries.
 *
 * Extracted from {@link ProcessEngineComponent}; behavior is preserved verbatim.
 */
@Slf4j
@Component
@Transactional
public class ProcessEventManager {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * Trigger process event
     * Supports triggering and propagation of message events, signal events, etc.
     */
    public EventTriggerResult triggerEvent(String processInstanceId, String eventId, String eventType, Map<String, Object> eventData) {
        try {
            // Verify process instance exists
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }

            List<String> triggeredExecutions = new ArrayList<>();
            String resultMessage = "";

            switch (eventType.toLowerCase()) {
                case "message" -> {
                    // Trigger message event
                    if (eventData != null && eventData.containsKey("messageName")) {
                        String messageName = (String) eventData.get("messageName");

                        // Find execution instances waiting for this message
                        List<Execution> messageExecutions = runtimeService.createExecutionQuery()
                                .processInstanceId(processInstanceId)
                                .messageEventSubscriptionName(messageName)
                                .list();

                        for (Execution execution : messageExecutions) {
                            runtimeService.messageEventReceived(messageName, execution.getId(), eventData);
                            triggeredExecutions.add(execution.getId());
                        }

                        resultMessage = "Triggered message event: " + messageName + ", affected executions: " + triggeredExecutions.size();
                    } else {
                        throw new WorkflowBusinessException("INVALID_EVENT_DATA", "Message event requires messageName parameter");
                    }
                }
                case "signal" -> {
                    // Trigger signal event
                    if (eventData != null && eventData.containsKey("signalName")) {
                        String signalName = (String) eventData.get("signalName");

                        // Find execution instances waiting for this signal
                        List<Execution> signalExecutions = runtimeService.createExecutionQuery()
                                .processInstanceId(processInstanceId)
                                .signalEventSubscriptionName(signalName)
                                .list();

                        for (Execution execution : signalExecutions) {
                            runtimeService.signalEventReceived(signalName, execution.getId(), eventData);
                            triggeredExecutions.add(execution.getId());
                        }

                        resultMessage = "Triggered signal event: " + signalName + ", affected executions: " + triggeredExecutions.size();
                    } else {
                        throw new WorkflowBusinessException("INVALID_EVENT_DATA", "Signal event requires signalName parameter");
                    }
                }
                case "timer" -> {
                    // Trigger timer event (usually auto-triggered by system, manual trigger provided here)
                    List<Execution> timerExecutions = runtimeService.createExecutionQuery()
                            .processInstanceId(processInstanceId)
                            .activityId(eventId)
                            .list();

                    for (Execution execution : timerExecutions) {
                        // Manually advance timer event
                        runtimeService.trigger(execution.getId(), eventData);
                        triggeredExecutions.add(execution.getId());
                    }

                    resultMessage = "Triggered timer event: " + eventId + ", affected executions: " + triggeredExecutions.size();
                }
                default -> {
                    throw new WorkflowBusinessException("UNSUPPORTED_EVENT_TYPE", "Unsupported event type: " + eventType);
                }
            }

            return EventTriggerResult.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .processInstanceId(processInstanceId)
                    .triggeredExecutions(triggeredExecutions)
                    .eventData(eventData)
                    .triggerTime(LocalDateTime.now())
                    .success(true)
                    .message(resultMessage)
                    .build();

        } catch (Exception e) {
            return EventTriggerResult.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .processInstanceId(processInstanceId)
                    .triggeredExecutions(new ArrayList<>())
                    .eventData(eventData)
                    .triggerTime(LocalDateTime.now())
                    .success(false)
                    .message("Failed to trigger event: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get sub-process information
     * Supports nested execution queries for sub-processes and call activities
     */
    public List<SubProcessInfo> getSubProcesses(String processInstanceId) {
        try {
            // Verify process instance exists
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (processInstance == null) {
                throw new WorkflowBusinessException("PROCESS_NOT_FOUND", "Process instance does not exist: " + processInstanceId);
            }

            List<SubProcessInfo> subProcesses = new ArrayList<>();

            // Find sub-process instances
            List<ProcessInstance> childProcessInstances = runtimeService.createProcessInstanceQuery()
                    .superProcessInstanceId(processInstanceId)
                    .list();

            for (ProcessInstance childProcess : childProcessInstances) {
                // Get call activity info
                Execution superExecution = runtimeService.createExecutionQuery()
                        .executionId(childProcess.getSuperExecutionId())
                        .singleResult();

                SubProcessInfo subProcessInfo = SubProcessInfo.builder()
                        .subProcessInstanceId(childProcess.getId())
                        .subProcessDefinitionKey(childProcess.getProcessDefinitionKey())
                        .subProcessDefinitionName(childProcess.getName())
                        .callActivityId(superExecution != null ? superExecution.getActivityId() : null)
                        .businessKey(childProcess.getBusinessKey())
                        .startTime(childProcess.getStartTime() != null ?
                            LocalDateTime.ofInstant(childProcess.getStartTime().toInstant(), ZoneId.systemDefault()) : null)
                        .startUserId(childProcess.getStartUserId())
                        .isActive(!childProcess.isEnded())
                        .isSuspended(childProcess.isSuspended())
                        .build();

                subProcesses.add(subProcessInfo);
            }

            // Find embedded sub-processes (within same process instance)
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();

            for (Execution execution : executions) {
                if (execution.getActivityId() != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(execution.getActivityId());

                    if (flowElement instanceof SubProcess) {
                        SubProcess subProcess = (SubProcess) flowElement;

                        SubProcessInfo subProcessInfo = SubProcessInfo.builder()
                                .subProcessInstanceId(execution.getId())
                                .subProcessDefinitionKey(subProcess.getId())
                                .subProcessDefinitionName(subProcess.getName())
                                .callActivityId(null) // Embedded sub-process has no call activity
                                .businessKey(null)
                                .startTime(LocalDateTime.now()) // Embedded sub-process start time is hard to determine precisely
                                .startUserId(processInstance.getStartUserId())
                                .isActive(!execution.isEnded())
                                .isSuspended(false)
                                .isEmbedded(true)
                                .build();

                        subProcesses.add(subProcessInfo);
                    }
                }
            }

            return subProcesses;

        } catch (Exception e) {
            throw new WorkflowBusinessException("SUBPROCESS_QUERY_ERROR", "Failed to query sub-processes: " + e.getMessage(), e);
        }
    }
}
