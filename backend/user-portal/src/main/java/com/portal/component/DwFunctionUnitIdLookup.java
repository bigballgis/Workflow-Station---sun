package com.portal.component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Resolves {@code dw_function_units.id} from the Function Unit code pinned at process start.
 *
 * <p>{@code dw_function_units.code} is unique, so this is the live Designer row — the same id
 * {@code dw_process_definitions.function_unit_version_id} already stores. Writing it onto
 * {@code up_process_instance.function_unit_version_id} lets {@link ChangeHistoryBpmnFormResolver}
 * take its preferred join. It does <em>not</em> freeze Task Form JSON: Admin catalog ids are
 * UUIDs and cannot fit this Long column. Catalog-pinned instances freeze form JSON via
 * {@link TaskFormDefinitionLoader} reading {@code sys_function_unit_contents}; unpinned
 * instances still load latest {@code dw_form_definitions} by code.
 */
final class DwFunctionUnitIdLookup {

    private DwFunctionUnitIdLookup() {
    }

    static Long findIdByCode(JdbcTemplate jdbcTemplate, String functionUnitCode) {
        if (jdbcTemplate == null || !StringUtils.hasText(functionUnitCode)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM dw_function_units WHERE code = ?",
                Long.class,
                functionUnitCode.trim());
        if (ids.size() != 1) {
            return null;
        }
        return ids.get(0);
    }
}
