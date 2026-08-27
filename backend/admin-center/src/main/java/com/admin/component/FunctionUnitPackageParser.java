package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Parses Developer Workstation-exported ZIP archives (manifest plus BPMN/forms/tables/actions).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitPackageParser {

    private final ObjectMapper objectMapper;

    public ParsedImportPackage parseBase64Zip(String base64Content) throws IOException {
        ImportZipReader.assertBase64Length(base64Content);
        byte[] zipBytes = Base64.getDecoder().decode(base64Content);
        return parseZipBytes(zipBytes);
    }

    @SuppressWarnings("unchecked")
    public ParsedImportPackage parseZipBytes(byte[] zipBytes) throws IOException {
        Map<String, byte[]> rawFiles = ImportZipReader.readEntries(zipBytes);

        Map<String, Object> manifest = null;
        if (rawFiles.containsKey("manifest.json")) {
            manifest = readJsonMap(rawFiles.get("manifest.json"), "manifest.json");
        } else if (rawFiles.containsKey("metadata.json")) {
            manifest = readJsonMap(rawFiles.get("metadata.json"), "metadata.json");
        }

        String code = manifest != null ? stringOrNull(manifest.get("code")) : null;
        String name = manifest != null ? stringOrNull(manifest.get("name")) : null;
        String version = manifest != null ? stringOrNull(manifest.get("version")) : null;
        String description = manifest != null ? stringOrNull(manifest.get("description")) : null;
        String iconSvg = FunctionUnitIconSvgSanitizer.sanitize(extractIconSvg(manifest));

        List<FunctionUnitManagerComponent.ContentInfo> contents = new ArrayList<>();
        List<FunctionUnitManagerComponent.ContentInfo> forms = new ArrayList<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> relationTables = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();
        List<Map<String, Object>> emailMonitors = new ArrayList<>();

        addProcessAndTableContents(rawFiles, contents);
        addEmailTemplates(rawFiles, contents);
        addDecisionsAndRelations(rawFiles, contents);
        addForms(rawFiles, forms);
        addActions(rawFiles, actions);
        addRelationTables(rawFiles, relationTables);
        addJsonDir(rawFiles, "connections/", connections);
        addJsonDir(rawFiles, "email-monitors/", emailMonitors);

        FunctionUnitManagerComponent.FunctionPackageContent packageContent =
                FunctionUnitManagerComponent.FunctionPackageContent.builder()
                        .code(code)
                        .name(name)
                        .version(version != null ? version : "1.0.0")
                        .description(description)
                        .dependencies(new ArrayList<>())
                        .contents(contents)
                        .build();

        return ParsedImportPackage.builder()
                .packageContent(packageContent)
                .forms(forms)
                .actions(actions)
                .relationTables(relationTables)
                .connections(connections)
                .emailMonitors(emailMonitors)
                .iconSvg(iconSvg)
                .build();
    }

    private void addProcessAndTableContents(Map<String, byte[]> rawFiles,
                                            List<FunctionUnitManagerComponent.ContentInfo> contents)
            throws IOException {
        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (fileName.endsWith(".bpmn")) {
                contents.add(content(ContentType.PROCESS, basename(fileName), fileName,
                        utf8(file.getValue()), null));
            }
        }
        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (fileName.startsWith("tables/") && fileName.endsWith(".json")) {
                readJsonValue(file.getValue(), fileName);
                contents.add(content(ContentType.DATA_TABLE, basename(fileName), fileName,
                        utf8(file.getValue()), null));
            }
        }
        if (rawFiles.containsKey("views/main_table_views.json")) {
            readJsonValue(rawFiles.get("views/main_table_views.json"), "views/main_table_views.json");
            contents.add(content(ContentType.MAIN_TABLE_VIEW, "main_table_views.json",
                    "views/main_table_views.json",
                    utf8(rawFiles.get("views/main_table_views.json")), null));
        }
    }

    @SuppressWarnings("unchecked")
    private void addEmailTemplates(Map<String, byte[]> rawFiles,
                                   List<FunctionUnitManagerComponent.ContentInfo> contents) throws IOException {
        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (!fileName.startsWith("email-templates/") || !fileName.endsWith(".json")) {
                continue;
            }
            Map<String, Object> templateData = readJsonMap(file.getValue(), fileName);
            String contentName = basename(fileName);
            Object nameObj = templateData.get("name");
            if (nameObj instanceof String templateName && !templateName.isBlank()) {
                contentName = templateName;
            }
            Object templateId = templateData.get("templateId");
            if (templateId == null) {
                templateId = templateData.get("id");
            }
            contents.add(content(ContentType.EMAIL_TEMPLATE, contentName, fileName,
                    utf8(file.getValue()), templateId != null ? String.valueOf(templateId) : null));
        }
    }

    private void addDecisionsAndRelations(Map<String, byte[]> rawFiles,
                                          List<FunctionUnitManagerComponent.ContentInfo> contents) throws IOException {
        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (fileName.startsWith("decisions/") && fileName.endsWith(".dmn")) {
                contents.add(content(ContentType.DECISION, basename(fileName), fileName,
                        utf8(file.getValue()), null));
            }
        }
        if (rawFiles.containsKey("relations/table_relations.json")) {
            readJsonValue(rawFiles.get("relations/table_relations.json"), "relations/table_relations.json");
            contents.add(content(ContentType.TABLE_RELATION, "table_relations.json",
                    "relations/table_relations.json",
                    utf8(rawFiles.get("relations/table_relations.json")), null));
        }
    }

    @SuppressWarnings("unchecked")
    private void addForms(Map<String, byte[]> rawFiles,
                          List<FunctionUnitManagerComponent.ContentInfo> forms) throws IOException {
        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (!fileName.startsWith("forms/") || !fileName.endsWith(".json")) {
                continue;
            }
            Map<String, Object> formData = readJsonMap(file.getValue(), fileName);
            String formName = stringOrNull(formData.get("formName"));
            if (formName == null) {
                formName = basename(fileName);
            }
            Object formIdObj = formData.get("formId");
            String sourceId = formIdObj != null ? String.valueOf(formIdObj) : null;
            Object configJson = formData.get("configJson");
            String configStr = configJson != null
                    ? objectMapper.writeValueAsString(configJson)
                    : utf8(file.getValue());
            forms.add(content(ContentType.FORM, formName, fileName, configStr, sourceId));
        }
    }

    @SuppressWarnings("unchecked")
    private void addActions(Map<String, byte[]> rawFiles, List<Map<String, Object>> actions) throws IOException {
        if (rawFiles.containsKey("actions.json")) {
            Object parsed = readJsonValue(rawFiles.get("actions.json"), "actions.json");
            if (!(parsed instanceof List<?> list)) {
                throw new IOException("Invalid actions.json in package: expected a JSON array");
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?>)) {
                    throw new IOException("Invalid actions.json in package: expected object entries");
                }
                actions.add((Map<String, Object>) item);
            }
            return;
        }
        addJsonDir(rawFiles, "actions/", actions);
    }

    @SuppressWarnings("unchecked")
    private void addRelationTables(Map<String, byte[]> rawFiles, List<Map<String, Object>> relationTables)
            throws IOException {
        if (!rawFiles.containsKey("relation-tables/relation_tables.json")) {
            return;
        }
        Object parsed = readJsonValue(rawFiles.get("relation-tables/relation_tables.json"),
                "relation-tables/relation_tables.json");
        if (!(parsed instanceof List<?> list)) {
            throw new IOException("Invalid relation-tables/relation_tables.json in package");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IOException("Invalid relation-tables/relation_tables.json in package");
            }
            relationTables.add((Map<String, Object>) item);
        }
    }

    @SuppressWarnings("unchecked")
    private void addJsonDir(Map<String, byte[]> rawFiles, String prefix, List<Map<String, Object>> out)
            throws IOException {
        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (!fileName.startsWith(prefix) || !fileName.endsWith(".json")) {
                continue;
            }
            out.add(readJsonMap(file.getValue(), fileName));
        }
    }

    private Map<String, Object> readJsonMap(byte[] data, String fileName) throws IOException {
        Object parsed = readJsonValue(data, fileName);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IOException("Invalid JSON object in package: " + fileName);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
    }

    private Object readJsonValue(byte[] data, String fileName) throws IOException {
        try {
            return objectMapper.readValue(data, Object.class);
        } catch (Exception e) {
            throw new IOException("Invalid JSON in package: " + fileName, e);
        }
    }

    private static FunctionUnitManagerComponent.ContentInfo content(
            ContentType type, String name, String path, String data, String sourceId) {
        return FunctionUnitManagerComponent.ContentInfo.builder()
                .contentType(type)
                .contentName(name)
                .contentPath("/" + path.replace('\\', '/'))
                .contentData(data)
                .sourceId(sourceId)
                .build();
    }

    private static String basename(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return slash >= 0 ? fileName.substring(slash + 1) : fileName;
    }

    private static String utf8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private String extractIconSvg(Map<String, Object> manifest) {
        if (manifest == null) {
            return null;
        }
        Object iconObj = manifest.get("icon");
        if (!(iconObj instanceof Map<?, ?> icon)) {
            return null;
        }
        Object svg = icon.get("svgContent");
        return svg instanceof String s && !s.isBlank() ? s : null;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    @Data
    @Builder
    public static class ParsedImportPackage {
        private FunctionUnitManagerComponent.FunctionPackageContent packageContent;
        private List<FunctionUnitManagerComponent.ContentInfo> forms;
        private List<Map<String, Object>> actions;
        private List<Map<String, Object>> relationTables;
        private List<Map<String, Object>> connections;
        private List<Map<String, Object>> emailMonitors;
        private String iconSvg;
    }
}
