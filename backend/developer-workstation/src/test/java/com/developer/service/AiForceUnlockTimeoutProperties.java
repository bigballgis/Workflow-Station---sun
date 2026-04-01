package com.developer.service;

import com.developer.service.impl.AiLockServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.cache.service.CacheService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AiLockService force unlock timeout auto-release.
 *
 * <p><b>Validates: Requirements 12.4, 12.6</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 15: 强制解锁超时自动释放")
class AiForceUnlockTimeoutProperties {

    private AiLockServiceImpl createService(CacheService cacheService) {
        UserDisplayNameService userDisplayNameService = mock(UserDisplayNameService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        when(userDisplayNameService.resolve(anyString()))
                .thenAnswer(invocation -> {
                    String uid = invocation.getArgument(0);
                    return "User_" + uid;
                });

        AiLockServiceImpl lockService = new AiLockServiceImpl(cacheService, userDisplayNameService);
        ReflectionTestUtils.setField(lockService, "ttlSeconds", 1800L);
        ReflectionTestUtils.setField(lockService, "forceUnlockTimeoutSeconds", 60L);
        return lockService;
    }

    private String buildLockJson(ObjectMapper objectMapper, String userId) {
        try {
            Map<String, String> lockValue = Map.of(
                    "userId", userId,
                    "userName", "User_" + userId,
                    "lockedAt", Instant.now().toString()
            );
            return objectMapper.writeValueAsString(lockValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildForceUnlockJson(ObjectMapper objectMapper, String requesterId) {
        try {
            Map<String, String> forceUnlockValue = Map.of(
                    "requesterId", requesterId,
                    "requestedAt", Instant.now().toString()
            );
            return objectMapper.writeValueAsString(forceUnlockValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Property 15 - Test 1: Force unlock request stores request in Redis and schedules auto-release.
     *
     * When user1 holds a lock and user2 requests force unlock,
     * cacheService.set should be called with the force-unlock key and a TTL of 60 seconds.
     *
     * <p><b>Validates: Requirements 12.4, 12.6</b></p>
     */
    @Property(tries = 100)
    void forceUnlockRequestStoresInRedisWithTimeout(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId1,
            @ForAll @StringLength(min = 1, max = 20) String userId2) {

        Assume.that(!userId1.equals(userId2));

        CacheService cacheService = mock(CacheService.class);
        AiLockServiceImpl lockService = createService(cacheService);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        String lockKey = "ai-gen-lock:" + functionUnitId;
        String forceUnlockKey = "ai-gen-force-unlock:" + functionUnitId;

        // user1 acquires lock
        when(cacheService.setIfAbsent(eq(lockKey), anyString(), any(Duration.class)))
                .thenReturn(true);
        lockService.tryAcquire(functionUnitId, userId1);

        // Setup: lock exists in Redis, held by user1
        String lockJson = buildLockJson(objectMapper, userId1);
        when(cacheService.getString(lockKey)).thenReturn(Optional.of(lockJson));

        // user2 requests force unlock
        lockService.requestForceUnlock(functionUnitId, userId2);

        // Verify: force-unlock key was stored with 60-second TTL
        verify(cacheService).set(eq(forceUnlockKey), anyString(), eq(Duration.ofSeconds(60)));
    }

    /**
     * Property 15 - Test 2: respondForceUnlock with accept=true releases the lock.
     *
     * When user1 holds a lock, user2 requests force unlock, and user1 responds with accept=true,
     * both the lock key and force-unlock key should be deleted.
     *
     * <p><b>Validates: Requirements 12.4, 12.6</b></p>
     */
    @Property(tries = 100)
    void respondForceUnlockAcceptReleasesLock(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId1,
            @ForAll @StringLength(min = 1, max = 20) String userId2) {

        Assume.that(!userId1.equals(userId2));

        CacheService cacheService = mock(CacheService.class);
        AiLockServiceImpl lockService = createService(cacheService);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        String lockKey = "ai-gen-lock:" + functionUnitId;
        String forceUnlockKey = "ai-gen-force-unlock:" + functionUnitId;

        // user1 acquires lock
        when(cacheService.setIfAbsent(eq(lockKey), anyString(), any(Duration.class)))
                .thenReturn(true);
        lockService.tryAcquire(functionUnitId, userId1);

        // Setup: lock exists, held by user1
        String lockJson = buildLockJson(objectMapper, userId1);
        when(cacheService.getString(lockKey)).thenReturn(Optional.of(lockJson));

        // user2 requests force unlock
        lockService.requestForceUnlock(functionUnitId, userId2);

        // Setup: force-unlock key exists in Redis
        String forceUnlockJson = buildForceUnlockJson(objectMapper, userId2);
        when(cacheService.getString(forceUnlockKey)).thenReturn(Optional.of(forceUnlockJson));

        // user1 responds with accept=true
        lockService.respondForceUnlock(functionUnitId, userId1, true);

        // Verify: both lock key and force-unlock key are deleted
        verify(cacheService).delete(lockKey);
        verify(cacheService).delete(forceUnlockKey);
    }

    /**
     * Property 15 - Test 3: respondForceUnlock with accept=false keeps the lock.
     *
     * When user1 holds a lock, user2 requests force unlock, and user1 responds with accept=false,
     * only the force-unlock key should be deleted; the lock key should remain.
     *
     * <p><b>Validates: Requirements 12.4, 12.6</b></p>
     */
    @Property(tries = 100)
    void respondForceUnlockRejectKeepsLock(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId1,
            @ForAll @StringLength(min = 1, max = 20) String userId2) {

        Assume.that(!userId1.equals(userId2));

        CacheService cacheService = mock(CacheService.class);
        AiLockServiceImpl lockService = createService(cacheService);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        String lockKey = "ai-gen-lock:" + functionUnitId;
        String forceUnlockKey = "ai-gen-force-unlock:" + functionUnitId;

        // user1 acquires lock
        when(cacheService.setIfAbsent(eq(lockKey), anyString(), any(Duration.class)))
                .thenReturn(true);
        lockService.tryAcquire(functionUnitId, userId1);

        // Setup: lock exists, held by user1
        String lockJson = buildLockJson(objectMapper, userId1);
        when(cacheService.getString(lockKey)).thenReturn(Optional.of(lockJson));

        // user2 requests force unlock
        lockService.requestForceUnlock(functionUnitId, userId2);

        // Setup: force-unlock key exists in Redis
        String forceUnlockJson = buildForceUnlockJson(objectMapper, userId2);
        when(cacheService.getString(forceUnlockKey)).thenReturn(Optional.of(forceUnlockJson));

        // user1 responds with accept=false
        lockService.respondForceUnlock(functionUnitId, userId1, false);

        // Verify: only force-unlock key is deleted
        verify(cacheService).delete(forceUnlockKey);

        // Verify: lock key was NOT deleted (only the force-unlock key delete should have happened)
        verify(cacheService, never()).delete(lockKey);
    }
}
