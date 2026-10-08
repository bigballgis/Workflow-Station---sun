package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.FormDefinitionRepository;
import com.developer.enums.TableType;
import com.developer.repository.TableDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.util.XmlEncodingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Design-time validation for cross-Function-Unit calls (BPMN {@code callActivity}).
 *
 * <p>Deliberately a separate component from {@link ProcessBpmnValidator} for two reasons:
 * <ul>
 *   <li>{@code ProcessBpmnValidator.validateMultiInstance} enforces the opposite rule — that
 *       every {@code formId} a node references belongs to the <em>current</em> Function Unit.
 *       A callActivity's form intentionally belongs to the <em>called</em> unit, so relaxing
 *       that check in place would weaken it for multi-instance too.</li>
 *   <li>Adding constructor dependencies to {@code ProcessBpmnValidator} would ripple through
 *       its existing test mocks.</li>
 * </ul>
 *
 * <p>Multi-instance sub-processes and cross-FU calls share no code path here: this class only
 * ever looks at {@code callActivity} elements, and {@code validateMultiInstance} only ever
 * looks at {@code subProcess} elements carrying {@code multiInstanceLoopCharacteristics}.
 */
@Component
@Slf4j
public class CallActivityBpmnValidator {

    /** Any-namespace {@code callActivity} with its id; {@code calledElement} is read separately. */
    private static final Pattern CALL_ACTIVITY_PATTERN = Pattern.compile(
            "<(?:\\w+:)?callActivity\\b([^>]*)>|<(?:\\w+:)?callActivity\\b([^>]*)/>",
            Pattern.DOTALL);

    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]*)\"");
    private static final Pattern CALLED_ELEMENT_ATTR = Pattern.compile("\\bcalledElement=\"([^\"]*)\"");

    /**
     * Designer extension property carrying the form to render the child's data with.
     * Stored by <em>name</em> rather than id: form ids are remapped on import, so an id would
     * dangle as soon as the called unit is imported into another environment.
     */
    private static final Pattern CHILD_FORM_NAME_PROPERTY = Pattern.compile(
            "<(?:\\w+:)?property\\b[^>]*\\bname=\"childFormName\"[^>]*\\bvalue=\"([^\"]*)\"");

    /** Designer property naming the sub-table a per-row call runs once per row of. */
    private static final Pattern ROWS_TABLE_PROPERTY = Pattern.compile(
            "<(?:\\w+:)?property\\b[^>]*\\bname=\"callRowsTable\"[^>]*\\bvalue=\"([^\"]*)\"");

    /** Loop characteristics on the call itself: it runs once per row. */
    private static final Pattern PER_ROW_LOOP = Pattern.compile("<(?:\\w+:)?multiInstanceLoopCharacteristics\\b");

    /** Guards against a malformed graph making cycle detection run away. */
    private static final int MAX_CALL_GRAPH_NODES = 500;

    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final TableDefinitionRepository tableDefinitionRepository;

    public CallActivityBpmnValidator(
            FunctionUnitRepository functionUnitRepository,
            ProcessDefinitionRepository processDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            TableDefinitionRepository tableDefinitionRepository) {
        this.functionUnitRepository = functionUnitRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.tableDefinitionRepository = tableDefinitionRepository;
    }

    /**
     * Validates every {@code callActivity} in the given BPMN.
     *
     * @param bpmnXml        the process XML being saved (plain XML, already decoded)
     * @param functionUnitId the unit that owns this process
     */
    public ValidationResult validateCallActivities(String bpmnXml, Long functionUnitId) {
        ValidationResult result = new ValidationResult();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return result;
        }

        List<CallActivityRef> calls = parseCallActivities(bpmnXml);
        if (calls.isEmpty()) {
            return result;
        }

        for (CallActivityRef call : calls) {
            validateSingleCall(call, result);
            validateRowsSource(call, functionUnitId, result);
        }

        // Cycle detection runs over the whole call graph, not just this unit's direct edges:
        // A -> B -> C -> A is only visible by walking transitively.
        if (result.isValid() && functionUnitId != null) {
            detectCycle(functionUnitId, calls, result);
        }

        return result;
    }

    private void validateSingleCall(CallActivityRef call, ValidationResult result) {
        if (call.calledElement() == null || call.calledElement().isBlank()) {
            result.addError("CALL_TARGET_MISSING",
                    "Call activity '" + call.elementId() + "' does not specify which Function Unit to call. "
                            + "Select a target Function Unit in the element properties panel.",
                    call.elementId());
            return;
        }

        Optional<FunctionUnit> targetOpt = functionUnitRepository.findByCode(call.calledElement());
        if (targetOpt.isEmpty()) {
            result.addError("CALL_TARGET_NOT_FOUND",
                    "Call activity '" + call.elementId() + "' targets Function Unit '"
                            + call.calledElement() + "', which does not exist.",
                    call.elementId());
            return;
        }

        FunctionUnit target = targetOpt.get();
        if (target.getStartupMode() == null || !target.getStartupMode().allowsBeingCalled()) {
            result.addError("CALL_TARGET_NOT_CALLABLE",
                    "Function Unit '" + target.getName() + "' cannot be called by another Function Unit. "
                            + "Set its Startup Mode to CALLABLE or BOTH to allow this.",
                    call.elementId());
            return;
        }

        // The form used to render the child's data must belong to the CALLED unit — the
        // opposite of the containment rule that applies to every other node type.
        if (call.childFormName() != null && !call.childFormName().isBlank()) {
            boolean formExists = formDefinitionRepository
                    .findByFunctionUnitIdAndFormName(target.getId(), call.childFormName())
                    .isPresent();
            if (!formExists) {
                result.addError("CALL_FORM_NOT_IN_TARGET",
                        "Call activity '" + call.elementId() + "' references form '" + call.childFormName()
                                + "', which does not exist in the called Function Unit '" + target.getName() + "'.",
                        call.elementId());
            }
        }
    }

    /**
     * A call that runs once per row must say which rows: without that the engine has no list
     * to start calls for and stops the request when it reaches the step. The rows must be one
     * of this unit's own sub-tables — the only rows a request carries.
     */
    private void validateRowsSource(CallActivityRef call, Long functionUnitId, ValidationResult result) {
        if (!call.perRow()) {
            return;
        }
        if (call.rowsTable() == null || call.rowsTable().isBlank()) {
            result.addError("CALL_ROWS_TABLE_MISSING",
                    "Call activity '" + call.elementId() + "' runs once per row but does not say which "
                            + "sub-table's rows. Choose one under Data passing in the element properties panel.",
                    call.elementId());
            return;
        }
        if (functionUnitId == null) {
            return;
        }
        boolean isOwnSubTable = tableDefinitionRepository
                .findByFunctionUnitIdAndTableName(functionUnitId, call.rowsTable())
                .filter(table -> table.getTableType() == TableType.SUB)
                .isPresent();
        if (!isOwnSubTable) {
            result.addError("CALL_ROWS_TABLE_NOT_FOUND",
                    "Call activity '" + call.elementId() + "' runs once per row of '" + call.rowsTable()
                            + "', which is not a sub-table of this Function Unit.",
                    call.elementId());
        }
    }

    /**
     * Rejects call cycles at design time.
     *
     * <p>A cycle would make the engine start child instances without end at runtime, so this is
     * refused rather than merely warned about. The walk reads each unit's <em>design-time</em>
     * BPMN so an unpublished edge still counts.
     */
    private void detectCycle(Long functionUnitId, List<CallActivityRef> callsBeingSaved, ValidationResult result) {
        FunctionUnit self = functionUnitRepository.findById(functionUnitId).orElse(null);
        if (self == null || self.getCode() == null) {
            return;
        }
        String selfCode = self.getCode();

        // Seed with the edges from the BPMN being saved (not yet persisted), then walk the
        // persisted graph from there.
        Set<String> frontier = new LinkedHashSet<>();
        for (CallActivityRef call : callsBeingSaved) {
            if (call.calledElement() != null && !call.calledElement().isBlank()) {
                frontier.add(call.calledElement());
            }
        }

        Deque<String> queue = new ArrayDeque<>(frontier);
        Set<String> visited = new HashSet<>();
        int examined = 0;

        while (!queue.isEmpty()) {
            String code = queue.poll();
            if (code == null || code.isBlank() || !visited.add(code)) {
                continue;
            }
            if (++examined > MAX_CALL_GRAPH_NODES) {
                log.warn("Call-graph cycle check for function unit {} stopped after {} nodes",
                        functionUnitId, MAX_CALL_GRAPH_NODES);
                return;
            }

            if (selfCode.equals(code)) {
                result.addError("CALL_CYCLE_DETECTED",
                        "This call chain comes back to Function Unit '" + self.getName()
                                + "'. Circular calls would start sub-processes endlessly at runtime.",
                        null);
                return;
            }

            for (String next : outgoingCallTargets(code)) {
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }
    }

    /** The codes this unit's persisted design-time BPMN calls out to. */
    private List<String> outgoingCallTargets(String functionUnitCode) {
        Optional<FunctionUnit> unitOpt = functionUnitRepository.findByCode(functionUnitCode);
        if (unitOpt.isEmpty()) {
            return List.of();
        }
        Optional<ProcessDefinition> processOpt =
                processDefinitionRepository.findByFunctionUnitId(unitOpt.get().getId());
        if (processOpt.isEmpty() || processOpt.get().getBpmnXml() == null) {
            return List.of();
        }

        String xml;
        try {
            // Stored base64-encoded; smartDecode tolerates plain XML too.
            xml = XmlEncodingUtil.smartDecode(processOpt.get().getBpmnXml());
        } catch (Exception e) {
            log.warn("Could not decode BPMN for function unit code {} during cycle check: {}",
                    functionUnitCode, e.getMessage());
            return List.of();
        }

        List<String> targets = new ArrayList<>();
        for (CallActivityRef call : parseCallActivities(xml)) {
            if (call.calledElement() != null && !call.calledElement().isBlank()) {
                targets.add(call.calledElement());
            }
        }
        return targets;
    }

    /** Extracts every callActivity element with its id, target code and optional child form name. */
    private List<CallActivityRef> parseCallActivities(String bpmnXml) {
        List<CallActivityRef> out = new ArrayList<>();
        Matcher matcher = CALL_ACTIVITY_PATTERN.matcher(bpmnXml);
        while (matcher.find()) {
            String attrs = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (attrs == null) {
                continue;
            }
            String elementId = firstGroup(ID_ATTR, attrs);
            String calledElement = firstGroup(CALLED_ELEMENT_ATTR, attrs);

            // The child form name lives in extension elements inside the callActivity body,
            // so look it up in the slice of XML starting at this element.
            String childFormName = null;
            String rowsTable = null;
            boolean perRow = false;
            if (matcher.group(1) != null) {
                String body = bodyOf(bpmnXml, matcher.end(), elementId);
                childFormName = firstGroup(CHILD_FORM_NAME_PROPERTY, body);
                rowsTable = firstGroup(ROWS_TABLE_PROPERTY, body);
                perRow = PER_ROW_LOOP.matcher(body).find();
            }

            out.add(new CallActivityRef(
                    elementId != null ? elementId : "(unnamed)", calledElement, childFormName, rowsTable, perRow));
        }
        return out;
    }

    /** XML between this element's opening tag and its matching close, best-effort. */
    private String bodyOf(String xml, int fromIndex, String elementId) {
        int close = xml.indexOf("</", fromIndex);
        while (close >= 0) {
            int tagEnd = xml.indexOf('>', close);
            if (tagEnd < 0) {
                break;
            }
            String tag = xml.substring(close, tagEnd);
            if (tag.contains("callActivity")) {
                return xml.substring(fromIndex, close);
            }
            close = xml.indexOf("</", tagEnd);
        }
        log.debug("No closing callActivity tag found for element {}", elementId);
        return "";
    }

    private String firstGroup(Pattern pattern, String input) {
        if (input == null) {
            return null;
        }
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    /** One callActivity as declared in the BPMN. */
    private record CallActivityRef(
            String elementId, String calledElement, String childFormName, String rowsTable, boolean perRow) {
    }
}
