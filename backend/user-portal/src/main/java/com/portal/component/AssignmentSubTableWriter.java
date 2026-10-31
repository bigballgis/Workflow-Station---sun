package com.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.*;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.jdbc.*;
import com.platform.common.subtable.SubTableStoreKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** A narrow row write into the authoritative JSON and, for a legacy physical table, that same exact row. */
@Component
public class AssignmentSubTableWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CatalogSnapshotReader catalogs;
    public AssignmentSubTableWriter(JdbcTemplate jdbc,ObjectMapper json,CatalogSnapshotReader catalogs) {
        this.jdbc=jdbc; this.json=json; this.catalogs=catalogs;
    }
    public Map<String,Object> assign(AssignmentSubTablePlan plan,Long rowId,Map<String,Object> suppliedKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !plan.tracked()) throw unavailable();
        if (new AssignmentAuthorityFence(jdbc).revision()!=plan.authorityRevision()) throw changed();
        var stored=jdbc.query("SELECT process_definition_id,function_unit_catalog_id,variables::text,lock_version FROM up_process_instance WHERE id=?",
                (rs,n)->new Stored(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4)),plan.instanceId());
        if (stored.size()!=1 || !plan.definitionId().equals(stored.get(0).definition())) throw unavailable();
        var state=stored.get(0);
        var table=catalogs.dataTable(state.catalog(),plan.table()).orElseThrow(this::unavailable);
        var fields=CatalogSnapshotReader.objects(table.get("fields"));
        if (fields.stream().noneMatch(field->plan.assigneeField().equals(field.get("fieldName")))) throw unavailable();
        var keys=catalogs.primaryKeyFields(state.catalog(),plan.table());
        if (keys.contains(plan.assigneeField()) || plan.assigneeField().equals(plan.foreignKey())) throw unavailable();
        Map<String,Object> key=key(rowId,suppliedKey,keys);
        ObjectNode variables=decode(state.variables());
        JsonNode rows=variables.path("__subTables__").path(SubTableStoreKeys.dwKey(plan.table()));
        ObjectNode row=find(rows,json.valueToTree(key));
        Integer previousVersion=version(row);
        int nextVersion=previousVersion==null ? 1 : Math.addExact(previousVersion,1);
        physical(plan,state.catalog(),fields,keys,row);
        row.put(plan.assigneeField(),plan.target());
        row.put(SubTableBindingScopeGuard.ROW_VERSION_FIELD,nextVersion);
        updateNestedCopies(variables.path("__subTables__"),SubTableStoreKeys.dwKey(plan.table()),json.valueToTree(key),plan,nextVersion);
        int updated=jdbc.update("UPDATE up_process_instance SET variables=?::jsonb,lock_version=lock_version+1,updated_at=CURRENT_TIMESTAMP "
                        +"WHERE id=? AND lock_version=? AND process_definition_id=?",variables.toString(),plan.instanceId(),state.version(),state.definition());
        if (updated!=1) throw changed();
        var result=new LinkedHashMap<String,Object>(); result.put("success",true); result.put("rowId",rowId);
        result.put("rowKey",key); result.put("assigneeId",plan.target()); result.put("assigneeName",plan.target());
        result.put("storeKey",SubTableStoreKeys.dwKey(plan.table())); result.put("assigneeField",plan.assigneeField());
        result.put("previousRowVersion",previousVersion); result.put("rowVersion",nextVersion);
        return result;
    }
    private Map<String,Object> key(Long rowId,Map<String,Object> supplied,List<String> keys) {
        Map<String,Object> result=supplied;
        if (result==null || result.isEmpty()) result=SubTableRowKeySupport.resolveRowKeyForAssign(rowId,null,keys);
        if (result==null || !result.keySet().equals(new HashSet<>(keys)) || result.values().stream().anyMatch(value->
                value==null || !(value instanceof String || value instanceof Number || value instanceof Boolean) || value.toString().isBlank())) throw unavailable();
        return Map.copyOf(result);
    }
    private Integer version(ObjectNode row) {
        JsonNode value=row.get(SubTableBindingScopeGuard.ROW_VERSION_FIELD);
        if (value==null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<0 || value.intValue()==Integer.MAX_VALUE) throw unavailable();
        return value.intValue();
    }
    private ObjectNode find(JsonNode rows,JsonNode key) {
        if (!rows.isArray()) throw unavailable();
        ObjectNode found=null;
        for (var row:rows) {
            if (!row.isObject()) throw unavailable();
            boolean matches=true; var entries=key.fields();
            while (entries.hasNext()) { var entry=entries.next(); if (!sameKey(entry.getValue(),row.get(entry.getKey()))) { matches=false; break; } }
            if (matches) { if (found!=null) throw unavailable(); found=(ObjectNode) row; }
        }
        if (found==null) throw unavailable();
        return found;
    }
    private boolean sameKey(JsonNode expected,JsonNode actual) {
        if (actual==null) return false;
        return expected.isNumber() && actual.isNumber() ? expected.decimalValue().compareTo(actual.decimalValue())==0 : expected.equals(actual);
    }
    private void updateNestedCopies(JsonNode tables,String store,JsonNode key,AssignmentSubTablePlan plan,int next) {
        if (!tables.isObject()) return;
        tables.fields().forEachRemaining(entry->{
            if (!entry.getValue().isArray()) return;
            for (JsonNode candidate:entry.getValue()) {
                if (!(candidate instanceof ObjectNode object)) continue;
                if (store.equals(entry.getKey()) && matchesKey(object,key)) {
                    object.put(plan.assigneeField(),plan.target());
                    object.put(SubTableBindingScopeGuard.ROW_VERSION_FIELD,next);
                }
                updateNestedCopies(object.path("__subTables__"),store,key,plan,next);
            }
        });
    }
    private boolean matchesKey(JsonNode row,JsonNode key) {
        var fields=key.fields();
        while (fields.hasNext()) { var entry=fields.next(); if (!sameKey(entry.getValue(),row.get(entry.getKey()))) return false; }
        return true;
    }
    private void physical(AssignmentSubTablePlan plan,String catalog,List<Map<String,Object>> fields,List<String> keys,ObjectNode row) {
        String name=SqlIdentifiers.requireIdentifier(plan.table());
        if (jdbc.queryForObject("SELECT to_regclass(?)::text",String.class,name)==null) return; // Designer tables are JSON-row stored.
        if (!new HashSet<>(PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbc,name)).equals(new HashSet<>(keys))) throw unavailable();
        String fk=foreignKey(plan,catalog,fields);
        if (fk.equals(plan.assigneeField())) throw unavailable();
        List<Object> parameters=new ArrayList<>(); parameters.add(plan.target());
        var predicates=new ArrayList<String>();
        for (String field:new LinkedHashSet<>(java.util.stream.Stream.concat(keys.stream(),java.util.stream.Stream.of(fk)).toList())) {
            JsonNode value=row.get(field);
            if (value==null || value.isNull() || !value.isValueNode()) throw unavailable();
            predicates.add(SqlIdentifiers.requireIdentifier(field)+"=?");
            parameters.add(json.convertValue(value,Object.class));
        }
        int updated=jdbc.update("UPDATE "+SqlIdentifiers.requireIdentifier(name)+" SET "+SqlIdentifiers.requireIdentifier(plan.assigneeField())
                +"=? WHERE "+String.join(" AND ",predicates),parameters.toArray());
        if (updated!=1) throw changed();
    }
    private String foreignKey(AssignmentSubTablePlan plan,String catalog,List<Map<String,Object>> fields) {
        String configured=plan.foreignKey();
        if (configured==null || configured.isBlank()) {
            var choices=catalogs.bindings(catalog).stream().filter(binding->"SUB".equals(binding.get("bindingType")))
                    .filter(binding->plan.table().equalsIgnoreCase(String.valueOf(binding.get("tableName"))))
                    .map(binding->binding.get("foreignKeyField")).filter(value->value instanceof String text && !text.isBlank()).distinct().toList();
            if (choices.size()!=1) throw unavailable();
            configured=(String)choices.get(0);
        }
        String field=configured;
        if (fields.stream().noneMatch(definition->field.equals(definition.get("fieldName")) && Boolean.TRUE.equals(definition.get("isForeignKey")))) throw unavailable();
        return field;
    }
    private ObjectNode decode(String value) {
        if (value==null) throw unavailable();
        try { var node=json.readTree(value); if (!(node instanceof ObjectNode object)) throw unavailable(); return object; }
        catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { throw unavailable(); }
    }
    private record Stored(String definition,String catalog,String variables,long version) { }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
    private AssignmentFailure changed() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED); }
}
