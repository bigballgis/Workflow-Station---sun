package com.portal.component;

import com.portal.dto.AuditFunctionUnitItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which function units may the current user review?
 *
 * <p>Drives both the audit menu (hidden entirely when this returns nothing) and
 * the unit switcher inside it. Kept apart from
 * {@link FunctionUnitAccessComponent#filterAccessibleFunctionUnits} on purpose:
 * that one answers "may start", this one answers "may review", and conflating
 * them would let a reviewer launch requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitAuditScopeComponent {

    private final JdbcTemplate jdbcTemplate;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;

    /**
     * Deployed units the user holds an audit grant on, ordered by name.
     * Returns empty rather than throwing when nothing is granted — an empty
     * audit scope is the normal state for most users, not an error.
     */
    public List<AuditFunctionUnitItem> listAuditableFunctionUnits(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> units;
        try {
            units = jdbcTemplate.queryForList("""
                    SELECT DISTINCT fu.id AS id, fu.code AS code, fu.name AS name
                    FROM dw_function_units fu
                    WHERE fu.code IS NOT NULL
                    ORDER BY fu.name
                    """);
        } catch (Exception ex) {
            log.error("Failed to list function units for audit scope of user {}: {}", userId, ex.getMessage(), ex);
            return List.of();
        }

        List<AuditFunctionUnitItem> auditable = new ArrayList<>();
        for (Map<String, Object> unit : units) {
            String code = (String) unit.get("code");
            if (code == null || code.isBlank()) {
                continue;
            }
            if (!functionUnitAccessComponent.canAuditFunctionUnit(userId, code)) {
                continue;
            }
            auditable.add(AuditFunctionUnitItem.builder()
                    .functionUnitId(String.valueOf(unit.get("id")))
                    .functionUnitCode(code)
                    .functionUnitName((String) unit.get("name"))
                    .build());
        }
        return auditable;
    }

    /** Guard for the audit list/detail endpoints. */
    public boolean canAudit(String userId, String functionUnitCode) {
        return functionUnitAccessComponent.canAuditFunctionUnit(userId, functionUnitCode);
    }
}
