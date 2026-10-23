package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartFormPopupSubmitRequest;
import com.portal.dto.ProcessStartRequest;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * New Request FORM_POPUP: start (optional) + persist ACTION row.
 */
@Component
@RequiredArgsConstructor
public class ProcessStartFormPopupComponent {

    private final ProcessComponent processComponent;
    private final ActionFormPopupSubmitComponent actionFormPopupSubmitComponent;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ActionDefinitionLookupComponent actionDefinitionLookupComponent;
    private final ProcessStartComponent processStartComponent;

    public ProcessStartFormPopupOutcome submit(
            String userId, String processKey, String actionId, ProcessStartFormPopupSubmitRequest request) {
        String processInstanceId = request.getProcessInstanceId();
        ProcessInstanceInfo instance;
        if (StringUtils.hasText(processInstanceId)) {
            ProcessInstance row = requireRunningInstanceForInitiator(processInstanceId, userId);
            // A started request's action is one of the release it was started on.
            requireFormPopup(actionDefinitionLookupComponent.findPinned(
                    ProcessVersionContext.of(row).catalogId(), actionId));
            instance = infoOf(row);
        } else {
            // A new request's action is one of the release it is about to start on.
            requireFormPopup(actionDefinitionLookupComponent.findPinned(
                    processStartComponent.activeCatalogIdForStart(userId, processKey), actionId));
            if (request.getFormData() == null) {
                throw new PortalException("400", "formData is required when starting a new process");
            }
            ProcessStartRequest start = ProcessStartRequest.builder()
                    .processDefinitionKey(processKey)
                    .formData(request.getFormData())
                    .batchStartContext(request.getBatchStartContext())
                    .emptiedSubTableKeys(request.getEmptiedSubTableKeys())
                    .subTableBindingScopes(request.getSubTableBindingScopes())
                    .priority("NORMAL")
                    .leaveFirstTaskOpen(true)
                    .build();
            instance = processComponent.startProcess(userId, processKey, start);
            processInstanceId = instance.getId();
            if (!StringUtils.hasText(processInstanceId)) {
                throw new PortalException("500", "Process start did not return an instance id");
            }
        }
        ActionTableRowRef actionRow = actionFormPopupSubmitComponent.submitForProcessInstance(
                processInstanceId, actionId, request.getPopupFormData(), userId);
        return new ProcessStartFormPopupOutcome(instance, actionRow);
    }

    private ProcessInstance requireRunningInstanceForInitiator(String processInstanceId, String userId) {
        ProcessInstance row = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new PortalException("404", "Process instance not found"));
        if (!userId.equals(row.getStartUserId())) {
            throw new PortalException("403", "You do not have permission to update this process");
        }
        if (!"RUNNING".equalsIgnoreCase(String.valueOf(row.getStatus()))) {
            throw new PortalException("400", "Process is not running");
        }
        return row;
    }

    private static ProcessInstanceInfo infoOf(ProcessInstance row) {
        ProcessInstanceInfo info = new ProcessInstanceInfo();
        info.setId(row.getId());
        info.setProcessDefinitionKey(row.getFunctionUnitCode());
        info.setStatus(row.getStatus());
        return info;
    }

    private static void requireFormPopup(java.util.Optional<ActionDefinition> found) {
        ActionDefinition action = found.orElseThrow(() -> new PortalException("404", "Action not found for this process"));
        if (!"FORM_POPUP".equals(action.getActionType())) {
            throw new PortalException("400", "Action is not FORM_POPUP");
        }
    }
}
