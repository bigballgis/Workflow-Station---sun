package com.platform.common.recordnote;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordNoteDesignTest {
    private Map<String,Object> config(boolean enabled) {
        var props=new LinkedHashMap<String,Object>();
        props.put("scope","TABLE");
        props.put("attachmentNotification",Map.of("enabled",enabled,"targetNodeKeys",List.of("node-stable")));
        return new LinkedHashMap<>(Map.of("rule",List.of(new LinkedHashMap<>(Map.of("type","recordNote","props",props)))));
    }
    @Test void saveAndRoundTripPreserveStableIdentifiersAndNodeReferences() {
        var config=config(true);
        RecordNoteDesign.prepare(config,false);
        String first=(String)((Map<?,?>)RecordNoteDesign.notes(config).get(0).get("props")).get("controlId");
        String form=(String)config.get("recordNoteFormKey");
        RecordNoteDesign.prepare(config,false);
        assertEquals(form,config.get("recordNoteFormKey"));
        assertEquals(first,((Map<?,?>)RecordNoteDesign.notes(config).get(0).get("props")).get("controlId"));
    }
    @Test void copyAllocatesANewFormAndControlIdentity() {
        var config=config(true); RecordNoteDesign.prepare(config,false);
        String form=(String)config.get("recordNoteFormKey");
        var props=(Map<?,?>)RecordNoteDesign.notes(config).get(0).get("props");
        String control=(String)props.get("controlId");
        RecordNoteDesign.prepare(config,true);
        assertNotEquals(form,config.get("recordNoteFormKey"));
        assertNotEquals(control,props.get("controlId"));
    }
    @Test void disabledLegacyFormsDoNotNeedAnExternalNotificationRule() {
        var props=new LinkedHashMap<String,Object>(); props.put("scope","TABLE");
        assertDoesNotThrow(() -> RecordNoteDesign.validate(props,Set.of()));
    }
    @Test void unresolvedTargetIsRejected() {
        var config=config(true); RecordNoteDesign.prepare(config,false);
        @SuppressWarnings("unchecked") var props=(Map<String,Object>)RecordNoteDesign.notes(config).get(0).get("props");
        assertThrows(IllegalArgumentException.class,() -> RecordNoteDesign.validate(props,Set.of("other-node")));
    }
    @Test void bpmnValidationRejectsExternalEntities() {
        var config=config(true); RecordNoteDesign.prepare(config,false);
        assertThrows(IllegalArgumentException.class,() -> RecordNoteBpmnValidator.validate(List.of(config),
                "<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///dev/null'>]><definitions/>"));
    }
}
