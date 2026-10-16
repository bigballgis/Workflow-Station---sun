package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.dto.RecordNoteDtos.UploadSource;
import com.portal.dto.RecordNoteDtos.UploadSourceKind;
import com.portal.service.RecordNoteService.RecordNoteException;
import com.portal.util.RecordNoteHtmlSupport;
import com.portal.util.WorkflowEnginePayloadHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Called after note write authorization; the client can name a surface, never its display text. */
@Component
@RequiredArgsConstructor
public class RecordNoteUploadSourceResolver {
    private final WorkflowEngineClient engine;
    private final ProcessComponent processes;
    private final FunctionUnitAccessComponent access;

    public UploadSource resolve(String user, NoteTarget target) {
        if (target.getSourceView() != null && !java.util.Set.of("AUDIT","REQUEST").contains(target.getSourceView())) {
            throw failure("Invalid upload surface");
        }
        if (target.getTaskId() != null && !target.getTaskId().isBlank()) {
            if (target.getSourceView() != null) throw failure("Task and Audit surfaces cannot be combined");
            var task = engine.getTaskById(target.getTaskId())
                    .map(WorkflowEnginePayloadHelper::singleTaskFromPayload)
                    .map(EngineTaskMapper::convertMapToTaskInfo)
                    .orElseThrow(() -> failure("Upload task is unavailable"));
            if (!target.getProcessInstanceId().equals(task.getProcessInstanceId())) {
                throw failure("Upload task does not belong to this request");
            }
            // Write access was already checked. An authorized auditor can add notes on a completed task;
            // its historical task identity is a valid source, independent of the current recipient round.
            String name = RecordNoteHtmlSupport.extractText(task.getTaskName());
            String step = RecordNoteHtmlSupport.extractText(task.getCurrentStepName());
            if (step != null && !step.equals(name)) name = name == null ? step : step + " / " + name;
            return new UploadSource(UploadSourceKind.TASK, target.getTaskId(), name);
        }
        if (target.getTargetId().startsWith("draft-")) {
            if ("AUDIT".equals(target.getSourceView())) throw failure("A draft is not an Audit upload");
            return new UploadSource(UploadSourceKind.NEW_REQUEST, null, null);
        }
        // FALLBACK(migration): older clients did not identify their request surface. Omit the source
        // instead of guessing whether an initiator opened My Request or Audit; remove after client rollout.
        if (target.getSourceView() == null) return null;
        var request = processes.getProcessDetail(target.getProcessInstanceId());
        if (request == null) throw failure("Upload request is unavailable");
        if ("REQUEST".equals(target.getSourceView()) && user.equals(request.getStartUserId())) {
            return new UploadSource(UploadSourceKind.MY_REQUEST, null, null);
        }
        if (!access.isSystemAdministrator(user)
                && !access.canAuditFunctionUnitAsActiveRole(user, request.getFunctionUnitCode())) {
            throw new RecordNoteException("FORBIDDEN", "Audit upload requires an audit grant on the active role");
        }
        return new UploadSource(UploadSourceKind.AUDIT, null, null);
    }

    private static RecordNoteException failure(String message) {
        return new RecordNoteException("NOTE_SOURCE_INVALID", message);
    }
}
