package com.admin.controller;

import com.admin.component.LdapSyncAuditListQueryComponent;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.LdapSyncAuditListQueryRequest;
import com.admin.ldap.LdapSyncAuditView;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;
import com.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The LDAP sync records page (D-51): read-only for SYS_ADMIN, Technical Leads and Auditors. Unlike
 * {@link LdapSyncController}, which exists only while LDAP sync is enabled, the records stay readable in every
 * environment (an environment without LDAP simply has none).
 */
@RestController
@RequestMapping("/ldap-sync")
@RequiredArgsConstructor
public class LdapSyncRecordController {

    private final LdapSyncAuditListQueryComponent auditListQueryComponent;

    @AdminAccess(AdminAudience.SHARED_READ)
    @PostMapping("/audit-logs/query")
    @Operation(summary = "Page LDAP sync records (shared list, true paging)")
    public ResponseEntity<ApiResponse<AdminListPage<LdapSyncAuditView>>> queryAuditLogs(
            @RequestBody @Valid LdapSyncAuditListQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(auditListQueryComponent.query(request)));
    }
}
