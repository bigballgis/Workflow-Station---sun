package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.recordnote.RecordNoteBpmnValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Preserve inline note policies in FORM catalog content and reject broken enabled references. */
final class RecordNotePackageValidator {
    private RecordNotePackageValidator() {}

    static void validate(List<FunctionUnitManagerComponent.ContentInfo> contents,
                         List<FunctionUnitManagerComponent.ContentInfo> forms,ObjectMapper mapper) throws IOException {
        List<Map<String,Object>> configs=new ArrayList<>();
        List<com.platform.common.recordnote.RecordNoteBindingValidator.Form> bindings=new ArrayList<>();
        for(var form:forms) {
            Map<String,Object> wrapper=mapper.readValue(form.getContentData(),new TypeReference<>() {});
            Object raw=wrapper.containsKey("configJson")?wrapper.get("configJson"):wrapper;
            if(raw instanceof String text) raw=mapper.readValue(text,new TypeReference<Map<String,Object>>() {});
            if(raw instanceof Map<?,?> map) {
                @SuppressWarnings("unchecked") var config=(Map<String,Object>)map;
                configs.add(config);
                Map<String,String> children=new java.util.HashMap<>();
                String primary=(String)wrapper.get("boundTableName");
                if(wrapper.get("tableBindings") instanceof List<?> tables) for(Object table:tables) {
                    if(table instanceof Map<?,?> binding && binding.get("tableName") instanceof String name) {
                        children.put(String.valueOf(binding.get("bindingId")),name);
                        if("PRIMARY".equals(binding.get("bindingType"))) primary=name;
                    }
                }
                bindings.add(new com.platform.common.recordnote.RecordNoteBindingValidator.Form(config,primary,children));
            }
        }
        String bpmn=contents.stream().filter(c -> c.getContentType()==ContentType.PROCESS)
                .map(FunctionUnitManagerComponent.ContentInfo::getContentData).findFirst().orElse(null);
        try {
            RecordNoteBpmnValidator.validate(configs,bpmn);
            com.platform.common.recordnote.RecordNoteBindingValidator.validate(bindings,bpmn);
        }
        catch(IllegalArgumentException e) { throw new IOException("Invalid Record Note notification configuration",e); }
    }
}
