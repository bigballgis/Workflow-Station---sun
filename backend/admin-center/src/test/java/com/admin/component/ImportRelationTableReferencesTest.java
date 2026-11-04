package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ImportRelationTableReferencesTest {
    @Test void localRelationIdsChangeWithoutChangingViewOrOrdinaryTableIdentity() throws Exception {
        var mapper=new ObjectMapper();
        var content=FunctionUnitManagerComponent.ContentInfo.builder().contentType(ContentType.FORM).contentData("""
                {"formId":10,"viewKey":"unchanged","tableBindings":[
                  {"bindingType":"PRIMARY","tableId":42,"tableName":"cases"},
                  {"bindingType":"RELATED","relationTableId":42}],
                 "configJson":{"rule":[{"type":"lookup","props":{"lookupConfig":"{\\"tableId\\":42}"}}]}}
                """).build();
        ImportRelationTableReferences.remap(List.of(content),Map.of(42L,900L),mapper);
        var result=mapper.readTree(content.getContentData());
        assertThat(result.path("viewKey").asText()).isEqualTo("unchanged");
        assertThat(result.path("tableBindings").get(0).path("tableId").asLong()).isEqualTo(42);
        assertThat(result.path("tableBindings").get(1).path("relationTableId").asLong()).isEqualTo(900);
        assertThat(mapper.readTree(result.path("configJson").path("rule").get(0).path("props").path("lookupConfig").asText())
                .path("tableId").asLong()).isEqualTo(900);
    }
    @Test void missingReferenceCannotAccidentallyQueryAnotherLocalRelationTable() {
        var content=FunctionUnitManagerComponent.ContentInfo.builder().contentType(ContentType.FORM)
                .contentData("{\"tableBindings\":[{\"bindingType\":\"RELATED\",\"relationTableId\":42}]}").build();
        assertThatThrownBy(() -> ImportRelationTableReferences.remap(List.of(content),Map.of(),new ObjectMapper()))
                .isInstanceOf(com.admin.exception.AdminBusinessException.class);
    }
}
