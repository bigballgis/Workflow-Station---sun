package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.form.SubTableBatchDefinition;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Validates ZIP and legacy JSON FORM contents before any catalog persistence. */
final class ImportBatchDefinitionValidator {
    private ImportBatchDefinitionValidator() { }

    static void validate(FunctionUnitPackageParser.ParsedImportPackage parsed, ObjectMapper mapper) throws IOException {
        validateContents(parsed.getPackageContent().getContents(), mapper);
        validateContents(parsed.getForms(), mapper);
    }

    private static void validateContents(List<FunctionUnitManagerComponent.ContentInfo> contents, ObjectMapper mapper) throws IOException {
        if (contents == null) return; // Legacy packages may omit this optional contents list.
        for (FunctionUnitManagerComponent.ContentInfo content : contents) {
            if (content.getContentType() != ContentType.FORM) continue;
            Map<String, Object> payload = mapper.readValue(content.getContentData(), new TypeReference<Map<String, Object>>() { });
            if (payload == null) throw new IOException("Invalid batch definition: FORM content must be an object");
            try { SubTableBatchDefinition.validate(payload.containsKey("configJson") ? payload.get("configJson") : payload); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid batch definition in FORM content", invalid); }
        }
    }
}
