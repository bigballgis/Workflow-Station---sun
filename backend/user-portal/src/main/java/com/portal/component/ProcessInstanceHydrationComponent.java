package com.portal.component;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * Ensures {@code up_process_instance} exists for engine-started flows (e.g. email inbound monitor)
 * that bypass {@link ProcessStartComponent#startProcess}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessInstanceHydrationComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /** Local row if present; otherwise hydrate from Flowable and persist. */
    public ProcessInstance requireProcessInstance(String processInstanceId) {
        return requireProcessInstance(processInstanceId, null);
    }

    /**
     * Prefer {@code engineSnapshot} when the caller already holds start-time process data
     * (email monitor internal hydrate). Snapshot path avoids a JWT-protected engine GET that
     * fails with 403 on service-to-service calls with only {@code X-Internal-Token}.
     */
    @Transactional
    public ProcessInstance requireProcessInstance(String processInstanceId, Map<String, Object> engineSnapshot) {
        Optional<ProcessInstance> local = processInstanceRepository.findById(processInstanceId);
        if (local.isPresent()) {
            return validateOrRestorePin(local.get());
        }
        if (hasUsableSnapshot(engineSnapshot)) {
            return persistFromEngineRow(processInstanceId, engineSnapshot, false);
        }
        return hydrateFromEngine(processInstanceId);
    }

    /**
     * The release a page reads a unit's content from. With a task or request in hand it is the
     * release that request was started on — never the unit's latest or currently active one — and
     * the page may name the unit by that release, its code or its process key; naming another
     * release of it is refused. Without either (a new request), the unit is served as named.
     */
    public String pinnedContentRef(String requested, String taskId, String processInstanceId) {
        String instanceId = StringUtils.hasText(processInstanceId) ? processInstanceId.trim() : null;
        if (StringUtils.hasText(taskId)) {
            Map<String, Object> task = workflowEngineClient.getTaskById(taskId.trim())
                    .map(com.portal.util.WorkflowEnginePayloadHelper::singleTaskFromPayload)
                    .orElseThrow(() -> new PortalException("404", "Task not found"));
            String taskInstanceId = stringValue(task.get("processInstanceId"));
            if (taskInstanceId == null || (instanceId != null && !instanceId.equals(taskInstanceId))) {
                throw new com.platform.common.functionunit.FunctionUnitVersionException(
                        "VERSION_CONTEXT_MISMATCH", processInstanceId);
            }
            instanceId = taskInstanceId;
        }
        if (instanceId == null) {
            return requested;
        }
        ProcessInstance instance = requireProcessInstance(instanceId);
        String pinned = instance.getFunctionUnitCatalogId();
        String named = requested == null ? "" : requested.trim();
        if (named.equalsIgnoreCase(pinned) || named.equals(instance.getFunctionUnitCode())
                || named.equals(instance.getProcessDefinitionKey())) {
            return pinned;
        }
        throw new com.platform.common.functionunit.FunctionUnitVersionException("VERSION_CONTEXT_MISMATCH", named);
    }

    @Transactional
    protected ProcessInstance hydrateFromEngine(String processInstanceId) {
        Optional<ProcessInstance> raced = processInstanceRepository.findById(processInstanceId);
        if (raced.isPresent()) {
            return validateOrRestorePin(raced.get());
        }

        Map<String, Object> engineRow = workflowEngineClient.getProcessInstance(processInstanceId)
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + processInstanceId));
        return persistFromEngineRow(processInstanceId, engineRow, true);
    }

    @Transactional
    protected ProcessInstance persistFromEngineRow(
            String processInstanceId, Map<String, Object> engineRow, boolean enrichLiveStatus) {
        Optional<ProcessInstance> raced = processInstanceRepository.findById(processInstanceId);
        if (raced.isPresent()) {
            return validateOrRestorePin(raced.get());
        }

        Map<String, Object> variables = extractVariables(engineRow);
        String processKey = stringValue(engineRow.get("processDefinitionKey"));
        CatalogPin pin = resolveCatalogPin(variables, stringValue(engineRow.get("processDefinitionId")));

        String startUserId = firstNonBlank(
                stringValue(engineRow.get("startUserId")),
                stringValue(variables.get("initiator")),
                "system");
        String startUserName = userDisplayNameResolver.resolve(startUserId);

        String portalStatus = mapEngineStatus(stringValue(engineRow.get("status")));
        String currentNode = null;
        String currentAssignee = null;
        if (enrichLiveStatus) {
            Optional<Map<String, Object>> liveStatus = workflowEngineClient.getProcessInstanceStatus(processInstanceId);
            if (liveStatus.isPresent()) {
                currentNode = stringValue(liveStatus.get().get("nextTaskName"));
                currentAssignee = stringValue(liveStatus.get().get("nextAssignee"));
            }
        }

        ProcessInstance entity = ProcessInstance.builder()
                .id(processInstanceId)
                .processInstanceId(processInstanceId)
                .processDefinitionId(stringValue(engineRow.get("processDefinitionId")))
                .processDefinitionKey(processKey != null ? processKey : "")
                .processDefinitionName(stringValue(engineRow.get("processDefinitionName")))
                .businessKey(stringValue(engineRow.get("businessKey")))
                .initiatorId(startUserId)
                .startUserId(startUserId)
                .startUserName(startUserName)
                .status(portalStatus)
                .currentNode(currentNode)
                .currentAssignee(currentAssignee)
                .variables(variables.isEmpty() ? new HashMap<>() : variables)
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();

        ProcessVersionContext.apply(entity, variables);
        entity.setVariables(new HashMap<>(variables));
        try {
            ProcessInstance saved = processInstanceRepository.save(entity);
            log.info("Hydrated portal process instance {} from workflow engine (key={}, startUser={}, snapshot={})",
                    processInstanceId, processKey, startUserId, !enrichLiveStatus);
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            return processInstanceRepository.findById(processInstanceId)
                    .orElseThrow(() -> new PortalException("404",
                            "Process instance not found: " + processInstanceId));
        }
    }

    private static boolean hasUsableSnapshot(Map<String, Object> engineSnapshot) {
        if (engineSnapshot == null || engineSnapshot.isEmpty()) {
            return false;
        }
        return StringUtils.hasText(stringValue(engineSnapshot.get("processDefinitionKey")))
                || engineSnapshot.get("variables") instanceof Map<?, ?>;
    }

    private record CatalogPin(String catalogId, String code, String versionLabel) {}

    private ProcessInstance validateOrRestorePin(ProcessInstance instance) {
        if (StringUtils.hasText(instance.getFunctionUnitCatalogId())
                && StringUtils.hasText(instance.getFunctionUnitCode())
                && StringUtils.hasText(instance.getFunctionUnitVersionLabel())) {
            Map<String, Object> variables = instance.getVariables() == null
                    ? new HashMap<>() : new HashMap<>(instance.getVariables());
            ProcessVersionContext.apply(instance, variables);
            if (!variables.equals(instance.getVariables())) {
                recordPinInVariables(instance, variables);
            }
            return instance;
        }
        Map<String, Object> variables = instance.getVariables() == null
                ? new HashMap<>() : new HashMap<>(instance.getVariables());
        retainPersistedPin(variables, "functionUnitId", instance.getFunctionUnitCatalogId());
        retainPersistedPin(variables, "functionUnitCode", instance.getFunctionUnitCode());
        retainPersistedPin(variables, "functionUnitVersionLabel", instance.getFunctionUnitVersionLabel());
        CatalogPin pin = resolveCatalogPin(variables, instance.getProcessDefinitionId());
        instance.setFunctionUnitCatalogId(pin.catalogId());
        instance.setFunctionUnitCode(pin.code());
        instance.setFunctionUnitVersionLabel(pin.versionLabel());
        Map<String, Object> restored = new HashMap<>(variables);
        ProcessVersionContext.apply(instance, restored);
        instance.setVariables(restored);
        return processInstanceRepository.save(instance);
    }

    /**
     * Pinned before its variables carried the release (requests started before runtime versioning):
     * every pinned-or-live decision reads the variables, so the pin is merged into them once. A JSON
     * merge that leaves {@code lock_version} alone, so concurrent first reads and an in-flight save never
     * collide; keys already present win, and a save that drops the pin is healed again on the next read.
     */
    private void recordPinInVariables(ProcessInstance instance, Map<String, Object> withPin) {
        Map<String, Object> pin = new LinkedHashMap<>();
        ProcessVersionContext.apply(instance, pin);
        boolean managed = entityManager != null && entityManager.contains(instance);
        if (managed) {
            entityManager.flush();   // the caller's pending writes land first: the refresh below would drop them
        }
        try {
            jdbcTemplate.update("UPDATE up_process_instance SET variables = CAST(? AS jsonb) || COALESCE(variables, '{}'::jsonb) "
                    + "WHERE id = ?", com.platform.common.util.JsonUtils.getObjectMapper().writeValueAsString(pin), instance.getId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Release pin of " + instance.getId() + " cannot be serialised", e);
        }
        if (managed) {
            entityManager.refresh(instance);   // in sync with the row, so nothing is left to flush
        } else {
            instance.setVariables(withPin);
        }
    }

    private void retainPersistedPin(Map<String, Object> variables, String key, String value) {
        if (!StringUtils.hasText(value)) return;
        Object previous = variables.putIfAbsent(key, value);
        if (previous != null && !value.equals(String.valueOf(previous))) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_CONTEXT_MISMATCH", key);
        }
    }

    private CatalogPin resolveCatalogPin(Map<String, Object> variables, String definitionId) {
        String catalogId = stringValue(variables.get("functionUnitId"));
        String code = stringValue(variables.get("functionUnitCode"));
        String version = stringValue(variables.get("functionUnitVersionLabel"));
        if (StringUtils.hasText(catalogId) && StringUtils.hasText(code) && StringUtils.hasText(version)) {
            return new CatalogPin(catalogId, code, version);
        }
        List<CatalogPin> candidates;
        if (StringUtils.hasText(catalogId)) {
            candidates = jdbcTemplate.query("SELECT id, code, version FROM sys_function_units WHERE id = ?",
                    (rs, row) -> new CatalogPin(rs.getString("id"), rs.getString("code"), rs.getString("version")), catalogId);
        } else if (StringUtils.hasText(definitionId)) {
            candidates = jdbcTemplate.query("""
                    SELECT DISTINCT fu.id, fu.code, fu.version FROM sys_function_units fu
                    JOIN sys_function_unit_contents c ON c.function_unit_id=fu.id AND c.content_type='PROCESS'
                    LEFT JOIN act_re_procdef pd ON pd.id_ = ?
                    WHERE c.flowable_process_definition_id = ?
                       OR c.flowable_process_definition_id = concat(pd.key_, ':', pd.version_, ':', pd.id_)
                    UNION
                    SELECT DISTINCT fu.id, fu.code, fu.version FROM sys_function_units fu
                    JOIN up_process_instance pi ON pi.function_unit_catalog_id=fu.id
                    WHERE pi.process_definition_id = ?
                    """, (rs, row) -> new CatalogPin(rs.getString("id"), rs.getString("code"), rs.getString("version")),
                    definitionId, definitionId, definitionId);
        } else candidates = List.of();
        if (candidates.size() != 1) throw new com.platform.common.functionunit.FunctionUnitVersionException(
                "VERSION_RECOVERY_REQUIRED", definitionId);
        return candidates.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractVariables(Map<String, Object> engineRow) {
        Object raw = engineRow.get("variables");
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
            // This metadata is generated only by the portal's catalog-pinned start
            // path. An engine-only/hydrated instance must remain batch-disabled;
            // it must never smuggle a client or engine supplied replacement into
            // the local runtime snapshot namespace.
            out.remove(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
            out.remove(com.portal.util.SubTableBatchMetadata.DELETED_ROWS_FIELD);
            return out;
        }
        return new HashMap<>();
    }

    private static String mapEngineStatus(String engineStatus) {
        if (!StringUtils.hasText(engineStatus)) {
            return "RUNNING";
        }
        return switch (engineStatus.toLowerCase()) {
            case "completed", "ended" -> "COMPLETED";
            case "suspended" -> "SUSPENDED";
            default -> "RUNNING";
        };
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
