package com.workflow.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * The function-unit release a request runs: the {@code functionUnitId} its engine variables carry, or —
 * for instances whose variables predate runtime versioning (e.g. called by a caller deployed earlier) —
 * the release the Portal pinned on its record. Never the unit's currently active release.
 */
@Component
@RequiredArgsConstructor
public class PinnedReleaseLookup {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param functionUnitIdVariable the request's {@code functionUnitId} variable, or null when absent
     * @throws FunctionUnitVersionException VERSION_RECOVERY_REQUIRED when neither names a release
     */
    public String catalogIdOf(Object functionUnitIdVariable, String processInstanceId) {
        if (functionUnitIdVariable != null && StringUtils.hasText(functionUnitIdVariable.toString())) {
            return functionUnitIdVariable.toString();
        }
        if (StringUtils.hasText(processInstanceId)) {
            List<String> pinned = jdbcTemplate.queryForList(
                    "SELECT function_unit_catalog_id FROM up_process_instance WHERE id = ?", String.class, processInstanceId);
            if (pinned.size() == 1 && StringUtils.hasText(pinned.get(0))) {
                return pinned.get(0);
            }
        }
        throw new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", processInstanceId);
    }
}
