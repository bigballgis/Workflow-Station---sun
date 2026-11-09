package com.admin.bi.component;

import com.platform.security.util.SecurityContextUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/**
 * The signed-in user behind a browser-facing BI endpoint, for audit and ownership fields. Who may call each endpoint
 * is decided by its {@code @AdminAccess} (platform access model WP9): a role check here would refuse Technical Leads
 * the interceptor admitted.
 */
@Component
public class BiAdminAccessGuard {

    public String requireAdminUserId() {
        return SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Admin Center authentication required"));
    }
}
