package com.portal.component;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.common.util.SafeUrlInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BPMN first-user-task parsing and assignee resolution helpers for process start.
 * Extracted from {@link ProcessComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessStartAssigneeResolver {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * Parses BPMN XML for first approval user task (skips initiator task)
     */
    Map<String, String> parseFirstUserTask(String bpmnXml, Map<String, Object> formData, String initiatorId) {
        Map<String, String> result = new HashMap<>();
        log.info("Parsing BPMN XML for first user task, initiatorId: {}", initiatorId);
        log.info("BPMN XML length: {}", bpmnXml != null ? bpmnXml.length() : 0);

        try {
            // Find all userTask tags
            int searchStart = 0;
            int taskCount = 0;

            while (true) {
                int userTaskStart = bpmnXml.indexOf("<userTask", searchStart);
                if (userTaskStart == -1) {
                    userTaskStart = bpmnXml.indexOf("<bpmn:userTask", searchStart);
                }

                if (userTaskStart == -1) {
                    break;
                }

                // Locate full userTask element including children
                int userTaskEnd = findClosingTag(bpmnXml, userTaskStart, "userTask");
                if (userTaskEnd == -1) {
                    userTaskEnd = findClosingTag(bpmnXml, userTaskStart, "bpmn:userTask");
                }
                if (userTaskEnd == -1) {
                    // Self-closing tag
                    userTaskEnd = bpmnXml.indexOf("/>", userTaskStart);
                    if (userTaskEnd == -1) {
                        break;
                    }
                    userTaskEnd += 2;
                }

                String userTaskElement = bpmnXml.substring(userTaskStart, userTaskEnd);
                taskCount++;

                // Extract task name
                String name = extractAttribute(userTaskElement, "name");

                // Parse custom:properties (aligned with DW designer and workflow-engine listeners)
                String taskDefKey = extractAttribute(userTaskElement, "id");
                String assigneeType = extractCustomProperty(userTaskElement, "assigneeType");
                String assigneeValue = extractCustomProperty(userTaskElement, "assigneeValue");
                String assigneeAnchor = extractCustomProperty(userTaskElement, "assigneeAnchor");
                String assigneeVariableExt = extractCustomProperty(userTaskElement, "assigneeVariable");
                String manualAssignVariable = extractCustomProperty(userTaskElement, "manualAssignVariable");
                String assignee = null;
                String candidateUsers = null;

                if (assigneeType != null) {
                    log.info("Found assigneeType: {} for task: {}", assigneeType, name);

                    String normalizedType = assigneeType.toUpperCase(Locale.ROOT);
                    switch (normalizedType) {
                        case "INITIATOR":
                        case "PROCESS_INITIATOR":
                            assignee = initiatorId;
                            break;
                        case "ENTITY_MANAGER":
                            if (isLastTaskAssigneeAnchor(assigneeAnchor)) {
                                result.put("assigneeType", assigneeType);
                                result.put("requiresClaim", "true");
                            } else {
                                assignee = getEntityManager(initiatorId);
                            }
                            break;
                        case "FUNCTION_MANAGER":
                        case "FUNCTIONAL_MANAGER":
                            if (isLastTaskAssigneeAnchor(assigneeAnchor)) {
                                result.put("assigneeType", assigneeType);
                                result.put("requiresClaim", "true");
                            } else {
                                assignee = getFunctionManager(initiatorId);
                            }
                            break;
                        case "HIERARCHY_ROLE":
                        case "BU_ROLE":
                        case "FIXED_BU_ROLE":
                        case "CURRENT_BU_ROLE":
                        case "CURRENT_PARENT_BU_ROLE":
                        case "INITIATOR_BU_ROLE":
                        case "INITIATOR_PARENT_BU_ROLE":
                            result.put("assigneeType", assigneeType);
                            result.put("requiresClaim", "true");
                            break;
                        case "MANUAL_ASSIGN":
                            result.put("assigneeType", assigneeType);
                            String userVar = (manualAssignVariable != null && !manualAssignVariable.isBlank())
                                    ? manualAssignVariable.trim()
                                    : "manualAssignee_" + (taskDefKey != null ? taskDefKey : "");
                            if (formData != null && formData.containsKey(userVar)) {
                                Object v = formData.get(userVar);
                                if (v != null) {
                                    assignee = firstUserIdFromCommaList(String.valueOf(v).trim());
                                }
                            }
                            if (assignee == null) {
                                result.put("requiresClaim", "true");
                            }
                            break;
                        case "ASSIGNEE_FROM_VARIABLE":
                            result.put("assigneeType", assigneeType);
                            if (assigneeVariableExt != null && !assigneeVariableExt.isBlank()
                                    && formData != null && formData.containsKey(assigneeVariableExt.trim())) {
                                Object v = formData.get(assigneeVariableExt.trim());
                                if (v != null) {
                                    assignee = firstUserIdFromCommaList(String.valueOf(v).trim());
                                }
                            }
                            if (assignee == null) {
                                result.put("requiresClaim", "true");
                            }
                            break;
                        case "ELEMENT_VARIABLE":
                            result.put("assigneeType", assigneeType);
                            result.put("requiresClaim", "true");
                            break;
                        case "BU_UNBOUNDED_ROLE":
                            result.put("assigneeType", assigneeType);
                            result.put("requiresClaim", "true");
                            break;
                        case "DEPT_OTHERS":
                            result.put("assigneeType", "DEPT_OTHERS");
                            result.put("requiresClaim", "true");
                            break;
                        case "PARENT_DEPT":
                            result.put("assigneeType", "PARENT_DEPT");
                            result.put("requiresClaim", "true");
                            break;
                        case "FIXED_DEPT":
                            result.put("assigneeType", "FIXED_DEPT");
                            result.put("assigneeValue", assigneeValue);
                            result.put("requiresClaim", "true");
                            break;
                        case "VIRTUAL_GROUP":
                            result.put("assigneeType", "VIRTUAL_GROUP");
                            result.put("assigneeValue", assigneeValue);
                            result.put("candidateGroups", assigneeValue);
                            result.put("requiresClaim", "true");
                            break;
                        default:
                            log.debug("assigneeType {} not in converged switch; trying legacy resolver", assigneeType);
                            assignee = resolveLegacyAssigneeType(assigneeType, assigneeValue, initiatorId);
                    }
                } else {
                    // Fall back to standard attribute parsing
                    assignee = extractAttribute(userTaskElement, "camunda:assignee");
                    if (assignee == null) {
                        assignee = extractAttribute(userTaskElement, "flowable:assignee");
                    }
                    if (assignee == null) {
                        assignee = extractAttribute(userTaskElement, "assignee");
                    }
                }

                // Skip initiator task (first task is usually initiator form)
                boolean isInitiatorTask = "initiator".equalsIgnoreCase(assigneeType)
                    || "INITIATOR".equalsIgnoreCase(assigneeType)
                    || "PROCESS_INITIATOR".equalsIgnoreCase(assigneeType)
                    || (assignee != null && (assignee.equals("${initiator}") || assignee.equals(initiatorId)));

                if (!isInitiatorTask || taskCount > 1) {
                    // First task requiring approval
                    if (name != null) {
                        result.put("name", name);
                    }

                    // Resolve assignee variable if not yet parsed
                    if (assignee != null) {
                        if (assignee.startsWith("${") && assignee.endsWith("}")) {
                            String varName = assignee.substring(2, assignee.length() - 1);
                            assignee = resolveProcessVariable(varName, formData, initiatorId);
                        }
                        result.put("assignee", assignee);
                    }

                    // Set candidate users
                    if (candidateUsers != null) {
                        result.put("candidateUsers", candidateUsers);
                        if (result.get("assignee") == null) {
                            result.put("assignee", candidateUsers.split(",")[0]);
                        }
                    }

                    // Check standard candidateUsers (multi-instance sign-off)
                    if (candidateUsers == null) {
                        candidateUsers = extractAttribute(userTaskElement, "flowable:candidateUsers");
                        if (candidateUsers == null) {
                            candidateUsers = extractAttribute(userTaskElement, "camunda:candidateUsers");
                        }
                        if (candidateUsers != null) {
                            List<String> resolvedCandidates = resolveCandidateUsers(candidateUsers, formData, initiatorId);
                            if (!resolvedCandidates.isEmpty()) {
                                result.put("candidateUsers", String.join(",", resolvedCandidates));
                                if (result.get("assignee") == null) {
                                    result.put("assignee", resolvedCandidates.get(0));
                                }
                            }
                        }
                    }

                    // Check candidateGroups (group task)
                    String candidateGroups = extractAttribute(userTaskElement, "flowable:candidateGroups");
                    if (candidateGroups == null) {
                        candidateGroups = extractAttribute(userTaskElement, "camunda:candidateGroups");
                    }
                    if (candidateGroups != null && result.get("assignee") == null) {
                        result.put("candidateGroups", candidateGroups);
                    }

                    break;
                }

                searchStart = userTaskEnd;
            }
        } catch (Exception e) {
            log.warn("Failed to parse BPMN for first user task: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Extracts property value from custom:properties
     */
    private String extractCustomProperty(String element, String propertyName) {
        try {
            // Find custom:property tags
            String searchPattern = "name=\"" + propertyName + "\"";
            int propIndex = element.indexOf(searchPattern);
            if (propIndex == -1) {
                return null;
            }

            // Read value attribute on property tag
            int lineStart = element.lastIndexOf("<", propIndex);
            int lineEnd = element.indexOf("/>", propIndex);
            if (lineEnd == -1) {
                lineEnd = element.indexOf(">", propIndex);
            }

            if (lineStart == -1 || lineEnd == -1) {
                return null;
            }

            String propertyTag = element.substring(lineStart, lineEnd);
            return extractAttribute(propertyTag, "value");
        } catch (Exception e) {
            log.warn("Failed to extract custom property {}: {}", propertyName, e.getMessage());
            return null;
        }
    }

    /**
     * Finds closing tag position
     */
    private int findClosingTag(String xml, int startIndex, String tagName) {
        String closingTag = "</" + tagName + ">";
        int closingIndex = xml.indexOf(closingTag, startIndex);
        if (closingIndex != -1) {
            return closingIndex + closingTag.length();
        }
        return -1;
    }

    private static boolean isLastTaskAssigneeAnchor(String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return false;
        }
        String u = anchor.trim().toUpperCase(Locale.ROOT);
        return "LAST_TASK_ASSIGNEE".equals(u) || "LAST".equals(u) || "CURRENT".equals(u);
    }

    private static String firstUserIdFromCommaList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) {
            return null;
        }
        int idx = commaSeparated.indexOf(',');
        return idx < 0 ? commaSeparated : commaSeparated.substring(0, idx).trim();
    }

    /**
     * Parses legacy assignment type (backward compatible)
     */
    private String resolveLegacyAssigneeType(String assigneeType, String assigneeValue, String initiatorId) {
        return switch (assigneeType.toLowerCase()) {
            case "initiator" -> initiatorId;
            case "manager", "entitymanager" -> getEntityManager(initiatorId);
            case "functionmanager" -> getFunctionManager(initiatorId);
            case "user" -> assigneeValue;
            default -> null;
        };
    }

    /**
     * Resolves process variables
     */
    private String resolveProcessVariable(String varName, Map<String, Object> formData, String initiatorId) {
        // Check form data first
        if (formData != null && formData.containsKey(varName)) {
            return String.valueOf(formData.get(varName));
        }

        // Handle special variables (seven standard assignment types)
        return switch (varName) {
            case "initiator" -> initiatorId;
            case "entityManager" -> getEntityManager(initiatorId);
            case "functionManager" -> getFunctionManager(initiatorId);
            default -> null;
        };
    }

    /**
     * Resolves candidate user expressions (multiple vars, e.g. ${entityManager},${functionManager})
     */
    private List<String> resolveCandidateUsers(String candidateUsersExpr, Map<String, Object> formData, String initiatorId) {
        List<String> result = new ArrayList<>();

        if (candidateUsersExpr == null || candidateUsersExpr.isEmpty()) {
            return result;
        }

        // Split multiple candidate user expressions
        String[] expressions = candidateUsersExpr.split(",");
        for (String expr : expressions) {
            expr = expr.trim();
            if (expr.startsWith("${") && expr.endsWith("}")) {
                String varName = expr.substring(2, expr.length() - 1);
                String resolved = resolveProcessVariable(varName, formData, initiatorId);
                if (resolved != null && !resolved.isEmpty()) {
                    result.add(resolved);
                } else {
                    log.warn("Failed to resolve candidate user variable: {}", varName);
                }
            } else if (!expr.isEmpty()) {
                // Literal user ID
                result.add(expr);
            }
        }

        return result;
    }

    /**
     * Resolves initiator entity manager
     */
    private String getEntityManager(String initiatorId) {
        try {
            // Try user ID first
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(initiatorId);
            log.info("Fetching user info for entity manager from: {}", userUrl);

            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = ApiResponseBodyUnwrap.unwrapDataMap(response);
            } catch (Exception e) {
                log.warn("Failed to get user by ID {}, trying by username: {}", initiatorId, e.getMessage());
            }

            // If lookup by ID fails, try username
            if (userInfo == null || userInfo.get("entityManagerId") == null) {
                String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + SafeUrlInput.encodeQueryValue(initiatorId) + "&size=1";
                log.info("Searching user by username from: {}", searchUrl);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
                    List<Map<String, Object>> users = searchResponse != null
                            ? ApiResponseBodyUnwrap.normalizeToListOfMaps(searchResponse)
                            : Collections.emptyList();
                    if (!users.isEmpty()) {
                        String foundUserId = (String) users.get(0).get("id");
                        String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(foundUserId);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                        userInfo = ApiResponseBodyUnwrap.unwrapDataMap(detailResponse);
                    }
                } catch (Exception e) {
                    log.warn("Failed to search user by username {}: {}", initiatorId, e.getMessage());
                }
            }

            if (userInfo == null || userInfo.get("entityManagerId") == null) {
                log.warn("User {} has no entity manager", initiatorId);
                return null;
            }

            String entityManagerId = (String) userInfo.get("entityManagerId");
            log.info("Found entity manager {} for user {}", entityManagerId, initiatorId);
            return entityManagerId;

        } catch (Exception e) {
            log.error("Failed to get entity manager for {}: {}", initiatorId, e.getMessage());
            return null;
        }
    }

    /**
     * Resolves initiator function manager
     */
    private String getFunctionManager(String initiatorId) {
        try {
            // Try user ID first
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(initiatorId);
            log.info("Fetching user info for function manager from: {}", userUrl);

            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = ApiResponseBodyUnwrap.unwrapDataMap(response);
            } catch (Exception e) {
                log.warn("Failed to get user by ID {}, trying by username: {}", initiatorId, e.getMessage());
            }

            // If lookup by ID fails, try username
            if (userInfo == null || userInfo.get("functionManagerId") == null) {
                String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + SafeUrlInput.encodeQueryValue(initiatorId) + "&size=1";
                log.info("Searching user by username from: {}", searchUrl);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
                    List<Map<String, Object>> users = searchResponse != null
                            ? ApiResponseBodyUnwrap.normalizeToListOfMaps(searchResponse)
                            : Collections.emptyList();
                    if (!users.isEmpty()) {
                        String foundUserId = (String) users.get(0).get("id");
                        String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(foundUserId);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                        userInfo = ApiResponseBodyUnwrap.unwrapDataMap(detailResponse);
                    }
                } catch (Exception e) {
                    log.warn("Failed to search user by username {}: {}", initiatorId, e.getMessage());
                }
            }

            if (userInfo == null || userInfo.get("functionManagerId") == null) {
                log.warn("User {} has no function manager", initiatorId);
                return null;
            }

            String functionManagerId = (String) userInfo.get("functionManagerId");
            log.info("Found function manager {} for user {}", functionManagerId, initiatorId);
            return functionManagerId;

        } catch (Exception e) {
            log.error("Failed to get function manager for {}: {}", initiatorId, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts attribute value from XML tag
     */
    static String extractAttribute(String tag, String attrName) {
        String pattern1 = attrName + "=\"";
        int start = tag.indexOf(pattern1);
        if (start != -1) {
            start += pattern1.length();
            int end = tag.indexOf("\"", start);
            if (end != -1) {
                return tag.substring(start, end);
            }
        }
        // Try single quotes
        String pattern2 = attrName + "='";
        start = tag.indexOf(pattern2);
        if (start != -1) {
            start += pattern2.length();
            int end = tag.indexOf("'", start);
            if (end != -1) {
                return tag.substring(start, end);
            }
        }
        return null;
    }
}
