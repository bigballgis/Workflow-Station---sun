package com.developer.service.impl;

import com.developer.dto.LockInfoResponse;
import com.developer.exception.AiGenerationException;
import com.developer.exception.AiLockConflictException;
import com.developer.service.AiLockService;
import com.platform.cache.service.CacheService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * AI 生成功能分布式锁服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiLockServiceImpl implements AiLockService {

    private static final String LOCK_KEY_PREFIX = "ai-gen-lock:";
    private static final String FORCE_UNLOCK_KEY_PREFIX = "ai-gen-force-unlock:";

    private final CacheService cacheService;
    private final RestTemplate restTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> pendingForceUnlocks = new ConcurrentHashMap<>();

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    @Value("${ai-generation.lock.ttl-seconds:1800}")
    private long ttlSeconds;

    @Value("${ai-generation.lock.force-unlock-timeout-seconds:60}")
    private long forceUnlockTimeoutSeconds;

    private final ConcurrentHashMap<String, String> userNameCache = new ConcurrentHashMap<>();

    @Override
    public LockInfoResponse tryAcquire(Long functionUnitId, String userId) {
        String key = buildKey(functionUnitId);
        String userName = resolveUserDisplayName(userId);

        LockValue lockValue = new LockValue(userId, userName, Instant.now().toString());

        // Pass the object directly — CacheService handles serialization
        boolean acquired = cacheService.setIfAbsent(key, lockValue, Duration.ofSeconds(ttlSeconds));

        if (acquired) {
            log.info("Lock acquired for functionUnitId={} by userId={}", functionUnitId, userId);
            return LockInfoResponse.builder()
                    .functionUnitId(functionUnitId)
                    .userId(userId)
                    .userName(userName)
                    .lockedAt(Instant.parse(lockValue.lockedAt()))
                    .locked(true)
                    .build();
        }

        // Lock already held — read existing lock info
        LockInfoResponse existingLock = readLockInfo(functionUnitId);
        if (existingLock != null && existingLock.getUserId().equals(userId)) {
            // Same user already holds the lock — treat as success and extend TTL
            cacheService.expire(key, Duration.ofSeconds(ttlSeconds));
            log.info("Lock already held by same userId={} for functionUnitId={}, extended TTL", userId, functionUnitId);
            return existingLock;
        }

        log.info("Lock conflict for functionUnitId={}: held by userId={}, requested by userId={}",
                functionUnitId, existingLock != null ? existingLock.getUserId() : "unknown", userId);
        throw new AiLockConflictException(existingLock != null ? existingLock : LockInfoResponse.builder()
                .functionUnitId(functionUnitId)
                .locked(true)
                .build());
    }

    @Override
    public void release(Long functionUnitId, String userId) {
        String key = buildKey(functionUnitId);
        LockInfoResponse currentLock = readLockInfo(functionUnitId);

        if (currentLock == null || !currentLock.isLocked()) {
            log.warn("Attempted to release non-existent lock for functionUnitId={} by userId={}", functionUnitId, userId);
            return;
        }

        if (!userId.equals(currentLock.getUserId())) {
            log.warn("Attempted to release lock for functionUnitId={} by userId={}, but lock is held by userId={}",
                    functionUnitId, userId, currentLock.getUserId());
            return;
        }

        cacheService.delete(key);
        log.info("Lock released for functionUnitId={} by userId={}", functionUnitId, userId);
    }

    @Override
    public void extendLock(Long functionUnitId, String userId) {
        String key = buildKey(functionUnitId);
        LockInfoResponse currentLock = readLockInfo(functionUnitId);

        if (currentLock == null || !currentLock.isLocked()) {
            log.warn("Attempted to extend non-existent lock for functionUnitId={} by userId={}", functionUnitId, userId);
            return;
        }

        if (!userId.equals(currentLock.getUserId())) {
            log.warn("Attempted to extend lock for functionUnitId={} by userId={}, but lock is held by userId={}",
                    functionUnitId, userId, currentLock.getUserId());
            return;
        }

        cacheService.expire(key, Duration.ofSeconds(ttlSeconds));
        log.info("Lock TTL extended for functionUnitId={} by userId={}", functionUnitId, userId);
    }

    @Override
    public LockInfoResponse getLockInfo(Long functionUnitId) {
        LockInfoResponse lockInfo = readLockInfo(functionUnitId);
        if (lockInfo != null) {
            return lockInfo;
        }
        return LockInfoResponse.builder()
                .functionUnitId(functionUnitId)
                .locked(false)
                .build();
    }

    @Override
    public void requestForceUnlock(Long functionUnitId, String requesterId) {
        String lockKey = buildKey(functionUnitId);
        String forceUnlockKey = buildForceUnlockKey(functionUnitId);

        // Check if lock exists
        if (!cacheService.exists(lockKey)) {
            throw new AiGenerationException("AI_LOCK_NOT_FOUND", "Lock does not exist");
        }

        // Check if requester is the lock holder
        LockInfoResponse currentLock = readLockInfo(functionUnitId);
        if (currentLock != null && requesterId.equals(currentLock.getUserId())) {
            throw new AiGenerationException("AI_FORCE_UNLOCK_SELF", "Cannot force unlock your own lock");
        }

        // Create force unlock request
        ForceUnlockRequest forceUnlockRequest = new ForceUnlockRequest(requesterId, Instant.now().toString());

        // Pass the object directly — CacheService handles serialization
        cacheService.set(forceUnlockKey, forceUnlockRequest, Duration.ofSeconds(forceUnlockTimeoutSeconds));
        log.info("Force unlock requested for functionUnitId={} by requesterId={}", functionUnitId, requesterId);

        // Register delayed task for auto-release
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                // Check if force-unlock key still exists (lock holder didn't respond)
                if (cacheService.exists(forceUnlockKey)) {
                    // Auto-release: delete lock key and force-unlock key
                    cacheService.delete(lockKey);
                    cacheService.delete(forceUnlockKey);
                    log.info("Force unlock auto-released lock for functionUnitId={} (lock holder did not respond within {} seconds)",
                            functionUnitId, forceUnlockTimeoutSeconds);
                }
            } catch (Exception e) {
                log.error("Error during force unlock auto-release for functionUnitId={}", functionUnitId, e);
            } finally {
                pendingForceUnlocks.remove(functionUnitId);
            }
        }, forceUnlockTimeoutSeconds, TimeUnit.SECONDS);

        pendingForceUnlocks.put(functionUnitId, future);
    }

    @Override
    public void respondForceUnlock(Long functionUnitId, String userId, boolean accept) {
        String forceUnlockKey = buildForceUnlockKey(functionUnitId);

        // Read force unlock request from Redis
        if (!cacheService.exists(forceUnlockKey)) {
            log.warn("No force unlock request found for functionUnitId={}, may have expired", functionUnitId);
            return;
        }

        // Verify the responder is the current lock holder
        LockInfoResponse currentLock = readLockInfo(functionUnitId);
        if (currentLock == null || !userId.equals(currentLock.getUserId())) {
            log.warn("Force unlock response from userId={} but lock is held by userId={} for functionUnitId={}",
                    userId, currentLock != null ? currentLock.getUserId() : "none", functionUnitId);
            return;
        }

        // Cancel the pending delayed task
        ScheduledFuture<?> pendingTask = pendingForceUnlocks.remove(functionUnitId);
        if (pendingTask != null) {
            pendingTask.cancel(false);
        }

        if (accept) {
            // Release the lock and delete force-unlock key
            String lockKey = buildKey(functionUnitId);
            cacheService.delete(lockKey);
            cacheService.delete(forceUnlockKey);
            log.info("Force unlock accepted for functionUnitId={} by userId={}", functionUnitId, userId);
        } else {
            // Just delete the force-unlock key (lock remains)
            cacheService.delete(forceUnlockKey);
            log.info("Force unlock rejected for functionUnitId={} by userId={}", functionUnitId, userId);
        }
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
    }

    private String buildKey(Long functionUnitId) {
        return LOCK_KEY_PREFIX + functionUnitId;
    }

    private String buildForceUnlockKey(Long functionUnitId) {
        return FORCE_UNLOCK_KEY_PREFIX + functionUnitId;
    }

    private LockInfoResponse readLockInfo(Long functionUnitId) {
        String key = buildKey(functionUnitId);
        Optional<LockValue> lockOpt = cacheService.get(key, LockValue.class);

        if (lockOpt.isEmpty()) {
            return null;
        }

        LockValue lockValue = lockOpt.get();
        return LockInfoResponse.builder()
                .functionUnitId(functionUnitId)
                .userId(lockValue.userId())
                .userName(lockValue.userName())
                .lockedAt(Instant.parse(lockValue.lockedAt()))
                .locked(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return userNameCache.computeIfAbsent(userId, uid -> {
            try {
                String url = adminCenterUrl + "/api/v1/admin/users/" + uid;
                Map<String, Object> userInfo = restTemplate.getForObject(url, Map.class);
                if (userInfo != null) {
                    String fullName = (String) userInfo.get("fullName");
                    if (fullName != null && !fullName.isEmpty()) return fullName;
                    String displayName = (String) userInfo.get("displayName");
                    if (displayName != null && !displayName.isEmpty()) return displayName;
                    String username = (String) userInfo.get("username");
                    if (username != null && !username.isEmpty()) return username;
                }
            } catch (Exception e) {
                log.warn("Failed to resolve user display name for {}: {}", uid, e.getMessage());
            }
            return uid;
        });
    }

    /**
     * Redis 锁值结构
     */
    private record LockValue(String userId, String userName, String lockedAt) {}

    /**
     * Redis 强制解锁请求结构
     */
    private record ForceUnlockRequest(String requesterId, String requestedAt) {}
}
