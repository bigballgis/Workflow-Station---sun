package com.platform.security.client;

import com.platform.common.constant.PlatformConstants;
import com.platform.security.util.SecurityContextUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * Marks outbound calls to one first-party platform service (Admin Center) as service calls: adds
 * {@code X-Service-Token}, and {@code X-User-Id} of the signed-in user when there is one. Headers a call site
 * already set are kept, so calls made on behalf of a specific user (e.g. an approver) stay as they are.
 * Requests to any other target pass untouched.
 *
 * <p>Admin Center authorizes every endpoint (platform access model §8.1); calls from Portal, the engine
 * and DW are admitted by this token, and the user they act for is judged by database roles.
 */
public class ServiceTokenRequestInterceptor implements ClientHttpRequestInterceptor {

    private final URI target;
    private final String serviceToken;

    /**
     * @param targetBaseUrl base URL of the service to mark (e.g. {@code admin-center.url})
     * @param serviceToken  {@code service.internal-token}; required — without it every call would be refused
     */
    public ServiceTokenRequestInterceptor(String targetBaseUrl, String serviceToken) {
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            throw new IllegalStateException("Target base URL must be configured for service-token calls");
        }
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("service.internal-token must be configured (SERVICE_INTERNAL_TOKEN)");
        }
        this.target = URI.create(targetBaseUrl.trim());
        this.serviceToken = serviceToken;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (targets(request.getURI())) {
            HttpHeaders headers = request.getHeaders();
            if (!headers.containsKey(PlatformConstants.HEADER_SERVICE_TOKEN)) {
                headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceToken);
            }
            if (!headers.containsKey(PlatformConstants.HEADER_USER_ID)) {
                SecurityContextUtils.getCurrentUserId()
                        .filter(id -> !id.isBlank())
                        .ifPresent(id -> headers.set(PlatformConstants.HEADER_USER_ID, id));
            }
        }
        return execution.execute(request, body);
    }

    boolean targets(URI uri) {
        return target.getScheme() != null && target.getScheme().equalsIgnoreCase(uri.getScheme())
                && target.getHost() != null && target.getHost().equalsIgnoreCase(uri.getHost())
                && port(target) == port(uri)
                && pathOf(uri).startsWith(pathOf(target));
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String pathOf(URI uri) {
        return uri.getPath() == null ? "" : uri.getPath();
    }
}
