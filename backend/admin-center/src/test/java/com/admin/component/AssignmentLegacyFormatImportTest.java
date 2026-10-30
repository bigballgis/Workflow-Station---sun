package com.admin.component;

import com.admin.dto.request.FunctionUnitImportRequest;
import com.admin.repository.FunctionUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** .fpkg is an accepted non-ZIP HTTP import format and must enforce the same portable policy contract. */
class AssignmentLegacyFormatImportTest {
    @Test void malformedPolicyIsRejectedByTheRealPublicImporterBeforeAnyCatalogLookupOrWrite() throws Exception {
        var constructor=FunctionUnitImportComponent.class.getConstructors()[0];
        Object[] dependencies=Arrays.stream(constructor.getParameterTypes()).map(type->{
            if (type==ObjectMapper.class) return new ObjectMapper();
            if (type==ImportBpmnStructureValidator.class) return new ImportBpmnStructureValidator();
            return mock(type);
        }).toArray();
        var importer=(FunctionUnitImportComponent)constructor.newInstance(dependencies);
        var repository=(FunctionUnitRepository)dependencies[0];
        var request=new FunctionUnitImportRequest(); request.setFileName("synthetic.fpkg"); request.setCode("synthetic");
        request.setFileContent("""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow.platform/schema/custom">
                <process id="synthetic"><startEvent id="start"/><userTask id="work"><extensionElements><custom:properties>
                <custom:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:99}"/>
                </custom:properties></extensionElements></userTask></process></definitions>
                """);
        assertAll(()->assertThrows(IllegalArgumentException.class,()->importer.importFunctionPackage(request,"synthetic-importer")),
                ()->verifyNoInteractions(repository));
    }
}
