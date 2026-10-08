package com.portal.client;

import com.platform.common.constant.PlatformConstants;
import com.portal.dto.bi.PortalDataViewDashboardResponse;
import com.portal.dto.bi.PortalGuestTokenRequest;
import com.portal.dto.bi.PortalGuestTokenResponse;
import com.portal.dto.bi.PortalUserDashboardResponse;
import com.portal.exception.PortalBiUpstreamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Fail-closed User Portal client for Admin Center's internal BI facade. */
@Component
public class PortalBiClient {

    private final RestTemplate restTemplate;
    private final String adminCenterUrl;
    private final String serviceInternalToken;

    public PortalBiClient(
            RestTemplate restTemplate,
            @Value("${admin-center.url:http://localhost:8090}") String adminCenterUrl,
            @Value("${service.internal-token:}") String serviceInternalToken) {
        this.restTemplate = restTemplate;
        this.adminCenterUrl = trimTrailingSlash(adminCenterUrl);
        this.serviceInternalToken = serviceInternalToken;
    }

    public List<PortalUserDashboardResponse> getDashboards(
            String userId, String activeBusinessUnitId) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(
                adminCenterUrl + "/api/v1/admin/internal/bi/dashboards");
        if (activeBusinessUnitId != null && !activeBusinessUnitId.isBlank()) {
            uri.queryParam("activeBusinessUnitId", activeBusinessUnitId);
        }
        try {
            ResponseEntity<List<PortalUserDashboardResponse>> response = restTemplate.exchange(
                    uri.build().encode().toUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(userId)),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    public PortalGuestTokenResponse getGuestToken(
            String userId, PortalGuestTokenRequest request) {
        try {
            ResponseEntity<PortalGuestTokenResponse> response = restTemplate.exchange(
                    adminCenterUrl + "/api/v1/admin/internal/bi/guest-token",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers(userId)),
                    PortalGuestTokenResponse.class);
            if (response.getBody() == null) {
                throw new PortalBiUpstreamException(
                        HttpStatus.BAD_GATEWAY, "BI_UPSTREAM_EMPTY_RESPONSE",
                        "Admin Center returned an empty BI response", null);
            }
            return response.getBody();
        } catch (PortalBiUpstreamException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    public List<PortalDataViewDashboardResponse> getDataViewDashboards(
            String userId, Long viewId) {
        try {
            ResponseEntity<List<PortalDataViewDashboardResponse>> response = restTemplate.exchange(
                    adminCenterUrl + "/api/v1/admin/internal/bi/data-views/" + viewId + "/dashboards",
                    HttpMethod.GET,
                    new HttpEntity<>(headers(userId)),
                    new ParameterizedTypeReference<>() {});
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (RestClientException ex) {
            throw translate(ex);
        }
    }

    private HttpHeaders headers(String userId) {
        if (serviceInternalToken == null || serviceInternalToken.isBlank()) {
            throw new PortalBiUpstreamException(
                    HttpStatus.SERVICE_UNAVAILABLE, "BI_SERVICE_AUTH_NOT_CONFIGURED",
                    "BI service authentication is not configured", null);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        headers.set(PlatformConstants.HEADER_USER_ID, userId);
        return headers;
    }

    private RuntimeException translate(RestClientException ex) {
        if (ex instanceof HttpClientErrorException.Unauthorized) {
            return new AuthenticationCredentialsNotFoundException(
                    "Admin Center rejected BI service authentication", ex);
        }
        if (ex instanceof HttpClientErrorException.Forbidden) {
            return new AccessDeniedException("Dashboard access denied", ex);
        }
        if (ex instanceof HttpClientErrorException.NotFound) {
            return new PortalBiUpstreamException(
                    HttpStatus.NOT_FOUND, "BI_DASHBOARD_NOT_FOUND",
                    "Dashboard was not found", ex);
        }
        if (ex instanceof HttpClientErrorException.BadRequest) {
            return new PortalBiUpstreamException(
                    HttpStatus.BAD_REQUEST, "BI_INVALID_REQUEST",
                    "Invalid BI request", ex);
        }
        return new PortalBiUpstreamException(
                HttpStatus.BAD_GATEWAY, "BI_UPSTREAM_UNAVAILABLE",
                "BI service is temporarily unavailable", ex);
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
