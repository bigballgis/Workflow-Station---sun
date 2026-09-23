package com.admin.bi.component;

import com.platform.security.util.SecurityContextUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Restricts browser-facing BI read/token endpoints to real Admin Center users. */
@Component
public class BiAdminAccessGuard {

    public String requireAdminUserId() {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Admin Center authentication required"));
        if (!SecurityContextUtils.hasRole("SYS_ADMIN")
                && !SecurityContextUtils.hasRole("AUDITOR")
                && !SecurityContextUtils.hasRole("SUPER_ADMIN")
                && !SecurityContextUtils.isSuperAdmin()) {
            throw new AccessDeniedException("Admin Center access required");
        }
        return userId;
    }
}
