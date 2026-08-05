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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
            return local.get();
        }
        if (hasUsableSnapshot(engineSnapshot)) {
            return persistFromEngineRow(processInstanceId, engineSnapshot, false);
        }
        return hydrateFromEngine(processInstanceId);
    }

    @Transactional
    protected ProcessInstance hydrateFromEngine(String processInstanceId) {
        Optional<ProcessInstance> raced = processInstanceRepository.findById(processInstanceId);
        if (raced.isPresent()) {
            return raced.get();
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
            return raced.get();
        }

        Map<String, Object> variables = extractVariables(engineRow);
        String processKey = stringValue(engineRow.get("processDefinitionKey"));
        CatalogPin pin = resolveCatalogPin(variables, processKey);

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

    private CatalogPin resolveCatalogPin(Map<String, Object> variables, String processKey) {
        String catalogId = stringValue(variables.get("functionUnitId"));
        String code = stringValue(variables.get("functionUnitCode"));
        String version = stringValue(variables.get("functionUnitVersionLabel"));
        if (StringUtils.hasText(catalogId) && StringUtils.hasText(code)) {
            return new CatalogPin(catalogId, code, version != null ? version : "");
        }
        if (StringUtils.hasText(processKey)) {
            Optional<CatalogPin> active = fetchActiveCatalogForStart(processKey);
            if (active.isPresent()) {
                return active.get();
            }
        }
        if (StringUtils.hasText(catalogId)) {
            return new CatalogPin(catalogId, code != null ? code : "", version != null ? version : "");
        }
        return new CatalogPin(null, code != null ? code : "", version != null ? version : "");
    }

    private Optional<CatalogPin> fetchActiveCatalogForStart(String code) {
        try {
            String enc = URLEncoder.encode(code, StandardCharsets.UTF_8);
            String url = adminCenterUrl + "/api/v1/admin/function-units/code/" + enc + "/active-for-start";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
            String id = stringValue(payload.get("id"));
            if (!StringUtils.hasText(id)) {
                return Optional.empty();
            }
            String versionLabel = payload.get("version") != null ? String.valueOf(payload.get("version")) : "";
            String resolvedCode = stringValue(payload.get("code"));
            return Optional.of(new CatalogPin(id, resolvedCode != null ? resolvedCode : code, versionLabel));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("fetchActiveCatalogForStart failed for {}: {}", code, e.getMessage());
            return Optional.empty();
        }
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
