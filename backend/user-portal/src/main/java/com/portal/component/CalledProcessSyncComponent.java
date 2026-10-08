package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the portal's records of a request and the Function Units it called in step.
 *
 * <p>A called Function Unit is started by the engine, not by a portal submission, so nothing used
 * to create its {@code up_process_instance} row: its tasks showed no Function Unit or Request ID in
 * To Do, and the calling request's "Sub-processes" tab had nothing to show. And while a request
 * waits at a call activity it has no task of its own, so its current step and assignee stayed
 * empty for as long as the call ran.
 *
 * <p>Both are fixed from the engine's call-aware process status (which reports the called unit's
 * task, the instance it belongs to, and the caller of a called instance):
 * <ul>
 *   <li>{@link #recordCalledWork} — once a status shows the work is inside a call, create/link
 *       the called instances' rows and give them their own current step.</li>
 *   <li>{@link #refreshCallers} — after work inside a called unit moves on, refresh each caller
 *       up the chain: its current step may now be a different child's task, or its own next one.</li>
 * </ul>
 * Both are best-effort: the action that triggered them (submit, complete) has already succeeded and
 * must not fail because a display column could not be refreshed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalledProcessSyncComponent {

    /** Same bound as the engine's walk down a call chain. */
    private static final int MAX_CALL_DEPTH = 5;

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceHydrationComponent hydrationComponent;
    private final ProcessInstanceSyncComponent processInstanceSyncComponent;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** The row a per-row call ran for, as handed to the called instance on deploy-compiled BPMN. */
    static final String CALL_ROW_VARIABLE = "__callRow";
    /** The platform's own row id, carried by rows the portal created. */
    private static final String PLATFORM_ROW_ID = "platformRowUuid";
    private static final String ROW_PREFIX = "row.";
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Records the called instances behind a status whose current task lives in one of them.
     * No-op when the status' task belongs to {@code processInstanceId} itself.
     */
    public void recordCalledWork(String processInstanceId, Map<String, Object> status) {
        if (processInstanceId == null || status == null) {
            return;
        }
        String taskInstanceId = asString(status.get("nextTaskProcessInstanceId"));
        if (taskInstanceId == null || taskInstanceId.equals(processInstanceId)) {
            return;
        }
        try {
            linkCalledInstances(processInstanceId, 0);
            processInstanceSyncComponent.updateProcessInstanceAssignee(
                    taskInstanceId,
                    asString(status.get("nextAssignee")),
                    asString(status.get("nextCandidateUsers")),
                    asString(status.get("nextTaskName")));
        } catch (RuntimeException e) {
            log.warn("Could not record the Function Units called by {}: {}", processInstanceId, e.getMessage());
        }
    }

    /**
     * Refreshes every caller above an instance whose status was just read, after work in it moved
     * on. Running callers get their new current task (and any calls it started recorded); a caller
     * that finished is left to the completion notification, which owns terminal status.
     */
    public void refreshCallers(Map<String, Object> status) {
        Map<String, Object> current = status;
        for (int depth = 0; depth < MAX_CALL_DEPTH && current != null; depth++) {
            String callerId = asString(current.get("superProcessInstanceId"));
            if (callerId == null) {
                return;
            }
            try {
                if (Boolean.TRUE.equals(current.get("completed"))) {
                    copyOutputsToCaller(asString(current.get("processInstanceId")), callerId);
                }
                Optional<Map<String, Object>> callerStatus = workflowEngineClient.getProcessInstanceStatus(callerId);
                if (callerStatus.isEmpty()) {
                    return;
                }
                current = callerStatus.get();
                if (Boolean.TRUE.equals(current.get("completed"))) {
                    continue;
                }
                String nextTaskName = asString(current.get("nextTaskName"));
                if (nextTaskName != null) {
                    processInstanceSyncComponent.updateProcessInstanceAssignee(
                            callerId,
                            asString(current.get("nextAssignee")),
                            asString(current.get("nextCandidateUsers")),
                            nextTaskName);
                    recordCalledWork(callerId, current);
                }
            } catch (RuntimeException e) {
                log.warn("Could not refresh calling process {}: {}", callerId, e.getMessage());
                return;
            }
        }
    }

    /**
     * Copies a finished call's output-mapped values into the caller's portal record.
     *
     * <p>The portal renders and re-submits forms from its own record, so a value that only reached
     * the engine would show up empty on the caller's next form and be overwritten by that empty
     * value on its next submit.
     * <ul>
     *   <li>A call that runs once: the engine already set the mapped fields on the caller
     *       ({@code flowable:out}); only those fields are copied over from it — anything else in
     *       the engine may be older than what the portal holds.</li>
     *   <li>A per-row call: each call's result goes into the row it ran for. The engine cannot
     *       address a row inside {@code __subTables__}, so the mapping is applied here, from the
     *       called instance's values, to the row it was handed ({@code __callRow}).</li>
     * </ul>
     */
    private void copyOutputsToCaller(String calledId, String callerId) {
        if (calledId == null) {
            return;
        }
        Optional<ProcessInstance> calledOpt = processInstanceRepository.findById(calledId);
        Optional<ProcessInstance> callerOpt = processInstanceRepository.findById(callerId);
        if (calledOpt.isEmpty() || callerOpt.isEmpty() || calledOpt.get().getCallActivityId() == null) {
            return;
        }
        ProcessInstance caller = callerOpt.get();
        CallOutputs outputs = readCallOutputs(caller.getProcessDefinitionKey(), calledOpt.get().getCallActivityId());
        Map<String, Object> vars = caller.getVariables() != null ? new HashMap<>(caller.getVariables()) : new HashMap<>();
        boolean changed = false;

        if (!outputs.onceTargets().isEmpty()) {
            Map<String, Object> callerEngineVars = engineVariables(callerId);
            for (String target : outputs.onceTargets()) {
                if (callerEngineVars.containsKey(target)) {
                    vars.put(target, callerEngineVars.get(target));
                    changed = true;
                }
            }
        }
        if (outputs.rowsTable() != null && !outputs.rowMappings().isEmpty()) {
            changed |= copyIntoCallRow(calledOpt.get(), outputs, vars);
        }

        if (changed) {
            caller.setVariables(vars);
            processInstanceRepository.save(caller);
            log.info("Copied results of called Function Unit {} back into {}", calledId, callerId);
        }
    }

    /** Writes one per-row call's mapped results into the row it ran for; returns whether it did. */
    @SuppressWarnings("unchecked")
    private boolean copyIntoCallRow(ProcessInstance called, CallOutputs outputs, Map<String, Object> callerVars) {
        Map<String, Object> calledVars = new HashMap<>();
        Map<String, Object> calledEngineVars = engineVariables(called.getId());
        calledVars.putAll(calledEngineVars);
        if (called.getVariables() != null) {
            calledVars.putAll(called.getVariables()); // what its forms last submitted wins
        }
        if (!(calledVars.get(CALL_ROW_VARIABLE) instanceof Map<?, ?> callRow)) {
            log.warn("Called instance {} has no {}; cannot tell which {} row its result belongs to",
                    called.getId(), CALL_ROW_VARIABLE, outputs.rowsTable());
            return false;
        }
        if (!(callerVars.get("__subTables__") instanceof Map<?, ?> subTables)
                || !(subTables.get("dw:" + outputs.rowsTable()) instanceof List<?> rows)) {
            return false;
        }

        List<String> keyFields = rowKeyFields(outputs.rowsTable(), stringKeyed(callRow));
        List<Object> updatedRows = new ArrayList<>(rows);
        for (int i = 0; i < updatedRows.size(); i++) {
            if (!(updatedRows.get(i) instanceof Map<?, ?> row) || !sameRow(row, callRow, keyFields)) {
                continue;
            }
            Map<String, Object> updated = new java.util.LinkedHashMap<>(stringKeyed(row));
            for (Map<String, String> mapping : outputs.rowMappings()) {
                updated.put(mapping.get("to").substring(ROW_PREFIX.length()), calledVars.get(mapping.get("from")));
            }
            updatedRows.set(i, updated);
            Map<String, Object> updatedSubTables = new java.util.LinkedHashMap<>(stringKeyed(subTables));
            updatedSubTables.put("dw:" + outputs.rowsTable(), updatedRows);
            callerVars.put("__subTables__", updatedSubTables);
            return true;
        }
        log.warn("Row of {} that called instance {} ran for is no longer on the request; result not copied",
                outputs.rowsTable(), called.getId());
        return false;
    }

    /**
     * How a row is recognised: the platform row id when the row carries one, otherwise the rows
     * table's primary key as designed. Never guessed from column names.
     */
    private List<String> rowKeyFields(String rowsTable, Map<String, Object> callRow) {
        if (callRow.get(PLATFORM_ROW_ID) != null) {
            return List.of(PLATFORM_ROW_ID);
        }
        return jdbcTemplate.queryForList(
                """
                        SELECT fd.field_name
                        FROM dw_field_definitions fd
                        JOIN dw_table_definitions td ON td.id = fd.table_id
                        WHERE td.table_name = ? AND COALESCE(fd.is_primary_key, false) = true
                        ORDER BY fd.sort_order, fd.id
                        """,
                String.class, rowsTable);
    }

    private static boolean sameRow(Map<?, ?> row, Map<?, ?> callRow, List<String> keyFields) {
        if (keyFields.isEmpty()) {
            return false;
        }
        for (String key : keyFields) {
            Object expected = callRow.get(key);
            if (expected == null || !String.valueOf(expected).equals(String.valueOf(row.get(key)))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> engineVariables(String processInstanceId) {
        return workflowEngineClient.getProcessInstance(processInstanceId)
                .map(row -> row.get("variables"))
                .filter(Map.class::isInstance)
                .map(v -> (Map<?, ?>) v)
                .map(CalledProcessSyncComponent::stringKeyed)
                .orElse(Map.of());
    }

    /**
     * What one call step copies back, from the caller's deployed BPMN: request fields set by
     * {@code flowable:out} (a call that runs once), and the row mappings of a per-row call, which
     * stay as the designer's {@code callOutputMapping} property because the engine cannot apply them.
     */
    private record CallOutputs(List<String> onceTargets, String rowsTable, List<Map<String, String>> rowMappings) {
        static final CallOutputs NONE = new CallOutputs(List.of(), null, List.of());
    }

    private CallOutputs readCallOutputs(String processDefinitionKey, String callActivityId) {
        if (processDefinitionKey == null) {
            return CallOutputs.NONE;
        }
        Optional<String> bpmn = workflowEngineClient.getBpmnXml(processDefinitionKey);
        if (bpmn.isEmpty() || !(bpmn.get().contains(":out") || bpmn.get().contains("callOutputMapping"))) {
            return CallOutputs.NONE;
        }
        try {
            org.w3c.dom.Document doc = BpmnMiXmlSupport.parseBpmnSecurely(bpmn.get());
            org.w3c.dom.NodeList calls = doc.getElementsByTagNameNS("*", "callActivity");
            for (int i = 0; i < calls.getLength(); i++) {
                org.w3c.dom.Element call = (org.w3c.dom.Element) calls.item(i);
                if (!callActivityId.equals(call.getAttribute("id"))) {
                    continue;
                }
                List<String> onceTargets = new ArrayList<>();
                org.w3c.dom.NodeList outs = call.getElementsByTagNameNS("*", "out");
                for (int j = 0; j < outs.getLength(); j++) {
                    String target = ((org.w3c.dom.Element) outs.item(j)).getAttribute("target");
                    if (!target.isBlank()) {
                        onceTargets.add(target);
                    }
                }
                String rowsTable = BpmnMiXmlSupport.findFirstPropertyValue(call, "callRowsTable");
                List<Map<String, String>> rowMappings = new ArrayList<>();
                String mappingJson = BpmnMiXmlSupport.findFirstPropertyValue(call, "callOutputMapping");
                if (mappingJson != null && !mappingJson.isBlank()) {
                    for (Map<String, Object> entry : JSON.readValue(mappingJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {})) {
                        Object from = entry.get("from");
                        Object to = entry.get("to");
                        if (from != null && to != null && String.valueOf(to).startsWith(ROW_PREFIX)) {
                            rowMappings.add(Map.of("from", String.valueOf(from), "to", String.valueOf(to)));
                        }
                    }
                }
                return new CallOutputs(onceTargets,
                        rowsTable != null && !rowsTable.isBlank() ? rowsTable : null, rowMappings);
            }
        } catch (Exception e) {
            log.warn("Could not read output mapping of {} in {}: {}", callActivityId, processDefinitionKey, e.getMessage());
        }
        return CallOutputs.NONE;
    }

    private static Map<String, Object> stringKeyed(Map<?, ?> map) {
        Map<String, Object> out = new HashMap<>();
        map.forEach((k, v) -> {
            if (k != null) {
                out.put(String.valueOf(k), v);
            }
        });
        return out;
    }

    /**
     * Creates (if missing) and links the rows of every Function Unit instance {@code callerId}
     * called, and of the ones those called in turn.
     */
    private void linkCalledInstances(String callerId, int depth) {
        if (depth >= MAX_CALL_DEPTH) {
            return;
        }
        Optional<ProcessInstance> callerOpt = processInstanceRepository.findById(callerId);
        if (callerOpt.isEmpty()) {
            return;
        }
        ProcessInstance caller = callerOpt.get();

        List<Map<String, Object>> subProcesses = workflowEngineClient.getSubProcesses(callerId);
        for (Map<String, Object> subProcess : subProcesses) {
            // Embedded sub-processes (the multi-instance kind) run inside the caller itself.
            if (Boolean.TRUE.equals(subProcess.get("embedded"))
                    || Boolean.TRUE.equals(subProcess.get("isEmbedded"))) {
                continue;
            }
            String childId = asString(subProcess.get("subProcessInstanceId"));
            if (childId == null) {
                continue;
            }
            ProcessInstance child = hydrationComponent.requireProcessInstance(childId);
            if (applyCallerLink(child, caller, asString(subProcess.get("callActivityId")))) {
                processInstanceRepository.save(child);
            }
            linkCalledInstances(childId, depth + 1);
        }
    }

    /**
     * Fills what a called instance cannot know on its own; returns whether anything changed.
     *
     * <p>The Function Unit code is the process key (they are the same by construction), set
     * explicitly because hydration resolves it from the start catalog, which a call-only unit is
     * not in. The start user is the caller's: the person whose request this work is part of.
     */
    static boolean applyCallerLink(ProcessInstance child, ProcessInstance caller, String callActivityId) {
        boolean changed = false;
        if (child.getParentProcessInstanceId() == null) {
            child.setParentProcessInstanceId(caller.getId());
            changed = true;
        }
        if (child.getCallActivityId() == null && callActivityId != null) {
            child.setCallActivityId(callActivityId);
            changed = true;
        }
        if (isBlank(child.getFunctionUnitCode()) && !isBlank(child.getProcessDefinitionKey())) {
            child.setFunctionUnitCode(child.getProcessDefinitionKey());
            changed = true;
        }
        if ((isBlank(child.getStartUserId()) || "system".equals(child.getStartUserId()))
                && !isBlank(caller.getStartUserId())) {
            child.setStartUserId(caller.getStartUserId());
            child.setInitiatorId(caller.getStartUserId());
            child.setStartUserName(caller.getStartUserName());
            changed = true;
        }
        return changed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String asString(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
