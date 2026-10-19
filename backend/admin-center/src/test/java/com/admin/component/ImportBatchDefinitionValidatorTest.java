package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportBatchDefinitionValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsNullFormAsPackageValidationError() {
        assertThatThrownBy(() -> ImportBatchDefinitionValidator.validate(pack(List.of(form("null")), List.of()), mapper))
                .isInstanceOf(IOException.class).hasMessageContaining("FORM content must be an object");
    }

    @Test
    void validatesBothLegacyContentsAndSeparateZipFormsBeforePersistence() {
        var bad = form("{\"configJson\":{\"batchForms\":{\"101\":{\"schemaVersion\":99,\"rule\":[]}}}}");
        assertThatThrownBy(() -> ImportBatchDefinitionValidator.validate(pack(List.of(bad), List.of()), mapper))
                .isInstanceOf(IOException.class).hasMessageContaining("batch");
        assertThatThrownBy(() -> ImportBatchDefinitionValidator.validate(pack(List.of(), List.of(bad)), mapper))
                .isInstanceOf(IOException.class).hasMessageContaining("batch");
        var legacy = form("{\"rule\":[]}");
        assertThatCode(() -> ImportBatchDefinitionValidator.validate(pack(List.of(legacy), List.of()), mapper))
                .doesNotThrowAnyException();
    }

    private static FunctionUnitManagerComponent.ContentInfo form(String json) {
        return FunctionUnitManagerComponent.ContentInfo.builder().contentType(ContentType.FORM).contentData(json).build();
    }

    private static FunctionUnitPackageParser.ParsedImportPackage pack(
            List<FunctionUnitManagerComponent.ContentInfo> contents, List<FunctionUnitManagerComponent.ContentInfo> forms) {
        return FunctionUnitPackageParser.ParsedImportPackage.builder().forms(forms).packageContent(
                FunctionUnitManagerComponent.FunctionPackageContent.builder().contents(contents).build()).build();
    }
}
