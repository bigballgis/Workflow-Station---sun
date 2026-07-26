package com.workflow.client;

import com.platform.common.util.SafeUrlInput;
import com.workflow.config.RestTemplateConfig;
import com.workflow.exception.AdminCenterUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin Center 客户端
 * 用于调用 admin-center 的 API 进行用户权限验证
 */
@Slf4j
@Component
public class AdminCenterClient {

    /** 用户信息缓存 TTL。短到足以让改名/停用在一分钟内生效，长到能扛住一页 To Do 的重复查询。 */
    private static final long USER_CACHE_TTL_MS = 60_000L;
    private static final int USER_CACHE_MAX_ENTRIES = 10_000;

    /**
     * 走短超时的 {@code internalApiRestTemplate}，不要用默认那个 10 分钟读超时的 bean
     * （见 {@link com.workflow.config.RestTemplateConfig}）。
     */
    private final RestTemplate restTemplate;

    private final Map<String, CachedUser> userInfoCache = new ConcurrentHashMap<>();

    public AdminCenterClient(
            @Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    /**
     * 检查用户是否是虚拟组成员
     * @param userId 用户ID
     * @param groupId 虚拟组ID
     * @return 是否是成员
     */
    public boolean isUserInVirtualGroup(String userId, String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> members = response.getBody();
            if (members == null || members.isEmpty()) {
                return false;
            }
            
            for (Map<String, Object> member : members) {
                if (userId.equals(member.get("userId"))) {
                    return true;
                }
            }
            return false;
            
        } catch (Exception e) {
            // FALLBACK(external): 权限查询降级为"无权限"（功能变少而非数据变错）；
            // 分派链路不走本方法（走 getUsersByVirtualGroupCode，那个会抛）。
            log.error("Failed to check if user {} is in virtual group {}: {}", userId, groupId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户所属的所有虚拟组ID
     * @param userId 用户ID
     * @return 虚拟组ID列表
     */
    public List<String> getUserVirtualGroupIds(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/virtual-groups";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> groups = response.getBody();
            if (groups == null || groups.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<String> groupIds = new ArrayList<>();
            for (Map<String, Object> group : groups) {
                // admin-center 返回的是 groupId 字段
                Object id = group.get("groupId");
                if (id == null) {
                    id = group.get("id");
                }
                if (id != null) {
                    groupIds.add(id.toString());
                }
            }
            return groupIds;
            
        } catch (Exception e) {
            // FALLBACK(external): 权限查询降级为空（用户暂时看不到候选组任务，不产生错误数据）。
            // portal 侧 WorkspaceTaskFilterComponent 有 last-known-good 缓存兜底。
            log.error("Failed to get virtual groups for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色编码列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> roles = response.getBody();
            if (roles == null || roles.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<String> roleCodes = new ArrayList<>();
            for (Map<String, Object> role : roles) {
                Object code = role.get("code");
                if (code != null) {
                    roleCodes.add(code.toString());
                }
            }
            return roleCodes;
            
        } catch (Exception e) {
            // FALLBACK(external): 权限查询降级为空角色（保守方向:少给权限而非多给）。
            log.error("Failed to get roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查 Admin Center 服务是否可用
     * @return 是否可用
     */
    public boolean isAvailable() {
        try {
            String url = adminCenterUrl + "/actuator/health";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> health = response.getBody();
            if (health != null && "UP".equals(health.get("status"))) {
                return true;
            }
            return false;
            
        } catch (Exception e) {
            log.warn("Admin Center is not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户详细信息
     * @param userId 用户ID（可以是ID或用户名）
     * @return 用户信息Map，包含 id, username, businessUnitId, entityManagerId, functionManagerId 等
     */
    public Map<String, Object> getUserInfo(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String key = userId.trim();

        CachedUser cached = userInfoCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        // 传输故障时 fetchUserInfo 抛 AdminCenterUnavailableException——异常穿透、不落缓存，
        // 避免把"服务抖动"当"查无此人"缓存 60 秒。
        Map<String, Object> resolved = fetchUserInfo(key);
        // "查无此人"(null)也要缓存：否则每次查不到的用户都会重新打一遍 HTTP（To Do 列表每行都会查）。
        userInfoCache.put(key, CachedUser.of(resolved));
        if (userInfoCache.size() > USER_CACHE_MAX_ENTRIES) {
            evictExpiredUserInfo();
        }
        return resolved;
    }

    /**
     * 批量解析用户信息：同一页里重复出现的 userId 只查一次。
     *
     * <p>To Do 列表的发起人/处理人高度重复，逐行查会把一页放大成几十次 HTTP。
     */
    public Map<String, Map<String, Object>> getUserInfoBatch(Collection<String> userIds) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            String key = userId.trim();
            if (out.containsKey(key)) {
                continue;
            }
            try {
                out.put(key, getUserInfo(key));
            } catch (AdminCenterUnavailableException e) {
                // FALLBACK(external): 批量展示场景（To Do 列表姓名列），单个用户解析故障降级为
                // 显示占位符，不让整页列表 500。分派链路不走本方法。
                log.warn("User info unavailable for {} in batch resolve: {}", key, e.getMessage());
                out.put(key, null);
            }
        }
        return out;
    }

    private Map<String, Object> fetchUserInfo(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (HttpClientErrorException.NotFound e) {
            // 只有"确实不存在这个 id"才值得再按用户名搜一次。
            return searchUserByUsername(userId);
        } catch (RestClientException e) {
            // 超时/熔断/5xx：结果未知，抛出而非返回 null——null 保留给"查无此人"。
            // 不做第二次调用：那只会在 admin-center 已经过载时雪上加霜。
            log.error("admin-center unavailable getting user {}: {}", userId, e.getMessage());
            throw new AdminCenterUnavailableException("Failed to get user info for " + userId, e);
        }
    }

    private Map<String, Object> searchUserByUsername(String userId) {
        try {
            String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword="
                    + SafeUrlInput.encodeQueryValue(userId) + "&size=1";
            ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> searchResult = searchResponse.getBody();
            if (searchResult != null && searchResult.get("content") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> users = (List<Map<String, Object>>) searchResult.get("content");
                if (!users.isEmpty()) {
                    return users.get(0);
                }
            }
        } catch (RestClientException e) {
            log.error("admin-center unavailable searching user by username {}: {}", userId, e.getMessage());
            throw new AdminCenterUnavailableException("Failed to search user by username " + userId, e);
        }
        return null;
    }

    private void evictExpiredUserInfo() {
        userInfoCache.entrySet().removeIf(e -> e.getValue().isExpired());
        if (userInfoCache.size() > USER_CACHE_MAX_ENTRIES) {
            userInfoCache.clear();
        }
    }

    private record CachedUser(Map<String, Object> value, long expiresAtMs) {
        static CachedUser of(Map<String, Object> value) {
            return new CachedUser(value, System.currentTimeMillis() + USER_CACHE_TTL_MS);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }
    
    // ==================== 任务分配相关 API ====================
    
    /**
     * 获取用户的业务单元ID
     * @param userId 用户ID
     * @return 业务单元ID，如果用户没有业务单元则返回null
     */
    /**
     * @param activeBusinessUnitId 可选；多 BU 时须传入与用户 UBR 一致的当前业务单元
     */
    public String getUserBusinessUnitId(String userId, String activeBusinessUnitId) {
        try {
            String base = adminCenterUrl + "/api/v1/admin/task-assignment/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit";
            String url = base;
            if (activeBusinessUnitId != null && !activeBusinessUnitId.isBlank()) {
                url = base + "?activeBusinessUnitId=" + java.net.URLEncoder.encode(activeBusinessUnitId, java.nio.charset.StandardCharsets.UTF_8);
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object businessUnitId = result.get("businessUnitId");
                if (businessUnitId != null && !businessUnitId.toString().isEmpty()) {
                    return businessUnitId.toString();
                }
            }
            return null;

        } catch (HttpClientErrorException.NotFound e) {
            // 用户不存在：这是"确实无数据"，与传输故障区分。
            return null;
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting business unit ID for user {}: {}", userId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get business unit ID for user " + userId, e);
        }
    }

    public String getUserBusinessUnitId(String userId) {
        return getUserBusinessUnitId(userId, null);
    }

    /**
     * 业务单元 id → code（工作台 activeBusinessUnitId 仍为 id 时的运行时转换）
     * @param businessUnitId 业务单元 id
     * @return BU code，未找到时返回 null
     */
    public String getBusinessUnitCodeById(String businessUnitId) {
        if (businessUnitId == null || businessUnitId.isBlank()) {
            return null;
        }
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/by-id/"
                    + businessUnitId.trim() + "/code";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object code = result.get("code");
                if (code != null && !code.toString().isEmpty()) {
                    return code.toString();
                }
            }
            return null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting business unit code for id {}: {}", businessUnitId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get business unit code for id " + businessUnitId, e);
        }
    }

    /**
     * 获取业务单元的父业务单元ID
     * @param businessUnitId 业务单元ID
     * @return 父业务单元ID，如果没有父级则返回null
     */
    public String getParentBusinessUnitId(String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/parent";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object parentId = result.get("parentBusinessUnitId");
                if (parentId != null && !parentId.toString().isEmpty()) {
                    return parentId.toString();
                }
            }
            return null;

        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting parent business unit for {}: {}", businessUnitId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get parent business unit ID for " + businessUnitId, e);
        }
    }
    
    /**
     * 获取业务单元中拥有指定角色的用户ID列表
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID（BU_BOUNDED类型）
     * @return 用户ID列表
     */
    public List<String> getUsersByBusinessUnitAndRole(String businessUnitId, String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/roles/" + SafeUrlInput.requirePathToken(roleId) + "/users";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            List<String> userIds = response.getBody();
            return userIds != null ? userIds : Collections.emptyList();

        } catch (HttpClientErrorException.NotFound e) {
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting users by business unit {} and role {}: {}",
                    businessUnitId, roleId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get users by business unit " + businessUnitId + " and role " + roleId, e);
        }
    }

    /**
     * 自某 BU 起沿父链向上，收集各层 BU 中拥有指定角色的用户 ID（并集、保序去重）。
     */
    public List<String> collectUserIdsForRoleInBusinessUnitHierarchy(String startBusinessUnitId, String roleId) {
        if (startBusinessUnitId == null || startBusinessUnitId.isBlank() || roleId == null || roleId.isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> union = new LinkedHashSet<>();
        String bu = startBusinessUnitId.trim();
        int guard = 0;
        final int maxHops = 256;
        while (bu != null && !bu.isEmpty() && guard++ < maxHops) {
            List<String> chunk = getUsersByBusinessUnitAndRole(bu, roleId.trim());
            if (chunk != null) {
                union.addAll(chunk);
            }
            bu = getParentBusinessUnitId(bu);
        }
        return new ArrayList<>(union);
    }
    
    /**
     * 获取拥有指定BU无关型角色的用户ID列表
     * @param roleId 角色ID（BU_UNBOUNDED类型）
     * @return 用户ID列表
     */
    public List<String> getUsersByUnboundedRole(String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/roles/" + SafeUrlInput.requirePathToken(roleId) + "/users";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            List<String> userIds = response.getBody();
            return userIds != null ? userIds : Collections.emptyList();

        } catch (HttpClientErrorException.NotFound e) {
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting users by unbounded role {}: {}", roleId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get users by unbounded role " + roleId, e);
        }
    }

    /**
     * 按虚拟组编码（BPMN VIRTUAL_GROUP 的 assigneeValue，如 DOCUMENT_VERIFIERS）获取成员用户 ID。
     */
    public List<String> getUsersByVirtualGroupCode(String code) {
        if (code == null || code.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = UriComponentsBuilder
                    .fromUriString(adminCenterUrl + "/api/v1/admin/task-assignment/virtual-groups/by-code/{code}/users")
                    .buildAndExpand(code.trim())
                    .toUriString();
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> userIds = response.getBody();
            return userIds != null ? userIds : Collections.emptyList();
        } catch (HttpClientErrorException.NotFound e) {
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting users by virtual group code {}: {}", code, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get users by virtual group code " + code, e);
        }
    }
    
    /**
     * 获取业务单元的准入角色ID列表
     * @param businessUnitId 业务单元ID
     * @return 角色ID列表
     */
    public List<String> getEligibleRoleIds(String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/eligible-roles";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            List<String> roleIds = response.getBody();
            return roleIds != null ? roleIds : Collections.emptyList();

        } catch (HttpClientErrorException.NotFound e) {
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.error("admin-center unavailable getting eligible role IDs for business unit {}: {}",
                    businessUnitId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to get eligible role IDs for business unit " + businessUnitId, e);
        }
    }
    
    /**
     * 检查角色是否是业务单元的准入角色
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @return 是否是准入角色
     */
    public boolean isEligibleRole(String businessUnitId, String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/roles/" + SafeUrlInput.requirePathToken(roleId) + "/eligible";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object eligible = result.get("eligible");
                return Boolean.TRUE.equals(eligible);
            }
            return false;

        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            log.error("admin-center unavailable checking role {} eligibility for business unit {}: {}",
                    roleId, businessUnitId, e.getMessage());
            throw new AdminCenterUnavailableException(
                    "Failed to check role " + roleId + " eligibility for business unit " + businessUnitId, e);
        }
    }
    
    /**
     * 获取所有BU绑定型角色
     * @return 角色列表
     */
    public List<Map<String, Object>> getBuBoundedRoles() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/roles/bu-bounded";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> roles = response.getBody();
            return roles != null ? roles : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get BU bounded roles: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取所有BU无关型角色
     * @return 角色列表
     */
    public List<Map<String, Object>> getBuUnboundedRoles() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/roles/bu-unbounded";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> roles = response.getBody();
            return roles != null ? roles : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get BU unbounded roles: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取 N8N 连接配置（含解密后 apiKey）
     * 调用 admin-center 的内部 API 获取完整的 N8N 连接配置信息
     * @param configId N8N 配置ID
     * @return N8N 配置信息Map，包含 id, name, baseUrl, apiKey, isActive 等；调用失败时返回 null
     */
    public Map<String, Object> getN8nConfig(String configId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/n8n-config/" + SafeUrlInput.requirePathToken(configId) + "/internal";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;

        } catch (Exception e) {
            // FALLBACK(external): N8N 集成配置获取失败降级为 null，调用方(委托节点)按"集成不可用"处理。
            log.error("Failed to get N8N config {}: {}", configId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取功能单元邮件连接凭据（内部 API）
     *
     * @throws IllegalStateException when admin-center reports system SMTP is not configured
     */
    public Optional<Map<String, Object>> getEmailConnectionCredentials(String functionUnitId, String connectionId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/internal/function-units/"
                    + SafeUrlInput.requirePathToken(functionUnitId)
                    + "/connections/"
                    + SafeUrlInput.requirePathToken(connectionId)
                    + "/credentials";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            if (isSystemSmtpNotConfigured(e.getResponseBodyAsString())) {
                String detail = extractErrorMessage(e.getResponseBodyAsString());
                throw new IllegalStateException(
                        detail != null && !detail.isBlank() ? detail : "System SMTP not configured",
                        e);
            }
            // FALLBACK(external): 其他凭据获取失败降级为 empty，调用方按连接不存在处理。
            log.error("Failed to get email connection credentials for {} / {}: status={} {}",
                    functionUnitId, connectionId, e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // FALLBACK(external): 邮件凭据获取失败降级为 empty，邮件轮询本轮跳过、下轮重试。
            log.error("Failed to get email connection credentials for {} / {}: {}",
                    functionUnitId, connectionId, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isSystemSmtpNotConfigured(String body) {
        return body != null && body.contains("SYSTEM_SMTP_NOT_CONFIGURED");
    }

    private static String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        int keyIdx = body.indexOf("\"message\"");
        if (keyIdx < 0) {
            return null;
        }
        int colon = body.indexOf(':', keyIdx);
        int firstQuote = body.indexOf('"', colon + 1);
        int secondQuote = firstQuote >= 0 ? body.indexOf('"', firstQuote + 1) : -1;
        if (firstQuote < 0 || secondQuote < 0) {
            return null;
        }
        return body.substring(firstQuote + 1, secondQuote).trim();
    }

    /**
     * 按功能单元 code 解析 Admin Center 中的功能单元 ID
     */
    public Optional<String> resolveFunctionUnitIdByCode(String functionUnitCode) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/internal/function-units/by-code/"
                    + functionUnitCode + "/id";
            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, String>>() {}
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.ofNullable(response.getBody().get("functionUnitId"));
            }
            return Optional.empty();
        } catch (Exception e) {
            // FALLBACK(external): FU id 解析失败降级为 empty，调用方(邮件监听)本轮跳过。
            log.error("Failed to resolve function unit id by code {}: {}", functionUnitCode, e.getMessage());
            return Optional.empty();
        }
    }

}
