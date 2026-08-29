package com.admin.component;

import com.admin.exception.AdminBusinessException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Remaps View Design access rules (targetCode → this environment's id) and rejects unpaired
 * or unresolvable BU/Role rules. Admin catalog stores the remapped JSON blob.
 */
@Component
@RequiredArgsConstructor
public class ImportViewAccessValidator {

    public static final String IMPORT_UNRESOLVED_CODE = "BIZ_VIEW_ACCESS_IMPORT_UNRESOLVED";
    public static final String PAIR_ERROR_CODE = "BIZ_VIEW_ACCESS_BU_ROLE_PAIR";

    private final RoleRepository roleRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final ObjectMapper objectMapper;

    public String remapAndValidate(String viewsJson) {
        if (viewsJson == null || viewsJson.isBlank()) {
            return viewsJson;
        }
        try {
            List<Map<String, Object>> views = objectMapper.readValue(
                    viewsJson, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> view : views) {
                String viewName = view.get("viewName") != null
                        ? String.valueOf(view.get("viewName")) : "unnamed";
                Object rulesObj = view.get("accessRules");
                if (!(rulesObj instanceof List<?> rules) || rules.isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> remapped = remapRules(viewName, rules);
                assertPaired(remapped);
                view.put("accessRules", remapped);
            }
            return objectMapper.writeValueAsString(views);
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AdminBusinessException("FU_IMPORT_VIEWS_INVALID",
                    "Invalid views/main_table_views.json: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> remapRules(String viewName, List<?> rules) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) raw;
            String targetType = rule.get("targetType") instanceof String s
                    ? s.trim().toUpperCase(Locale.ROOT) : null;
            if (targetType == null || targetType.isBlank()) {
                continue;
            }
            String targetCode = stringVal(rule.get("targetCode"));
            String targetId = stringVal(rule.get("targetId"));
            if (targetCode != null) {
                String resolved = resolveId(targetType, targetCode);
                if (resolved == null) {
                    throw new AdminBusinessException(IMPORT_UNRESOLVED_CODE,
                            "View '" + viewName + "': could not resolve access rule "
                                    + targetType + " code=" + targetCode
                                    + " — ensure BU/Role codes exist in target environment");
                }
                targetId = resolved;
            } else if (targetId == null) {
                throw new AdminBusinessException(IMPORT_UNRESOLVED_CODE,
                        "View '" + viewName + "': access rule " + targetType
                                + " has neither targetCode nor targetId");
            }
            rule.put("targetId", targetId);
            out.add(rule);
        }
        return out;
    }

    private String resolveId(String targetType, String code) {
        if ("ROLE".equals(targetType)) {
            return roleRepository.findByCode(code).map(r -> r.getId()).orElse(null);
        }
        if ("BUSINESS_UNIT".equals(targetType)) {
            return businessUnitRepository.findByCode(code).map(bu -> bu.getId()).orElse(null);
        }
        return null;
    }

    private void assertPaired(List<Map<String, Object>> rules) {
        Set<String> buIds = new HashSet<>();
        Set<String> roleIds = new HashSet<>();
        for (Map<String, Object> rule : rules) {
            String type = String.valueOf(rule.get("targetType")).toUpperCase(Locale.ROOT);
            String id = stringVal(rule.get("targetId"));
            if (id == null) {
                continue;
            }
            if ("BUSINESS_UNIT".equals(type)) {
                buIds.add(id);
            } else if ("ROLE".equals(type)) {
                roleIds.add(id);
            }
        }
        if (buIds.isEmpty() && roleIds.isEmpty()) {
            return;
        }
        if (buIds.isEmpty() || roleIds.isEmpty()) {
            throw new AdminBusinessException(PAIR_ERROR_CODE,
                    "Business units and roles must both be configured, or both left empty");
        }
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
