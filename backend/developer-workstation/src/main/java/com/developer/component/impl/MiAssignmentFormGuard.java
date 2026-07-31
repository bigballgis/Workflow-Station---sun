package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates that configured BPMN MI assignment contracts have a matching marker in the bound sub-form rule.
 */
final class MiAssignmentFormGuard {

    private final List<FormDefinition> forms;
    private final Map<String, NodeContract> contractsBySubTable = new LinkedHashMap<>();

    MiAssignmentFormGuard(List<FormDefinition> forms) {
        this.forms = forms;
    }

    void validate(
            Map<String, String> properties,
            String userTaskId,
            String subProcessId,
            Long configuredTableId,
            TableDefinition resolvedTable,
            boolean staleIdFallback,
            ValidationResult result) {
        String mode = normalized(properties.get("assigneeMode"));
        String subTableName = normalized(properties.get("subTableName"));
        if (!isSupportedMode(mode) || subTableName == null) {
            return;
        }

        AssignmentContract contract = new AssignmentContract(
                mode,
                normalized(properties.get("assigneeField")),
                normalized(properties.get("roleField")),
                normalized(properties.get("buField")));
        NodeContract previous = contractsBySubTable.putIfAbsent(
                subTableName, new NodeContract(userTaskId, contract));
        if (previous != null && !previous.contract().equals(contract)) {
            result.addError(
                    "CONFLICTING_MI_ASSIGNMENT_CONFIG",
                    "SubTable '" + subTableName + "' has conflicting MI assignment settings on nodes "
                            + previous.nodeId() + " and " + userTaskId,
                    subProcessId);
            return;
        }
        if (!contract.isComplete()) {
            return;
        }

        List<BindingLocation> bindings = findBindings(configuredTableId, resolvedTable, staleIdFallback);
        boolean componentPresent = bindings.stream().anyMatch(BindingLocation::hasMiAssignmentComponent);
        if (!componentPresent) {
            result.addError(
                    "MISSING_MI_ASSIGNMENT_COMPONENT",
                    "MI assignment node " + userTaskId + " for SubTable '" + subTableName
                            + "' requires a miAssignment component in the bound sub-form",
                    subProcessId);
        }
    }

    private List<BindingLocation> findBindings(
            Long configuredTableId, TableDefinition resolvedTable, boolean staleIdFallback) {
        List<BindingLocation> exact = locationsMatching(
                binding -> binding.getTable() != null
                        && configuredTableId.equals(binding.getTable().getId()));
        if (!exact.isEmpty() || !staleIdFallback || resolvedTable == null) {
            return exact;
        }
        String resolvedName = resolvedTable.getTableName();
        return locationsMatching(binding -> binding.getTable() != null
                && resolvedName != null
                && resolvedName.equals(binding.getTable().getTableName()));
    }

    private List<BindingLocation> locationsMatching(
            java.util.function.Predicate<FormTableBinding> predicate) {
        return forms.stream()
                .flatMap(form -> form.getTableBindings().stream()
                        .filter(predicate)
                        .map(binding -> new BindingLocation(form, binding)))
                .toList();
    }

    private static boolean containsMiAssignment(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type != null && "miAssignment".equals(String.valueOf(type))) {
                return true;
            }
            return map.values().stream().anyMatch(MiAssignmentFormGuard::containsMiAssignment);
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

    private static Object findSubForm(Map<String, Object> configJson, Long bindingId) {
        if (configJson == null || !(configJson.get("subForms") instanceof Map<?, ?> subForms)) {
            return null;
        }
        for (Map.Entry<?, ?> entry : subForms.entrySet()) {
            if (String.valueOf(bindingId).equals(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isSupportedMode(String mode) {
        return "user".equalsIgnoreCase(mode)
                || "role".equalsIgnoreCase(mode)
                || "both".equalsIgnoreCase(mode);
    }

    private record AssignmentContract(
            String mode, String assigneeField, String roleField, String buField) {

        private AssignmentContract {
            mode = mode.toLowerCase(java.util.Locale.ROOT);
        }

        boolean isComplete() {
            boolean userComplete = !"role".equals(mode) && assigneeField != null;
            boolean roleComplete = !"user".equals(mode) && roleField != null;
            return switch (mode) {
                case "user" -> userComplete;
                case "role" -> roleComplete;
                case "both" -> userComplete && roleComplete;
                default -> false;
            };
        }
    }

    private record NodeContract(String nodeId, AssignmentContract contract) {
    }

    private record BindingLocation(FormDefinition form, FormTableBinding binding) {
        boolean hasMiAssignmentComponent() {
            Object subForm = findSubForm(form.getConfigJson(), binding.getId());
            if (!(subForm instanceof Map<?, ?> subFormMap)) {
                return false;
            }
            return containsMiAssignment(subFormMap.get("rule"));
        }
    }
}
