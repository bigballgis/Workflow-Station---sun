package com.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;

/**
 * Environment-variable APIs are same-origin through the gateway. They do not
 * emit CORS headers. Other admin APIs keep the global CORS mapping.
 */
public class VaultCatalogCorsProcessor extends DefaultCorsProcessor {

    @Override
    public boolean processRequest(
            CorsConfiguration config, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (isVaultCatalog(request)) {
            return true;
        }
        return super.processRequest(config, request, response);
    }

    static boolean isVaultCatalog(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.equals("/environment-variables")
                || path.startsWith("/environment-variables/")
                || path.equals("/internal/environment-variables")
                || path.startsWith("/internal/environment-variables/");
    }
}
