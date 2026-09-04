package com.admin.component;

import com.admin.dto.response.DataTableContentDTO;
import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.FunctionUnitContentItemDTO;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.dto.response.ProcessContentDTO;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.util.ChecksumUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Function unit content management: add/query content rows and assemble the full
 * content response (BPMN, forms with table bindings, data tables) with a 5-minute cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitContentComponent {

    private final FunctionUnitContentRepository contentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final FunctionUnitLookup functionUnitLookup;
    private final FormTableBindingLoader bindingLoader;

    /**
     * In-memory cache for assembled function unit content (forms + bindings + BPMN + data tables).
     * Key: functionUnitId. TTL: 5 min. Avoids repeated DB queries + JOINs for the same FU.
     */
    private final Map<String, CachedContent> assembledContentCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedContent> eldest) {
                    return size() > 50;
                }
            });

    private static final long CONTENT_CACHE_TTL_MS = java.util.concurrent.TimeUnit.MINUTES.toMillis(5);

    private record CachedContent(FunctionUnitContentResponse response, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CONTENT_CACHE_TTL_MS;
        }
    }

    /**
     * Add function unit content (with source id)
     * @param sourceId Source content id (e.g. developer-workstation dw_form_definitions.id)
     */
    @Transactional
    public void addFunctionUnitContent(String functionUnitId, ContentType contentType,
                                       String contentName, String contentData, String sourceId) {
        FunctionUnit functionUnit = functionUnitLookup.getById(functionUnitId);

        String contentChecksum = ChecksumUtils.sha256Hex(contentData);
        String contentPath = "/" + contentType.name().toLowerCase() + "s/" + contentName;

        FunctionUnitContent unitContent = FunctionUnitContent.builder()
                .id(UUID.randomUUID().toString())
                .functionUnit(functionUnit)
                .contentType(contentType)
                .contentName(contentName)
                .contentPath(contentPath)
                .contentData(contentData)
                .checksum(contentChecksum)
                .sourceId(sourceId)
                .build();
        contentRepository.save(unitContent);

        log.info("Added content {} of type {} with sourceId {} to function unit {}", contentName, contentType, sourceId, functionUnitId);
    }

    /**
     * Get function unit by process definition key
     * Locate via content whose flowable_process_definition_id starts with processKey:
     */
    @Transactional(readOnly = true)
    public FunctionUnit getFunctionUnitByProcessKey(String processKey) {
        List<FunctionUnitContent> results = contentRepository.findAllByProcessDefinitionKey(processKey);
        if (results.isEmpty()) {
            throw new FunctionUnitNotFoundException("Function unit not found for process definition key: " + processKey);
        }
        // List ordered by content.createdAt DESC; newest row may be on a disabled catalog version.
        // For portal tasks/assignment by processDefinitionKey, prefer enabled catalog row to avoid false disabled.
        for (FunctionUnitContent c : results) {
            FunctionUnit fu = c.getFunctionUnit();
            if (fu != null && Boolean.TRUE.equals(fu.getEnabled())) {
                return fu;
            }
        }
        return results.get(0).getFunctionUnit();
    }

    /**
     * Get all contents for function unit
     */
    public List<FunctionUnitContent> getFunctionUnitContents(String functionUnitId) {
        return contentRepository.findByFunctionUnitId(functionUnitId);
    }

    /**
     * Get function unit contents filtered by type.
     * <p>null type returns all; valid type filters; invalid type throws AdminBusinessException.
     *
     * <p><b>Validates: Requirements 35.1, 35.2, 35.3</b>
     */
    @Transactional(readOnly = true)
    public List<FunctionUnitContentItemDTO> getContentsByType(String functionUnitId, String type) {
        List<FunctionUnitContent> contents;
        if (type == null || type.isBlank()) {
            contents = contentRepository.findByFunctionUnitId(functionUnitId);
        } else {
            ContentType requestedType;
            try {
                requestedType = ContentType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AdminBusinessException("INVALID_CONTENT_TYPE", "Invalid content type: " + type);
            }
            contents = contentRepository.findByFunctionUnitIdAndContentType(functionUnitId, requestedType);
        }
        return contents.stream()
                .map(c -> FunctionUnitContentItemDTO.builder()
                        .id(c.getId())
                        .contentType(c.getContentType().name())
                        .contentName(c.getContentName())
                        .contentData(c.getContentData())
                        .sourceId(c.getSourceId())
                        .build())
                .toList();
    }

    /**
     * Assemble full function unit content (BPMN, forms, data tables, etc.).
     * <p>Includes Base64 BPMN decode, latest config_json from dw_form_definitions,
     * load tableBindings and attach to form content.
     *
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b>
     */
    @Transactional(readOnly = true)
    public FunctionUnitContentResponse assembleFunctionUnitContent(String id) {
        // Check cache first
        CachedContent cached = assembledContentCache.get(id);
        if (cached != null && !cached.isExpired()) {
            return cached.response();
        }

        FunctionUnit unit = functionUnitLookup.getById(id);
        List<FunctionUnitContent> contents = contentRepository.findByFunctionUnitId(id);

        List<FormContentDTO> forms = new ArrayList<>();
        List<ProcessContentDTO> processes = new ArrayList<>();
        List<DataTableContentDTO> dataTables = new ArrayList<>();

        for (FunctionUnitContent content : contents) {
            String data = content.getContentData();

            if (content.getContentType() == ContentType.PROCESS && data != null) {
                data = decodeBase64IfNeeded(data);
                String processKey = extractProcessKey(data);
                processes.add(ProcessContentDTO.builder()
                        .id(content.getId())
                        .name(content.getContentName())
                        .sourceId(content.getSourceId())
                        .data(data)
                        .type(ContentType.PROCESS.name())
                        .flowableProcessDefinitionKey(content.getFlowableProcessDefinitionId() != null
                                ? content.getFlowableProcessDefinitionId()
                                : processKey)
                        .build());
            } else if (content.getContentType() == ContentType.FORM) {
                FormDefinitionSnapshot latest = fetchLatestFormDefinitionOrFallback(content, data);
                forms.add(FormContentDTO.builder()
                        .id(content.getId())
                        .name(content.getContentName())
                        .sourceId(content.getSourceId())
                        .data(latest.configJson())
                        .formType(latest.formType())
                        // Lets the portal tell a node's To Do design from its My Requests one.
                        .scene(latest.scene())
                        .type(ContentType.FORM.name())
                        .build());
            } else if (content.getContentType() == ContentType.DATA_TABLE) {
                dataTables.add(DataTableContentDTO.builder()
                        .id(content.getId())
                        .name(content.getContentName())
                        .sourceId(content.getSourceId())
                        .data(data)
                        .type(ContentType.DATA_TABLE.name())
                        .build());
            }
        }

        appendDetailFormsMissingFromSnapshot(unit, forms);

        // Attach tableBindings to each form
        bindingLoader.attachTableBindings(forms);

        FunctionUnitContentResponse response = FunctionUnitContentResponse.builder()
                .id(unit.getId())
                .name(unit.getName())
                .code(unit.getCode())
                .version(unit.getVersion())
                .description(unit.getDescription())
                .status(unit.getStatus().name())
                .forms(forms)
                .processes(processes)
                .dataTables(dataTables)
                .build();
        // Cache the assembled result
        assembledContentCache.put(id, new CachedContent(response, System.currentTimeMillis()));
        return response;
    }

    /**
     * Attempt Base64 decode; return raw data if not Base64 encoded.
     */
    private String decodeBase64IfNeeded(String data) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(data);
            String result = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            log.info("Decoded BPMN XML, length: {}", result.length());
            return result;
        } catch (IllegalArgumentException e) {
            log.info("BPMN data is not Base64 encoded, using raw data");
            return data;
        }
    }

    /**
     * Adds the Function Unit's DETAIL forms that {@code sys_function_unit_contents} does not carry.
     *
     * <p>The content rows are written only at import/deploy time, so a form created in the designer
     * afterwards is invisible here. That is tolerable for TASK/PROCESS forms, which a running
     * process reaches through its BPMN, but not for DETAIL forms: a Main Table View stores its
     * {@code detail_form_id} live in {@code dw_main_table_view_configs}, and the portal resolves
     * that id against this list. When the id is not in it the record page has nothing to render and
     * reports "This record could not be loaded" — for every view whose detail form post-dates the
     * last deploy.
     *
     * <p>Scoped to DETAIL on purpose. These forms are addressed by id from live view config rather
     * than by the deployed process, so the snapshot is the wrong authority for them; widening this
     * to every form type would quietly change what a deployed version means.
     *
     * <p>Best-effort: any lookup failure leaves the assembled list untouched.
     */
    private void appendDetailFormsMissingFromSnapshot(FunctionUnit unit, List<FormContentDTO> forms) {
        if (unit == null || unit.getCode() == null || unit.getCode().isBlank()) {
            return;
        }
        // id 留空是对的：这些表单没有对应的 content 行，而下游一律按 sourceId 匹配。
        Set<String> known = forms.stream()
                .map(FormContentDTO::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT f.id, f.form_name, f.config_json::text AS config_json, f.form_type, f.scene
                    FROM dw_form_definitions f
                    INNER JOIN dw_function_units d ON d.id = f.function_unit_id
                    WHERE d.code = ? AND f.form_type = 'DETAIL'
                    """, unit.getCode());
            for (Map<String, Object> row : rows) {
                String sourceId = String.valueOf(row.get("id"));
                if (known.contains(sourceId)) {
                    continue;
                }
                forms.add(FormContentDTO.builder()
                        .name((String) row.get("form_name"))
                        .sourceId(sourceId)
                        .data((String) row.get("config_json"))
                        .formType((String) row.get("form_type"))
                        .scene((String) row.get("scene"))
                        .type(ContentType.FORM.name())
                        .build());
            }
        } catch (Exception e) {
            log.warn("Could not add live DETAIL forms for function unit {}: {}",
                    unit.getCode(), e.getMessage());
        }
    }

    private record FormDefinitionSnapshot(String configJson, String formType, String scene) {}

    /**
     * For FORM content, try to fetch the latest config_json + form_type from dw_form_definitions
     * (the content_data may be a stale snapshot from import time).
     */
    private FormDefinitionSnapshot fetchLatestFormDefinitionOrFallback(
            FunctionUnitContent content, String fallbackData) {
        if (content.getSourceId() == null) {
            return new FormDefinitionSnapshot(fallbackData, null, null);
        }
        try {
            Long sourceIdLong = Long.parseLong(content.getSourceId());
            org.springframework.jdbc.core.ResultSetExtractor<FormDefinitionSnapshot> extractor = rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new FormDefinitionSnapshot(
                        rs.getString("config_json"),
                        rs.getString("form_type"),
                        rs.getString("scene"));
            };
            FormDefinitionSnapshot latest = jdbcTemplate.query(
                    """
                            SELECT config_json::text AS config_json, form_type, scene
                            FROM dw_form_definitions
                            WHERE id = ?
                            """,
                    extractor,
                    sourceIdLong);
            if (latest != null && latest.configJson() != null) {
                log.info("Using latest config_json/form_type from dw_form_definitions for form sourceId={}",
                        content.getSourceId());
                return latest;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid sourceId format: {}", content.getSourceId());
        } catch (Exception e) {
            log.warn("Could not fetch latest form definition for form sourceId={}, using content_data: {}",
                    content.getSourceId(), e.getMessage());
        }
        return new FormDefinitionSnapshot(fallbackData, null, null);
    }

    /**
     * Extract {@code <process id="...">} attribute value from BPMN XML.
     */
    private String extractProcessKey(String bpmnXml) {
        try {
            int processStart = bpmnXml.indexOf("<bpmn:process");
            if (processStart == -1) {
                processStart = bpmnXml.indexOf("<process");
            }
            if (processStart != -1) {
                int idStart = bpmnXml.indexOf("id=\"", processStart);
                if (idStart != -1) {
                    idStart += 4;
                    int idEnd = bpmnXml.indexOf("\"", idStart);
                    if (idEnd != -1) {
                        return bpmnXml.substring(idStart, idEnd);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract process key from BPMN XML: {}", e.getMessage());
        }
        return null;
    }
}
