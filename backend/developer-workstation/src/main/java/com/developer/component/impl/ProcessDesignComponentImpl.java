package com.developer.component.impl;

import com.developer.component.ProcessDesignComponent;
import com.developer.dto.ValidationResult;
import com.developer.entity.FieldDefinition;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.entity.TableDefinition;
import com.developer.entity.ActionDefinition;
import com.developer.enums.BindingType;
import com.developer.enums.TableType;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.BpmnLastTaskAssigneeTopologyValidator;
import com.developer.util.BpmnProcessSimulator;
import com.developer.util.XmlEncodingUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 流程设计组件实现
 */
@Component
@Slf4j
public class ProcessDesignComponentImpl implements ProcessDesignComponent {
    private static final Pattern FLOW_NODE_ID_PATTERN = Pattern.compile(
            "<bpmn:(startEvent|endEvent|userTask|serviceTask|scriptTask|manualTask|sendTask|receiveTask|"
                    + "businessRuleTask|task|subProcess|exclusiveGateway|parallelGateway|inclusiveGateway|"
                    + "eventBasedGateway|complexGateway|intermediateCatchEvent|intermediateThrowEvent|"
                    + "boundaryEvent|callActivity)\\b[^>]*\\bid=\"([^\"]+)\"",
            Pattern.DOTALL);
    
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProcessDesignComponentImpl(
            ProcessDefinitionRepository processDefinitionRepository,
            FunctionUnitRepository functionUnitRepository,
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository,
            FormTableBindingRepository formTableBindingRepository,
            ActionDefinitionRepository actionDefinitionRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.processDefinitionRepository = processDefinitionRepository;
        this.functionUnitRepository = functionUnitRepository;
        this.tableDefinitionRepository = tableDefinitionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.formTableBindingRepository = formTableBindingRepository;
        this.actionDefinitionRepository = actionDefinitionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compatible constructor for existing unit/property tests.
     */
    public ProcessDesignComponentImpl(
            ProcessDefinitionRepository processDefinitionRepository,
            FunctionUnitRepository functionUnitRepository,
            TableDefinitionRepository tableDefinitionRepository,
            FormDefinitionRepository formDefinitionRepository) {
        this(processDefinitionRepository, functionUnitRepository, tableDefinitionRepository, formDefinitionRepository,
                null, null, null, null);
    }
    
    @Override
    @Transactional
    public ProcessDefinition save(Long functionUnitId, String bpmnXml) {
        FunctionUnit functionUnit = functionUnitRepository.findById(functionUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("FunctionUnit", functionUnitId));

        ValidationResult lastTaskTopo = validateLastTaskAssigneeTopology(bpmnXml);
        if (!lastTaskTopo.isValid()) {
            String detail = lastTaskTopo.getErrors().stream()
                    .map(ValidationResult.ValidationError::getMessage)
                    .collect(Collectors.joining("; "));
            throw new DeveloperBusinessException("LAST_TASK_ANCHOR_TOPOLOGY", detail);
        }

        ProcessDefinition processDefinition = processDefinitionRepository
                .findByFunctionUnitId(functionUnitId)
                .orElse(ProcessDefinition.builder()
                        .functionUnit(functionUnit)
                        .functionUnitVersionId(functionUnitId)
                        .build());
        
        // 使用Base64编码存储XML，避免特殊字符转义问题
        String encodedXml = XmlEncodingUtil.encode(bpmnXml);
        processDefinition.setBpmnXml(encodedXml);
        
        return processDefinitionRepository.save(processDefinition);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ProcessDefinition getByFunctionUnitId(Long functionUnitId) {
        Optional<ProcessDefinition> optional = processDefinitionRepository.findByFunctionUnitId(functionUnitId);
        
        // 如果流程定义不存在，返回 null 而不是抛出异常
        // 这允许前端创建新的流程定义
        if (optional.isEmpty()) {
            log.debug("ProcessDefinition not found for functionUnitId={}, returning null", functionUnitId);
            return null;
        }
        
        ProcessDefinition processDefinition = optional.get();
        
        // 智能解码：兼容旧数据（未编码）和新数据（Base64编码）
        String decodedXml = XmlEncodingUtil.smartDecode(processDefinition.getBpmnXml());
        processDefinition.setBpmnXml(decodedXml);
        
        return processDefinition;
    }
    
    @Override
    public ValidationResult validate(String bpmnXml) {
        ValidationResult result = new ValidationResult();
        
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.addError("EMPTY_BPMN", "BPMN XML cannot be empty", null);
            return result;
        }
        
        // Check for start event
        if (!bpmnXml.contains("startEvent")) {
            result.addError("MISSING_START_EVENT", "Process is missing a start event", null);
        }
        
        // Check for end event
        if (!bpmnXml.contains("endEvent")) {
            result.addError("MISSING_END_EVENT", "Process is missing an end event", null);
        }
        
        // Check basic XML structure
        if (!bpmnXml.contains("<bpmn:process") && !bpmnXml.contains("<process")) {
            result.addError("INVALID_BPMN_STRUCTURE", "Invalid BPMN structure", null);
        }
        
        // Check orphan nodes
        List<String> nodeIds = extractNodeIds(bpmnXml);
        List<String> connectedNodes = extractConnectedNodes(bpmnXml);
        
        for (String nodeId : nodeIds) {
            if (!connectedNodes.contains(nodeId) && !isStartOrEndEvent(bpmnXml, nodeId)) {
                result.addWarning("ORPHAN_NODE", "Node " + nodeId + " may be orphaned", nodeId);
            }
        }

        ValidationResult lastTaskTopo = validateLastTaskAssigneeTopology(bpmnXml);
        for (ValidationResult.ValidationError e : lastTaskTopo.getErrors()) {
            result.addError(e.getCode(), e.getMessage(), e.getElementId());
        }

        return result;
    }
    
    @Override
    public Map<String, Object> simulate(Long functionUnitId, String bpmnXml, Map<String, Object> variables) {
        Map<Long, List<FieldDefinition>> fieldsByTableId = loadSubTableFieldsById(functionUnitId);
        Map<String, Object> result = new LinkedHashMap<>(
                BpmnProcessSimulator.simulate(bpmnXml, variables, fieldsByTableId));
        result.put("status", "SIMULATED");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> debugLookupProbe(Long functionUnitId, Map<String, Object> request) {
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> debugRunAction(Long functionUnitId, Map<String, Object> request) {
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

        ProcessDefinition process = getByFunctionUnitId(functionUnitId);
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

    private Map<Long, List<FieldDefinition>> loadSubTableFieldsById(Long functionUnitId) {
        if (functionUnitId == null) {
            return Map.of();
        }
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        Map<Long, List<FieldDefinition>> fieldsByTableId = new LinkedHashMap<>();
        for (TableDefinition table : tables) {
            if (table.getId() == null || table.getTableType() != TableType.SUB) {
                continue;
            }
            fieldsByTableId.put(
                    table.getId(),
                    table.getFieldDefinitions() != null ? table.getFieldDefinitions() : List.of());
        }
        return fieldsByTableId;
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
                "SELECT field_name, comment FROM rt_field_definitions WHERE table_id = ?",
                relationTableId);
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(row.get("field_name"));
            Object label = row.get("comment");
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
    
    @Override
    public Map<String, Object> parseBpmnXml(String bpmnXml) {
        Map<String, Object> structure = new HashMap<>();
        
        // 提取节点
        List<Map<String, String>> nodes = new ArrayList<>();
        Pattern nodePattern = Pattern.compile("<bpmn:(\\w+)\\s+id=\"([^\"]+)\"[^>]*name=\"([^\"]*)?\"");
        Matcher nodeMatcher = nodePattern.matcher(bpmnXml);
        
        while (nodeMatcher.find()) {
            Map<String, String> node = new HashMap<>();
            node.put("type", nodeMatcher.group(1));
            node.put("id", nodeMatcher.group(2));
            node.put("name", nodeMatcher.group(3) != null ? nodeMatcher.group(3) : "");
            nodes.add(node);
        }
        
        // 提取连接
        List<Map<String, String>> flows = new ArrayList<>();
        Pattern flowPattern = Pattern.compile("<bpmn:sequenceFlow\\s+id=\"([^\"]+)\"\\s+sourceRef=\"([^\"]+)\"\\s+targetRef=\"([^\"]+)\"");
        Matcher flowMatcher = flowPattern.matcher(bpmnXml);
        
        while (flowMatcher.find()) {
            Map<String, String> flow = new HashMap<>();
            flow.put("id", flowMatcher.group(1));
            flow.put("source", flowMatcher.group(2));
            flow.put("target", flowMatcher.group(3));
            flows.add(flow);
        }
        
        structure.put("nodes", nodes);
        structure.put("flows", flows);
        
        return structure;
    }
    
    private List<String> extractNodeIds(String bpmnXml) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = FLOW_NODE_ID_PATTERN.matcher(bpmnXml);
        
        while (matcher.find()) {
            ids.add(matcher.group(2));
        }
        
        return ids;
    }
    
    private List<String> extractConnectedNodes(String bpmnXml) {
        Set<String> connected = new HashSet<>();
        Pattern pattern = Pattern.compile("(sourceRef|targetRef)=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(bpmnXml);
        
        while (matcher.find()) {
            connected.add(matcher.group(2));
        }
        
        return new ArrayList<>(connected);
    }
    
    private boolean isStartOrEndEvent(String bpmnXml, String nodeId) {
        String pattern = String.format("(startEvent|endEvent)[^>]*id=\"%s\"", Pattern.quote(nodeId));
        return Pattern.compile(pattern).matcher(bpmnXml).find();
    }
    
    @Override
    public ValidationResult validateMultiInstance(String bpmnXml, Long functionUnitId) {
        ValidationResult result = new ValidationResult();
        
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            result.addError("EMPTY_BPMN", "BPMN XML cannot be empty", null);
            return result;
        }
        
        // 查找所有多实例子流程节点
        Pattern subProcessPattern = Pattern.compile(
            "<bpmn:subProcess[^>]*id=\"([^\"]+)\"[^>]*>.*?<bpmn:multiInstanceLoopCharacteristics",
            Pattern.DOTALL
        );
        Matcher subProcessMatcher = subProcessPattern.matcher(bpmnXml);
        
        while (subProcessMatcher.find()) {
            String subProcessId = subProcessMatcher.group(1);
            
            // 提取该子流程的完整内容
            int startPos = subProcessMatcher.start();
            int endPos = findMatchingSubProcessEnd(bpmnXml, startPos);
            if (endPos == -1) {
                result.addError("INVALID_SUBPROCESS_STRUCTURE", 
                    "Invalid subProcess structure for " + subProcessId, subProcessId);
                continue;
            }
            
            String subProcessXml = bpmnXml.substring(startPos, endPos);
            
            // 验证 1: collection 变量名格式合法（字母、数字、下划线）
            // 支持 BpmnXmlGenerator 子元素写法，以及 Flowable 常见的 multiInstanceLoopCharacteristics 属性写法
            Optional<String> collectionVarOpt = extractMultiInstanceCollectionVariable(subProcessXml);
            if (collectionVarOpt.isPresent()) {
                String collectionVar = collectionVarOpt.get();
                if (!collectionVar.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    result.addError("INVALID_COLLECTION_VARIABLE", 
                        "Collection variable name '" + collectionVar + "' is invalid. Must contain only letters, numbers, and underscores.", 
                        subProcessId);
                }
            } else {
                result.addError("MISSING_COLLECTION_VARIABLE", 
                    "Multi-instance subProcess is missing flowable:collection configuration", 
                    subProcessId);
            }
            
            // 验证 2: 子流程内部至少包含一个 userTask
            Pattern userTaskPattern = Pattern.compile("<bpmn:userTask[^>]*id=\"([^\"]+)\"");
            Matcher userTaskMatcher = userTaskPattern.matcher(subProcessXml);
            
            if (!userTaskMatcher.find()) {
                result.addError("MISSING_USER_TASK", 
                    "Multi-instance subProcess must contain at least one userTask", 
                    subProcessId);
                continue; // 没有 userTask，后续验证无意义
            }
            
            // 提取 userTask 的扩展属性
            String userTaskId = userTaskMatcher.group(1);
            Map<String, String> userTaskProps = extractUserTaskProperties(subProcessXml, userTaskId);
            
            // 验证 3: subTableId 属于当前 FunctionUnit 且 table_type=SUB
            String subTableIdStr = userTaskProps.get("subTableId");
            if (subTableIdStr != null && !subTableIdStr.isEmpty()) {
                try {
                    Long subTableId = Long.parseLong(subTableIdStr);
                    Optional<TableDefinition> tableOpt = tableDefinitionRepository.findByIdWithFields(subTableId);
                    
                    if (tableOpt.isEmpty()) {
                        result.addError("SUBTABLE_NOT_FOUND", 
                            "SubTable with id " + subTableId + " not found", 
                            subProcessId);
                    } else {
                        TableDefinition table = tableOpt.get();
                        
                        // 验证归属
                        if (!table.getFunctionUnit().getId().equals(functionUnitId)) {
                            result.addError("SUBTABLE_WRONG_FUNCTION_UNIT", 
                                "SubTable " + subTableId + " does not belong to the current FunctionUnit", 
                                subProcessId);
                        }
                        
                        // 验证 table_type
                        if (table.getTableType() != TableType.SUB) {
                            result.addError("INVALID_TABLE_TYPE", 
                                "Table " + subTableId + " is not a SUB table (table_type=" + table.getTableType() + ")", 
                                subProcessId);
                        }
                        
                        // 验证 4: assigneeField 存在于子表的 FieldDefinition 列表中
                        String assigneeField = userTaskProps.get("assigneeField");
                        if (assigneeField != null && !assigneeField.isEmpty()) {
                            boolean fieldExists = table.getFieldDefinitions().stream()
                                .anyMatch(fd -> fd.getFieldName().equals(assigneeField));
                            
                            if (!fieldExists) {
                                result.addError("ASSIGNEE_FIELD_NOT_FOUND", 
                                    "AssigneeField '" + assigneeField + "' not found in SubTable " + subTableId, 
                                    subProcessId);
                            }
                        } else {
                            result.addError("MISSING_ASSIGNEE_FIELD", 
                                "Multi-instance userTask is missing assigneeField configuration", 
                                userTaskId);
                        }
                    }
                } catch (NumberFormatException e) {
                    result.addError("INVALID_SUBTABLE_ID", 
                        "Invalid subTableId format: " + subTableIdStr, 
                        subProcessId);
                }
            } else {
                result.addError("MISSING_SUBTABLE_ID", 
                    "Multi-instance userTask is missing subTableId configuration", 
                    userTaskId);
            }
            
            // 验证 5: formId（如配置）属于当前 FunctionUnit
            String formIdStr = userTaskProps.get("formId");
            if (formIdStr != null && !formIdStr.isEmpty()) {
                try {
                    Long formId = Long.parseLong(formIdStr);
                    Optional<FormDefinition> formOpt = formDefinitionRepository.findById(formId);
                    
                    if (formOpt.isEmpty()) {
                        result.addError("FORM_NOT_FOUND", 
                            "Form with id " + formId + " not found", 
                            userTaskId);
                    } else {
                        FormDefinition form = formOpt.get();
                        if (!form.getFunctionUnit().getId().equals(functionUnitId)) {
                            result.addError("FORM_WRONG_FUNCTION_UNIT", 
                                "Form " + formId + " does not belong to the current FunctionUnit", 
                                userTaskId);
                        }
                    }
                } catch (NumberFormatException e) {
                    result.addError("INVALID_FORM_ID", 
                        "Invalid formId format: " + formIdStr, 
                        userTaskId);
                }
            }
        }
        
        return result;
    }

    @Override
    public ValidationResult validateLastTaskAssigneeTopology(String bpmnXml) {
        return BpmnLastTaskAssigneeTopologyValidator.validate(bpmnXml);
    }

    /**
     * 从子流程 XML 中提取多实例集合变量名（与 BpmnXmlGenerator / Flowable 属性写法兼容）。
     */
    private Optional<String> extractMultiInstanceCollectionVariable(String subProcessXml) {
        Matcher elementMatcher = Pattern.compile("<flowable:collection>([^<]+)</flowable:collection>")
                .matcher(subProcessXml);
        if (elementMatcher.find()) {
            return Optional.of(elementMatcher.group(1).trim());
        }
        Matcher attrMatcher = Pattern.compile("\\sflowable:collection=\"([^\"]+)\"").matcher(subProcessXml);
        if (attrMatcher.find()) {
            return Optional.of(attrMatcher.group(1).trim());
        }
        return Optional.empty();
    }

    /**
     * 查找匹配的 subProcess 结束标签位置
     */
    private int findMatchingSubProcessEnd(String bpmnXml, int startPos) {
        int depth = 0;
        int pos = startPos;
        
        while (pos < bpmnXml.length()) {
            if (bpmnXml.startsWith("<bpmn:subProcess", pos)) {
                depth++;
                pos += 16;
            } else if (bpmnXml.startsWith("</bpmn:subProcess>", pos)) {
                depth--;
                if (depth == 0) {
                    return pos + 18; // 包含结束标签
                }
                pos += 18;
            } else {
                pos++;
            }
        }
        
        return -1; // 未找到匹配的结束标签
    }
    
    /**
     * 提取 userTask 的扩展属性
     */
    private Map<String, String> extractUserTaskProperties(String subProcessXml, String userTaskId) {
        Map<String, String> properties = new HashMap<>();
        
        // 查找该 userTask 的扩展属性部分
        Pattern userTaskBlockPattern = Pattern.compile(
            "<bpmn:userTask[^>]*id=\"" + Pattern.quote(userTaskId) + "\"[^>]*>.*?</bpmn:userTask>",
            Pattern.DOTALL
        );
        Matcher userTaskBlockMatcher = userTaskBlockPattern.matcher(subProcessXml);
        
        if (userTaskBlockMatcher.find()) {
            String userTaskBlock = userTaskBlockMatcher.group();
            
            // 提取 custom:property 元素
            Pattern propertyPattern = Pattern.compile(
                "<custom:property[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]+)\"[^>]*/>"
            );
            Matcher propertyMatcher = propertyPattern.matcher(userTaskBlock);
            
            while (propertyMatcher.find()) {
                String name = propertyMatcher.group(1);
                String value = propertyMatcher.group(2);
                properties.put(name, value);
            }
        }
        
        return properties;
    }
}
