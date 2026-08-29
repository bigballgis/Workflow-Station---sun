package com.portal.component;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Queries tasks delegated to a user: active delegation rules come from the local database,
 * the delegators' pending tasks from the workflow engine (in parallel per delegator).
 * Extracted from {@link TaskQueryComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegatedTaskQueryComponent {

    /** Page size for a single delegator task fetch (avoid oversized responses) */
    private static final int DELEGATOR_ENGINE_PAGE_SIZE = 200;

    private final WorkflowEngineClient workflowEngineClient;
    private final DelegationRuleRepository delegationRuleRepository;

    /**
     * Query tasks delegated to a user.
     *
     * Delegation info is stored in the local database and combined with Flowable task info.
     */
    public List<TaskInfo> queryDelegatedTasks(String userId) {
        // Check if the Flowable engine is available
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        // Get active delegation rules where the current user is the delegate
        List<DelegationRule> delegations = delegationRuleRepository
                .findActiveDelegationsForDelegate(userId, LocalDateTime.now());

        if (delegations.isEmpty()) {
            return Collections.emptyList();
        }

        // Get list of delegators
        Set<String> delegatorIds = delegations.stream()
                .map(DelegationRule::getDelegatorId)
                .collect(Collectors.toSet());

        SecurityContext ctx = SecurityContextHolder.getContext();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        // 说明：每个 delegator 的叶子任务只做 workflow-engine HTTP（不碰 DB），不参与连接放大，
        // 因此保留 commonPool；也避免“运行在有界池上的 delegatedFuture 又向同一有界池提交子任务并 join”
        // 造成的有界池自等待死锁（父占满线程等子、子在队列无线程可跑）。
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

    /**
     * Convert one delegator's pending tasks into a list with the delegate as assignee
     * (single delegator; can query multiple delegators in parallel).
     */
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
                            .bpmnRoleIds(taskInfo.getBpmnRoleIds())
                            .assignmentType("DELEGATED")
                            .assignee(delegateUserId)
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
