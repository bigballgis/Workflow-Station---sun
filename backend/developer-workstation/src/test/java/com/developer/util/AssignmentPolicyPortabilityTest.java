package com.developer.util;

import com.platform.common.assignment.AssignmentExclusionBpmnValidator;
import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentPolicyPortabilityTest {
    @Test void clonedProcessRetainsNodeReferencesAndBackupConfigurationThroughEncodedRewriters() {
        String source = XmlEncodingUtil.encode(model("review"));
        String remapped = BpmnIdRewriter.rewrite(source, Map.of(), Map.of(), Map.of());
        String cloned = BpmnProcessIdRewriter.rewriteToFunctionUnitCode(remapped, "cloned_fu");
        var before = AssignmentExclusionBpmnValidator.policies(XmlEncodingUtil.smartDecode(source));
        var after = AssignmentExclusionBpmnValidator.policies(XmlEncodingUtil.smartDecode(cloned));
        assertEquals(before, after);
        assertEquals("cloned_fu", BpmnProcessIdRewriter.extractProcessId(cloned));
        assertEquals(java.util.List.of("backup-user"), after.get("work").backup().userRefs());
        AssignmentDesignValidation.requireValid(cloned);
    }

    @Test void deletedReferencedNodeBlocksSaveAndImportForPlainOrEncodedDesigns() {
        for (String bpmn : java.util.List.of(model("missing"), XmlEncodingUtil.encode(model("missing")))) {
            var failure = assertThrows(AssignmentFailure.class, () -> AssignmentDesignValidation.requireValid(bpmn));
            assertEquals("ASSIGNMENT_POLICY_INVALID", failure.getDetails().get("reason"));
        }
    }

    private String model(String target) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow/custom" targetNamespace="test">
                  <process id="original_fu"><userTask id="work"><extensionElements><custom:properties>
                    <custom:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,
                    &quot;excludePreviousHandlers&quot;:true,&quot;excludeSelectedNodeHandlers&quot;:true,&quot;selectedNodeKeys&quot;:[&quot;%s&quot;],
                    &quot;backup&quot;:{&quot;enabled&quot;:true,&quot;mode&quot;:&quot;USERS&quot;,&quot;userRefs&quot;:[&quot;backup-user&quot;]}}"/>
                  </custom:properties></extensionElements></userTask><userTask id="review"/></process>
                </definitions>
                """.formatted(target);
    }
}
