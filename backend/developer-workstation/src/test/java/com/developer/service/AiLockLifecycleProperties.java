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
import static com.developer.service.AiLockTestSupport.stubExistingLock;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AiLockService lock lifecycle consistency.
 *
 * <p><b>Validates: Requirements 1.8, 11.4, 11.6, 11.7</b></p>
 */
@Tag("Feature: ai-function-unit-generation, Property 3: 锁生命周期一致性")
class AiLockLifecycleProperties {

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

    /**
     * Property 3 - Test 1: Release deletes lock from Redis.
     *
     * After a user acquires a lock and then releases it,
     * cacheService.delete should be called with the correct lock key.
     *
     * <p><b>Validates: Requirements 1.8, 11.4</b></p>
     */
    @Property(tries = 100)
    void releaseDeletesLockFromRedis(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId) {

        CacheService cacheService = mock(CacheService.class);
        AiLockServiceImpl lockService = createService(cacheService);

        String lockKey = "ai-gen-lock:" + functionUnitId;

        // User acquires lock successfully
        when(cacheService.setIfAbsent(eq(lockKey), any(), any(Duration.class)))
                .thenReturn(true);

        lockService.tryAcquire(functionUnitId, userId);

        // Setup for release: readLockInfo needs to find the lock owned by this user
        stubExistingLock(cacheService, lockKey, userId);

        // User releases lock
        lockService.release(functionUnitId, userId);

        // Verify: delete was called with the correct key
        verify(cacheService).delete(lockKey);
    }

    /**
     * Property 3 - Test 2: ExtendLock resets TTL to 30 minutes.
     *
     * After a user acquires a lock and then extends it,
     * cacheService.expire should be called with the correct key and 1800-second duration.
     *
     * <p><b>Validates: Requirements 11.6, 11.7</b></p>
     */
    @Property(tries = 100)
    void extendLockResetsTtl(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId) {

        CacheService cacheService = mock(CacheService.class);
        AiLockServiceImpl lockService = createService(cacheService);

        String lockKey = "ai-gen-lock:" + functionUnitId;

        // User acquires lock successfully
        when(cacheService.setIfAbsent(eq(lockKey), any(), any(Duration.class)))
                .thenReturn(true);

        lockService.tryAcquire(functionUnitId, userId);

        // Setup for extendLock: readLockInfo needs to find the lock owned by this user
        stubExistingLock(cacheService, lockKey, userId);

        // User extends lock
        lockService.extendLock(functionUnitId, userId);

        // Verify: expire was called with the correct key and 30-minute duration
        verify(cacheService).expire(lockKey, Duration.ofSeconds(1800));
    }
}
