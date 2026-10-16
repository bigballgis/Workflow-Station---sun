package com.portal.recordnote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.service.RecordNoteService.RecordNoteException;
import com.platform.common.recordnote.RecordNoteRows;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request ownership and control configuration are resolved from the frozen catalog, never live DW. */
@Repository
@RequiredArgsConstructor
public class RecordNoteCatalogRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public record Request(String id,String catalogId,String title,Map<String,Object> variables,
                          List<JsonNode> tables,List<JsonNode> forms) {}
    public record Control(JsonNode props,String tableName,JsonNode table) {
        public boolean enabled() { return props.path("attachmentNotification").path("enabled").asBoolean(); }
        public List<String> nodes() {
            List<String> result=new ArrayList<>();
            props.path("attachmentNotification").path("targetNodeKeys").forEach(n -> result.add(n.asText()));
            return result;
        }
    }

    public Request request(String id,boolean lock) {
        String sql="SELECT id,function_unit_catalog_id,title,variables FROM up_process_instance WHERE id=?"
                +(lock?" FOR SHARE NOWAIT":"");
        List<Request> result=jdbc.query(sql,(rs,n) -> new Request(rs.getString("id"),
                rs.getString("function_unit_catalog_id"),rs.getString("title"),
                map(rs.getString("variables")),new ArrayList<>(),new ArrayList<>()),id);
        if (result.size()!=1) throw failure("NOTE_REQUEST_UNRESOLVED");
        Request request=result.get(0);
        // FALLBACK(migration): legacy Request-anchored streams have proven ownership without a catalog.
        // Notifications wait explicitly; remove after historical Requests have trusted catalog pins.
        if(request.catalogId()==null) return request;
        jdbc.query("SELECT content_type,content_data FROM sys_function_unit_contents WHERE function_unit_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    String type=rs.getString("content_type");
                    if ("FORM".equals(type)) request.forms().add(json(rs.getString("content_data")));
                    if ("DATA_TABLE".equals(type)) request.tables().add(json(rs.getString("content_data")));
                },request.catalogId());
        return request;
    }

    public java.util.Set<String> targetTables(Request request,List<String> nodes) {
        List<String> processes=jdbc.queryForList("SELECT content_data FROM sys_function_unit_contents "
                + "WHERE function_unit_id=? AND content_type='PROCESS'",String.class,request.catalogId());
        if(processes.size()!=1) throw failure("NOTE_PROCESS_UNRESOLVED");
        String raw=processes.get(0);
        String bpmn=raw.stripLeading().startsWith("\"")?json(raw).asText():raw;
        var configured=com.platform.common.recordnote.RecordNoteBpmnValidator.targetTables(bpmn);
        java.util.Set<String> result=new java.util.HashSet<>();
        for(String node:nodes) {
            if(!configured.containsKey(node)) throw failure("NOTE_TARGET_UNRESOLVED");
            result.add(configured.get(node));
        }
        return result;
    }

    public boolean notificationsEnabled(Request request) {
        for (JsonNode form : request.forms()) if (enabledNote(form)) return true;
        return false;
    }
    private boolean enabledNote(JsonNode node) {
        if(node.has("configJson") && node.path("configJson").isTextual()
                && enabledNote(json(node.path("configJson").asText()))) return true;
        if ("recordNote".equals(node.path("type").asText())
                && node.path("props").path("attachmentNotification").path("enabled").asBoolean()) return true;
        for (JsonNode child : node) if (enabledNote(child)) return true;
        return false;
    }

    public Control control(Request request,NoteTarget target) {
        List<Control> matches=new ArrayList<>();
        for (JsonNode form : request.forms()) {
            JsonNode config=form.has("configJson")?unwrap(form.path("configJson")):form;
            locate(config,form,null,request,target,matches);
        }
        if (matches.size()!=1) throw failure("NOTE_CONTROL_UNRESOLVED");
        Control control=matches.get(0);
        if (!target.getTargetType().equals(control.props().path("scope").asText("TABLE"))) {
            throw failure("NOTE_SCOPE_MISMATCH");
        }
        requireRow(request,target,control.tableName(),control.table());
        return control;
    }

    public void requireTarget(Request request,NoteTarget target) {
        // Legacy Request-anchored streams are unambiguous; new configured submissions still validate their scope.
        if(request.id().equals(target.getTargetId())) return;
        JsonNode schema=table(request,target.getTableId());
        requireRow(request,target,schema.path("tableName").asText(),schema);
    }

    private void locate(JsonNode value,JsonNode form,String binding,Request request,NoteTarget target,List<Control> out) {
        if (value.isObject()) {
            JsonNode props=value.path("props");
            if ("recordNote".equals(value.path("type").asText())
                    && target.getControlId().equals(props.path("controlId").asText())
                    && target.getFormKey().equals(props.path("formKey").asText())) {
                String tableName=hostTable(form,binding);
                JsonNode schema=sourceTable(request,form,binding,target);
                if (!tableName.equals(schema.path("tableName").asText())) throw failure("NOTE_TABLE_MISMATCH");
                out.add(new Control(props,tableName,schema));
            }
            value.fields().forEachRemaining(entry -> {
                if ("subForms".equals(entry.getKey()) && entry.getValue().isObject()) {
                    entry.getValue().fields().forEachRemaining(sub -> locate(sub.getValue(),form,sub.getKey(),request,target,out));
                } else {
                    String nestedBinding=value.has("_bindingId")?value.path("_bindingId").asText():binding;
                    locate(entry.getValue(),form,nestedBinding,request,target,out);
                }
            });
        } else if (value.isArray()) for (JsonNode child : value) locate(child,form,binding,request,target,out);
    }

    private String hostTable(JsonNode form,String binding) {
        for (JsonNode b : form.path("tableBindings")) {
            if (binding==null?"PRIMARY".equals(b.path("bindingType").asText())
                    :binding.equals(b.path("bindingId").asText())) return b.path("tableName").asText();
        }
        if (binding==null && form.path("boundTableName").isTextual()) return form.path("boundTableName").asText();
        throw failure("NOTE_BINDING_UNRESOLVED");
    }

    private JsonNode sourceTable(Request request,JsonNode form,String binding,NoteTarget target) {
        if("DW".equals(target.getTableKind())) return table(request,target.getTableId());
        for(JsonNode configured:form.path("tableBindings")) {
            boolean host=binding==null?"PRIMARY".equals(configured.path("bindingType").asText())
                    :binding.equals(configured.path("bindingId").asText());
            if(host && target.getTableId().equals(configured.path("relationTableId").asText())) {
                return mapper.valueToTree(Map.of("tableName",configured.path("tableName").asText(),
                        "tableId",target.getTableId(),"tableKind","RT"));
            }
        }
        throw failure("NOTE_BINDING_UNRESOLVED");
    }

    public JsonNode table(Request request,String tableId) {
        List<JsonNode> tables=request.tables().stream().filter(t -> tableId.equals(t.path("tableId").asText())).toList();
        if (tables.size()!=1) throw failure("NOTE_TABLE_UNRESOLVED");
        return tables.get(0);
    }

    private void requireRow(Request request,NoteTarget target,String tableName,JsonNode schema) {
        if ("TABLE".equals(target.getTargetType())) {
            if (!request.id().equals(target.getTargetId())) throw failure("NOTE_REQUEST_MISMATCH");
            return;
        }
        if (!"DW".equals(target.getTableKind())) throw failure("NOTE_TABLE_KIND_MISMATCH");
        List<Map<String,Object>> matches=RecordNoteRows.rows(request.variables(),tableName).stream()
                .filter(row -> selector(row,schema).equals(target.getTargetId())).toList();
        if (matches.size()!=1) throw failure("NOTE_ROW_UNRESOLVED");
    }

    public String selector(Map<String,Object> row,JsonNode schema) {
        List<String> keys=new ArrayList<>();
        schema.path("fields").forEach(f -> { if(f.path("isPrimaryKey").asBoolean()) keys.add(f.path("fieldName").asText()); });
        if (keys.size()==1 && row.get(keys.get(0))!=null) return String.valueOf(row.get(keys.get(0)));
        return row.get("platformRowUuid")==null?"":String.valueOf(row.get("platformRowUuid"));
    }

    /** An empty key is an explicit unresolved identity; callers retain a pending intent. */
    public Map<String,Object> rowKey(Map<String,Object> row,JsonNode schema) {
        Map<String,Object> key=new LinkedHashMap<>();
        for(JsonNode field:schema.path("fields")) if(field.path("isPrimaryKey").asBoolean()) {
            String name=field.path("fieldName").asText();
            if(row.get(name)==null) return Map.of();
            key.put(name,row.get(name));
        }
        if(key.isEmpty() && row.get("platformRowUuid")!=null) key.put("platformRowUuid",row.get("platformRowUuid"));
        return key;
    }

    public Map<String,Object> map(String value) {
        try { return value==null?Map.of():mapper.readValue(value,new TypeReference<>() {}); }
        catch(Exception e) { throw new IllegalStateException("Invalid persisted note context",e); }
    }
    public JsonNode json(String value) {
        try { return unwrap(mapper.readTree(value)); }
        catch(Exception e) { throw new IllegalStateException("Invalid frozen note configuration",e); }
    }
    private JsonNode unwrap(JsonNode node) {
        try { return node.isTextual()?mapper.readTree(node.textValue()):node; }
        catch(Exception e) { throw new IllegalStateException("Invalid nested note configuration",e); }
    }
    private static RecordNoteException failure(String code) { return new RecordNoteException(code,"Record Note context cannot be verified"); }
}
