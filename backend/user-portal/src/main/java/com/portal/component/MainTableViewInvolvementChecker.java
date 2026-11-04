package com.portal.component;

import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.subtable.SubTableStoreKeys;
import com.portal.entity.ProcessInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** View involvement: initiator, actual historic assignee, or a person declared by the pinned MI design. */
@Component
@RequiredArgsConstructor
public class MainTableViewInvolvementChecker {
    private final JdbcTemplate jdbcTemplate;
    private record PersonField(String storeKey, String fieldName) { }
    private final Map<String,List<PersonField>> declarations = Collections.synchronizedMap(
            new LinkedHashMap<>(16,0.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,List<PersonField>> entry) {
                    return size() > 128;
                }
            });

    public boolean isUserInvolved(String userId, ProcessInstance instance) {
        if (userId == null || userId.isBlank() || instance == null) return false;
        if (userId.equals(instance.getStartUserId())) return true;
        if (hasHistoricAssigneeTask(userId,instance.getId())) return true;
        return isMiParticipant(userId,instance.getFunctionUnitCatalogId(),instance.getVariables());
    }

    /** Exact configured canonical slice and field only; ordinary JSON names cannot create a grant. */
    public boolean isMiParticipant(String userId, String catalogId, Map<String,Object> variables) {
        if (userId == null || userId.isBlank() || variables == null
                || !(variables.get("__subTables__") instanceof Map<?,?> tables) || tables.isEmpty()) return false;
        if (catalogId == null || catalogId.isBlank()) throw CatalogSnapshotReader.invalid(catalogId);
        for (PersonField field : declarations.computeIfAbsent(catalogId,this::loadDeclarations)) {
            Object raw = tables.get(field.storeKey());
            if (raw == null) continue; // This request has no rows in this configured MI table.
            if (!(raw instanceof List<?> rows)) throw CatalogSnapshotReader.invalid(catalogId);
            for (Object row : rows) {
                if (!(row instanceof Map<?,?> values)) throw CatalogSnapshotReader.invalid(catalogId);
                if (userId.equals(AssignmentPersonIdentity.optional(values.get(field.fieldName())))) return true;
            }
        }
        return false;
    }

    private List<PersonField> loadDeclarations(String catalogId) {
        var processes = jdbcTemplate.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type='PROCESS'",String.class,catalogId);
        if (processes.size()!=1) throw CatalogSnapshotReader.invalid(catalogId);
        var fields = BpmnMiXmlSupport.miUserFields(ChangeHistoryBpmnFormResolver.decodeBpmnXml(processes.get(0)));
        if (fields.isEmpty()) return List.of();
        var declared = jdbcTemplate.queryForList("SELECT c.content_data::jsonb->>'tableName' AS table_name, "
                + "f->>'fieldName' AS field_name FROM sys_function_unit_contents c "
                + "CROSS JOIN LATERAL jsonb_array_elements(c.content_data::jsonb->'fields') f "
                + "WHERE c.function_unit_id=? AND c.content_type='DATA_TABLE' "
                + "AND c.content_data::jsonb->>'tableType'='SUB'",catalogId);
        return fields.stream().map(field -> {
            long count = declared.stream().filter(row -> field.tableName().equals(row.get("table_name"))
                    && field.fieldName().equals(row.get("field_name"))).count();
            if (count!=1) throw CatalogSnapshotReader.invalid(catalogId);
            return new PersonField(SubTableStoreKeys.dwKey(field.tableName()),field.fieldName());
        }).distinct().toList();
    }

    private boolean hasHistoricAssigneeTask(String userId, String instanceId) {
        if (instanceId == null || instanceId.isBlank()) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ACT_HI_TASKINST "
                + "WHERE PROC_INST_ID_ = ? AND ASSIGNEE_ = ?",Integer.class,instanceId,userId);
        return count != null && count > 0;
    }
}
