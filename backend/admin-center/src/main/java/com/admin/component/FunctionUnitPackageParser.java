package com.admin.component;

import com.admin.enums.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses Developer Workstation-exported ZIP archives (manifest or metadata descriptor plus BPMN/forms/tables/actions).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitPackageParser {

    private final ObjectMapper objectMapper;

    public ParsedImportPackage parseBase64Zip(String base64Content) throws IOException {
        byte[] zipBytes = Base64.getDecoder().decode(base64Content);
        return parseZipBytes(zipBytes);
    }

    @SuppressWarnings("unchecked")
    public ParsedImportPackage parseZipBytes(byte[] zipBytes) throws IOException {
        Map<String, byte[]> rawFiles = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                rawFiles.put(entry.getName(), baos.toByteArray());
            }
        }

        Map<String, Object> manifest = null;
        if (rawFiles.containsKey("manifest.json")) {
            manifest = objectMapper.readValue(rawFiles.get("manifest.json"), Map.class);
        } else if (rawFiles.containsKey("metadata.json")) {
            manifest = objectMapper.readValue(rawFiles.get("metadata.json"), Map.class);
        }

        String code = manifest != null ? stringOrNull(manifest.get("code")) : null;
        String name = manifest != null ? stringOrNull(manifest.get("name")) : null;
        String version = manifest != null ? stringOrNull(manifest.get("version")) : null;
        String description = manifest != null ? stringOrNull(manifest.get("description")) : null;
        String iconSvg = extractIconSvg(manifest);

        List<FunctionUnitManagerComponent.ContentInfo> contents = new ArrayList<>();
        List<FunctionUnitManagerComponent.ContentInfo> forms = new ArrayList<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> relationTables = new ArrayList<>();

        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (fileName.endsWith(".bpmn")) {
                String bpmn = new String(file.getValue(), StandardCharsets.UTF_8);
                String contentName = fileName.contains("/")
                        ? fileName.substring(fileName.lastIndexOf('/') + 1)
                        : fileName;
                contents.add(FunctionUnitManagerComponent.ContentInfo.builder()
                        .contentType(ContentType.PROCESS)
                        .contentName(contentName)
                        .contentPath("/" + fileName)
                        .contentData(bpmn)
                        .build());
            }
        }

        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (fileName.startsWith("tables/") && fileName.endsWith(".json")) {
                contents.add(FunctionUnitManagerComponent.ContentInfo.builder()
                        .contentType(ContentType.DATA_TABLE)
                        .contentName(fileName.substring(fileName.lastIndexOf('/') + 1))
                        .contentPath("/" + fileName)
                        .contentData(new String(file.getValue(), StandardCharsets.UTF_8))
                        .build());
            }
        }

        for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
            String fileName = file.getKey();
            if (fileName.startsWith("forms/") && fileName.endsWith(".json")) {
                try {
                    Map<String, Object> formData = objectMapper.readValue(file.getValue(), Map.class);
                    String formName = stringOrNull(formData.get("formName"));
                    if (formName == null) {
                        formName = fileName.substring(fileName.lastIndexOf('/') + 1);
                    }
                    Object formIdObj = formData.get("formId");
                    String sourceId = formIdObj != null ? String.valueOf(formIdObj) : null;
                    Object configJson = formData.get("configJson");
                    String configStr = configJson != null
                            ? objectMapper.writeValueAsString(configJson)
                            : new String(file.getValue(), StandardCharsets.UTF_8);
                    forms.add(FunctionUnitManagerComponent.ContentInfo.builder()
                            .contentType(ContentType.FORM)
                            .contentName(formName)
                            .contentPath("/" + fileName)
                            .contentData(configStr)
                            .sourceId(sourceId)
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to parse form file {}: {}", fileName, e.getMessage());
                }
            }
        }

        // Relation-table (rt_) structures exported by Developer Workstation
        if (rawFiles.containsKey("relation-tables/relation_tables.json")) {
            try {
                relationTables.addAll(objectMapper.readValue(
                        rawFiles.get("relation-tables/relation_tables.json"), List.class));
            } catch (Exception e) {
                log.warn("Failed to parse relation_tables.json: {}", e.getMessage());
            }
        }

        if (rawFiles.containsKey("actions.json")) {
            List<Map<String, Object>> actionList = objectMapper.readValue(rawFiles.get("actions.json"), List.class);
            actions.addAll(actionList);
        } else {
            for (Map.Entry<String, byte[]> file : rawFiles.entrySet()) {
                String fileName = file.getKey();
                if (fileName.startsWith("actions/") && fileName.endsWith(".json")) {
                    try {
                        actions.add(objectMapper.readValue(file.getValue(), Map.class));
                    } catch (Exception e) {
                        log.warn("Failed to parse action file {}: {}", fileName, e.getMessage());
                    }
                }
            }
        }

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
                .iconSvg(iconSvg)
                .build();
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
        private String iconSvg;
    }
}
