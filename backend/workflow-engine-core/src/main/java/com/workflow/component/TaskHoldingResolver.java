package com.workflow.component;

import com.platform.security.service.LostEntryTaskPolicy;
import com.platform.security.service.LostEntryTaskPolicy.PoolKind;
import com.platform.security.service.LostEntryTaskPolicy.TaskHolding;
import com.workflow.client.VirtualGroupMembersClient;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssigneeType;
import com.workflow.enums.AssignmentType;
import com.workflow.listener.AssigneeUserIdNormalizer;
import com.workflow.repository.EngineActorIdentities;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.util.MiRoleRowProperties;
import com.workflow.util.TaskPoolKinds;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a Flowable task as {@link LostEntryTaskPolicy} sees it: who holds it, how its people were chosen and who is in
 * its pool (platform access model §7.3, D-17, D-39).
 *
 * <p>The kind follows {@link TaskPoolKinds}, the rule the task DTOs carry to the Portal. Pool members: candidate users,
 * then the members of candidate groups and of a legacy virtual-group MI row. Candidate users come in the order the
 * node's assignment named them ({@code ASSIGNEE_FROM_VARIABLE}: the variable's order); people that order does not
 * name, and every other node, follow by user id. Flowable keeps no order of its own (D-39 takes the first one).</p>
 *
 * <p>The rules judge people by user id, so a holding is given in user ids: a task that holds a username instead
 * (created before the platform assigned by id) is read as its user's, by {@link EngineActorIdentities}' rule.</p>
 */
@Component
@RequiredArgsConstructor
public class TaskHoldingResolver {

    private final TaskService taskService;
    private final BpmnActionParser bpmnActionParser;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final VirtualGroupMembersClient virtualGroupMembersClient;
    private final LostEntryTaskPolicy lostEntryTaskPolicy;
    private final EngineActorIdentities engineActorIdentities;

    /** The task's holding, in user ids. */
    public TaskHolding resolve(Task task) {
        TaskHolding read = read(task, extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(task.getId()));
        return inUserIds(Map.of(task.getId(), read)).get(task.getId());
    }

    /** The task's holding as the task stores its people (ids, or usernames on older tasks); see {@link #inUserIds}. */
    TaskHolding read(Task task, Optional<ExtendedTaskInfo> extended) {
        Set<String> candidates = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            if (StringUtils.hasText(link.getUserId())) {
                candidates.add(link.getUserId().trim());
            }
            if (StringUtils.hasText(link.getGroupId())) {
                groups.add(link.getGroupId().trim());
            }
        }
        extended.filter(row -> row.getAssignmentType() == AssignmentType.VIRTUAL_GROUP)
                .map(ExtendedTaskInfo::getAssignmentTarget)
                .ifPresent(target -> addGroups(groups, target));
        String rawType = bpmnActionParser.getUserTaskExtensionPropertyValue(
                task.getProcessDefinitionId(), task.getTaskDefinitionKey(), "assigneeType");
        Set<String> members = inAssignmentOrder(task, rawType, candidates);
        for (String group : groups) {
            members.addAll(virtualGroupMembersClient.memberUserIds(group));
        }
        return new TaskHolding(task.getAssignee(), kindOf(rawType, extended, candidates, groups), members);
    }

    /** The holdings (by task id) in user ids, with one lookup for all of them. */
    Map<String, TaskHolding> inUserIds(Map<String, TaskHolding> holdings) {
        Set<String> people = new HashSet<>();
        holdings.values().forEach(holding -> people.addAll(holding.people()));
        Map<String, String> userIds = people.isEmpty() ? Map.of() : engineActorIdentities.userIds(people);
        Map<String, TaskHolding> inUserIds = new LinkedHashMap<>();
        holdings.forEach((taskId, holding) -> {
            Set<String> members = new LinkedHashSet<>();
            holding.poolMemberIds().forEach(member -> members.add(userIds.getOrDefault(member, member)));
            String holder = holding.held() ? userIds.getOrDefault(holding.assigneeId(), holding.assigneeId()) : null;
            inUserIds.put(taskId, new TaskHolding(holder, holding.kind(), members));
        });
        return inUserIds;
    }

    /** True when the holder lost Portal entry and {@code actorId} is a pool member with entry (D-07, D-17). */
    public boolean mayTakeOverFromLostHolder(Task task, String actorId) {
        if (!StringUtils.hasText(task.getAssignee()) || !StringUtils.hasText(actorId)) {
            return false;
        }
        TaskHolding holding = resolve(task);
        Set<String> people = holding.people();
        people.add(actorId);
        return LostEntryTaskPolicy.mayTakeOver(actorId, holding, lostEntryTaskPolicy.withPortalEntry(people));
    }

    private static PoolKind kindOf(String rawType, Optional<ExtendedTaskInfo> extended, Set<String> candidates,
                                   Set<String> groups) {
        boolean miRoleRow = extended.flatMap(row -> MiRoleRowProperties.parse(row.getExtendedProperties())).isPresent();
        if (!groups.isEmpty() || miRoleRow) {
            return PoolKind.MEMBERSHIP;
        }
        return TaskPoolKinds.of(rawType, false, false, !candidates.isEmpty());
    }

    private Set<String> inAssignmentOrder(Task task, String rawType, Set<String> candidates) {
        Set<String> ordered = new LinkedHashSet<>();
        if (!candidates.isEmpty() && AssigneeType.fromCode(rawType) == AssigneeType.ASSIGNEE_FROM_VARIABLE) {
            String variable = firstText(property(task, "assigneeVariable"), property(task, "assigneeValue"));
            if (variable != null) {
                AssigneeUserIdNormalizer.userIdsOfVariable(taskService.getVariable(task.getId(), variable.trim()))
                        .stream().filter(candidates::contains).forEach(ordered::add);
            }
        }
        candidates.stream().sorted().forEach(ordered::add);
        return ordered;
    }

    private String property(Task task, String name) {
        return bpmnActionParser.getUserTaskExtensionPropertyValue(
                task.getProcessDefinitionId(), task.getTaskDefinitionKey(), name);
    }

    private static String firstText(String a, String b) {
        return StringUtils.hasText(a) ? a : StringUtils.hasText(b) ? b : null;
    }

    private static void addGroups(Set<String> groups, String commaSeparated) {
        for (String group : List.of(commaSeparated.split(","))) {
            if (!group.isBlank()) {
                groups.add(group.trim());
            }
        }
    }
}
