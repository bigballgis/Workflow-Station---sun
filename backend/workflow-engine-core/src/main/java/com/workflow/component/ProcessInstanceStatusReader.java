package com.workflow.component;

import lombok.extern.slf4j.Slf4j;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process Instance Status Reader
 * Read-only lookups for process-instance status and current activity node,
 * resolving against both runtime and historic data.
 *
 * Extracted from {@link ProcessEngineComponent}; behavior is preserved verbatim.
 */
@Slf4j
@Component
@Transactional
public class ProcessInstanceStatusReader {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    /**
     * Get process instance status
     * Used to check if process is completed and get last activity node
     */
    public Map<String, Object> getProcessInstanceStatus(String processInstanceId) {
        Map<String, Object> status = new HashMap<>();

        try {
            // First check runtime process instance
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (processInstance != null) {
                // Process is still running
                status.put("completed", false);
                status.put("processInstanceId", processInstanceId);
                status.put("state", processInstance.isSuspended() ? "SUSPENDED" : "RUNNING");

                // Get current active tasks
                List<Task> tasks = taskService.createTaskQuery()
                        .processInstanceId(processInstanceId)
                        .list();

                if (!tasks.isEmpty()) {
                    Task currentTask = tasks.get(0);
                    status.put("nextTaskName", currentTask.getName());
                    status.put("nextAssignee", currentTask.getAssignee());
                    status.put("nextTaskId", currentTask.getId());

                    List<String> candidateUserIds = new ArrayList<>();
                    for (org.flowable.identitylink.api.IdentityLink link
                            : taskService.getIdentityLinksForTask(currentTask.getId())) {
                        if ("candidate".equals(link.getType())
                                && link.getUserId() != null
                                && !link.getUserId().isBlank()) {
                            candidateUserIds.add(link.getUserId().trim());
                        }
                    }
                    if (!candidateUserIds.isEmpty()) {
                        status.put("nextCandidateUsers", String.join(",", candidateUserIds));
                    }
                }

                return status;
            }

            // Process not in runtime table, check history
            HistoricProcessInstance historicProcessInstance = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (historicProcessInstance != null) {
                status.put("completed", true);
                status.put("processInstanceId", processInstanceId);
                status.put("state", "COMPLETED");
                status.put("endTime", historicProcessInstance.getEndTime());

                // Get last activity node (prioritize end events)
                List<HistoricActivityInstance> endEvents = historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .activityType("endEvent")
                        .orderByHistoricActivityInstanceStartTime()
                        .desc()
                        .list();

                if (!endEvents.isEmpty()) {
                    HistoricActivityInstance endEvent = endEvents.get(0);
                    String activityName = endEvent.getActivityName();
                    if (activityName != null && !activityName.isEmpty() &&
                        !activityName.equalsIgnoreCase("End")) {
                        status.put("lastActivityName", activityName);
                        return status;
                    }
                }

                // If end event has no meaningful name, get the last user task
                List<HistoricActivityInstance> userTasks = historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .activityType("userTask")
                        .finished()
                        .orderByHistoricActivityInstanceEndTime()
                        .desc()
                        .list();

                if (!userTasks.isEmpty()) {
                    status.put("lastActivityName", userTasks.get(0).getActivityName());
                } else {
                    status.put("lastActivityName", "Completed");
                }

                return status;
            }

            // Process instance does not exist
            status.put("completed", false);
            status.put("error", "Process instance does not exist");
            return status;

        } catch (Exception e) {
            log.error("Failed to get process instance status: {}", e.getMessage(), e);
            status.put("completed", false);
            status.put("error", e.getMessage());
            return status;
        }
    }

    /**
     * Get current activity node of process instance
     */
    public Map<String, Object> getCurrentActivity(String processInstanceId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // First check runtime process instance
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (processInstance != null) {
                // Process still running, get current activity nodes
                List<org.flowable.engine.runtime.Execution> executions = runtimeService
                        .createExecutionQuery()
                        .processInstanceId(processInstanceId)
                        .list();

                org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());

                // Prioritize non-SequenceFlow activity nodes (userTask, endEvent, gateway, etc.)
                // SequenceFlow name is typically a condition label (e.g. "Yes"/"No"), not suitable as currentNode
                Map<String, Object> fallback = null;
                for (org.flowable.engine.runtime.Execution execution : executions) {
                    String activityId = execution.getActivityId();
                    if (activityId != null) {
                        org.flowable.bpmn.model.FlowElement flowElement = bpmnModel.getFlowElement(activityId);

                        if (flowElement != null) {
                            // Skip SequenceFlow - its name is a condition label (e.g. "Yes"/"No"), not a node name
                            if (flowElement instanceof org.flowable.bpmn.model.SequenceFlow) {
                                log.debug("Skipping SequenceFlow {} (name: {}) for process {}",
                                        activityId, flowElement.getName(), processInstanceId);
                                if (fallback == null) {
                                    fallback = new HashMap<>();
                                    fallback.put("activityId", activityId);
                                    fallback.put("activityName", flowElement.getName());
                                    fallback.put("activityType", "SequenceFlow");
                                    fallback.put("processInstanceId", processInstanceId);
                                    fallback.put("state", "RUNNING");
                                }
                                continue;
                            }

                            result.put("activityId", activityId);
                            result.put("activityName", flowElement.getName());
                            result.put("activityType", flowElement.getClass().getSimpleName().replace("Impl", ""));
                            result.put("processInstanceId", processInstanceId);
                            result.put("state", "RUNNING");

                            log.info("Current activity for process {}: {} ({})",
                                    processInstanceId, flowElement.getName(), flowElement.getClass().getSimpleName());

                            return result;
                        }
                    }
                }

                // If only SequenceFlow found, try to resolve its target node as current activity
                if (fallback != null) {
                    String seqFlowId = (String) fallback.get("activityId");
                    org.flowable.bpmn.model.FlowElement seqFlow = bpmnModel.getFlowElement(seqFlowId);
                    if (seqFlow instanceof org.flowable.bpmn.model.SequenceFlow) {
                        String targetRef = ((org.flowable.bpmn.model.SequenceFlow) seqFlow).getTargetRef();
                        org.flowable.bpmn.model.FlowElement targetElement = bpmnModel.getFlowElement(targetRef);
                        if (targetElement != null) {
                            result.put("activityId", targetRef);
                            result.put("activityName", targetElement.getName());
                            result.put("activityType", targetElement.getClass().getSimpleName().replace("Impl", ""));
                            result.put("processInstanceId", processInstanceId);
                            result.put("state", "RUNNING");

                            log.info("Current activity (resolved from SequenceFlow target) for process {}: {} ({})",
                                    processInstanceId, targetElement.getName(), targetElement.getClass().getSimpleName());
                            return result;
                        }
                    }
                    // Cannot resolve target node, return SequenceFlow as last resort
                    log.warn("Could not resolve SequenceFlow target for process {}, returning SequenceFlow as fallback", processInstanceId);
                    return fallback;
                }

                result.put("error", "Current activity node not found");
                return result;
            }

            // Process not in runtime table, check history
            HistoricProcessInstance historicProcessInstance = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (historicProcessInstance != null) {
                // Process completed, get last activity node
                List<HistoricActivityInstance> activities = historyService
                        .createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .orderByHistoricActivityInstanceStartTime()
                        .desc()
                        .list();

                if (!activities.isEmpty()) {
                    HistoricActivityInstance lastActivity = activities.get(0);
                    result.put("activityId", lastActivity.getActivityId());
                    result.put("activityName", lastActivity.getActivityName());
                    result.put("activityType", lastActivity.getActivityType());
                    result.put("processInstanceId", processInstanceId);
                    result.put("state", "COMPLETED");
                    return result;
                }
            }

            result.put("error", "Process instance does not exist");
            return result;

        } catch (Exception e) {
            log.error("Failed to get current activity: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
