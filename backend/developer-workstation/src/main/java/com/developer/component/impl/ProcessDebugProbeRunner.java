package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.ProcessDefinition;
import com.developer.enums.BindingType;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 调试探针 / 动作试跑协作类。
 *
 * <p>从 {@link ProcessDesignComponentImpl} 拆出，负责调试 lookup live probe 与 action runner（dry run）。
 * 依赖 FormTableBinding/ActionDefinition 仓库、JdbcTemplate、ObjectMapper（仅调试场景需要，可为 null）。
 * 校验、SQL、异常码/消息逐字保留，行为零变化。</p>
 */
@Component
@Slf4j
public class ProcessDebugProbeRunner {

    private final FormDefinitionRepository formDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProcessDebugProbeRunner(
            FormDefinitionRepository formDefinitionRepository,
            FormTableBindingRepository formTableBindingRepository,
            ActionDefinitionRepository actionDefinitionRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.formDefinitionRepository = formDefinitionRepository;
        this.formTableBindingRepository = formTableBindingRepository;
        this.actionDefinitionRepository = actionDefinitionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    Map<String, Object> debugLookupProbe(Long functionUnitId, Map<String, Object> request) {
        requireDebugDependencies();
        Long formId = toLong(request.get("formId"));
        Long bindingId = toLong(request.get("bindingId"));
        if (formId == null || bindingId == null) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                    "lookup probe requires formId and bindingId");
        }

        FormDefinition form = formDefinitionRepository.findById(formId)
                .orElseThrow(() -> new DeveloperBusinessException(
                        "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                        "formId does not exist: " + formId));
        if (!Objects.equals(form.getFunctionUnit().getId(), functionUnitId)) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_PERMISSION_DENIED",
                    "form does not belong to current function unit");
        }

        FormTableBinding binding = formTableBindingRepository.findByFormIdOrderBySortOrder(formId).stream()
                .filter(item -> Objects.equals(item.getId(), bindingId))
                .findFirst()
                .orElseThrow(() -> new DeveloperBusinessException(
                        "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                        "bindingId does not exist in this form: " + bindingId));
        if (binding.getBindingType() != BindingType.RELATED) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                    "bindingId is not a RELATED binding in this form");
        }
        Long relationTableId = binding.getRelationTableId();
        if (relationTableId == null && binding.getTable() != null) {
            relationTableId = binding.getTable().getId();
        }
        if (relationTableId == null) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                    "bindingId has no relation table target");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lookupConfig = request.get("lookupConfig") instanceof Map<?, ?> config
                ? (Map<String, Object>) config
                : Map.of();
        List<String> searchFields = toStringList(lookupConfig.get("searchFields"));
        List<String> displayFields = toStringList(lookupConfig.get("displayFields"));
        List<Map<String, Object>> filterConditions = toMapList(lookupConfig.get("filterConditions"));

        if (searchFields.isEmpty() && displayFields.isEmpty()) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                    "lookupConfig.searchFields or displayFields is required");
        }

        String keyword = request.get("keyword") == null ? "" : String.valueOf(request.get("keyword"));
        int page = normalizePage(request.get("page"));
        int size = normalizeSize(request.get("size"));
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeVariables = request.get("runtimeVariables") instanceof Map<?, ?> vars
                ? (Map<String, Object>) vars
                : Map.of();

        List<String> allowedFields = getRelationFieldNames(relationTableId);
        Map<String, String> fieldLabels = getRelationFieldLabels(relationTableId);

        List<Map<String, Object>> appliedFilters = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        params.add(relationTableId);

        for (Map<String, Object> condition : filterConditions) {
            String fieldName = condition.get("fieldName") == null ? null : String.valueOf(condition.get("fieldName"));
            if (fieldName == null || !allowedFields.contains(fieldName)) {
                continue;
            }
            String rawValue = condition.get("value") == null ? "" : String.valueOf(condition.get("value"));
            String resolved = resolveTemplate(rawValue, runtimeVariables);
            predicates.add("data->>'" + sanitizeIdentifier(fieldName) + "' = ?");
            params.add(resolved);

            Map<String, Object> filterEntry = new LinkedHashMap<>();
            filterEntry.put("fieldName", fieldName);
            filterEntry.put("value", resolved);
            appliedFilters.add(filterEntry);
        }

        List<String> searchable = searchFields.stream()
                .filter(allowedFields::contains)
                .map(this::sanitizeIdentifier)
                .toList();
        if (!keyword.isBlank() && !searchable.isEmpty()) {
            String like = "%" + keyword + "%";
            String keywordClause = searchable.stream()
                    .map(field -> "data->>'" + field + "' ILIKE ?")
                    .collect(Collectors.joining(" OR "));
            predicates.add("(" + keywordClause + ")");
            searchable.forEach(ignored -> params.add(like));
        }

        String whereClause = predicates.isEmpty() ? "" : " AND " + String.join(" AND ", predicates);
        List<Object> countParams = new ArrayList<>(params);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rt_table_data_rows WHERE table_id = ?" + whereClause,
                Long.class,
                countParams.toArray());
        if (total == null) {
            total = 0L;
        }

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add(page * size);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT data FROM rt_table_data_rows WHERE table_id = ?" + whereClause + " ORDER BY id LIMIT ? OFFSET ?",
                (rs, rowNum) -> parseJsonRow(rs.getString("data")),
                queryParams.toArray());

        List<String> columnsToShow = !displayFields.isEmpty() ? displayFields : searchFields;
        List<Map<String, Object>> columns = columnsToShow.stream()
                .filter(allowedFields::contains)
                .map(field -> {
                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("fieldName", field);
                    column.put("label", fieldLabels.getOrDefault(field, field));
                    return column;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("appliedFilters", appliedFilters);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        return result;
    }

    /**
     * Debug action runner (dry run)。流程定义由门面解析后传入，避免协作类反向依赖门面。
     */
    Map<String, Object> debugRunAction(Long functionUnitId, Map<String, Object> request, ProcessDefinition process) {
        requireDebugDependencies();
        String nodeId = request.get("nodeId") == null ? null : String.valueOf(request.get("nodeId"));
        String actionIdRaw = request.get("actionId") == null ? null : String.valueOf(request.get("actionId"));
        if (nodeId == null || nodeId.isBlank() || actionIdRaw == null || actionIdRaw.isBlank()) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_ACTION_NOT_FOUND",
                    "action runner requires nodeId and actionId");
        }

        Long actionId;
        try {
            actionId = Long.parseLong(actionIdRaw);
        } catch (NumberFormatException e) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_ACTION_NOT_FOUND",
                    "actionId is invalid: " + actionIdRaw);
        }

        ActionDefinition action = actionDefinitionRepository.findById(actionId)
                .orElseThrow(() -> new DeveloperBusinessException(
                        "BIZ_DEBUG_ACTION_NOT_FOUND",
                        "actionId does not exist: " + actionIdRaw));
        if (!Objects.equals(action.getFunctionUnit().getId(), functionUnitId)) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_PERMISSION_DENIED",
                    "action does not belong to current function unit");
        }

        if (process == null || process.getBpmnXml() == null) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_ACTION_NOT_FOUND",
                    "process definition not found");
        }
        List<String> nodeActionIds = extractActionIdsFromNode(process.getBpmnXml(), nodeId);
        if (!nodeActionIds.isEmpty() && nodeActionIds.stream().noneMatch(id -> id.equals(String.valueOf(actionId)))) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_ACTION_NOT_FOUND",
                    "actionId does not belong to node: " + nodeId);
        }

        long start = System.currentTimeMillis();
        @SuppressWarnings("unchecked")
        Map<String, Object> formData = request.get("formData") instanceof Map<?, ?> fd
                ? (Map<String, Object>) fd
                : Map.of();

        Map<String, Object> actionResult = new LinkedHashMap<>();
        actionResult.put("code", "OK");
        actionResult.put("message", "Action executed");
        actionResult.put("testResult", "SUCCESS");
        actionResult.put("actionName", action.getActionName());

        Map<String, Object> variablePatches = inferVariablePatches(action, formData);
        List<String> logs = List.of(
                "validate input",
                "invoke action handler",
                "build output patch"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("actionResult", actionResult);
        result.put("variablePatches", variablePatches);
        result.put("logs", logs);
        result.put("durationMs", Math.max(1, System.currentTimeMillis() - start));
        return result;
    }

    private void requireDebugDependencies() {
        if (formTableBindingRepository == null || actionDefinitionRepository == null
                || jdbcTemplate == null || objectMapper == null) {
            throw new IllegalStateException("Debug dependencies are not initialized");
        }
    }

    private List<String> getRelationFieldNames(Long relationTableId) {
        if (relationTableId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT field_name FROM rt_field_definitions WHERE table_id = ? ORDER BY sort_order ASC",
                (rs, rowNum) -> rs.getString("field_name"),
                relationTableId);
    }

    private Map<String, String> getRelationFieldLabels(Long relationTableId) {
        if (relationTableId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT field_name, display_name FROM rt_field_definitions WHERE table_id = ?",
                relationTableId);
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(row.get("field_name"));
            Object label = row.get("display_name");
            labels.put(fieldName, label == null ? fieldName : String.valueOf(label));
        }
        return labels;
    }

    private Map<String, Object> parseJsonRow(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse relation table row JSON: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new DeveloperBusinessException(
                    "BIZ_DEBUG_LOOKUP_CONFIG_INVALID",
                    "invalid field name: " + identifier);
        }
        return identifier;
    }

    private String resolveTemplate(String raw, Map<String, Object> runtimeVariables) {
        if (raw == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}").matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = runtimeVariables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String str = String.valueOf(item).trim();
            if (!str.isEmpty()) {
                result.add(str);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private int normalizePage(Object pageRaw) {
        Integer parsed = toInteger(pageRaw);
        if (parsed == null || parsed < 0) {
            return 0;
        }
        return parsed;
    }

    private int normalizeSize(Object sizeRaw) {
        Integer parsed = toInteger(sizeRaw);
        if (parsed == null || parsed <= 0) {
            return 20;
        }
        return Math.min(parsed, 200);
    }

    private Integer toInteger(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> extractActionIdsFromNode(String bpmnXml, String nodeId) {
        if (bpmnXml == null || bpmnXml.isBlank() || nodeId == null || nodeId.isBlank()) {
            return List.of();
        }
        String nodeBlock = extractNodeBlock(bpmnXml, nodeId);
        if (nodeBlock == null) {
            return List.of();
        }
        Pattern actionIdsPattern = Pattern.compile(
                "<(?:custom:|custom_1:)?(?:property|values)\\s+name=\"actionIds\"\\s+value=\"([^\"]+)\"\\s*/>");
        Matcher actionMatcher = actionIdsPattern.matcher(nodeBlock);
        if (!actionMatcher.find()) {
            return List.of();
        }
        String raw = actionMatcher.group(1);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String unescaped = raw
                .replace("&#34;", "\"")
                .replace("&quot;", "\"");
        try {
            List<?> parsed = objectMapper.readValue(unescaped, List.class);
            return parsed.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        } catch (Exception ignored) {
            String cleaned = unescaped.replaceAll("[\\[\\]\\s\"]", "");
            if (cleaned.isBlank()) {
                return List.of();
            }
            return Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    private String extractNodeBlock(String bpmnXml, String nodeId) {
        List<String> nodeTags = List.of("userTask", "serviceTask");
        for (String tag : nodeTags) {
            Pattern nodePattern = Pattern.compile(
                    "<bpmn:" + tag + "[^>]*id=\"" + Pattern.quote(nodeId) + "\"[^>]*>([\\s\\S]*?)</bpmn:" + tag + ">",
                    Pattern.DOTALL);
            Matcher nodeMatcher = nodePattern.matcher(bpmnXml);
            if (nodeMatcher.find()) {
                return nodeMatcher.group(1);
            }
        }
        return null;
    }

    private Map<String, Object> inferVariablePatches(ActionDefinition action, Map<String, Object> formData) {
        Map<String, Object> patches = new LinkedHashMap<>();
        String actionType = action.getActionType() != null ? action.getActionType().name() : "";
        switch (actionType) {
            case "APPROVE" -> patches.put("approval_status", "APPROVED");
            case "REJECT" -> patches.put("approval_status", "REJECTED");
            default -> {
                // keep empty by default
            }
        }

        Object configuredPatches = action.getConfigJson() != null ? action.getConfigJson().get("debugVariablePatches") : null;
        if (configuredPatches instanceof Map<?, ?> configuredMap) {
            for (Map.Entry<?, ?> entry : configuredMap.entrySet()) {
                if (entry.getKey() != null) {
                    patches.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        if (patches.isEmpty() && formData.containsKey("approval_status")) {
            patches.put("approval_status", formData.get("approval_status"));
        }
        return patches;
    }
}
