package com.developer.service;

import com.developer.dto.LockInfoResponse;
import com.developer.exception.AiLockConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static com.developer.service.AiLockTestSupport.stubExistingLock;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AiLockService lock exclusivity.
 *
 * <p>Validates: Requirements 11.1, 11.2</p>
 */
@Tag("Feature: ai-function-unit-generation, Property 2: lock exclusivity")
class AiLockExclusivityProperties {

    /**
     * Property 2: Lock Exclusivity
     *
     * For the same functionUnitId, two different userIds cannot simultaneously
     * successfully acquire the lock. When the first user holds the lock,
     * the second user's tryAcquire must throw AiLockConflictException
     * containing the first user's info.
     *
     * <p><b>Validates: Requirements 11.1, 11.2</b></p>
     */
    @Property(tries = 100)
    void lockExclusivity(
            @ForAll @LongRange(min = 1, max = 10000) Long functionUnitId,
            @ForAll @StringLength(min = 1, max = 20) String userId1,
            @ForAll @StringLength(min = 1, max = 20) String userId2) {

        Assume.that(!userId1.equals(userId2));

        // Setup mocks
        CacheService cacheService = mock(CacheService.class);
        UserDisplayNameService userDisplayNameService = mock(UserDisplayNameService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Create service instance via constructor
        AiLockServiceImpl lockService = new AiLockServiceImpl(cacheService, userDisplayNameService);

        // Set @Value fields via reflection
        ReflectionTestUtils.setField(lockService, "ttlSeconds", 1800L);
        ReflectionTestUtils.setField(lockService, "forceUnlockTimeoutSeconds", 60L);

        String lockKey = "ai-gen-lock:" + functionUnitId;

        when(userDisplayNameService.resolve(anyString()))
                .thenAnswer(invocation -> {
                    String uid = invocation.getArgument(0);
                    return "User_" + uid;
                });

        // --- First user acquires the lock successfully ---
        // setIfAbsent returns true for the first user
        when(cacheService.setIfAbsent(eq(lockKey), any(), any(Duration.class)))
                .thenReturn(true)   // first call: user1 acquires
                .thenReturn(false); // second call: user2 fails to acquire

        LockInfoResponse user1Lock = lockService.tryAcquire(functionUnitId, userId1);
        assertThat(user1Lock).isNotNull();
        assertThat(user1Lock.getUserId()).isEqualTo(userId1);
        assertThat(user1Lock.isLocked()).isTrue();

        // --- Second user tries to acquire the same lock ---
        // When setIfAbsent returns false, the service reads existing lock info
        // Build the JSON that would be stored in Redis for user1
        String user1LockJson;
        try {
            Map<String, String> lockValue = Map.of(
                    "userId", userId1,
                    "userName", "User_" + userId1,
                    "lockedAt", Instant.now().toString()
            );
            user1LockJson = objectMapper.writeValueAsString(lockValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        stubExistingLock(cacheService, lockKey, userId1);

        // Second user's tryAcquire should throw AiLockConflictException
        assertThatThrownBy(() -> lockService.tryAcquire(functionUnitId, userId2))
                .isInstanceOf(AiLockConflictException.class)
                .satisfies(ex -> {
                    AiLockConflictException conflictEx = (AiLockConflictException) ex;
                    LockInfoResponse lockInfo = conflictEx.getLockInfo();
                    assertThat(lockInfo).isNotNull();
                    assertThat(lockInfo.getUserId()).isEqualTo(userId1);
                });
    }
}
