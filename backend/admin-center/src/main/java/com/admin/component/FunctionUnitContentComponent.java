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
import java.util.UUID;

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

    private record FormDefinitionSnapshot(String configJson, String formType) {}

    /**
     * For FORM content, try to fetch the latest config_json + form_type from dw_form_definitions
     * (the content_data may be a stale snapshot from import time).
     */
    private FormDefinitionSnapshot fetchLatestFormDefinitionOrFallback(
            FunctionUnitContent content, String fallbackData) {
        if (content.getSourceId() == null) {
            return new FormDefinitionSnapshot(fallbackData, null);
        }
        try {
            Long sourceIdLong = Long.parseLong(content.getSourceId());
            org.springframework.jdbc.core.ResultSetExtractor<FormDefinitionSnapshot> extractor = rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new FormDefinitionSnapshot(
                        rs.getString("config_json"),
                        rs.getString("form_type"));
            };
            FormDefinitionSnapshot latest = jdbcTemplate.query(
                    """
                            SELECT config_json::text AS config_json, form_type
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
        return new FormDefinitionSnapshot(fallbackData, null);
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
