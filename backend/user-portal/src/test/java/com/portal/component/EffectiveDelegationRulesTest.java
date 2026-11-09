package com.portal.component;

import com.platform.security.service.PortalEntryService;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationRule;
import com.portal.repository.DelegationRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A standing delegation (D-18, AC-34) or a single-task delegation (D-55) stops working while either party lacks Portal
 * entry, and is never changed.
 */
class EffectiveDelegationRulesTest {

    private final DelegationRuleRepository repository = mock(DelegationRuleRepository.class);
    private final PortalEntryService portalEntryService = mock(PortalEntryService.class);
    private final EffectiveDelegationRules rules = new EffectiveDelegationRules(repository, portalEntryService);

    @Test
    void aRuleIsInForceOnlyWhileBothPartiesHaveEntry() {
        DelegationRule fine = rule(1L, "a", "me");
        DelegationRule delegatorGone = rule(2L, "gone", "me");
        when(repository.findActiveDelegationsForDelegate(eq("me"), any()))
                .thenReturn(new ArrayList<>(List.of(fine, delegatorGone)));
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("a", "me"));

        assertThat(rules.inForceForDelegate("me")).containsExactly(fine);
        assertThat(delegatorGone.getInactiveReason()).isEqualTo(EffectiveDelegationRules.DELEGATOR_LOST_PORTAL_ENTRY);
    }

    @Test
    void theDelegationPageSeesWhyARuleIsNotInForce() {
        DelegationRule delegateGone = rule(3L, "me", "gone");
        DelegationRule fine = rule(4L, "me", "b");
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("me", "b"));

        rules.markLostEntry(List.of(delegateGone, fine));

        assertThat(delegateGone.getInactiveReason()).isEqualTo(EffectiveDelegationRules.DELEGATE_LOST_PORTAL_ENTRY);
        assertThat(fine.getInactiveReason()).isNull();
    }

    @Test
    void aSingleTaskDelegationToAPersonIsNotInEffectWhileEitherPartyLacksEntry() {
        TaskInfo fine = delegatedTo("t1", "me", "b");
        TaskInfo delegateGone = delegatedTo("t2", "me", "gone");
        TaskInfo delegatorGone = delegatedTo("t3", "left", "b");
        TaskInfo notDelegated = TaskInfo.builder().taskId("t4").assignee("me").build();
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("me", "b"));

        rules.markSingleTaskDelegations(List.of(fine, delegateGone, delegatorGone, notDelegated), "me");

        assertThat(fine.getDelegationInactiveReason()).isNull();
        assertThat(delegateGone.getDelegationInactiveReason()).isEqualTo(EffectiveDelegationRules.DELEGATE_LOST_PORTAL_ENTRY);
        assertThat(delegatorGone.getDelegationInactiveReason()).isEqualTo(EffectiveDelegationRules.DELEGATOR_LOST_PORTAL_ENTRY);
        assertThat(notDelegated.getDelegationInactiveReason()).isNull();
        assertThat(rules.singleTaskDelegationInForce(fine, "b")).isTrue();
        assertThat(rules.singleTaskDelegationInForce(delegateGone, "gone")).isFalse();
    }

    @Test
    void forADelegationToABuRoleTheActingMemberIsTheDelegate() {
        TaskInfo toBuRole = TaskInfo.builder().taskId("t5").assignee("boss").delegatorId("boss")
                .delegatedTargetType("BU_ROLE").delegatedBuCode("FIN").delegatedRoleCode("MANAGER").build();
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("boss", "member"));

        assertThat(rules.singleTaskDelegationInForce(toBuRole, "member")).isTrue();
        assertThat(rules.singleTaskDelegationInForce(toBuRole, "member-without-entry")).isFalse();
    }

    @Test
    void tasksWithoutASingleTaskDelegationCostNoLookup() {
        rules.markSingleTaskDelegations(List.of(TaskInfo.builder().taskId("t6").assignee("me").build()), "me");

        org.mockito.Mockito.verifyNoInteractions(portalEntryService);
    }

    private static TaskInfo delegatedTo(String taskId, String delegator, String delegate) {
        return TaskInfo.builder().taskId(taskId).assignee(delegator).delegatorId(delegator)
                .delegatedTargetType("USER").delegatedTo(delegate).build();
    }

    private static DelegationRule rule(Long id, String delegator, String delegate) {
        return DelegationRule.builder().id(id).delegatorId(delegator).delegateId(delegate).build();
    }
}
