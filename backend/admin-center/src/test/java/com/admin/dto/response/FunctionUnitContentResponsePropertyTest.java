package com.admin.dto.response;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Tag;

import java.util.List;

/**
 * Property 19: Admin Center API 统一响应格式
 * 验证 DTO 类的构建和数据完整性
 *
 * **Validates: Requirements 28.2, 28.3, 28.4, 28.5**
 *
 * NOTE: Full API response format testing (verifying all endpoints return ApiResponse structure)
 * requires the controller to be refactored to extend BaseController first.
 * This test validates the DTO layer that will be used after the refactoring.
 */
@Tag("Feature: function-unit-design-review, Property 19: Admin Center API unified response format")
class FunctionUnitContentResponsePropertyTest {

    @Property(tries = 100)
    @Label("FunctionUnitContentResponse builder preserves all fields")
    void contentResponseBuilderPreservesAllFields(
            @ForAll @StringLength(min = 1, max = 64) String id,
            @ForAll @StringLength(min = 1, max = 100) String name,
            @ForAll @StringLength(min = 1, max = 50) String code,
            @ForAll @StringLength(min = 1, max = 20) String version,
            @ForAll @StringLength(min = 0, max = 200) String description) {

        FunctionUnitContentResponse response = FunctionUnitContentResponse.builder()
                .id(id)
                .name(name)
                .code(code)
                .version(version)
                .description(description)
                .status("DEPLOYED")
                .forms(List.of())
                .processes(List.of())
                .dataTables(List.of())
                .build();

        assert response.getId().equals(id);
        assert response.getName().equals(name);
        assert response.getCode().equals(code);
        assert response.getVersion().equals(version);
        assert response.getDescription().equals(description);
        assert response.getStatus().equals("DEPLOYED");
        assert response.getForms() != null;
        assert response.getProcesses() != null;
        assert response.getDataTables() != null;
    }

    @Property(tries = 100)
    @Label("FormContentDTO builder preserves all fields including tableBindings")
    void formContentDtoBuilderPreservesFields(
            @ForAll @StringLength(min = 1, max = 64) String id,
            @ForAll @StringLength(min = 1, max = 100) String name,
            @ForAll @StringLength(min = 0, max = 64) String sourceId) {

        TableBindingDTO binding = TableBindingDTO.builder()
                .bindingId(1L)
                .bindingType("PRIMARY")
                .bindingMode("EDITABLE")
                .tableName("test_table")
                .build();

        FormContentDTO dto = FormContentDTO.builder()
                .id(id)
                .name(name)
                .sourceId(sourceId)
                .data("{}")
                .type("FORM")
                .formType("PROCESS")
                .tableBindings(List.of(binding))
                .build();

        assert dto.getId().equals(id);
        assert dto.getName().equals(name);
        assert dto.getSourceId().equals(sourceId);
        assert dto.getType().equals("FORM");
        assert "PROCESS".equals(dto.getFormType());
        assert dto.getTableBindings().size() == 1;
        assert dto.getTableBindings().get(0).getBindingType().equals("PRIMARY");
    }

    @Property(tries = 100)
    @Label("ProcessContentDTO and DataTableContentDTO builder preserves fields")
    void processAndDataTableDtoBuilderPreservesFields(
            @ForAll @StringLength(min = 1, max = 64) String id,
            @ForAll @StringLength(min = 1, max = 100) String name) {

        ProcessContentDTO process = ProcessContentDTO.builder()
                .id(id)
                .name(name)
                .data("<bpmn/>")
                .type("PROCESS")
                .build();

        DataTableContentDTO dataTable = DataTableContentDTO.builder()
                .id(id)
                .name(name)
                .data("{}")
                .type("DATA_TABLE")
                .build();

        assert process.getId().equals(id);
        assert process.getType().equals("PROCESS");
        assert dataTable.getId().equals(id);
        assert dataTable.getType().equals("DATA_TABLE");
    }

    @Property(tries = 100)
    @Label("TableBindingDTO preserves all binding metadata")
    void tableBindingDtoPreservesMetadata(
            @ForAll @LongRange(min = 1, max = 10000) long bindingId,
            @ForAll @IntRange(min = 0, max = 100) int sortOrder) {

        String[] bindingTypes = {"PRIMARY", "SUB", "RELATED"};
        String[] bindingModes = {"EDITABLE", "READONLY"};

        for (String type : bindingTypes) {
            for (String mode : bindingModes) {
                TableBindingDTO dto = TableBindingDTO.builder()
                        .bindingId(bindingId)
                        .bindingType(type)
                        .bindingMode(mode)
                        .sortOrder(sortOrder)
                        .tableName("table_" + bindingId)
                        .build();

                assert dto.getBindingId() == bindingId;
                assert dto.getBindingType().equals(type);
                assert dto.getBindingMode().equals(mode);
                assert dto.getSortOrder() == sortOrder;
            }
        }
    }
}
