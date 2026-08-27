package com.portal.component;

import com.platform.security.util.SecurityContextUtils;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationRule;
import com.portal.repository.DelegationRuleRepository;
import com.portal.util.RequestContextInheritanceUtils;
import com.portal.util.WorkflowEnginePayloadHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Queries tasks delegated to a user: standing rules plus engine single-task overlay
 * (USER {@code delegated_to} or current workspace BU+Role). Does not rewrite assignee.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegatedTaskQueryComponent {

    private static final int DELEGATOR_ENGINE_PAGE_SIZE = 200;

    private final WorkflowEngineClient workflowEngineClient;
    private final DelegationRuleRepository delegationRuleRepository;

    public List<TaskInfo> queryDelegatedTasks(String userId) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        LinkedHashMap<String, TaskInfo> byId = new LinkedHashMap<>();
        for (TaskInfo overlay : loadEngineRuntimeOverlay(userId)) {
            if (overlay.getTaskId() != null) {
                byId.putIfAbsent(overlay.getTaskId(), overlay);
            }
        }
        for (TaskInfo standing : loadStandingRuleDelegatedTasks(userId)) {
            if (standing.getTaskId() != null) {
                byId.putIfAbsent(standing.getTaskId(), standing);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<TaskInfo> loadEngineRuntimeOverlay(String userId) {
        String buId = SecurityContextUtils.getCurrentActiveBusinessUnitId().orElse(null);
        String roleId = SecurityContextUtils.getCurrentActiveRoleId().orElse(null);
        Optional<Map<String, Object>> result = workflowEngineClient.getDelegatedRuntimeTasks(buId, roleId);
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(result.get());
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        List<TaskInfo> out = new ArrayList<>();
        for (Map<String, Object> taskMap : tasks) {
            TaskInfo mapped = EngineTaskMapper.convertMapToTaskInfo(taskMap);
            mapped.setAssignmentType("DELEGATED");
            mapped.setDelegatorId(mapped.getDelegatorId() != null ? mapped.getDelegatorId() : mapped.getAssignee());
            mapped.setDelegatorName(mapped.getDelegatorName() != null ? mapped.getDelegatorName() : mapped.getAssigneeName());
            out.add(mapped);
        }
        return out;
    }

    private List<TaskInfo> loadStandingRuleDelegatedTasks(String userId) {
        List<DelegationRule> delegations = delegationRuleRepository
                .findActiveDelegationsForDelegate(userId, LocalDateTime.now());
        if (delegations.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> delegatorIds = delegations.stream()
                .map(DelegationRule::getDelegatorId)
                .collect(Collectors.toSet());

        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        List<CompletableFuture<List<TaskInfo>>> futures = delegatorIds.stream()
                .map(delegatorId -> CompletableFuture.supplyAsync(() -> RequestContextInheritanceUtils.runWithInheritedRequestAndSecurity(
                        ctx, attrs, () -> loadDelegatedTasksForDelegator(userId, delegatorId))))
                .toList();

        List<TaskInfo> delegatedTasks = new ArrayList<>();
        for (CompletableFuture<List<TaskInfo>> f : futures) {
            try {
                delegatedTasks.addAll(f.join());
            } catch (Exception e) {
                log.warn("Failed to join delegated task future: {}", e.getMessage());
            }
        }
        return delegatedTasks;
    }

    private List<TaskInfo> loadDelegatedTasksForDelegator(String delegateUserId, String delegatorId) {
        List<TaskInfo> delegatedTasks = new ArrayList<>();
        try {
            for (int p = 0; ; p++) {
                Optional<Map<String, Object>> result =
                        workflowEngineClient.getUserTasks(delegatorId, p, DELEGATOR_ENGINE_PAGE_SIZE);
                if (result.isEmpty()) {
                    break;
                }
                Map<String, Object> responseBody = result.get();
                List<Map<String, Object>> tasks = WorkflowEnginePayloadHelper.taskListFromPayload(responseBody);
                if (tasks == null || tasks.isEmpty()) {
                    break;
                }
                for (Map<String, Object> taskMap : tasks) {
                    TaskInfo taskInfo = EngineTaskMapper.convertMapToTaskInfo(taskMap);
                    TaskInfo delegatedTask = TaskInfo.builder()
                            .taskId(taskInfo.getTaskId())
                            .taskName(taskInfo.getTaskName())
                            .description(taskInfo.getDescription())
                            .processInstanceId(taskInfo.getProcessInstanceId())
                            .processDefinitionKey(taskInfo.getProcessDefinitionKey())
                            .processDefinitionName(taskInfo.getProcessDefinitionName())
                            .bpmnAssigneeType(taskInfo.getBpmnAssigneeType())
                            .bpmnBusinessUnitId(taskInfo.getBpmnBusinessUnitId())
                            .assignmentType("DELEGATED")
                            .assignee(taskInfo.getAssignee())
                            .assigneeName(taskInfo.getAssigneeName())
                            .delegatorId(delegatorId)
                            .delegatorName(delegatorId)
                            .initiatorId(taskInfo.getInitiatorId())
                            .initiatorName(taskInfo.getInitiatorName())
                            .priority(taskInfo.getPriority())
                            .status(taskInfo.getStatus())
                            .createTime(taskInfo.getCreateTime())
                            .dueDate(taskInfo.getDueDate())
                            .isOverdue(taskInfo.getIsOverdue())
                            .formKey(taskInfo.getFormKey())
                            .variables(taskInfo.getVariables())
                            .build();
                    delegatedTasks.add(delegatedTask);
                }
                if (tasks.size() < DELEGATOR_ENGINE_PAGE_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get delegated tasks for delegator {}: {}", delegatorId, e.getMessage());
        }
        return delegatedTasks;
    }
}
