package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.recordnote.RecordNoteDesign;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordNotePackageValidatorTest {
    final ObjectMapper mapper=new ObjectMapper();
    FunctionUnitManagerComponent.ContentInfo content(ContentType type,String data) {
        var value=new FunctionUnitManagerComponent.ContentInfo();
        value.setContentType(type); value.setContentData(data); return value;
    }
    Map<String,Object> config(String node) {
        var props=new LinkedHashMap<String,Object>(Map.of("scope","TABLE","attachmentNotification",
                Map.of("enabled",true,"targetNodeKeys",List.of(node))));
        var config=new LinkedHashMap<String,Object>(Map.of("rule",List.of(new LinkedHashMap<>(Map.of("type","recordNote","props",props)))));
        RecordNoteDesign.prepare(config,false); return config;
    }
    String bpmn() { return """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
              <process id="renamed-process"><subProcess id="arbitrary-scope"><extensionElements><f:properties>
              <f:property name="subTableName" value="unknown_business_table"/><f:property name="roleField" value="team_xyz"/>
              </f:properties></extensionElements><multiInstanceLoopCharacteristics/>
              <subProcess id="nested"><userTask id="stable-node" name="Renamed label"/></subProcess>
              </subProcess></process></definitions>
            """; }
    @Test void portableInlineConfigurationIsValidatedWithoutRewritingItsIdentifiers() throws Exception {
        Map<String,Object> config=config("stable-node");
        for(Object serialized:List.of(config,mapper.writeValueAsString(config))) {
            String formJson=mapper.writeValueAsString(Map.of("configJson",serialized,"boundTableName","arbitrary_main"));
            var form=content(ContentType.FORM,formJson);
            RecordNotePackageValidator.validate(List.of(content(ContentType.PROCESS,bpmn())),List.of(form),mapper);
            assertEquals(formJson,form.getContentData());
        }
    }
    @Test void brokenEnabledNodeReferencesAreRejectedAtBothPackageEntryPointsSharedParser() throws Exception {
        var form=content(ContentType.FORM,mapper.writeValueAsString(Map.of("configJson",config("deleted-node"))));
        assertThrows(java.io.IOException.class,() -> RecordNotePackageValidator.validate(
                List.of(content(ContentType.PROCESS,bpmn())),List.of(form),mapper));
    }
    @Test void disabledLegacyFormNeedsNoNotificationProcess() throws Exception {
        var form=content(ContentType.FORM,"{\"configJson\":{\"rule\":[{\"type\":\"recordNote\"}]}}");
        assertDoesNotThrow(() -> RecordNotePackageValidator.validate(List.of(),List.of(form),mapper));
    }
    @Test void bareLegacyConfigurationCannotBypassEnabledReferenceValidation() throws Exception {
        var form=content(ContentType.FORM,mapper.writeValueAsString(config("deleted-node")));
        assertThrows(java.io.IOException.class,() -> RecordNotePackageValidator.validate(
                List.of(content(ContentType.PROCESS,bpmn())),List.of(form),mapper));
    }
    @Test void disabledBareLegacyConfigurationRemainsImportable() {
        var form=content(ContentType.FORM,"{\"rule\":[{\"type\":\"recordNote\"}]}");
        assertDoesNotThrow(() -> RecordNotePackageValidator.validate(List.of(),List.of(form),mapper));
    }
    @Test void transactionNoteCannotTargetAnotherTablesTasks() throws Exception {
        var config=config("stable-node");
        @SuppressWarnings("unchecked") var props=(Map<String,Object>)RecordNoteDesign.notes(config).get(0).get("props");
        props.put("scope","RECORD");
        var form=content(ContentType.FORM,mapper.writeValueAsString(Map.of("configJson",config,"boundTableName","another_table")));
        assertThrows(java.io.IOException.class,() -> RecordNotePackageValidator.validate(
                List.of(content(ContentType.PROCESS,bpmn())),List.of(form),mapper));
    }

}
