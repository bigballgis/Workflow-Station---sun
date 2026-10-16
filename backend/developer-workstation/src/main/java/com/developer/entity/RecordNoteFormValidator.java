package com.developer.entity;

import com.platform.common.recordnote.RecordNoteBindingValidator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Both designer save and deployment use the persisted form/table binding contract. */
public final class RecordNoteFormValidator {
    private RecordNoteFormValidator() {}
    private static RecordNoteBindingValidator.Form describe(FormDefinition form,Map<String,Object> config) {
        boolean enabled=com.platform.common.recordnote.RecordNoteDesign.notes(config).stream().anyMatch(note ->
                note.get("props") instanceof Map<?,?> props && props.get("attachmentNotification") instanceof Map<?,?> policy
                        && Boolean.TRUE.equals(policy.get("enabled")));
        if(!enabled) return new RecordNoteBindingValidator.Form(config,null,Map.of());
        Map<String,String> children=new HashMap<>();
        String primary=form.getBoundTableName();
        if(form.getTableBindings()!=null) for(FormTableBinding binding:form.getTableBindings()) {
            children.put(String.valueOf(binding.getId()),binding.getTableName());
            if(binding.getBindingType()==com.developer.enums.BindingType.PRIMARY) primary=binding.getTableName();
        }
        return new RecordNoteBindingValidator.Form(config,primary,children);
    }
    public static void validate(FormDefinition form,Map<String,Object> config,String bpmn) {
        RecordNoteBindingValidator.validate(List.of(describe(form,config)),bpmn);
    }
}
