package com.workflow.client;

import com.platform.common.util.SafeUrlInput;
import com.workflow.config.RestTemplateConfig;
import com.workflow.exception.AdminCenterUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Members of a legacy virtual-group task's group (D-17: the group is that task's pool). Kept apart from
 * {@link AdminCenterClient}, which is already past the file-size limit; unlike its {@code isUserInVirtualGroup}, a
 * failed lookup throws, because the answer decides who may take a task over.
 */
@Component
public class VirtualGroupMembersClient {

    private final RestTemplate restTemplate;
    private final String adminCenterUrl;

    public VirtualGroupMembersClient(@Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate restTemplate,
                                     @Value("${admin-center.url}") String adminCenterUrl) {
        this.restTemplate = restTemplate;
        this.adminCenterUrl = adminCenterUrl;
    }

    /** User ids of the group's members; empty when the group no longer exists. */
    public List<String> memberUserIds(String groupId) {
        String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members";
        try {
            List<Map<String, Object>> members = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
            if (members == null) {
                return List.of();
            }
            return members.stream().map(m -> m.get("userId")).filter(Objects::nonNull)
                    .map(Object::toString).distinct().toList();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (RestClientException e) {
            throw new AdminCenterUnavailableException("Failed to list members of virtual group " + groupId, e);
        }
    }
}
