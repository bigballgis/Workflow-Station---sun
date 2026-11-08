package com.portal.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.portal.dto.ChangeHistoryContext;
import com.portal.entity.ProcessInstance;
import com.portal.component.ChangeHistoryComponent;
import com.portal.repository.ProcessInstanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Function Unit data calls made by the automation engine. Actor comes from the workspace, not the request. */
@Component
public class AutomationRecordComponent {

    private static final String SUB_TABLES = "__subTables__";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final AutomationAdminClient admin;
    private final ProcessInstanceRepository processes;
    private final ChangeHistoryComponent history;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AutomationIdempotency idempotency;

    public AutomationRecordComponent(AutomationAdminClient admin, ProcessInstanceRepository processes,
                                     ChangeHistoryComponent history, JdbcTemplate jdbc, ObjectMapper mapper,
                                     AutomationIdempotency idempotency) {
        this.admin = admin;
        this.processes = processes;
        this.history = history;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.idempotency = idempotency;
    }

    public Map<String, Object> schemaByCode(String functionUnitCode, String externalProjectId, String flowId) {
        AutomationAdminClient.Scope scope = open(externalProjectId, flowId, "read");
        if (functionUnitCode == null || !functionUnitCode.equals(codeOf(scope.functionUnitId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This flow cannot access that Function Unit");
        }
        return schema(scope);
    }

    public Map<String, Object> schemaByRecord(String processInstanceId, String externalProjectId, String flowId) {
        AutomationAdminClient.Scope scope = open(externalProjectId, flowId, "read");
        ProcessInstance process = load(processInstanceId, scope);
        Map<String, Object> body = schema(scope);
        body.put("processInstanceId", process.getId());
        body.put("lockVersion", process.getLockVersion());
        return body;
    }

    public Map<String, Object> read(String processInstanceId, String externalProjectId, String flowId) {
        AutomationAdminClient.Scope scope = open(externalProjectId, flowId, "read");
        return recordView(load(processInstanceId, scope));
    }

    public Map<String, Object> query(Map<String, Object> body) {
        String externalProjectId = text(body, "externalProjectId");
        String flowId = text(body, "flowId");
        AutomationAdminClient.Scope scope = open(externalProjectId, flowId, "query");
        if (!text(body, "functionUnitCode").equals(codeOf(scope.functionUnitId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This flow cannot query that Function Unit");
        }
        List<Map<String, Object>> filters = filters(body.get("filters"));
        AutomationAccessRules.requireFilters(filters, mainFields(scope.functionUnitId()));
        int size = AutomationAccessRules.pageSize(intValue(body.get("size")));
        int page = page(body.get("page"));
        return Map.of("records", queryRows(scope.catalogId(), filters, page, size), "page", page, "size", size);
    }

    @Transactional
    public Map<String, Object> patchFields(String processInstanceId, Map<String, Object> body, String idempotencyKey) {
        return write(idempotencyKey, body, () -> patchFieldsNow(processInstanceId, body));
    }

    @Transactional
    public Map<String, Object> addRow(String processInstanceId, long bindingId, Map<String, Object> body,
                                      String idempotencyKey) {
        return write(idempotencyKey, body, () -> mutateRow(processInstanceId, bindingId, null, body, false));
    }

    @Transactional
    public Map<String, Object> replaceRow(String processInstanceId, long bindingId, String rowId,
                                          Map<String, Object> body, String idempotencyKey) {
        return write(idempotencyKey, body, () -> mutateRow(processInstanceId, bindingId, rowId, body, false));
    }

    @Transactional
    public Map<String, Object> deleteRow(String processInstanceId, long bindingId, String rowId,
                                         Map<String, Object> body, String idempotencyKey) {
        return write(idempotencyKey, body, () -> mutateRow(processInstanceId, bindingId, rowId, body, true));
    }

    private Map<String, Object> patchFieldsNow(String processInstanceId, Map<String, Object> body) {
        AutomationAdminClient.Scope scope = open(text(body, "externalProjectId"), text(body, "flowId"), "write");
        ProcessInstance process = loadForUpdate(processInstanceId, scope, longValue(body.get("lockVersion")));
        Map<String, Object> fields = objectMap(body.get("fields"));
        Set<String> allowed = mainFields(scope.functionUnitId());
        Map<String, Object> variables = copy(process.getVariables());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            if (!allowed.contains(field.getKey())) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Field is not on the main table");
            }
            before.put(field.getKey(), variables.get(field.getKey()));
            after.put(field.getKey(), field.getValue());
            variables.put(field.getKey(), field.getValue());
        }
        return save(process, variables, text(body, "externalProjectId"), text(body, "flowId"), before, after);
    }

    private Map<String, Object> mutateRow(String processInstanceId, long bindingId, String rowId,
                                          Map<String, Object> body, boolean delete) {
        AutomationAdminClient.Scope scope = open(text(body, "externalProjectId"), text(body, "flowId"), "subtable");
        requireBinding(scope.functionUnitId(), bindingId);
        ProcessInstance process = loadForUpdate(processInstanceId, scope, longValue(body.get("lockVersion")));
        String primaryKey = primaryKey(bindingId);
        Map<String, Object> incoming = delete ? Map.of() : objectMap(body.get("fields"));
        if (incoming.containsKey(primaryKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "The server assigns the sub-table primary key");
        }
        Map<String, Object> variables = copy(process.getVariables());
        List<Map<String, Object>> rows = rows(variables, bindingId);
        if (delete) {
            if (!rows.removeIf(row -> rowId.equals(String.valueOf(row.get(primaryKey))))) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sub-table row was not found");
            }
        } else if (rowId == null) {
            Map<String, Object> created = new LinkedHashMap<>(incoming);
            created.put(primaryKey, UUID.randomUUID().toString());
            rows.add(created);
        } else {
            Map<String, Object> current = rows.stream().filter(row -> rowId.equals(String.valueOf(row.get(primaryKey))))
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "Sub-table row was not found"));
            current.putAll(incoming);
            current.put(primaryKey, rowId);
        }
        subTables(variables).put(String.valueOf(bindingId), rows);
        return save(process, variables, text(body, "externalProjectId"), text(body, "flowId"), Map.of(),
                Map.of(SUB_TABLES, subTables(variables)));
    }

    private Map<String, Object> save(ProcessInstance process, Map<String, Object> variables,
                                     String externalProjectId, String flowId, Map<String, Object> before,
                                     Map<String, Object> after) {
        process.setVariables(variables);
        process.setVariablesJson(json(variables));
        ProcessInstance saved = processes.save(process);
        String userId = admin.identity(externalProjectId, flowId).userId();
        history.recordFieldChanges(ChangeHistoryContext.builder().processInstanceId(saved.getId())
                .userId(userId).build(), before, after);
        return recordView(saved);
    }

    private AutomationAdminClient.Scope open(String externalProjectId, String flowId, String capability) {
        AutomationAccessRules.requireRunContext(externalProjectId, flowId);
        AutomationAdminClient.Scope scope = admin.scope(externalProjectId, flowId);
        AutomationAccessRules.requireCapability(scope.capabilitySet(), capability);
        return scope;
    }

    private ProcessInstance load(String processInstanceId, AutomationAdminClient.Scope scope) {
        ProcessInstance process = processes.findById(processInstanceId).orElseThrow(() -> new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND, "Process instance was not found"));
        AutomationAccessRules.requireCatalog(scope.catalogId(), process.getFunctionUnitCatalogId());
        return process;
    }

    private ProcessInstance loadForUpdate(String processInstanceId, AutomationAdminClient.Scope scope, Long lockVersion) {
        ProcessInstance process = load(processInstanceId, scope);
        if (lockVersion == null || !lockVersion.equals(process.getLockVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "lockVersion does not match");
        }
        return process;
    }

    private Map<String, Object> write(String idempotencyKey, Map<String, Object> body,
                                      java.util.function.Supplier<Map<String, Object>> action) {
        return idempotency.once(idempotencyKey, body, action);
    }

    private List<Map<String, Object>> queryRows(String catalogId, List<Map<String, Object>> filters, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, lock_version, variables::text FROM up_process_instance
                WHERE function_unit_catalog_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(catalogId);
        for (Map<String, Object> filter : filters) {
            sql.append(" AND variables ->> ? = ?");
            args.add(filter.get("field"));
            args.add(String.valueOf(filter.get("value")));
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1L) * size);
        return jdbc.query(sql.toString(), (rs, row) -> recordView(rs.getString(1), rs.getLong(2), rs.getString(3)),
                args.toArray());
    }

    private Map<String, Object> schema(AutomationAdminClient.Scope scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("functionUnitId", scope.functionUnitId());
        body.put("catalogId", scope.catalogId());
        body.put("mainFields", List.copyOf(mainFields(scope.functionUnitId())));
        return body;
    }

    private Set<String> mainFields(long functionUnitId) {
        return Set.copyOf(jdbc.query("""
                SELECT f.field_name FROM dw_field_definitions f
                JOIN dw_table_definitions t ON t.id = f.table_id
                WHERE t.function_unit_id = ? AND t.table_type = 'MAIN'
                """, (rs, row) -> rs.getString(1), functionUnitId));
    }

    private void requireBinding(long functionUnitId, long bindingId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dw_form_table_bindings b
                JOIN dw_form_definitions f ON f.id = b.form_id
                WHERE b.id = ? AND f.function_unit_id = ? AND b.binding_type = 'SUB'
                """, Integer.class, bindingId, functionUnitId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Sub-table binding is not in this Function Unit");
        }
    }

    private String primaryKey(long bindingId) {
        List<String> keys = jdbc.query("""
                SELECT f.field_name FROM dw_field_definitions f
                JOIN dw_form_table_bindings b ON b.table_id = f.table_id
                WHERE b.id = ? AND f.is_primary_key = TRUE
                """, (rs, row) -> rs.getString(1), bindingId);
        if (keys.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Sub-table has no primary key");
        }
        return keys.get(0);
    }

    private String codeOf(long functionUnitId) {
        List<String> codes = jdbc.query("SELECT code FROM dw_function_units WHERE id = ?",
                (rs, row) -> rs.getString(1), functionUnitId);
        if (codes.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Function Unit was not found");
        }
        return codes.get(0);
    }

    private Map<String, Object> recordView(ProcessInstance process) {
        return recordView(process.getId(), process.getLockVersion(), json(copy(process.getVariables())));
    }

    private Map<String, Object> recordView(String id, Long lockVersion, String variablesJson) {
        Map<String, Object> variables = readMap(variablesJson);
        Map<String, Object> fields = new LinkedHashMap<>(variables);
        fields.remove(SUB_TABLES);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processInstanceId", id);
        body.put("lockVersion", lockVersion);
        body.put("fields", fields);
        body.put("subTables", variables.getOrDefault(SUB_TABLES, Map.of()));
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> subTables(Map<String, Object> variables) {
        Object current = variables.computeIfAbsent(SUB_TABLES, key -> new LinkedHashMap<String, Object>());
        if (!(current instanceof Map<?, ?> map)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Sub-table storage is not an object");
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Map<String, Object> variables, long bindingId) {
        Object current = subTables(variables).computeIfAbsent(String.valueOf(bindingId), key -> new ArrayList<>());
        if (!(current instanceof List<?> list)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Sub-table rows are not a list");
        }
        return (List<Map<String, Object>>) list;
    }

    private Map<String, Object> copy(Map<String, Object> variables) {
        return variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Stored record cannot be read");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Record cannot be serialized");
        }
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static int page(Object value) {
        int resolved = value instanceof Number number ? number.intValue() : 1;
        if (resolved < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "page must be at least 1");
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "fields are required");
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> filters(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> filters = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Each filter must be an object");
            }
            filters.add((Map<String, Object>) map);
        }
        return filters;
    }
}
