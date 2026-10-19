package com.platform.common.recordnote;

import java.util.Collection;
import java.util.Map;

/** Validate transaction-note scope using explicit form bindings before deployment. */
public final class RecordNoteBindingValidator {
    private RecordNoteBindingValidator() {}
    public record Form(Map<String,Object> config,String primaryTable,Map<String,String> childTables) {}

    public static void validate(Collection<Form> forms,String bpmn) {
        boolean enabled=forms.stream().flatMap(f -> RecordNoteDesign.notes(f.config()).stream())
                .anyMatch(note -> note.get("props") instanceof Map<?,?> p
                        && p.get("attachmentNotification") instanceof Map<?,?> a && Boolean.TRUE.equals(a.get("enabled")));
        if(!enabled) return;
        Map<String,String> targets=RecordNoteBpmnValidator.targetTables(bpmn);
        for(Form form:forms) visit(form.config(),form.primaryTable(),form.childTables(),targets);
    }

    private static void visit(Object value,String table,Map<String,String> bindings,Map<String,String> targets) {
        if(value instanceof Map<?,?> map) {
            if(map.get("_bindingId")!=null) table=bindings.get(String.valueOf(map.get("_bindingId")));
            if("recordNote".equals(map.get("type")) && map.get("props") instanceof Map<?,?> props) check(props,table,targets);
            for(var entry:map.entrySet()) {
                if("subForms".equals(entry.getKey()) && entry.getValue() instanceof Map<?,?> subForms) {
                    for(var sub:subForms.entrySet()) visit(sub.getValue(),bindings.get(String.valueOf(sub.getKey())),bindings,targets);
                } else visit(entry.getValue(),table,bindings,targets);
            }
        } else if(value instanceof Collection<?> values) for(Object child:values) visit(child,table,bindings,targets);
    }

    private static void check(Map<?,?> props,String table,Map<String,String> targets) {
        if(!(props.get("attachmentNotification") instanceof Map<?,?> policy) || !Boolean.TRUE.equals(policy.get("enabled"))) return;
        if(table==null || table.isBlank()) throw new IllegalArgumentException("Record Note notification source has no table binding");
        if(!"RECORD".equals(props.get("scope"))) return;
        if(!(policy.get("targetNodeKeys") instanceof Collection<?> nodes)) throw new IllegalArgumentException("Missing notification targets");
        for(Object node:nodes) if(!table.equals(targets.get(node))) {
            throw new IllegalArgumentException("Transaction Notes must target tasks bound to the same transaction table");
        }
    }
}
