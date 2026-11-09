package com.workflow.component;

import com.platform.security.service.PortalEntryService;
import com.workflow.client.AdminCenterClient;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.util.WorkflowActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Who may act for the holder of a task delegated for this one task: the user it names, or a member of the BU + Role it
 * names in their current workspace. The delegation is in effect only while its delegator ({@code delegatedBy}, else the
 * holder: the Portal's {@code EffectiveDelegationRules} reads it the same way) and the acting delegate both have Portal
 * entry (platform access model D-55, as standing rules under D-18); the holder can always handle the task themselves.
 */
@Component
@RequiredArgsConstructor
public class SingleTaskDelegateCheck {

    private final TaskActionService taskActionService;
    private final AdminCenterClient adminCenterClient;
    private final PortalEntryService portalEntryService;

    /** {@code actorUserId} is the delegate of {@code extended} and may act for {@code holderId}. */
    public boolean mayActFor(ExtendedTaskInfo extended, String actorUserId, String holderId) {
        if (!isDelegate(extended, actorUserId)) {
            return false;
        }
        String delegator = StringUtils.hasText(extended.getDelegatedBy()) ? extended.getDelegatedBy().trim() : holderId.trim();
        Set<String> withEntry = portalEntryService.withPortalEntry(List.of(actorUserId.trim(), delegator));
        return withEntry.contains(actorUserId.trim()) && withEntry.contains(delegator);
    }

    private boolean isDelegate(ExtendedTaskInfo extended, String actorUserId) {
        if (extended == null || !StringUtils.hasText(actorUserId) || !extended.isDelegated()) {
            return false;
        }
        if (StringUtils.hasText(extended.getDelegatedTo())
                && taskActionService.engineActorMatchesPortalUser(extended.getDelegatedTo(), actorUserId)) {
            return true;
        }
        if (!extended.isBuRoleDelegated()) {
            return false;
        }
        String buCode = WorkflowActorResolver.currentActiveBusinessUnitId()
                .map(id -> adminCenterClient.getBusinessUnitCodeById(id.trim()))
                .filter(StringUtils::hasText)
                .orElse(null);
        String roleCode = WorkflowActorResolver.currentActiveRoleId()
                .map(id -> adminCenterClient.getRoleCodeById(id.trim()))
                .filter(StringUtils::hasText)
                .orElse(null);
        return buCode != null && roleCode != null
                && buCode.equalsIgnoreCase(extended.getDelegatedBuCode().trim())
                && roleCode.equalsIgnoreCase(extended.getDelegatedRoleCode().trim());
    }
}
