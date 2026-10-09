package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.CalledFunctionUnitInstance;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the Function Unit sub-processes a request started, for read-only display on that request.
 *
 * <h2>Live read, not a copy</h2>
 * A called Function Unit runs as its own process instance with its own variables. Its data is read
 * from there on demand rather than mirrored into the caller: a mirror would be stale between the
 * child's saves, and keeping two copies of the same rows in step is precisely what has gone wrong
 * repeatedly with sub-table snapshots in this codebase.
 *
 * <h2>Access</h2>
 * Whether a viewer may see this is decided by the <em>calling</em> request — if you can open the
 * request, you can see what its sub-processes produced, because they are part of that request.
 * Being able to see the data here grants nothing else: opening the called unit directly, or acting
 * on its tasks, still goes through that unit's own authorisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalledFunctionUnitComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;

    /** Loads the called unit's deployed forms. Lazy: ProcessComponent is a large, widely wired bean. */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProcessComponent processComponent;

    /**
     * The sub-processes started by this request, oldest first.
     *
     * <p>Children are fetched in one indexed query rather than asked for per call activity: a
     * detail page that made one engine round-trip per node would reintroduce the N+1 shape that
     * has made portal hot paths slow before.
     *
     * @param parentProcessInstanceId the calling request
     * @return one entry per child instance; empty when this request calls nothing
     */
    public List<CalledFunctionUnitInstance> findCalledInstances(String parentProcessInstanceId) {
        if (parentProcessInstanceId == null || parentProcessInstanceId.isBlank()) {
            return List.of();
        }

        List<ProcessInstance> children =
                processInstanceRepository.findByParentProcessInstanceId(parentProcessInstanceId);
        if (children.isEmpty()) {
            // Either this request calls nothing, or its children predate the parent link (or were
            // created by an engine path that does not set it). Ask Flowable, which owns the
            // relation, and record what it says so later reads are a plain indexed query.
            children = linkChildrenFromEngine(parentProcessInstanceId);
        }
        if (children.isEmpty()) {
            return List.of();
        }

        // The call activity's label and chosen form live in the CALLING unit's BPMN, so it is
        // parsed once and shared across every child rather than per child.
        Map<String, CallActivityDisplay> callActivities =
                readCallActivities(parentProcessInstanceId);

        List<CalledFunctionUnitInstance> out = new ArrayList<>(children.size());
        // Forms are loaded once per called unit for this page, not once per child: a per-row call
        // can start many children of the same unit.
        Map<String, List<Map<String, Object>>> formsByUnit = new HashMap<>();
        for (ProcessInstance child : children) {
            CallActivityDisplay display = child.getCallActivityId() != null
                    ? callActivities.get(child.getCallActivityId())
                    : null;
            String unitCode = child.getFunctionUnitCode() != null
                    ? child.getFunctionUnitCode()
                    : child.getProcessDefinitionKey();
            Map<String, Object> childForm = display != null && display.childFormName() != null
                    ? findForm(formsByUnit, unitCode, display.childFormName())
                    : null;
            List<Map<String, Object>> childFields = childForm == null ? mainTableFields(formsByUnit, unitCode) : null;

            out.add(CalledFunctionUnitInstance.builder()
                    .processInstanceId(child.getId())
                    .callActivityId(child.getCallActivityId())
                    .callActivityName(display != null ? display.name() : null)
                    .functionUnitCode(child.getFunctionUnitCode() != null
                            ? child.getFunctionUnitCode()
                            : child.getProcessDefinitionKey())
                    .functionUnitName(child.getProcessDefinitionName())
                    .childFormName(display != null ? display.childFormName() : null)
                    .childForm(childForm)
                    .childFields(childFields)
                    .status(child.getStatus())
                    .currentNode(child.getCurrentNode())
                    .startTime(child.getStartTime())
                    .endTime(child.getEndTime())
                    .formData(child.getVariables() != null
                            ? new HashMap<>(child.getVariables())
                            : Map.of())
                    .build());
        }

        out.sort((a, b) -> {
            if (a.getStartTime() == null || b.getStartTime() == null) {
                return 0;
            }
            return a.getStartTime().compareTo(b.getStartTime());
        });
        return out;
    }

    /**
     * Asks the engine which instances this one called, and records the link on the portal rows.
     *
     * <p>Only reached when no linked children are on file, so the engine round-trip happens once
     * per request rather than on every view. Children the portal has never heard of are skipped
     * rather than created here: row creation belongs to the hydration path, and inventing rows
     * from a partial engine payload would produce instances missing their catalog pin.
     *
     * @return the child rows now linked to this parent
     */
    private List<ProcessInstance> linkChildrenFromEngine(String parentProcessInstanceId) {
        List<Map<String, Object>> subProcesses =
                workflowEngineClient.getSubProcesses(parentProcessInstanceId);
        if (subProcesses.isEmpty()) {
            return List.of();
        }

        List<ProcessInstance> linked = new ArrayList<>();
        for (Map<String, Object> subProcess : subProcesses) {
            // Embedded sub-processes (the multi-instance kind) live inside this very instance and
            // are not Function Unit calls. Both spellings are accepted because the engine DTO's
            // field is `isEmbedded`, whose JSON name depends on accessor naming.
            if (Boolean.TRUE.equals(subProcess.get("embedded"))
                    || Boolean.TRUE.equals(subProcess.get("isEmbedded"))) {
                continue;
            }
            String childId = asString(subProcess.get("subProcessInstanceId"));
            String callActivityId = asString(subProcess.get("callActivityId"));
            if (childId == null || callActivityId == null) {
                continue;
            }

            Optional<ProcessInstance> childOpt = processInstanceRepository.findById(childId);
            if (childOpt.isEmpty()) {
                log.debug("Engine reports sub-process {} of {} but the portal has no row for it yet",
                        childId, parentProcessInstanceId);
                continue;
            }
            ProcessInstance child = childOpt.get();
            child.setParentProcessInstanceId(parentProcessInstanceId);
            child.setCallActivityId(callActivityId);
            linked.add(processInstanceRepository.save(child));
        }
        return linked;
    }

    /**
     * The called unit's main-table fields, taken from the PRIMARY binding of any of its deployed
     * forms (they all bind the same main table); null when none can be found.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mainTableFields(
            Map<String, List<Map<String, Object>>> formsByUnit, String unitCode) {
        for (Map<String, Object> form : formsOf(formsByUnit, unitCode)) {
            if (!(form.get("tableBindings") instanceof List<?> bindings)) {
                continue;
            }
            for (Object binding : bindings) {
                if (binding instanceof Map<?, ?> b && "PRIMARY".equals(b.get("bindingType"))
                        && b.get("fieldDefinitions") instanceof List<?> defs && !defs.isEmpty()) {
                    List<Map<String, Object>> fields = new ArrayList<>();
                    for (Object def : defs) {
                        if (def instanceof Map<?, ?> d && d.get("fieldName") != null) {
                            Map<String, Object> field = new HashMap<>();
                            field.put("fieldName", d.get("fieldName"));
                            field.put("displayName", d.get("displayName"));
                            fields.add(field);
                        }
                    }
                    return fields;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> formsOf(Map<String, List<Map<String, Object>>> formsByUnit, String unitCode) {
        if (unitCode == null || processComponent == null) {
            return List.of();
        }
        return formsByUnit.computeIfAbsent(unitCode, code -> {
            try {
                return processComponent.getFunctionUnitContents(code, "FORM");
            } catch (RuntimeException e) {
                log.warn("Could not load forms of called Function Unit {}: {}", code, e.getMessage());
                return List.of();
            }
        });
    }

    /** The called unit's deployed form with that name, or null when it has none by that name. */
    private Map<String, Object> findForm(
            Map<String, List<Map<String, Object>>> formsByUnit, String unitCode, String formName) {
        for (Map<String, Object> form : formsOf(formsByUnit, unitCode)) {
            if (formName.equals(form.get("name")) || formName.equals(form.get("formName"))) {
                return form;
            }
        }
        log.debug("Called Function Unit {} has no deployed form named '{}'", unitCode, formName);
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * Call-activity id → how it should be labelled and which form renders its child's data.
     *
     * <p>Returns empty rather than failing when the BPMN cannot be read: the child instances and
     * their data are still worth showing, just without the designer's labelling.
     */
    private Map<String, CallActivityDisplay> readCallActivities(String parentProcessInstanceId) {
        Optional<ProcessInstance> parentOpt = processInstanceRepository.findById(parentProcessInstanceId);
        if (parentOpt.isEmpty()) {
            return Map.of();
        }
        String processDefinitionKey = parentOpt.get().getProcessDefinitionKey();
        if (processDefinitionKey == null || processDefinitionKey.isBlank()) {
            return Map.of();
        }

        try {
            Optional<String> bpmnOpt = workflowEngineClient.getBpmnXml(processDefinitionKey);
            if (bpmnOpt.isEmpty()) {
                return Map.of();
            }
            Document document = BpmnMiXmlSupport.parseBpmnSecurely(bpmnOpt.get());
            org.w3c.dom.NodeList nodes = document.getElementsByTagNameNS("*", "callActivity");

            Map<String, CallActivityDisplay> out = new HashMap<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (!(nodes.item(i) instanceof Element callActivity)) {
                    continue;
                }
                String id = callActivity.getAttribute("id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                String name = callActivity.getAttribute("name");
                String childFormName = BpmnMiXmlSupport.findFirstPropertyValue(callActivity, "childFormName");
                out.put(id, new CallActivityDisplay(
                        name != null && !name.isBlank() ? name : null,
                        childFormName != null && !childFormName.isBlank() ? childFormName : null));
            }
            return out;
        } catch (Exception e) {
            log.warn("Could not read call activities of process {}: {}",
                    parentProcessInstanceId, e.getMessage());
            return Map.of();
        }
    }

    /** Designer-facing labelling for one call activity. */
    private record CallActivityDisplay(String name, String childFormName) {
    }
}
