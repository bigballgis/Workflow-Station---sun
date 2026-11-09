package com.portal.component;

import com.portal.dto.TaskInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Names the single-task delegate of the task shown on the task page: the user's name (full name, else display
 * name, else username, as the change history names people), or the names of the business unit and role. Read-only
 * display: a name that cannot be found stays null and the page shows the stored id or code.
 */
@Component
@RequiredArgsConstructor
public class TaskDelegateDisplayNames {

    private static final String USER_NAME_SQL = "SELECT COALESCE(NULLIF(TRIM(full_name), ''),"
            + " NULLIF(TRIM(display_name), ''), username) FROM sys_users WHERE id = ?";
    private static final String BU_NAME_SQL = "SELECT name FROM sys_business_units WHERE code = ?";
    private static final String ROLE_NAME_SQL = "SELECT name FROM sys_roles WHERE code = ?";

    private final JdbcTemplate jdbcTemplate;

    /** Sets the delegate's names on {@code task}; a task without a single-task delegation is left as it is. */
    public void annotate(TaskInfo task) {
        if (hasText(task.getDelegatedTo())) {
            task.setDelegatedToName(first(USER_NAME_SQL, task.getDelegatedTo().trim()));
        }
        if (hasText(task.getDelegatedBuCode()) && hasText(task.getDelegatedRoleCode())) {
            task.setDelegatedBuName(first(BU_NAME_SQL, task.getDelegatedBuCode().trim()));
            task.setDelegatedRoleName(first(ROLE_NAME_SQL, task.getDelegatedRoleCode().trim()));
        }
    }

    private String first(String sql, String key) {
        List<String> names = jdbcTemplate.queryForList(sql, String.class, key);
        return names.isEmpty() || !hasText(names.get(0)) ? null : names.get(0).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
