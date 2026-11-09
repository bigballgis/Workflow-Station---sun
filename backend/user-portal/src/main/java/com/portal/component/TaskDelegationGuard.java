package com.portal.component;

import com.platform.common.i18n.I18nService;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Refuses delegations and transfers the engine would refuse, with a business error instead of the engine's refusal
 * surfacing as a 500 (platform access model): delegation only where the engine can keep it (D-56); transfer and
 * delegation of a task someone holds by its holder only (D-60).
 */
@Component
@RequiredArgsConstructor
public class TaskDelegationGuard {

    private final I18nService i18nService;

    public void requireDelegable(TaskInfo task) {
        if (!task.isDelegable()) {
            throw new PortalException("400", i18nService.getMessage("portal.task_not_delegable"));
        }
    }

    public void requireMayTransfer(TaskInfo task, String userId, String portalUsername) {
        if (!TaskPermissionEvaluator.mayTransfer(task, userId, portalUsername)) {
            throw new PortalException("403", i18nService.getMessage("portal.task_transfer_holder_only"));
        }
    }

    public void requireMayDelegate(TaskInfo task, String userId, String portalUsername) {
        if (!TaskPermissionEvaluator.mayDelegate(task, userId, portalUsername)) {
            throw new PortalException("403", i18nService.getMessage("portal.task_delegate_holder_only"));
        }
    }
}
