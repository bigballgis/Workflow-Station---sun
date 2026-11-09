package com.workflow.component;

import com.workflow.dto.request.ClaimPoolResyncRequest;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssigneeType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.repository.FlowableRuntimeTaskQueries;
import com.workflow.repository.FlowableRuntimeTaskQueries.TaskNode;
import com.workflow.util.AssigneeRoleIdsSupport;
import com.workflow.util.BpmnPoolBusinessUnit;
import com.workflow.util.MiRoleRowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds the open tasks of BU + Role claim pools and the codes naming each pool (platform access model WP8b-2): BPMN
 * BU + Role nodes, manual BU + Role assignment, and MI rows assigned by role. Hierarchy-role pools are not resynced
 * (D-38): their BU comes from an anchor user at task creation and is not kept on the task.
 *
 * <p>Every path (Admin push, periodic resync, single task) names a pool in codes the same way: current designers
 * write codes, older deployments ids, and {@link PoolCodes} turns ids into codes. A pool task carries its extended
 * row whenever it has one, so a release clears the row's claim on every path.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimPoolTaskLocator {

    private final TaskService taskService;
    private final BpmnActionParser bpmnActionParser;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final FlowableRuntimeTaskQueries runtimeTaskQueries;
    private final PoolCodes poolCodes;

    /** An open pool task; {@code miRow} is its MI row when it has one. */
    public record PoolTask(Task task, String businessUnitCode, List<String> roleCodes, ExtendedTaskInfo miRow) {
    }

    /** Every open BU + Role pool task (D-37 backstop). */
    public List<PoolTask> all() {
        return locate(null);
    }

    /** The open tasks whose pool is one of {@code pools} (Admin push after a UBR change). */
    public List<PoolTask> of(List<ClaimPoolResyncRequest.Pool> pools) {
        poolCodes.learn(pools);
        return locate(pools.stream().map(p -> p.businessUnitCode() + '|' + p.roleCode()).collect(Collectors.toSet()));
    }

    /** The task's pool, or empty when it is not a BU + Role pool task. */
    public Optional<PoolTask> of(Task task) {
        Optional<ExtendedTaskInfo> row = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(task.getId());
        Optional<MiRoleRowProperties> miPool = row.flatMap(r -> MiRoleRowProperties.parse(r.getExtendedProperties()));
        if (miPool.isPresent()) {
            return Optional.of(new PoolTask(task, miPool.get().businessUnitCode(), miPool.get().roleCodes(), row.get()));
        }
        return poolOfNode(task.getProcessDefinitionId(), task.getTaskDefinitionKey())
                .or(() -> manualPool(task))
                .map(poolCodes::of)
                .map(pool -> new PoolTask(task, pool.businessUnit(), pool.roles(), row.orElse(null)));
    }

    /** @param wanted "buCode|roleCode" of the pools to keep, or null for every pool */
    private List<PoolTask> locate(Set<String> wanted) {
        Map<Task, Pool> nodeTasks = new LinkedHashMap<>();
        for (TaskNode node : runtimeTaskQueries.distinctOpenTaskNodes()) {
            String type = property(node, "assigneeType");
            AssigneeType assigneeType = AssigneeType.fromCode(type);
            if (assigneeType == AssigneeType.BU_ROLE) {
                poolOfNode(node.processDefinitionId(), node.taskDefinitionKey()).map(poolCodes::of)
                        .filter(pool -> pool.isAmong(wanted))
                        .ifPresent(pool -> tasksOf(node).forEach(t -> nodeTasks.put(t, pool)));
            } else if (assigneeType == AssigneeType.MANUAL_ASSIGN) {
                for (Task t : tasksOf(node)) {
                    manualPool(t).map(poolCodes::of).filter(pool -> pool.isAmong(wanted))
                            .ifPresent(pool -> nodeTasks.put(t, pool));
                }
            }
        }
        Map<String, ExtendedTaskInfo> rows = nodeTasks.isEmpty() ? Map.of() : extendedTaskInfoRepository
                .findByTaskIdInAndIsDeletedFalse(nodeTasks.keySet().stream().map(Task::getId).toList()).stream()
                .collect(Collectors.toMap(ExtendedTaskInfo::getTaskId, Function.identity(), (a, b) -> a));
        List<PoolTask> found = new ArrayList<>();
        nodeTasks.forEach((t, pool) -> found.add(new PoolTask(t, pool.businessUnit(), pool.roles(), rows.get(t.getId()))));
        found.addAll(miRoleRows(wanted));
        return found;
    }

    private List<PoolTask> miRoleRows(Set<String> wanted) {
        Map<String, ExtendedTaskInfo> rows = extendedTaskInfoRepository.findOpenMiRoleRows().stream()
                .collect(Collectors.toMap(ExtendedTaskInfo::getTaskId, Function.identity(), (a, b) -> a));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<PoolTask> found = new ArrayList<>();
        for (Task t : taskService.createTaskQuery().taskIds(rows.keySet()).list()) {
            ExtendedTaskInfo row = rows.get(t.getId());
            Optional<MiRoleRowProperties> props;
            try {
                props = MiRoleRowProperties.parse(row.getExtendedProperties());
            } catch (IllegalStateException e) {
                // One corrupt row must not stop every other pool's resync; it stays as it is and is reported.
                log.error("MI role row of task {} is unreadable, its pool is not resynced: {}", t.getId(), e.getMessage());
                continue;
            }
            props.map(p -> new Pool(p.businessUnitCode(), p.roleCodes()))
                    .filter(pool -> pool.isAmong(wanted))
                    .ifPresent(p -> found.add(new PoolTask(t, p.businessUnit(), p.roles(), row)));
        }
        return found;
    }

    private Optional<Pool> poolOfNode(String processDefinitionId, String taskDefinitionKey) {
        TaskNode node = new TaskNode(processDefinitionId, taskDefinitionKey);
        if (AssigneeType.fromCode(property(node, "assigneeType")) != AssigneeType.BU_ROLE) {
            return Optional.empty();
        }
        String bu = BpmnPoolBusinessUnit.resolve(AssigneeType.BU_ROLE,
                property(node, "businessUnitId"), property(node, "assigneeValue"));
        List<String> roles = AssigneeRoleIdsSupport.parseRoleIds(property(node, "roleIds"), property(node, "roleId"));
        if (!StringUtils.hasText(bu) || roles.isEmpty()) {
            log.warn("BU Role node {}/{} names no business unit or role; its pool is not resynced",
                    processDefinitionId, taskDefinitionKey);
            return Optional.empty();
        }
        return Optional.of(new Pool(bu.trim(), roles));
    }

    /** Manual assignment that fell back to BU + Role (no user chosen), per {@code TaskAssignmentListener}. */
    private Optional<Pool> manualPool(Task task) {
        TaskNode node = new TaskNode(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        if (AssigneeType.fromCode(property(node, "assigneeType")) != AssigneeType.MANUAL_ASSIGN) {
            return Optional.empty();
        }
        String defKey = task.getTaskDefinitionKey() != null ? task.getTaskDefinitionKey() : "task";
        String userVar = variableName(node, "manualAssignVariable", "manualAssignee_" + defKey);
        String buVar = variableName(node, "manualAssignBuVariable", "manualAssignBu_" + defKey);
        String roleVar = variableName(node, "manualAssignRoleVariable", "manualAssignRole_" + defKey);
        Map<String, Object> vars = taskService.getVariables(task.getId(), List.of(userVar, buVar, roleVar));
        if (StringUtils.hasText(text(vars.get(userVar)))) {
            return Optional.empty();
        }
        String bu = text(vars.get(buVar));
        String role = text(vars.get(roleVar));
        if (!StringUtils.hasText(bu) || !StringUtils.hasText(role)) {
            return Optional.empty();
        }
        return Optional.of(new Pool(bu.trim(), List.of(role.trim())));
    }

    private List<Task> tasksOf(TaskNode node) {
        return taskService.createTaskQuery()
                .processDefinitionId(node.processDefinitionId())
                .taskDefinitionKey(node.taskDefinitionKey())
                .list();
    }

    private String property(TaskNode node, String name) {
        return bpmnActionParser.getUserTaskExtensionPropertyValue(
                node.processDefinitionId(), node.taskDefinitionKey(), name);
    }

    private String variableName(TaskNode node, String property, String defaultName) {
        String configured = property(node, property);
        return StringUtils.hasText(configured) ? configured.trim() : defaultName;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** A pool as BPMN or a row names it: BU and role values (codes; ids on older deployments until {@link PoolCodes}). */
    record Pool(String businessUnit, List<String> roles) {

        /** In {@code wanted} ("buCode|roleCode"), or {@code wanted} is null (every pool). */
        boolean isAmong(Set<String> wanted) {
            return wanted == null || roles.stream().anyMatch(role -> wanted.contains(businessUnit + '|' + role));
        }
    }
}
