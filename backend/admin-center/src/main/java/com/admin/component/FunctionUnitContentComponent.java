package com.admin.component;

import com.admin.dto.response.DataTableContentDTO;
import com.admin.dto.response.FormContentDTO;
import com.admin.dto.response.FunctionUnitContentItemDTO;
import com.admin.dto.response.FunctionUnitContentResponse;
import com.admin.dto.response.ProcessContentDTO;
import com.admin.dto.response.TableBindingDTO;
import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitNotFoundException;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.util.ChecksumUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.platform.common.functionunit.CatalogSnapshotReader catalogSnapshots;

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

        if (Boolean.TRUE.equals(functionUnit.getProcessDeployed())
                || functionUnit.getStatus() == com.admin.enums.FunctionUnitStatus.DEPLOYED
                || jdbcTemplate.queryForObject("SELECT COUNT(*) FROM up_process_instance WHERE function_unit_catalog_id=?",
                        Long.class, functionUnitId) > 0) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException("VERSION_CONTENT_IMMUTABLE", functionUnitId);
        }
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
     * Locate through the Engine repository; a long process key can have a UUID-only definition id.
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
     * <p>Forms and their metadata are read exclusively from the selected release snapshot.
     *
     * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b>
     */
    @Transactional(readOnly = true)
    public FunctionUnitContentResponse assembleFunctionUnitContent(String id) {
        return assembleFunctionUnitContent(id, false);
    }

    /** Start consistency checks must see the committed catalog, never a cached assembly. */
    @Transactional(readOnly = true)
    public FunctionUnitContentResponse assembleFunctionUnitContent(String id, boolean forStart) {
        // Check cache first
        CachedContent cached = forStart ? null : assembledContentCache.get(id);
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
                        .flowableProcessDefinitionKey(processKey)
                        .flowableProcessDefinitionId(content.getFlowableProcessDefinitionId())
                        .build());
            } else if (content.getContentType() == ContentType.FORM) {
                forms.add(assembleFormContent(content, data));
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


        var bindingMapper = objectMapper.copy().configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        for (FormContentDTO form : forms) {
            var snapshot = catalogSnapshots.form(id, form.getSourceId());
            form.setTableBindings(catalogSnapshots.formBindings(id, snapshot).stream()
                    .map(binding -> bindingMapper.convertValue(binding, TableBindingDTO.class)).toList());
        }

        FunctionUnitContentResponse response = FunctionUnitContentResponse.builder()
                .startContentAuthoritative(forStart ? true : null)
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
        if (!forStart) assembledContentCache.put(id, new CachedContent(response, System.currentTimeMillis()));
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

    private FormContentDTO assembleFormContent(FunctionUnitContent content, String data) {
        CatalogFormSnapshot.Payload payload = CatalogFormSnapshot.unwrap(objectMapper, data);
        FormDefinitionSnapshot snapshot = formSnapshot(content, payload.configJson());
        FormContentDTO form = FormContentDTO.builder()
                .id(content.getId())
                .name(content.getContentName())
                .sourceId(content.getSourceId())
                .data(snapshot.configJson())
                .publishedFieldPermissions(payload.publishedFieldPermissions())
                .formType(snapshot.formType())
                // Lets the portal tell a node's To Do design from its My Requests one.
                .scene(snapshot.scene())
                .type(ContentType.FORM.name())
                .build();
        if (payload.freezeBindings()) {
            List<TableBindingDTO> frozen = payload.tableBindings();
            form.setTableBindings(frozen != null ? frozen : List.of());
        }
        if (form.getTableBindings() == null) form.setTableBindings(List.of());
        return form;
    }

    private record FormDefinitionSnapshot(String configJson, String formType, String scene) {}

    private FormDefinitionSnapshot formSnapshot(
            FunctionUnitContent content, String snapshotData) {
        try {
            com.fasterxml.jackson.databind.JsonNode wrapper = objectMapper.readTree(content.getContentData());
            return new FormDefinitionSnapshot(snapshotData,
                    wrapper.path("formType").isTextual() ? wrapper.get("formType").asText() : null,
                    wrapper.path("scene").isTextual() ? wrapper.get("scene").asText() : null);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_SNAPSHOT_INCOMPLETE", content.getId());
        }
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
