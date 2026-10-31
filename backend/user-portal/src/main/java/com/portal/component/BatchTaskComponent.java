package com.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.BatchCompleteRequest;
import com.portal.dto.BatchCompleteResponse;
import com.portal.dto.BatchTaskGroup;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskCompletionResult;
import com.platform.common.assignment.AssignmentFailure;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import com.portal.service.TaskActionService;
import com.portal.util.BatchTaskEligibility;
import com.portal.util.WorkflowEnginePayloadHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * To Do「可批量操作」：按「流程版本 + 节点」分组，以及对选中任务逐条执行同一个通过/驳回动作。
 *
 * <p>批量提交不信任前端：每条任务重新从引擎取，复核节点 allowBatch、本人办理（未认领的 BU Role
 * 认领池任务先替本人认领）、动作属于该节点、意见必填；随后走与单条 {@code POST /tasks/{id}/complete}
 * 完全相同的路径（含动作后邮件），
 * 每条独立事务，单条失败不影响其他任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchTaskComponent {

    private final TodoListQueryComponent todoListQueryComponent;
    private final RequestIdEnricher requestIdEnricher;
    private final TaskActionService taskActionService;
    private final WorkflowEngineClient workflowEngineClient;
    private final TaskProcessComponent taskProcessComponent;
    private final BatchCompletionRecovery completion;
    private final MineTaskListCache mineTaskListCache;

    public List<BatchTaskGroup> listGroups(String userId) {
        List<TaskInfo> eligible = todoListQueryComponent.listMergedTodoTasks(userId).stream()
                .filter(t -> BatchTaskEligibility.isEligible(t, userId, BatchTaskEligibility::isOpenCandidateTask))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (eligible.isEmpty()) {
            return List.of();
        }
        // Fills functionUnitCatalogId (the release pinned at start) that the group key is built from.
        requestIdEnricher.enrichTaskRequestIds(eligible);
        Map<String, String> versions = requestIdEnricher.loadCatalogVersions(eligible.stream()
                .map(TaskInfo::getFunctionUnitCatalogId)
                .toList());

        Map<String, List<TaskInfo>> byGroup = new LinkedHashMap<>();
        for (TaskInfo t : eligible) {
            byGroup.computeIfAbsent(BatchTaskEligibility.groupKeyOf(t) + "\n" + t.getTaskDefinitionKey(),
                    k -> new ArrayList<>()).add(t);
        }
        List<BatchTaskGroup> groups = new ArrayList<>();
        for (List<TaskInfo> tasks : byGroup.values()) {
            TaskInfo first = tasks.get(0);
            List<BatchTaskGroup.BatchTaskAction> actions;
            try {
                actions = batchActions(tasks);
            } catch (com.platform.common.functionunit.FunctionUnitVersionException e) {
                // FALLBACK(ux): issue #1701 — only this group leaves the batch view (less function, no wrong
                // data); opening one of its tasks still reports the version error. 钉住的版本解析不出动作(如运行时版本化之前部署的版本)：该组不进入批量，
                // 不拖垮整个待办页；打开其中的任务时详情页同样会报这个版本错误，记日志不静默
                log.warn("Skip batch group {} at node {}: {}", BatchTaskEligibility.groupKeyOf(first),
                        first.getTaskDefinitionKey(), e.getDetails());
                continue;
            }
            if (actions.isEmpty()) {
                continue;
            }
            groups.add(new BatchTaskGroup(
                    BatchTaskEligibility.groupKeyOf(first),
                    first.getProcessDefinitionKey(),
                    first.getFunctionUnitName() != null ? first.getFunctionUnitName() : first.getProcessDefinitionName(),
                    first.getFunctionUnitCatalogId() == null ? null : versions.get(first.getFunctionUnitCatalogId()),
                    first.getTaskDefinitionKey(),
                    // A group is one node: name it by the task, not the step (an MI task's step is its sub-process).
                    first.getTaskName() != null ? first.getTaskName() : first.getCurrentStepName(),
                    tasks.size(),
                    actions));
        }
        return groups;
    }

    public BatchCompleteResponse complete(String userId, String username, BatchCompleteRequest request) {
        List<String> succeeded = new ArrayList<>();
        List<BatchCompleteResponse.Failure> failed = new ArrayList<>();
        List<BatchCompleteResponse.Pending> pending = new ArrayList<>();
        Map<String,java.util.UUID> completedOperations=new LinkedHashMap<>();
        for (String taskId : new LinkedHashSet<>(request.taskIds())) {
            try {
                var result=completion.run(taskId,request,userId,username,()->completeOne(taskId,userId,username,request));
                if (result!=null && result.state()!=null && !"READY".equals(result.state())) {
                    pending.add(new BatchCompleteResponse.Pending(taskId,result.operationId(),result.state()));
                } else {
                    succeeded.add(taskId);
                    if (result!=null && result.operationId()!=null) completedOperations.put(taskId,result.operationId());
                }
            } catch (Exception e) {
                if (request.operationIds().containsKey(taskId) && e instanceof AssignmentFailure typed
                        && "ASSIGNMENT_DEPENDENCY_UNAVAILABLE".equals(typed.getDetails().get("reason"))) {
                    pending.add(new BatchCompleteResponse.Pending(taskId,request.operationIds().get(taskId),"DELIVERY_UNKNOWN"));
                    continue;
                }
                log.warn("Batch complete failed for task {} by user {}: {}", taskId, userId, e.getMessage());
                failed.add(new BatchCompleteResponse.Failure(taskId,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        if (!succeeded.isEmpty()) {
            // The To Do list / groups / badge read a 15s Mine cache; completed tasks must leave it now.
            mineTaskListCache.invalidate();
        }
        return new BatchCompleteResponse(succeeded, failed,pending,completedOperations);
    }

    private TaskCompletionResult completeOne(String taskId, String userId, String username, BatchCompleteRequest request) {
        Map<String, Object> engineTask = fetchEngineTask(taskId)
                .orElseThrow(() -> new PortalException("404", "Task not found or already completed"));
        TaskInfo task = EngineTaskMapper.convertMapToTaskInfo(engineTask);
        if ("COMPLETED".equalsIgnoreCase(task.getStatus())) {
            throw new PortalException("409", "Task already completed");
        }
        taskProcessComponent.annotateClaimState(task, userId, username);
        if (!BatchTaskEligibility.isEligible(task, userId,
                t -> taskProcessComponent.canProcessTask(t, userId, username))) {
            throw new PortalException("403", "Task is not available for batch processing");
        }
        TaskActionInfo action = taskActionService.getTaskActionsFromPayload(engineTask).stream()
                .filter(a -> request.actionId().equals(a.getActionId()))
                .findFirst()
                .orElseThrow(() -> new PortalException("400", "Action is not configured on this node"));
        String completeAction = BatchTaskEligibility.completeActionOf(action);
        if (completeAction == null) {
            throw new PortalException("400", "Action does not support batch processing");
        }
        JsonNode config = BatchTaskEligibility.parseConfig(action);
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        if (BatchTaskEligibility.requiresComment(config) && comment == null) {
            throw new PortalException("400", "Comment is required");
        }
        if (task.isClaimable()) {
            // Unclaimed BU Role pool task: picking it for the batch is the explicit claim. A colleague
            // who claimed it meanwhile makes claimTask refuse, and only this row fails. Other candidate
            // pools need nothing here: completeTask claims them, as for a single submit.
            taskProcessComponent.claimTask(taskId, userId, username);
        }

        return completion.complete(taskId,request,completeAction,userId,username);
    }

    /**
     * Actions are per node of one process version, so any open task of the group answers;
     * a task closed since the list was read (another tab, a colleague) carries no actions.
     */
    private List<BatchTaskGroup.BatchTaskAction> batchActions(List<TaskInfo> groupTasks) {
        Optional<Map<String, Object>> engineTask = groupTasks.stream()
                .map(t -> fetchEngineTask(t.getTaskId()))
                .flatMap(Optional::stream)
                .filter(m -> !"COMPLETED".equalsIgnoreCase(String.valueOf(m.get("status"))))
                .findFirst();
        if (engineTask.isEmpty()) {
            return List.of();
        }
        List<BatchTaskGroup.BatchTaskAction> out = new ArrayList<>();
        for (TaskActionInfo a : taskActionService.getTaskActionsFromPayload(engineTask.get())) {
            if (BatchTaskEligibility.completeActionOf(a) == null) {
                continue;
            }
            JsonNode config;
            try {
                config = BatchTaskEligibility.parseConfig(a);
            } catch (IllegalArgumentException e) {
                // 配置损坏的动作不进入批量（详情页同样会对它报错），记日志不静默
                log.warn("Skip batch action {} with invalid configJson", a.getActionId());
                continue;
            }
            out.add(new BatchTaskGroup.BatchTaskAction(a.getActionId(), a.getActionName(), a.getActionType(),
                    a.getIcon(), a.getButtonColor(), BatchTaskEligibility.requiresComment(config),
                    BatchTaskEligibility.confirmMessage(config), a.getConfigJson()));
        }
        return out;
    }

    private Optional<Map<String, Object>> fetchEngineTask(String taskId) {
        return workflowEngineClient.getTaskById(taskId)
                .map(WorkflowEnginePayloadHelper::singleTaskFromPayload);
    }
}
