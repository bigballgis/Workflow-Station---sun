package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.UserPortalAuditRecord;
import com.portal.entity.ChangeHistory;
import com.portal.entity.ProcessInstance;
import com.platform.security.entity.User;
import com.platform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Display enrichment for Admin Center User Portal audit list
 * (Request ID / FU name / stage / file original name / sub-table display / user display).
 * Failures degrade display only — never block the audit query.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPortalAuditEnricher {

    private static final Pattern LOOKS_LIKE_UUID = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern UPLOAD_FILE_PATH = Pattern.compile(
            "(?i)/(?:api/v\\d+/)?upload/files/([^/?#]+)");
    private static final Set<String> ASSIGNEE_VALUE_FIELDS = Set.of(
            "assignee", "assigneeuserid", "assigneeid");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final RequestIdEnricher requestIdEnricher;

    public record StageNameMaps(
            Map<String, String> taskInstanceIdToName,
            Map<String, String> taskDefinitionKeyToName) {
    }

    /**
     * Batch-load task display names from Flowable historic tasks (same DB).
     */
    public Map<String, StageNameMaps> resolveStageNamesFromDb(Set<String> processInstanceIds) {
        Map<String, StageNameMaps> result = new HashMap<>();
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return result;
        }
        Map<String, Map<String, String>> byTaskId = new HashMap<>();
        Map<String, Map<String, String>> byDefKey = new HashMap<>();
        try {
            String placeholders = processInstanceIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<String> args = new ArrayList<>(processInstanceIds);
            RowCallbackHandler handler = rs -> {
                String piId = rs.getString("PROC_INST_ID_");
                String name = rs.getString("NAME_");
                if (piId == null || name == null || name.isBlank()) {
                    return;
                }
                String trimmed = name.trim();
                String taskId = rs.getString("ID_");
                String defKey = rs.getString("TASK_DEF_KEY_");
                if (taskId != null && !taskId.isBlank()) {
                    byTaskId.computeIfAbsent(piId, k -> new HashMap<>()).putIfAbsent(taskId, trimmed);
                }
                if (defKey != null && !defKey.isBlank()) {
                    byDefKey.computeIfAbsent(piId, k -> new HashMap<>()).putIfAbsent(defKey, trimmed);
                }
            };
            jdbcTemplate.query(
                    "SELECT ID_, NAME_, TASK_DEF_KEY_, PROC_INST_ID_ FROM ACT_HI_TASKINST "
                            + "WHERE PROC_INST_ID_ IN (" + placeholders + ")",
                    handler,
                    args.toArray());
        } catch (Exception e) {
            // FALLBACK(ux): stage labels unavailable — admin/portal history still renders stageId
            log.debug("Could not resolve stage names from ACT_HI_TASKINST: {}", e.getMessage());
        }
        for (String piId : processInstanceIds) {
            result.put(piId, new StageNameMaps(
                    byTaskId.getOrDefault(piId, Map.of()),
                    byDefKey.getOrDefault(piId, Map.of())));
        }
        return result;
    }

    public List<UserPortalAuditRecord> toAuditRecords(
            List<ChangeHistory> entities,
            Map<String, ProcessInstance> piMap) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Set<String> processInstanceIds = entities.stream()
                .map(ChangeHistory::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> userIds = new HashSet<>();
        entities.forEach(e -> {
            if (e.getUserId() != null) {
                userIds.add(e.getUserId());
            }
            collectUserIdCandidates(e.getOldValue(), userIds);
            collectUserIdCandidates(e.getNewValue(), userIds);
        });
        Map<String, String> usernameMap = resolveUserDisplayMap(userIds);
        Map<String, StageNameMaps> stageMapsByPi = resolveStageNamesFromDb(processInstanceIds);

        Set<String> fuCodes = piMap.values().stream()
                .map(ProcessInstance::getFunctionUnitCode)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> fuNameByCode = resolveFunctionUnitNames(fuCodes);
        RequestIdEnricher.SpecCache requestIdSpecs = requestIdEnricher.resolveSpecs(fuCodes);

        Set<String> subTableKeys = entities.stream()
                .map(ChangeHistory::getSubTableName)
                .map(ChangeHistoryComponent::normalizeSubTableNameForHistory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> subTableDisplayByKey = resolveSubTableDisplayNames(subTableKeys);

        Set<String> storedFileNames = new HashSet<>();
        entities.forEach(e -> {
            collectStoredFileName(e.getOldValue(), storedFileNames);
            collectStoredFileName(e.getNewValue(), storedFileNames);
        });
        Map<String, String> originalFileNameByStored = resolveOriginalFileNames(storedFileNames);

        return entities.stream()
                .map(entity -> toAuditRecord(
                        entity,
                        piMap.get(entity.getProcessInstanceId()),
                        usernameMap,
                        stageMapsByPi.getOrDefault(
                                entity.getProcessInstanceId(),
                                new StageNameMaps(Map.of(), Map.of())),
                        fuNameByCode,
                        subTableDisplayByKey,
                        originalFileNameByStored,
                        requestIdSpecs))
                .collect(Collectors.toList());
    }

    private UserPortalAuditRecord toAuditRecord(
            ChangeHistory entity,
            ProcessInstance pi,
            Map<String, String> usernameMap,
            StageNameMaps stageNames,
            Map<String, String> fuNameByCode,
            Map<String, String> subTableDisplayByKey,
            Map<String, String> originalFileNameByStored,
            RequestIdEnricher.SpecCache requestIdSpecs) {
        String stageName = null;
        if (entity.getTaskInstanceId() != null && !entity.getTaskInstanceId().isBlank()) {
            stageName = stageNames.taskInstanceIdToName().get(entity.getTaskInstanceId());
        }
        if (stageName == null && entity.getStageId() != null && !entity.getStageId().isBlank()) {
            stageName = stageNames.taskDefinitionKeyToName().get(entity.getStageId());
        }
        String formName = pi != null ? pi.getProcessDefinitionName() : null;
        String subTable = entity.getSubTableName();
        String subTableKey = ChangeHistoryComponent.normalizeSubTableNameForHistory(subTable);
        String subTableDisplay = subTableKey != null
                ? subTableDisplayByKey.getOrDefault(subTableKey, subTable)
                : subTable;
        boolean hasSubTable = subTable != null && !subTable.isBlank();
        String fuCode = pi != null ? pi.getFunctionUnitCode() : null;
        String requestId = null;
        if (pi != null && fuCode != null) {
            Map<String, Object> variables = resolveProcessVariables(pi);
            requestId = requestIdEnricher.buildRequestId(requestIdSpecs, fuCode, variables);
        }
        return UserPortalAuditRecord.builder()
                .id(entity.getId())
                .processInstanceId(entity.getProcessInstanceId())
                .taskInstanceId(entity.getTaskInstanceId())
                .stageId(entity.getStageId())
                .stageName(stageName)
                .userId(entity.getUserId())
                .userName(usernameMap.getOrDefault(entity.getUserId(), entity.getUserId()))
                .timestamp(entity.getTimestamp())
                .fieldName(entity.getFieldName())
                .oldValue(formatAuditDisplayValue(
                        entity.getFieldName(), entity.getOldValue(), usernameMap, originalFileNameByStored))
                .newValue(formatAuditDisplayValue(
                        entity.getFieldName(), entity.getNewValue(), usernameMap, originalFileNameByStored))
                .changeType(entity.getChangeType() != null ? entity.getChangeType().name() : null)
                .subTableName(subTable)
                .subTableDisplayName(subTableDisplay)
                .rowIdentifier(entity.getRowIdentifier())
                .functionUnitCode(fuCode)
                .functionUnitName(fuCode != null ? fuNameByCode.get(fuCode) : null)
                .formName(formName)
                .tableName(hasSubTable ? (subTableDisplay != null ? subTableDisplay : subTable) : formName)
                .processTitle(buildProcessTitle(pi, entity.getProcessInstanceId(), requestId))
                .build();
    }

    /**
     * Priority: Request ID → process title → businessKey → definitionName · shortId.
     */
    static String buildProcessTitle(ProcessInstance pi, String processInstanceId, String requestId) {
        String fromRequestId = trimToNull(requestId);
        if (fromRequestId != null) {
            return fromRequestId;
        }
        if (pi != null) {
            String title = trimToNull(pi.getTitle());
            if (title != null) {
                return title;
            }
            String businessKey = trimToNull(pi.getBusinessKey());
            if (businessKey != null) {
                return businessKey;
            }
            String defName = trimToNull(pi.getProcessDefinitionName());
            String shortId = shortProcessId(processInstanceId != null ? processInstanceId : pi.getId());
            if (defName != null && shortId != null) {
                return defName + " · " + shortId;
            }
            if (defName != null) {
                return defName;
            }
        }
        return shortProcessId(processInstanceId);
    }

    private Map<String, Object> resolveProcessVariables(ProcessInstance pi) {
        if (pi.getVariables() != null && !pi.getVariables().isEmpty()) {
            return pi.getVariables();
        }
        String json = pi.getVariablesJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // FALLBACK(ux): missing variables → skip Request ID, keep other title fallbacks
            log.debug("Could not parse variables_json for process {}: {}", pi.getId(), e.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> resolveUserDisplayMap(Set<String> userIds) {
        Map<String, String> usernameMap = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return usernameMap;
        }
        userRepository.findAllById(userIds).forEach(u -> {
            if (u != null && u.getId() != null) {
                usernameMap.put(u.getId(), displayNameForUser(u));
            }
        });
        return usernameMap;
    }

    private Map<String, String> resolveOriginalFileNames(Set<String> storedNames) {
        Map<String, String> map = new HashMap<>();
        if (storedNames == null || storedNames.isEmpty()) {
            return map;
        }
        try {
            String placeholders = storedNames.stream().map(s -> "?").collect(Collectors.joining(","));
            List<String> args = new ArrayList<>(storedNames);
            RowCallbackHandler handler = rs -> {
                String stored = rs.getString("stored_name");
                String original = rs.getString("original_name");
                if (stored != null && original != null && !original.isBlank()) {
                    map.put(stored, original.trim());
                }
            };
            jdbcTemplate.query(
                    "SELECT stored_name, original_name FROM dw_uploaded_files WHERE stored_name IN ("
                            + placeholders + ")",
                    handler,
                    args.toArray());
        } catch (Exception e) {
            // FALLBACK(ux): show stored file name when original_name lookup fails
            log.debug("Could not resolve original file names: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, String> resolveFunctionUnitNames(Set<String> codes) {
        Map<String, String> map = new HashMap<>();
        if (codes == null || codes.isEmpty()) {
            return map;
        }
        try {
            String placeholders = codes.stream().map(c -> "?").collect(Collectors.joining(","));
            List<String> args = new ArrayList<>(codes);
            RowCallbackHandler handler = rs -> {
                String code = rs.getString("code");
                String name = rs.getString("name");
                if (code != null && name != null && !name.isBlank()) {
                    map.putIfAbsent(code, name.trim());
                }
            };
            jdbcTemplate.query(
                    "SELECT code, name FROM sys_function_units WHERE code IN (" + placeholders + ")",
                    handler,
                    args.toArray());
        } catch (Exception e) {
            // FALLBACK(ux): show functionUnitCode when name lookup fails
            log.debug("Could not resolve function unit names: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, String> resolveSubTableDisplayNames(Set<String> normalizedKeys) {
        Map<String, String> map = new HashMap<>();
        if (normalizedKeys == null || normalizedKeys.isEmpty()) {
            return map;
        }
        try {
            RowCallbackHandler handler = rs -> {
                String tableName = rs.getString("table_name");
                String display = rs.getString("display_name");
                String key = ChangeHistoryComponent.normalizeSubTableNameForHistory(tableName);
                if (key != null && normalizedKeys.contains(key) && display != null && !display.isBlank()) {
                    map.putIfAbsent(key, display.trim());
                }
            };
            jdbcTemplate.query(
                    """
                            SELECT table_name, table_display_name AS display_name
                            FROM dw_table_definitions
                            WHERE table_display_name IS NOT NULL AND table_display_name <> ''
                            UNION ALL
                            SELECT table_name, display_name
                            FROM rt_table_definitions
                            WHERE display_name IS NOT NULL AND display_name <> ''
                            """,
                    handler);
        } catch (Exception e) {
            // FALLBACK(ux): show raw subTableName when display_name lookup fails
            log.debug("Could not resolve sub-table display names: {}", e.getMessage());
        }
        return map;
    }

    private String formatAuditDisplayValue(
            String fieldName,
            String raw,
            Map<String, String> usernameMap,
            Map<String, String> originalFileNameByStored) {
        if (raw == null) {
            return null;
        }
        String stored = extractStoredFileName(raw);
        if (stored != null) {
            return originalFileNameByStored.getOrDefault(stored, stored);
        }
        if (isAssigneeValueField(fieldName) || LOOKS_LIKE_UUID.matcher(raw.trim()).matches()) {
            String userId = extractBareUserId(raw);
            if (userId != null && usernameMap.containsKey(userId)) {
                return usernameMap.get(userId);
            }
        }
        return raw;
    }

    private static void collectUserIdCandidates(String raw, Set<String> out) {
        String id = extractBareUserId(raw);
        if (id != null) {
            out.add(id);
        }
    }

    private static String extractBareUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (LOOKS_LIKE_UUID.matcher(trimmed).matches()) {
            return trimmed;
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            int idIdx = trimmed.indexOf("\"id\"");
            if (idIdx < 0) {
                idIdx = trimmed.indexOf("\"userId\"");
            }
            if (idIdx >= 0) {
                int colon = trimmed.indexOf(':', idIdx);
                int q1 = trimmed.indexOf('"', colon + 1);
                int q2 = q1 >= 0 ? trimmed.indexOf('"', q1 + 1) : -1;
                if (q1 >= 0 && q2 > q1) {
                    String candidate = trimmed.substring(q1 + 1, q2);
                    if (LOOKS_LIKE_UUID.matcher(candidate).matches()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static void collectStoredFileName(String raw, Set<String> out) {
        String stored = extractStoredFileName(raw);
        if (stored != null) {
            out.add(stored);
        }
    }

    private static String extractStoredFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var m = UPLOAD_FILE_PATH.matcher(raw.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String displayNameForUser(User u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) {
            return u.getFullName().trim();
        }
        if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) {
            return u.getDisplayName().trim();
        }
        return u.getUsername();
    }

    private static boolean isAssigneeValueField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = fieldName.trim().toLowerCase().replace("_", "");
        return ASSIGNEE_VALUE_FIELDS.contains(normalized);
    }

    private static String shortProcessId(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        String id = processInstanceId.trim();
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
