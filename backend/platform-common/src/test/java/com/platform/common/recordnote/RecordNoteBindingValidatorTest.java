package com.platform.common.recordnote;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordNoteBindingValidatorTest {
    String bpmn="""
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
            <process><userTask id="work"><extensionElements><f:properties>
            <f:property name="subTableName" value="arbitrary_lines"/><f:property name="assigneeField" value="person_q"/>
            </f:properties></extensionElements><multiInstanceLoopCharacteristics/></userTask></process></definitions>
            """;
    Map<String,Object> note(String scope) {
        return Map.of("type","recordNote","props",Map.of("scope",scope,"attachmentNotification",Map.of("enabled",true,"targetNodeKeys",List.of("work"))));
    }
    @Test void aNestedTransactionNoteUsesItsExplicitBindingInsteadOfTheMainTable() {
        var config=Map.<String,Object>of("subForms",Map.of("portable-binding",Map.of("rule",List.of(note("RECORD")))));
        var form=new RecordNoteBindingValidator.Form(config,"arbitrary_main",Map.of("portable-binding","arbitrary_lines"));
        assertDoesNotThrow(() -> RecordNoteBindingValidator.validate(List.of(form),bpmn));
    }
    @Test void transactionNotesCannotExpandToAnotherTablesTransactions() {
        var form=new RecordNoteBindingValidator.Form(Map.of("rule",List.of(note("RECORD"))),"another_table",Map.of());
        assertThrows(IllegalArgumentException.class,() -> RecordNoteBindingValidator.validate(List.of(form),bpmn));
    }
    @Test void caseNotesMayAggregateConfiguredRowsAcrossTables() {
        var form=new RecordNoteBindingValidator.Form(Map.of("rule",List.of(note("TABLE"))),"arbitrary_main",Map.of());
        assertDoesNotThrow(() -> RecordNoteBindingValidator.validate(List.of(form),bpmn));
    }
}
