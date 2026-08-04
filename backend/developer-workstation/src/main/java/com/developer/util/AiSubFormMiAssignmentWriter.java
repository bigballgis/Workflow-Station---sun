package com.developer.util;

import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;
import com.developer.exception.AiGenerationException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Materialises the {@code miAssignment} sub-form component for AI-generated multi-instance nodes.
 *
 * <p>{@code configJson.subForms} is keyed by {@link FormTableBinding#getId()}, which the database
 * assigns while the generated data is being written, so the model cannot emit the entry itself —
 * the same constraint {@link AiBpmnFormBindingWriter} solves for {@code formId}. Without the
 * component, a BPMN node carrying a complete assignment contract fails deploy with
 * {@code MISSING_MI_ASSIGNMENT_COMPONENT}
 * (see {@code com.developer.component.impl.MiAssignmentFormGuard}), and a node carrying an
 * incomplete one silently leaves the sub-table rows with no way to pick a participant.</p>
 *
 * <p>The written shape mirrors what the designer persists — see the {@code miAssignment} drag rule
 * in {@code frontend/developer-workstation/src/main.ts} and
 * {@code nestAssignmentFieldsIntoContainer} in
 * {@code frontend/developer-workstation/src/utils/miAssignmentConfig.ts} — including
 * {@code _miAdopted}, so reopening the form never re-runs adoption over an author's layout.</p>
 */
@Slf4j
public final class AiSubFormMiAssignmentWriter {

    /** Matches {@code form.miAssignmentTitle} in the designer's English locale. */
    private static final String CONTAINER_TITLE = "Assignment Mode";

    private AiSubFormMiAssignmentWriter() {
    }

    /** BPMN assignment contract for one sub table. */
    private record AssignmentContract(
            boolean allowUser, boolean allowRole,
            String assigneeField, String roleField, String buField) {

        boolean isComplete() {
            if (!allowUser && !allowRole) {
                return false;
            }
            if (allowUser && assigneeField == null) {
                return false;
            }
            return !allowRole || roleField != null;
        }

        /**
         * Fixed reading order inside the container: BU narrows the role list, so it precedes Role.
         * Mirrors {@code assignmentChildFieldOrder}.
         */
        List<String> childFieldOrder() {
            List<String> order = new ArrayList<>();
            for (String field : new String[]{assigneeField, buField, roleField}) {
                if (field != null) {
                    order.add(field);
                }
            }
            return order;
        }
    }

    /**
     * Write one {@code miAssignment} container into every sub-form bound to a table that an MI node
     * assigns from. Returns a description per binding touched, for the caller to log.
     *
     * @param fieldRuleFactory builds a form-create rule for a table field, so the seeded controls
     *                         look exactly like the ones the rest of the AI write path produces
     * @param defaultOptions   form-create options for a sub-form that had none yet
     */
    public static List<String> writeAssignmentContainers(
            String bpmnXml,
            List<FormDefinition> forms,
            Function<FieldDefinition, Map<String, Object>> fieldRuleFactory,
            Supplier<Map<String, Object>> defaultOptions) {

        List<String> written = new ArrayList<>();
        if (bpmnXml == null || bpmnXml.isBlank() || forms == null || forms.isEmpty()) {
            return written;
        }

        Map<String, AssignmentContract> contracts = parseContracts(bpmnXml);
        if (contracts.isEmpty()) {
            return written;
        }

        for (FormDefinition form : forms) {
            if (form == null || form.getTableBindings() == null) {
                continue;
            }
            for (FormTableBinding binding : form.getTableBindings()) {
                String note = writeBinding(form, binding, contracts, fieldRuleFactory, defaultOptions);
                if (note != null) {
                    written.add(note);
                }
            }
        }
        return written;
    }

    private static Map<String, AssignmentContract> parseContracts(String bpmnXml) {
        Map<String, AssignmentContract> contracts = new LinkedHashMap<>();
        Set<String> conflicting = new LinkedHashSet<>();
        try {
            Document document = AiBpmnMiTaskScanner.parseSecurely(bpmnXml);
            for (AiBpmnMiTaskScanner.MiTask task : AiBpmnMiTaskScanner.scan(document)) {
                String subTableName = task.property("subTableName");
                String mode = task.property("assigneeMode");
                if (subTableName == null || mode == null) {
                    continue;
                }
                mode = mode.toLowerCase(java.util.Locale.ROOT);
                if (!"user".equals(mode) && !"role".equals(mode) && !"both".equals(mode)) {
                    continue;
                }
                AssignmentContract contract = new AssignmentContract(
                        "user".equals(mode) || "both".equals(mode),
                        "role".equals(mode) || "both".equals(mode),
                        task.property("assigneeField"),
                        task.property("roleField"),
                        task.property("buField"));
                AssignmentContract previous = contracts.putIfAbsent(subTableName, contract);
                if (previous != null && !previous.equals(contract)) {
                    // 与 MiAssignmentFormGuard 的 CONFLICTING_MI_ASSIGNMENT_CONFIG 同判定：
                    // 两个节点对同一子表给了不同契约，写哪一个都是猜——留给部署校验报错。
                    conflicting.add(subTableName);
                }
            }
        } catch (Exception e) {
            log.error("Failed to read multi-instance assignment contracts from AI-generated BPMN", e);
            throw new AiGenerationException("AI_BPMN_MI_BINDING_FAILED",
                    "Failed to read multi-instance assignment contracts from AI-generated BPMN: "
                            + e.getMessage());
        }
        for (String subTableName : conflicting) {
            log.warn("Sub table '{}' has conflicting MI assignment settings across nodes — no "
                    + "assignment component written", subTableName);
            contracts.remove(subTableName);
        }
        return contracts;
    }

    private static String writeBinding(
            FormDefinition form,
            FormTableBinding binding,
            Map<String, AssignmentContract> contracts,
            Function<FieldDefinition, Map<String, Object>> fieldRuleFactory,
            Supplier<Map<String, Object>> defaultOptions) {

        if (binding == null || binding.getBindingType() != BindingType.SUB || binding.getTable() == null) {
            return null;
        }
        TableDefinition table = binding.getTable();
        AssignmentContract contract = table.getTableName() == null
                ? null : contracts.get(table.getTableName().trim());
        if (contract == null || !contract.isComplete()) {
            return null;
        }
        if (binding.getId() == null) {
            // 只可能出现在没走 flush 的单测里：真实写入路径在调用前已 flush 拿到 id。
            log.warn("Sub-table binding for '{}' has no database id yet — skipping MI assignment "
                    + "component", table.getTableName());
            return null;
        }

        Map<String, Object> configJson = form.getConfigJson() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(form.getConfigJson());
        Map<String, Object> subForms = new LinkedHashMap<>();
        if (configJson.get("subForms") instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> subForms.put(String.valueOf(key), value));
        }

        String key = String.valueOf(binding.getId());
        Map<String, Object> entry = subForms.get(key) instanceof Map<?, ?> existingEntry
                ? castEntry(existingEntry) : new LinkedHashMap<>();

        List<Object> rule = entry.get("rule") instanceof List<?> existingRule
                ? new ArrayList<>(existingRule) : new ArrayList<>();
        if (containsMiAssignment(rule)) {
            return null;
        }
        if (rule.isEmpty()) {
            rule = seedRuleFromTable(table, fieldRuleFactory);
        }

        List<Object> nested = nestAssignmentFields(rule, contract, table, fieldRuleFactory);
        if (nested == null) {
            log.warn("MI assignment contract for sub table '{}' names no field that exists on the "
                    + "table — no assignment component written", table.getTableName());
            return null;
        }

        entry.put("rule", nested);
        if (!(entry.get("options") instanceof Map)) {
            entry.put("options", defaultOptions.get());
        }
        subForms.put(key, entry);
        configJson.put("subForms", subForms);
        // 新建顶层 Map 再 set：configJson 是 @JdbcTypeCode(JSON) 的可变 Map，就地改内容在
        // 快照与当前值是同一引用时会被脏检查判成"没变"，UPDATE 不会发出。
        form.setConfigJson(configJson);

        return "form '" + form.getFormName() + "' binding " + binding.getId()
                + " (sub table '" + table.getTableName() + "')";
    }

    private static List<Object> seedRuleFromTable(
            TableDefinition table, Function<FieldDefinition, Map<String, Object>> fieldRuleFactory) {
        List<Object> rule = new ArrayList<>();
        for (FieldDefinition field : sortedFields(table)) {
            rule.add(fieldRuleFactory.apply(field));
        }
        return rule;
    }

    private static List<FieldDefinition> sortedFields(TableDefinition table) {
        if (table.getFieldDefinitions() == null) {
            return List.of();
        }
        List<FieldDefinition> fields = new ArrayList<>(table.getFieldDefinitions());
        fields.sort(Comparator.comparingInt(f -> f.getSortOrder() != null ? f.getSortOrder() : 0));
        return fields;
    }

    /**
     * Pull the contract's fields out of the rule tree and wrap them in the container, placed where
     * the first of them sat. Returns {@code null} when not a single child could be built.
     */
    private static List<Object> nestAssignmentFields(
            List<Object> rule,
            AssignmentContract contract,
            TableDefinition table,
            Function<FieldDefinition, Map<String, Object>> fieldRuleFactory) {

        List<String> order = contract.childFieldOrder();
        Set<String> owned = new LinkedHashSet<>(order);
        Map<String, Object> harvested = new LinkedHashMap<>();
        List<Object> remaining = new ArrayList<>(rule);
        int anchor = harvestOwnedFields(remaining, owned, harvested, true);

        List<Object> children = new ArrayList<>();
        for (String field : order) {
            Object child = harvested.get(field);
            if (child == null) {
                child = buildFieldRule(table, field, fieldRuleFactory);
            }
            if (child != null) {
                children.add(child);
            } else {
                log.warn("MI assignment field '{}' is not defined on sub table '{}' — the assignment "
                        + "component will be missing that control", field, table.getTableName());
            }
        }
        if (children.isEmpty()) {
            return null;
        }

        Map<String, Object> container = new LinkedHashMap<>();
        container.put("type", "miAssignment");
        container.put("title", CONTAINER_TITLE);
        container.put("props", new LinkedHashMap<String, Object>());
        // 一次性认领标记：没有它，设计器每次打开都会把字段重新吸回容器，作者拖出去的布局会被静默还原。
        container.put("_miAdopted", true);
        container.put("children", children);

        List<Object> result = new ArrayList<>(remaining);
        result.add(anchor >= 0 && anchor <= result.size() ? anchor : result.size(), container);
        return result;
    }

    /**
     * Remove every rule bound to one of {@code owned} from the tree, recording it by field name.
     * Returns the top-level index the first removal came from, or {@code -1} when none was found
     * at the top level.
     */
    @SuppressWarnings("unchecked")
    private static int harvestOwnedFields(
            List<Object> rules, Set<String> owned, Map<String, Object> harvested, boolean topLevel) {
        int anchor = -1;
        int kept = 0;
        for (int i = 0; i < rules.size(); ) {
            Object item = rules.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                i++;
                kept++;
                continue;
            }
            Object field = map.get("field");
            if (field instanceof String name && owned.contains(name)) {
                harvested.putIfAbsent(name, item);
                rules.remove(i);
                if (topLevel && anchor < 0) {
                    anchor = kept;
                }
                continue;
            }
            if (map.get("children") instanceof List<?> children) {
                harvestOwnedFields((List<Object>) children, owned, harvested, false);
            }
            i++;
            kept++;
        }
        return anchor;
    }

    private static Object buildFieldRule(
            TableDefinition table, String fieldName,
            Function<FieldDefinition, Map<String, Object>> fieldRuleFactory) {
        for (FieldDefinition field : sortedFields(table)) {
            if (fieldName.equals(field.getFieldName())) {
                return fieldRuleFactory.apply(field);
            }
        }
        return null;
    }

    /** Same recursive marker search {@code MiAssignmentFormGuard} runs at deploy time. */
    private static boolean containsMiAssignment(Object value) {
        if (value instanceof Map<?, ?> map) {
            if ("miAssignment".equals(String.valueOf(map.get("type")))) {
                return true;
            }
            return map.values().stream().anyMatch(AiSubFormMiAssignmentWriter::containsMiAssignment);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsMiAssignment(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Object> castEntry(Map<?, ?> entry) {
        Map<String, Object> copy = new LinkedHashMap<>();
        entry.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }
}
