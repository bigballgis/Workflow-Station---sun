package com.portal.service.impl;

import com.platform.common.dataview.RuntimeDataView;
import java.util.*;

/** Release payloads independent of the designer database. */
final class PublishedViewFixture {
    final Map<String,Object> definition=new LinkedHashMap<>();
    final List<Map<String,Object>> tables=new ArrayList<>();
    final List<Map<String,Object>> forms=new ArrayList<>();
    final List<RuntimeDataView.Reference> references=new ArrayList<>();
    final Map<String,Object> table=new LinkedHashMap<>();
    String code="fu-test", tableName="main";
    String tableType="MAIN";
    boolean admin=true;
    long id=3, tableId=1;
    PublishedViewFixture() {
        definition.put("viewName","Main"); definition.put("fields",List.of());
        definition.put("sortConfig",List.of()); definition.put("filterConfig",Map.of());
        table.put("fields",new ArrayList<Map<String,Object>>()); tables.add(table);
    }
    RuntimeDataView build() {
        table.put("tableName",tableName);table.put("tableId",tableId);table.put("tableType",tableType);
        return new RuntimeDataView(id,1L,tableId,"019252f1-a579-4b56-aa08-4d7576b4c8e0","release-1",code,"Test FU","1.0.0",
                "<svg></svg>",tableName,tableName,tableType,definition,tables,forms,references,admin);
    }
    static Map<String,Object> field(String name,int order) {
        return new LinkedHashMap<>(Map.of("fieldName",name,"displayLabel",name,"sortOrder",order,
                "visible",true,"systemField",false,"columnType","field","selectDisplay","value"));
    }
    static Map<String,Object> fk(String name,String parent,String key) {
        return new LinkedHashMap<>(Map.of("fieldName",name,"isForeignKey",true,"refTableName",parent,"refPrimaryKeyFields",List.of(key)));
    }
    void parent(String name,long id) { tables.add(Map.of("tableName",name,"tableId",id,"tableType","MAIN","fields",List.of())); }
    void bindings(String main,String sub) {
        forms.add(Map.of("configJson",Map.of(),"tableBindings",List.of(
                Map.of("bindingId",1L,"bindingType","PRIMARY","tableName",main),
                Map.of("bindingId",2L,"bindingType","SUB","tableName",sub))));
    }
}
