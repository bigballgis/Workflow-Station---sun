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
            String detail = httpFailureDetail(ex);
            log.warn("Vault KV EXISTS failed: path={} {}", secretPath, detail, ex);
            throw new VaultSecretUnavailableException("Vault KV request failed: " + detail, ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV EXISTS failed: path={} {}", secretPath, ex.toString(), ex);
            throw new VaultSecretUnavailableException("Unable to reach Vault: " + ex, ex);
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
            String detail = httpFailureDetail(ex);
            log.warn("Vault KV WRITE failed: path={} {}", secretPath, detail, ex);
            throw new VaultSecretUnavailableException("Vault KV write failed: " + detail, ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV WRITE failed: path={} {}", secretPath, ex.toString(), ex);
            throw new VaultSecretUnavailableException("Unable to reach Vault: " + ex, ex);
        }
    }

    @Override
    public boolean dataFieldExists(String secretPath, String field) {
        requireAddr();
        if (!StringUtils.hasText(secretPath) || !StringUtils.hasText(field)) {
            return false;
        }
        return stringField(loadInnerData(secretPath, true), field) != null;
    }

    @Override
    public String readDataField(String secretPath, String field) {
        requireAddr();
        requireField(field);
        String cacheKey = fieldCacheKey(secretPath, field);
        CachedPassword hit = passwordCache.get(cacheKey);
        if (hit != null && !hit.expired()) {
            return hit.password();
        }
        String value = stringField(loadInnerData(secretPath, false), field);
        if (value == null) {
            throw new VaultSecretNotFoundException("Vault secret field not found");
        }
        putPasswordCache(cacheKey, value);
        return value;
    }

    @Override
    public void upsertDataField(String secretPath, String field, String value) {
        requireAddr();
        requireField(field);
        if (!StringUtils.hasText(value)) {
            throw new VaultSecretUnavailableException("Vault password is blank");
        }
        Map<String, Object> inner = loadInnerData(secretPath, true);
        inner.put(field, value);
        postInnerData(secretPath, inner);
        putPasswordCache(fieldCacheKey(secretPath, field), value);
    }

    private Map<String, Object> loadInnerData(String secretPath, boolean emptyOn404) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    kvDataUrl(secretPath), HttpMethod.GET, new HttpEntity<>(vaultHeaders(true)), MAP_TYPE);
            return copyKvInner(response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (emptyOn404 && ex.getStatusCode().value() == 404) {
                return new LinkedHashMap<>();
            }
            if (ex.getStatusCode().value() == 404) {
                throw new VaultSecretNotFoundException("Vault secret not found", ex);
            }
            String detail = httpFailureDetail(ex);
            log.warn("Vault KV GET failed: path={} {}", secretPath, detail, ex);
            throw new VaultSecretUnavailableException("Vault KV request failed: " + detail, ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV GET failed: path={} {}", secretPath, ex.toString(), ex);
            throw new VaultSecretUnavailableException("Unable to reach Vault: " + ex, ex);
        }
    }

    private void postInnerData(String secretPath, Map<String, Object> inner) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", inner);
        try {
            restTemplate.exchange(
                    kvDataUrl(secretPath),
                    HttpMethod.POST,
                    new HttpEntity<>(body, vaultHeaders(true)),
                    MAP_TYPE);
        } catch (HttpStatusCodeException ex) {
            String detail = httpFailureDetail(ex);
            log.warn("Vault KV WRITE failed: path={} {}", secretPath, detail, ex);
            throw new VaultSecretUnavailableException("Vault KV write failed: " + detail, ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV WRITE failed: path={} {}", secretPath, ex.toString(), ex);
            throw new VaultSecretUnavailableException("Unable to reach Vault: " + ex, ex);
        }
    }

    private static void requireField(String field) {
        if (!StringUtils.hasText(field)) {
            throw new VaultSecretNotFoundException("Vault secret field is blank");
        }
    }

    private static String fieldCacheKey(String secretPath, String field) {
        return secretPath + "\n" + field;
    }

    private static String stringField(Map<String, Object> inner, String field) {
        Object value = inner.get(field);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }

    private static Map<String, Object> copyKvInner(Map<String, Object> body) {
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
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : inner.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private String fetchPassword(String secretPath) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    kvDataUrl(secretPath), HttpMethod.GET, new HttpEntity<>(vaultHeaders(true)), MAP_TYPE);
            return passwordFromKvBody(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return translateReadFailure(secretPath, ex);
        } catch (VaultSecretNotFoundException | VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault KV GET failed: path={} {}", secretPath, ex.toString(), ex);
            throw new VaultSecretUnavailableException("Unable to reach Vault: " + ex, ex);
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

    private String translateReadFailure(String secretPath, HttpStatusCodeException ex) {
        int status = ex.getStatusCode().value();
        if (status == 404) {
            log.warn("Vault KV GET failed: path={} status=404", secretPath);
            throw new VaultSecretNotFoundException("Vault secret not found", ex);
        }
        String detail = httpFailureDetail(ex);
        log.warn("Vault KV GET failed: path={} {}", secretPath, detail, ex);
        throw new VaultSecretUnavailableException("Vault KV request failed: " + detail, ex);
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
            String detail = httpFailureDetail(ex);
            log.warn("Vault Kubernetes login failed: url={} role={} {}", url, settings.role(), detail, ex);
            throw new VaultSecretUnavailableException(
                    "Vault Kubernetes login failed: url=" + url + " role=" + settings.role() + " " + detail, ex);
        } catch (VaultSecretUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Vault Kubernetes login failed: url={} role={} {}", url, settings.role(), ex.toString(), ex);
            throw new VaultSecretUnavailableException(
                    "Unable to reach Vault for login: url=" + url + " role=" + settings.role() + " " + ex, ex);
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
        Object password = copyKvInner(body).get("password");
        if (!(password instanceof String text) || !StringUtils.hasText(text)) {
            throw new VaultSecretUnavailableException("Vault secret data.password is missing");
        }
        return text;
    }

    /**
     * Status and response body only. Never includes the request JWT or a client token.
     */
    private static String httpFailureDetail(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            body = "(empty)";
        } else {
            body = redactVaultBody(body.trim());
            if (body.length() > 400) {
                body = body.substring(0, 400) + "...";
            }
        }
        return "status=" + ex.getStatusCode().value() + " body=" + body;
    }

    private static String redactVaultBody(String body) {
        return body
                .replaceAll("(?i)(\"(?:client_token|jwt|password)\"\\s*:\\s*\")[^\"]*\"", "$1[redacted]\"")
                .replaceAll("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[redacted-jwt]");
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
