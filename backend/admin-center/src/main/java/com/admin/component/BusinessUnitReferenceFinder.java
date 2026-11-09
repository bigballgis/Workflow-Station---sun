package com.admin.component;

import com.admin.dto.response.ReferenceNotice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.BusinessUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a business unit is used, shown before it is changed or deleted (platform access model §8.3; notice only, the
 * existing delete checks for child units and members stay as they are). Every place names the unit by code, or by id
 * in older definitions:
 * <ul>
 *   <li>BU + Role nodes of published processes: {@code businessUnitId}, or {@code assigneeValue} of a BU_ROLE node in
 *       older BPMN — the same reading as the engine's {@code BpmnPoolBusinessUnit};</li>
 *   <li>access rules of published main table views ({@code targetType = BUSINESS_UNIT});</li>
 *   <li>function unit access and relation table access rows, and BI Audience assignments, targeting the unit;</li>
 *   <li>memberships (UBR) and the roles bound to it.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BusinessUnitReferenceFinder {

    private static final String TARGET_TYPE = "BUSINESS_UNIT";
    private static final Pattern USER_TASK =
            Pattern.compile("<(?:[\\w-]+:)?userTask\\b[^>]*?(?<!/)>([\\s\\S]*?)</(?:[\\w-]+:)?userTask>");
    private static final Set<String> ASSIGNMENT_PROPERTIES = Set.of("assigneeType", "businessUnitId", "assigneeValue");

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    public ReferenceNotice findForNotice(BusinessUnit unit) {
        Set<String> names = Set.of(unit.getId(), unit.getCode());
        List<String> others = new ArrayList<>();
        count(others, "admin.role.ref_ubr",
                "SELECT COUNT(*) FROM sys_user_business_unit_roles WHERE business_unit_id = ?", unit.getId());
        count(others, "admin.role.ref_bu_binding",
                "SELECT COUNT(*) FROM sys_business_unit_roles WHERE business_unit_id = ?", unit.getId());
        count(others, "admin.role.ref_rt_access", "SELECT COUNT(*) FROM rt_table_access"
                + " WHERE target_type = ? AND target_id IN (?, ?)", TARGET_TYPE, unit.getId(), unit.getCode());
        count(others, "admin.role.ref_bi_assignment", "SELECT COUNT(*) FROM bi_dashboard_assignment"
                + " WHERE target_type = ? AND target_id IN (?, ?)", TARGET_TYPE, unit.getId(), unit.getCode());

        Set<String> codes = new TreeSet<>(jdbcTemplate.queryForList("SELECT DISTINCT fu.code"
                        + " FROM sys_function_unit_access a JOIN sys_function_units fu ON fu.id = a.function_unit_id"
                        + " WHERE a.target_type = ? AND a.target_id IN (?, ?)",
                String.class, TARGET_TYPE, unit.getId(), unit.getCode()));
        for (PublishedContents.Content content : PublishedContents.mayName(jdbcTemplate,
                List.of(unit.getCode(), unit.getId()))) {
            if (content.process() ? assignsToUnit(content.data(), names) : grantsViewToUnit(content, names)) {
                codes.add(content.functionUnitCode());
            }
        }
        return new ReferenceNotice(PublishedContents.currentNames(jdbcTemplate, codes), others);
    }

    /** Whether a user task of the process is a pool of the unit (or names it as its fixed business unit). */
    static boolean assignsToUnit(String bpmn, Set<String> unitNames) {
        Matcher task = USER_TASK.matcher(bpmn);
        while (task.find()) {
            Map<String, String> properties = new HashMap<>();
            for (Map.Entry<String, String> property : PublishedContents.properties(task.group(1))) {
                if (ASSIGNMENT_PROPERTIES.contains(property.getKey())) {
                    properties.putIfAbsent(property.getKey(), property.getValue().trim());
                }
            }
            String unit = properties.getOrDefault("businessUnitId", "");
            if (unit.isEmpty() && "BU_ROLE".equals(properties.get("assigneeType"))) {
                unit = properties.getOrDefault("assigneeValue", "");
            }
            if (!unit.isEmpty() && unitNames.contains(unit)) {
                return true;
            }
        }
        return false;
    }

    private boolean grantsViewToUnit(PublishedContents.Content views, Set<String> unitNames) {
        for (Map<?, ?> rule : PublishedContents.viewAccessRules(objectMapper, views)) {
            if (TARGET_TYPE.equals(rule.get("targetType")) && rule.get("targetId") instanceof String id
                    && unitNames.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private void count(List<String> references, String labelKey, String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (count != null && count > 0) {
            references.add(i18nService.getMessage(labelKey, count));
        }
    }
}
