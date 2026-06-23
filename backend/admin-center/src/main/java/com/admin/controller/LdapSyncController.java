package com.admin.controller;

import com.admin.dto.response.PageResult;
import com.admin.ldap.LdapSyncAuditService;
import com.admin.ldap.LdapSyncAuditView;
import com.platform.common.dto.ApiResponse;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * LDAP 同步管理接口（仅 {@code ldap.enabled=true} 时暴露）。
 *
 * <p>权限：本服务 Spring 层 permitAll（由 Kong 做边缘认证），故此处显式做 {@code systemadmin} 校验
 * （SYS_ADMIN / SUPER_ADMIN / 权限 {@code system:admin}），与源系统「/ldap 前缀需 systemadmin」语义一致。</p>
 *
 * <p>手动触发为异步执行（避免长同步阻塞 HTTP），返回「已受理」，进度经 {@code /status} 轮询。</p>
 */
@Slf4j
@RestController
@RequestMapping("/ldap-sync")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapSyncController {

    private static final String ERR_FORBIDDEN = "FORBIDDEN";
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    private final LdapSyncAuditService ldapSyncAuditService;

    @PostMapping("/full")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerFull() {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        ldapSyncAuditService.triggerFullAsync();
        return accepted("FULL");
    }

    @PostMapping("/incremental")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerIncremental() {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        ldapSyncAuditService.triggerIncrementalAsync();
        return accepted("INCREMENTAL");
    }

    @PostMapping("/hermes-groups")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerHermesGroups() {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        ldapSyncAuditService.triggerHermesGroupFullAsync();
        return accepted("HERMES_AD_GROUP");
    }

    @PostMapping("/hermes-groups/incremental")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerHermesGroupsIncremental() {
        if (!isSystemAdmin()) {
            return forbidden();
        }
        ldapSyncAuditService.triggerHermesGroupIncrementalAsync();
        return accepted("HERMES_AD_INCR");
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<LdapSyncAuditView>>> status() {
        if (!isSystemAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error(ERR_FORBIDDEN, "system:admin required"));
        }
        return ResponseEntity.ok(ApiResponse.success(ldapSyncAuditService.recentAudits()));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResult<LdapSyncAuditView>>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!isSystemAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error(ERR_FORBIDDEN, "system:admin required"));
        }
        return ResponseEntity.ok(ApiResponse.success(ldapSyncAuditService.auditPage(page, size)));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> accepted(String type) {
        log.info("LDAP {} sync accepted (async)", type);
        return ResponseEntity.accepted().body(ApiResponse.success(
                Map.of("started", true, "type", type)));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.error(ERR_FORBIDDEN, "system:admin required"));
    }

    /** SYS_ADMIN / SUPER_ADMIN / 超管 / 权限 system:admin 任一即视为系统管理员。 */
    private boolean isSystemAdmin() {
        return SecurityContextUtils.isSuperAdmin()
                || SecurityContextUtils.hasRole("SYS_ADMIN")
                || SecurityContextUtils.hasRole("SUPER_ADMIN")
                || SecurityContextUtils.hasPermission(SYSTEM_ADMIN_PERMISSION);
    }
}
