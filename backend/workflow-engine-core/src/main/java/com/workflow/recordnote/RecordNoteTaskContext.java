package com.workflow.recordnote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.recordnote.RecordNoteTaskTarget;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads node configuration from the deployed BPMN and row schema from its frozen catalog. */
@Component
@RequiredArgsConstructor
public class RecordNoteTaskContext {
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private RuntimeService runtime;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private RepositoryService definitions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private record FrozenDefinition(boolean enabled,List<JsonNode> tables,Map<String,Map<String,String>> tasks) {}
    private record DefinitionKey(String definition,String catalog) {}
    private final Map<DefinitionKey,FrozenDefinition> definitionsCache=java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(128,0.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<DefinitionKey,FrozenDefinition> entry) { return size()>128; }
            });
    private final Map<String,String> processCatalogs=java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(512,0.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry) { return size()>512; }
            });

    public boolean enabled(String definition,String process) { return frozen(definition,process).enabled(); }

    private FrozenDefinition frozen(String definition,String process) {
        String catalog=processCatalogs.computeIfAbsent(process,pid -> catalog(definition,pid));
        if(catalog==null) return new FrozenDefinition(false,List.of(),Map.of());
        return definitionsCache.computeIfAbsent(new DefinitionKey(definition,catalog),this::loadDefinition);
    }

    private String catalog(String definition,String process) {
        var pinned=jdbc.queryForList("SELECT function_unit_catalog_id FROM up_process_instance WHERE id=?",
                String.class,process);
        String id=pinned.size()==1?pinned.get(0):null;
        if(id==null) {
            Object value=runtime.getVariable(process,"functionUnitId");
            if(value instanceof String text) id=text;
        }
        String key=definitions.getBpmnModel(definition).getMainProcess().getId();
        if(id!=null) {
            var codes=jdbc.queryForList("SELECT code FROM sys_function_units WHERE id=?",String.class,id);
            if(codes.size()==1 && key.equals(codes.get(0))) return id;
        }
        // A direct/called engine start may not yet have a Portal row. An exact immutable deployment
        // association is valid metadata; an inherited caller catalog is never treated as the child's.
        var exact=jdbc.queryForList("SELECT DISTINCT c.function_unit_id FROM sys_function_unit_contents c "
                + "JOIN sys_function_units u ON u.id=c.function_unit_id "
                + "WHERE c.flowable_process_definition_id=? AND u.code=?",String.class,definition,key);
        return exact.size()==1?exact.get(0):null;
    }

    private FrozenDefinition loadDefinition(DefinitionKey key) {
        var contents=jdbc.queryForList("SELECT content_type,content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type IN ('FORM','DATA_TABLE','PROCESS')",key.catalog());
        boolean enabled=false;
        List<JsonNode> tables=new java.util.ArrayList<>();
        String bpmn=null;
        for(var content:contents) {
            if("PROCESS".equals(content.get("content_type"))) {
                String raw=(String)content.get("content_data");
                bpmn=raw.stripLeading().startsWith("\"")?parse(raw).asText():raw;
                continue;
            }
            JsonNode node=parse((String)content.get("content_data"));
            if("FORM".equals(content.get("content_type"))) enabled|=hasEnabledNote(node);
            else tables.add(node);
        }
        return new FrozenDefinition(enabled,List.copyOf(tables),enabled
                ?com.platform.common.recordnote.RecordNoteBpmnValidator.targetConfigurations(bpmn):Map.of());
    }

    private boolean hasEnabledNote(JsonNode node) {
        if(node.has("configJson") && node.path("configJson").isTextual()
                && hasEnabledNote(parse(node.path("configJson").asText()))) return true;
        if (node.isObject() && "recordNote".equals(node.path("type").asText())
                && node.path("props").path("attachmentNotification").path("enabled").asBoolean()) return true;
        for (JsonNode child : node) if (hasEnabledNote(child)) return true;
        return false;
    }

    public RecordNoteTaskTarget capture(Task task) {
        String table = property(task,"subTableName");
        if (table == null) return null; // Not an MI row task; it is not a configured notification target.
        Map<String,Object> row = currentRow(task);
        if (row == null) return null; // A missing execution row is exposed as unresolved by the resolver.
        Map<String,Object> key = rowKey(task,table,row);
        // No trustworthy identity means a pending notification, never a guessed row or a failed business task.
        if(key.isEmpty()) return null;
        String field = property(task,"assigneeField");
        String roleField = property(task,"roleField");
        boolean team = com.platform.common.recordnote.SubTaskAssignmentMode.allowsRole(property(task,"assigneeMode"))
                && roleField != null && !com.workflow.util.AssigneeRoleIdsSupport.resolveRoleCodesFromItem(row,roleField).isEmpty();
        String mode = team ? "TEAM" : "PERSON";
        String recipient = team ? task.getAssignee() : userId(field == null ? null : row.get(field));
        org.flowable.bpmn.model.FlowElement element = definitions.getBpmnModel(task.getProcessDefinitionId())
                .getFlowElement(task.getTaskDefinitionKey());
        String scope = RecordNoteTaskScope.key(element);
        var activation=activation(task);
        if(activation==null) return null;
        return new RecordNoteTaskTarget(task.getId(),task.getTaskDefinitionKey(),scope,table,key,
                activation.id(),mode,field,recipient,recipient == null ? "WAITING_CLAIM" : "ACTIVE",activation.context());
    }

    public String personAtCompletion(Task task, RecordNoteTaskTarget target) {
        Map<String,Object> row = currentRow(task);
        if (target.personField() == null) return null;
        Object all = runtime.getVariable(task.getProcessInstanceId(), "__subTables__");
        if (all instanceof Map<?, ?> stores) {
            List<Map<String,Object>> matching = com.platform.common.recordnote.RecordNoteRows.rows(
                    Map.of("__subTables__",stores),target.tableName()).stream()
                    .filter(candidate -> com.platform.common.recordnote.RecordNoteRows.matches(candidate,target.rowKey())).toList();
            if (matching.size() == 1) row = matching.get(0);
            if (matching.size() > 1) return null; // Finish the business task, but keep notification identity unresolved.
        }
        return row == null ? null : userId(row.get(target.personField()));
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> currentRow(Task task) {
        var element=definitions.getBpmnModel(task.getProcessDefinitionId()).getFlowElement(task.getTaskDefinitionKey());
        String variable=RecordNoteTaskScope.elementVariable(element);
        if(variable==null) return null;
        Object value = runtime.getVariable(task.getExecutionId(),variable);
        return value instanceof Map<?,?> map ? (Map<String,Object>)map : null;
    }

    private Map<String,Object> rowKey(Task task,String table,Map<String,Object> row) {
        Map<String,Object> key = new LinkedHashMap<>();
        for (JsonNode schema : frozen(task.getProcessDefinitionId(),task.getProcessInstanceId()).tables()) {
            if (!table.equals(schema.path("tableName").asText())) continue;
            for (JsonNode f : schema.path("fields")) {
                if (f.path("isPrimaryKey").asBoolean()) {
                    String name = f.path("fieldName").asText();
                    if (!nonempty(row.get(name))) return Map.of();
                    key.put(name,row.get(name));
                }
            }
        }
        if (key.isEmpty() && nonempty(row.get("platformRowUuid"))) key.put("platformRowUuid",row.get("platformRowUuid"));
        return key;
    }

    private record Activation(String id,Map<String,String> context) {}

    private Activation activation(Task task) {
        String id=task.getExecutionId(), nearest=null;
        Map<String,String> context=new LinkedHashMap<>();
        for(int depth=0;id!=null && depth<50;depth++) {
            Execution execution=runtime.createExecutionQuery().executionId(id).singleResult();
            if(execution==null) return null;
            if(runtime.hasVariableLocal(id,"nrOfInstances")) {
                if(execution.getActivityId()==null || execution.getParentId()==null) return null;
                if(nearest==null) nearest=id;
                context.put(execution.getActivityId()+":"+execution.getParentId(),id);
            }
            id=execution.getParentId();
        }
        return nearest==null?null:new Activation(nearest,Map.copyOf(context));
    }

    public Map<String,String> activationForScope(String executionId,String activityId) {
        Execution execution=runtime.createExecutionQuery().executionId(executionId).singleResult();
        if(execution==null || !activityId.equals(execution.getActivityId()) || execution.getParentId()==null) return Map.of();
        return Map.of(activityId+":"+execution.getParentId(),executionId);
    }

    public String property(String definition,String process,String node,String name) {
        String value=frozen(definition,process).tasks().getOrDefault(node,Map.of()).get(name);
        return value==null || value.isBlank()?null:value.trim();
    }

    private String property(Task task,String name) {
        return property(task.getProcessDefinitionId(),task.getProcessInstanceId(),task.getTaskDefinitionKey(),name);
    }

    public static String userId(Object value) {
        return com.platform.common.recordnote.RecordNoteRecipients.userId(value);
    }
    private static boolean nonempty(Object value) { return value != null && !String.valueOf(value).isBlank(); }
    private JsonNode parse(String value) {
        try { JsonNode node=mapper.readTree(value); return node.isTextual()?mapper.readTree(node.textValue()):node; }
        catch (Exception e) { throw new IllegalStateException("Invalid frozen Record Note schema",e); }
    }
}
