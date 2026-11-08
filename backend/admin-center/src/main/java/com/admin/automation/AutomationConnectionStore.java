package com.admin.automation;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** JDBC access for automation identity and published scope. */
@Component
public class AutomationConnectionStore {

    private final JdbcTemplate jdbc;

    public AutomationConnectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> activeUserLabel(String userId) {
        List<String> labels = jdbc.query(
                "SELECT COALESCE(display_name, full_name, username) FROM sys_users WHERE id = ? AND deleted = false",
                (rs, row) -> rs.getString(1), userId);
        return labels.stream().findFirst();
    }

    public List<AccountRow> listAccounts() {
        return jdbc.query("""
                SELECT user_id, display_name, platform_default
                FROM ac_service_accounts ORDER BY display_name
                """, (rs, row) -> new AccountRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
    }

    public Optional<AccountRow> findAccount(String userId) {
        List<AccountRow> rows = jdbc.query("""
                SELECT user_id, display_name, platform_default
                FROM ac_service_accounts WHERE user_id = ?
                """, (rs, row) -> new AccountRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)), userId);
        return rows.stream().findFirst();
    }

    public Optional<AccountRow> platformDefault() {
        List<AccountRow> rows = jdbc.query("""
                SELECT user_id, display_name, platform_default
                FROM ac_service_accounts WHERE platform_default = TRUE
                """, (rs, row) -> new AccountRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
        return rows.stream().findFirst();
    }

    public void saveAccount(String userId, String displayName, boolean platformDefault) {
        if (platformDefault) {
            jdbc.update("""
                    UPDATE ac_service_accounts SET platform_default = FALSE
                    WHERE platform_default = TRUE AND user_id <> ?
                    """, userId);
        }
        jdbc.update("""
                INSERT INTO ac_service_accounts (user_id, display_name, platform_default)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    platform_default = CASE
                        WHEN ? THEN EXCLUDED.platform_default
                        ELSE ac_service_accounts.platform_default
                    END
                """, userId, displayName, platformDefault, platformDefault);
    }

    public Optional<String> boundUserId(String workspaceId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT service_account_user_id FROM ac_automation_runtime_connection WHERE workspace_id = ?",
                    String.class, workspaceId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void bind(String workspaceId, String userId) {
        jdbc.update("""
                INSERT INTO ac_automation_runtime_connection (workspace_id, service_account_user_id)
                VALUES (?, ?)
                ON CONFLICT (workspace_id) DO UPDATE
                SET service_account_user_id = EXCLUDED.service_account_user_id,
                    updated_at = CURRENT_TIMESTAMP
                """, workspaceId, userId);
    }

    public void unbind(String workspaceId) {
        jdbc.update("DELETE FROM ac_automation_runtime_connection WHERE workspace_id = ?", workspaceId);
    }

    public List<String> enabledCatalogIds(long functionUnitId) {
        return jdbc.query("""
                SELECT sys.id FROM sys_function_units sys
                JOIN dw_function_units dw ON dw.code = sys.code
                WHERE dw.id = ? AND sys.enabled = TRUE
                """, (rs, row) -> rs.getString(1), functionUnitId);
    }

    public boolean assignedToGroup(long functionUnitId, String groupId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dw_function_unit_dev_groups
                WHERE function_unit_id = ? AND virtual_group_id = ?
                """, Integer.class, functionUnitId, groupId);
        return count != null && count > 0;
    }

    public void updateFlowIdentity(String flowId, String serviceAccountUserId) {
        jdbc.update("""
                UPDATE ac_automation_flow_scope
                SET service_account_user_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE flow_id = ?
                """, serviceAccountUserId, flowId);
    }

    public void saveScope(String flowId, String workspaceId, long functionUnitId,
                          String catalogId, String capabilities, String serviceAccountUserId) {
        jdbc.update("""
                INSERT INTO ac_automation_flow_scope
                    (flow_id, workspace_id, function_unit_id, catalog_id, capabilities, service_account_user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (flow_id) DO UPDATE SET
                    workspace_id = EXCLUDED.workspace_id,
                    function_unit_id = EXCLUDED.function_unit_id,
                    catalog_id = EXCLUDED.catalog_id,
                    capabilities = EXCLUDED.capabilities,
                    service_account_user_id = EXCLUDED.service_account_user_id,
                    updated_at = CURRENT_TIMESTAMP
                """, flowId, workspaceId, functionUnitId, catalogId, capabilities, serviceAccountUserId);
    }

    public Optional<ScopeRow> findScope(String flowId) {
        List<ScopeRow> rows = jdbc.query("""
                SELECT flow_id, workspace_id, function_unit_id, catalog_id, capabilities, service_account_user_id
                FROM ac_automation_flow_scope WHERE flow_id = ?
                """, (rs, row) -> new ScopeRow(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getString(6)), flowId);
        return rows.stream().findFirst();
    }

    public record AccountRow(String userId, String displayName, boolean platformDefault) {
    }

    public record ScopeRow(String flowId, String workspaceId, long functionUnitId,
                           String catalogId, String capabilities, String serviceAccountUserId) {
    }
}
