package com.admin.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentExclusionBpmnValidator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentPackageImportTest {
    final FunctionUnitPackageParser parser = new FunctionUnitPackageParser(new ObjectMapper());

    @Test void bothPackageParsingPathsPreserveTheSamePortablePolicy() throws Exception {
        byte[] zip = archive(model("review"));
        var raw = parser.parseZipBytes(zip);
        var encoded = parser.parseBase64Zip(Base64.getEncoder().encodeToString(zip));
        String bpmn = raw.getPackageContent().getContents().get(0).getContentData();
        assertEquals(bpmn, encoded.getPackageContent().getContents().get(0).getContentData());
        assertTrue(AssignmentExclusionBpmnValidator.policies(bpmn).get("work").excludeSelectedNodeHandlers());
    }

    @Test void brokenTaskReferenceIsRejectedBeforeAnImportCanPersistIt() throws Exception {
        byte[] zip = archive(model("missing"));
        assertThrows(IllegalArgumentException.class, () -> parser.parseZipBytes(zip));
        assertThrows(IllegalArgumentException.class, () -> parser.parseBase64Zip(Base64.getEncoder().encodeToString(zip)));
    }

    private byte[] archive(String bpmn) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{\"code\":\"synthetic_fu\",\"name\":\"Synthetic FU\",\"version\":\"1\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("process.bpmn"));
            zip.write(bpmn.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String model(String selected) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow/custom" targetNamespace="test">
                  <process id="synthetic_fu"><userTask id="work"><extensionElements><custom:properties>
                    <custom:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:false,
                    &quot;excludePreviousHandlers&quot;:false,&quot;excludeSelectedNodeHandlers&quot;:true,&quot;selectedNodeKeys&quot;:[&quot;%s&quot;],
                    &quot;backup&quot;:{&quot;enabled&quot;:false}}"/></custom:properties></extensionElements></userTask><userTask id="review"/>
                  </process>
                </definitions>
                """.formatted(selected);
    }
}
