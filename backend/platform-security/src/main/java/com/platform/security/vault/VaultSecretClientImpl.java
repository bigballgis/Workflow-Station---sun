package com.platform.security.vault;

import com.platform.common.util.SafeUrlInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kubernetes-auth Vault KV v2 reader with short-lived token and password caches.
 * Never logs secret values, JWT, or client tokens.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true")
public class VaultSecretClientImpl implements VaultSecretClient {

    /** Bean name in admin-center RestTemplateConfig — must not use the @Primary outbound template. */
    public static final String REST_TEMPLATE_BEAN = "vaultRestTemplate";

    private static final long CACHE_TTL_MS = 45_000L;
    private static final int CACHE_MAX = 256;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final VaultClientSettings settings;
    private final ConcurrentHashMap<String, CachedPassword> passwordCache = new ConcurrentHashMap<>();
    private final Object tokenLock = new Object();
    private String cachedToken;
    private long tokenExpiresAtMillis;

    @Autowired
    public VaultSecretClientImpl(
            @Qualifier(REST_TEMPLATE_BEAN) ObjectProvider<RestTemplate> restTemplate,
            VaultClientSettings settings) {
        this(requireVaultRestTemplate(restTemplate), settings);
    }

    VaultSecretClientImpl(RestTemplate restTemplate, VaultClientSettings settings) {
        this.restTemplate = restTemplate;
        this.settings = settings;
    }

    private static RestTemplate requireVaultRestTemplate(ObjectProvider<RestTemplate> restTemplate) {
        RestTemplate template = restTemplate.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException(
                    "Required RestTemplate bean '" + REST_TEMPLATE_BEAN + "' is missing");
        }
        return template;
    }

    @Override
    public String readPassword(String secretPath) {
        requireAddr();
        if (!StringUtils.hasText(secretPath)) {
            throw new VaultSecretNotFoundException("Vault secret path is blank");
        }
        CachedPassword hit = passwordCache.get(secretPath);
        if (hit != null && !hit.expired()) {
            return hit.password();
        }
        String password = fetchPassword(secretPath);
        putPasswordCache(secretPath, password);
        return password;
    }

    private void requireAddr() {
        if (!StringUtils.hasText(settings.addr())) {
            throw new VaultSecretUnavailableException("VAULT_ADDR is not configured");
        }
    }

    @Override
    public boolean secretExists(String secretPath) {
        requireAddr();
        if (!StringUtils.hasText(secretPath)) {
            return false;
        }
        try {
            restTemplate.exchange(
                    kvDataUrl(secretPath), HttpMethod.GET, new HttpEntity<>(vaultHeaders(true)), MAP_TYPE);
            return true;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            log.warn("Vault KV EXISTS failed: status={}", ex.getStatusCode().value());
            throw new VaultSecretUnavailableException("Vault KV request failed", ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV EXISTS failed: {}", ex.getMessage());
            throw new VaultSecretUnavailableException("Unable to reach Vault", ex);
        }
    }

    @Override
    public void writePassword(String secretPath, String password) {
        requireAddr();
        if (!StringUtils.hasText(password)) {
            throw new VaultSecretUnavailableException("Vault password is blank");
        }
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("password", password);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", inner);
        try {
            restTemplate.exchange(
                    kvDataUrl(secretPath),
                    HttpMethod.POST,
                    new HttpEntity<>(body, vaultHeaders(true)),
                    MAP_TYPE);
            putPasswordCache(secretPath, password);
        } catch (HttpStatusCodeException ex) {
            log.warn("Vault KV WRITE failed: status={}", ex.getStatusCode().value());
            throw new VaultSecretUnavailableException("Vault KV write failed", ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV WRITE failed: {}", ex.getMessage());
            throw new VaultSecretUnavailableException("Unable to reach Vault", ex);
        }
    }

    private String fetchPassword(String secretPath) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    kvDataUrl(secretPath), HttpMethod.GET, new HttpEntity<>(vaultHeaders(true)), MAP_TYPE);
            return passwordFromKvBody(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return translateReadFailure(ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV GET failed: {}", ex.getMessage());
            throw new VaultSecretUnavailableException("Unable to reach Vault", ex);
        }
    }

    private String kvDataUrl(String secretPath) {
        try {
            return settings.addr() + "/v1/" + encodedPath(settings.kvMount())
                    + "/data/" + encodedPath(secretPath);
        } catch (IllegalArgumentException ex) {
            throw new VaultSecretNotFoundException("Vault secret path is invalid", ex);
        }
    }

    private String translateReadFailure(HttpStatusCodeException ex) {
        HttpStatusCode status = ex.getStatusCode();
        log.warn("Vault KV GET failed: status={}", status.value());
        if (status.value() == 404) {
            throw new VaultSecretNotFoundException("Vault secret not found", ex);
        }
        throw new VaultSecretUnavailableException("Vault KV request failed", ex);
    }

    private HttpHeaders vaultHeaders(boolean includeToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(settings.namespace())) {
            headers.set("X-Vault-Namespace", settings.namespace());
        }
        if (includeToken) {
            headers.set("X-Vault-Token", sessionToken());
        }
        return headers;
    }

    private String sessionToken() {
        if (StringUtils.hasText(settings.staticToken())) {
            return settings.staticToken();
        }
        synchronized (tokenLock) {
            long now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedToken) && now < tokenExpiresAtMillis) {
                return cachedToken;
            }
            loginKubernetes();
            return cachedToken;
        }
    }

    private void loginKubernetes() {
        if (!StringUtils.hasText(settings.authMount()) || !StringUtils.hasText(settings.role())) {
            throw new VaultSecretUnavailableException("VAULT_AUTH_MOUNT and VAULT_ROLE are required");
        }
        String url = settings.addr() + "/v1/auth/" + encodedPath(settings.authMount()) + "/login";
        Map<String, String> body = new LinkedHashMap<>();
        body.put("role", settings.role());
        body.put("jwt", readServiceAccountJwt());
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, vaultHeaders(false)), MAP_TYPE);
            applyLogin(response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.warn("Vault Kubernetes login failed: status={}", ex.getStatusCode().value());
            throw new VaultSecretUnavailableException("Vault Kubernetes login failed", ex);
        } catch (VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault Kubernetes login failed: {}", ex.getMessage());
            throw new VaultSecretUnavailableException("Unable to reach Vault for login", ex);
        }
    }

    private void applyLogin(Map<String, Object> body) {
        if (body == null || !(body.get("auth") instanceof Map<?, ?> auth)) {
            throw new VaultSecretUnavailableException("Vault login response is missing auth");
        }
        Object token = auth.get("client_token");
        if (!(token instanceof String text) || !StringUtils.hasText(text)) {
            throw new VaultSecretUnavailableException("Vault login did not return a client token");
        }
        long leaseSeconds = leaseSeconds(auth.get("lease_duration"));
        long skew = settings.refreshSkewSeconds();
        long usable = Math.max(1L, leaseSeconds - skew);
        cachedToken = text;
        tokenExpiresAtMillis = System.currentTimeMillis() + usable * 1000L;
    }

    private static long leaseSeconds(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        return 0L;
    }

    private String readServiceAccountJwt() {
        String file = settings.tokenFile();
        if (!StringUtils.hasText(file) || file.contains("..")) {
            throw new VaultSecretUnavailableException("VAULT_SERVICE_ACCOUNT_TOKEN_FILE is invalid");
        }
        try {
            String jwt = Files.readString(Path.of(file)).trim();
            if (!StringUtils.hasText(jwt)) {
                throw new VaultSecretUnavailableException("Kubernetes service account token is empty");
            }
            return jwt;
        } catch (VaultSecretUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VaultSecretUnavailableException("Unable to read Kubernetes service account token", ex);
        }
    }

    private static String passwordFromKvBody(Map<String, Object> body) {
        if (body == null) {
            throw new VaultSecretUnavailableException("Vault returned an empty body");
        }
        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            throw new VaultSecretUnavailableException("Vault data is not an object");
        }
        Object innerObj = data.get("data");
        if (!(innerObj instanceof Map<?, ?> inner)) {
            throw new VaultSecretUnavailableException("Vault KV v2 data.data is not an object");
        }
        Object password = inner.get("password");
        if (!(password instanceof String text) || !StringUtils.hasText(text)) {
            throw new VaultSecretUnavailableException("Vault secret data.password is missing");
        }
        return text;
    }

    static String encodedPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new IllegalArgumentException("Invalid identifier for URL path");
        }
        String trimmed = rawPath.trim();
        if (trimmed.startsWith("/") || trimmed.endsWith("/")) {
            throw new IllegalArgumentException("Invalid identifier for URL path");
        }
        String[] segments = trimmed.split("/");
        List<String> safe = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Invalid identifier for URL path");
            }
            safe.add(SafeUrlInput.requirePathToken(segment));
        }
        return String.join("/", safe);
    }

    private void putPasswordCache(String path, String password) {
        if (passwordCache.size() >= CACHE_MAX) {
            passwordCache.clear();
        }
        passwordCache.put(path, new CachedPassword(password, System.currentTimeMillis()));
    }

    private record CachedPassword(String password, long cachedAt) {
        boolean expired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS;
        }
    }
}
