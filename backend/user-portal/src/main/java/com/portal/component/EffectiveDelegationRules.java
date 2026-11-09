package com.portal.component;

import com.platform.security.service.PortalEntryService;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationRule;
import com.portal.repository.DelegationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Delegations in force (platform access model §7.3): a standing rule (D-18) or a single-task delegation (D-55) stops
 * working while its delegator or its delegate has no Portal entry, and works again once both have it. Nothing is
 * changed or deleted. The one place the delegated-task list, the handling permission, the task page and the delegation
 * page read it from.
 *
 * <p>A single-task delegation to a BU + Role has no one delegate: there the delegate is the member who looks at or
 * acts on the task.</p>
 */
@Component
@RequiredArgsConstructor
public class EffectiveDelegationRules {

    public static final String DELEGATOR_LOST_PORTAL_ENTRY = "DELEGATOR_LOST_PORTAL_ENTRY";
    public static final String DELEGATE_LOST_PORTAL_ENTRY = "DELEGATE_LOST_PORTAL_ENTRY";

    private final DelegationRuleRepository delegationRuleRepository;
    private final PortalEntryService portalEntryService;

    /** Active rules delegating to {@code delegateId} now whose two parties both have Portal entry. */
    public List<DelegationRule> inForceForDelegate(String delegateId) {
        List<DelegationRule> rules = markLostEntry(
                delegationRuleRepository.findActiveDelegationsForDelegate(delegateId, LocalDateTime.now()));
        return rules.stream().filter(rule -> rule.getInactiveReason() == null).toList();
    }

    /**
     * Sets {@link TaskInfo#getDelegationInactiveReason()} on the tasks with a single-task delegation (D-55).
     *
     * @param actingUserId the member looking at or acting on the tasks: the delegate of a delegation to a BU + Role
     */
    public void markSingleTaskDelegations(Collection<TaskInfo> tasks, String actingUserId) {
        List<TaskInfo> delegated = tasks.stream().filter(EffectiveDelegationRules::hasSingleTaskDelegation).toList();
        if (delegated.isEmpty()) {
            return;
        }
        Set<String> withEntry = portalEntryService.withPortalEntry(delegated.stream()
                .flatMap(task -> Stream.of(delegatorOf(task), delegateOf(task, actingUserId)))
                .filter(Objects::nonNull).toList());
        for (TaskInfo task : delegated) {
            task.setDelegationInactiveReason(reason(delegatorOf(task), delegateOf(task, actingUserId), withEntry));
        }
    }

    /** Whether {@code task}'s single-task delegation is in effect for {@code actingUserId} as its delegate (D-55). */
    public boolean singleTaskDelegationInForce(TaskInfo task, String actingUserId) {
        String delegator = delegatorOf(task);
        String delegate = delegateOf(task, actingUserId);
        Set<String> withEntry = portalEntryService.withPortalEntry(
                Stream.of(delegator, delegate).filter(Objects::nonNull).toList());
        return reason(delegator, delegate, withEntry) == null;
    }

    private static String reason(String delegator, String delegate, Set<String> withEntry) {
        if (!withEntry.contains(delegator)) {
            return DELEGATOR_LOST_PORTAL_ENTRY;
        }
        return delegate != null && !withEntry.contains(delegate) ? DELEGATE_LOST_PORTAL_ENTRY : null;
    }

    private static boolean hasSingleTaskDelegation(TaskInfo task) {
        return task != null && (hasText(task.getDelegatedTo())
                || (hasText(task.getDelegatedBuCode()) && hasText(task.getDelegatedRoleCode())));
    }

    /** The delegator: whoever delegated it, else the holder it is done on behalf of. */
    private static String delegatorOf(TaskInfo task) {
        return hasText(task.getDelegatorId()) ? task.getDelegatorId().trim() : task.getAssignee();
    }

    private static String delegateOf(TaskInfo task, String actingUserId) {
        return hasText(task.getDelegatedTo()) ? task.getDelegatedTo().trim() : actingUserId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Sets {@link DelegationRule#getInactiveReason()} on rules whose delegator or delegate lost Portal entry. */
    public List<DelegationRule> markLostEntry(List<DelegationRule> rules) {
        if (rules.isEmpty()) {
            return rules;
        }
        Set<String> withEntry = portalEntryService.withPortalEntry(rules.stream()
                .flatMap(rule -> Stream.of(rule.getDelegatorId(), rule.getDelegateId())).toList());
        for (DelegationRule rule : rules) {
            if (!withEntry.contains(rule.getDelegatorId())) {
                rule.setInactiveReason(DELEGATOR_LOST_PORTAL_ENTRY);
            } else if (!withEntry.contains(rule.getDelegateId())) {
                rule.setInactiveReason(DELEGATE_LOST_PORTAL_ENTRY);
            } else {
                rule.setInactiveReason(null);
            }
        }
        return rules;
    }
}
