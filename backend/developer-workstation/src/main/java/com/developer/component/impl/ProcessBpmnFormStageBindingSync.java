package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormStageBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps {@code dw_form_stage_bindings} synchronized with the BPMN-embedded bindings whenever a
 * process definition is saved.
 *
 * <p>The Bind Process Node dialog ({@code useFormNodeBinding.ts}) only ever writes
 * {@code formId}/{@code formName}/{@code formReadOnly} (and the REQUEST-scene equivalents) onto
 * the BPMN XML — never onto {@code dw_form_stage_bindings} directly. That table is, however, the
 * <em>only</em> thing user-portal's runtime Task Form resolution reads
 * ({@code TaskFormDefinitionLoader.fetchTaskFormByStageId} /
 * {@code FormStageBindingController}'s GET). Without this sync, a Function Unit whose bindings
 * were only ever set through the dialog has a BPMN that looks fully bound in the designer while
 * every task silently falls back to the read-only Process Form reference view at runtime.
 *
 * <p>Called from {@link ProcessDesignComponentImpl#save} after the BPMN XML is persisted, so
 * every path that changes the deployed process (manual save, auto-save) keeps the table current
 * without each caller having to remember to sync it separately.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessBpmnFormStageBindingSync {

    private final BpmnFormStageBindingParser parser;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FormStageBindingRepository formStageBindingRepository;

    /**
     * Reconciles {@code dw_form_stage_bindings} for every form owned by {@code functionUnitId}
     * against what the given BPMN XML actually declares.
     *
     * <p>Delete-then-insert per affected form: the table is small (single-digit to low tens of
     * rows per Function Unit) and carries no history worth preserving row-for-row, so a full
     * replace is simpler and less bug-prone than a fine-grained diff. A form is "affected" when
     * either the new XML declares a binding for it, or it already has rows on record — the
     * latter half of that union is what makes unbinding the last node for a form (unchecking
     * everything in the dialog) correctly clear its stale rows too, not just skip re-syncing it.
     *
     * <p>A BPMN XML that fails to parse yields an empty {@code parsedBindings} list (see
     * {@link BpmnFormStageBindingParser#parse}) but does NOT short-circuit this method: forms
     * that already have rows are still included via the "already has rows on record" half of the
     * union, so a save with unparsable XML leaves existing bindings untouched rather than wiping
     * them — it just cannot add or remove any.
     */
    public void sync(Long functionUnitId, String bpmnXml) {
        List<BpmnFormStageBindingParser.ParsedBinding> parsedBindings = parser.parse(bpmnXml);

        Map<Long, FormDefinition> formsById = new HashMap<>();
        for (FormDefinition form : formDefinitionRepository.findByFunctionUnitId(functionUnitId)) {
            formsById.put(form.getId(), form);
        }
        if (formsById.isEmpty()) {
            return;
        }

        Map<Long, List<BpmnFormStageBindingParser.ParsedBinding>> byFormId = new HashMap<>();
        for (BpmnFormStageBindingParser.ParsedBinding binding : parsedBindings) {
            FormDefinition form = formsById.get(binding.formId());
            if (form == null) {
                // Stale/foreign formId (form deleted, or a copy-pasted node from another FU's
                // BPMN) — skip rather than fail the whole save over unrelated dirty data.
                log.warn("Skipping BPMN form-stage binding for formId={} (stage={}): no such form "
                        + "in functionUnitId={}", binding.formId(), binding.stageId(), functionUnitId);
                continue;
            }
            byFormId.computeIfAbsent(binding.formId(), ignored -> new ArrayList<>()).add(binding);
        }

        Set<Long> affectedFormIds = new HashSet<>(byFormId.keySet());
        for (FormDefinition form : formsById.values()) {
            if (!formStageBindingRepository.findByFormId(form.getId()).isEmpty()) {
                affectedFormIds.add(form.getId());
            }
        }

        for (Long formId : affectedFormIds) {
            FormDefinition form = formsById.get(formId);
            formStageBindingRepository.deleteByFormId(formId);
            for (BpmnFormStageBindingParser.ParsedBinding binding : byFormId.getOrDefault(formId, List.of())) {
                formStageBindingRepository.save(FormStageBinding.builder()
                        .form(form)
                        .stageId(binding.stageId())
                        .stageName(binding.stageName())
                        .readOnly(binding.readOnly())
                        .scene(binding.scene())
                        .build());
            }
        }
    }
}
