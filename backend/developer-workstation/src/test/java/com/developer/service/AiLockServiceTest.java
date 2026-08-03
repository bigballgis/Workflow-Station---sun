package com.developer.service;

import com.developer.dto.LockInfoResponse;
import com.developer.exception.AiLockConflictException;
import com.developer.service.impl.AiLockServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.cache.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static com.developer.service.AiLockTestSupport.stubExistingLock;
import static org.mockito.Mockito.*;

/**
 * AiLockService 单元测试
 * 锁获取/释放/续期具体场景
 */
@ExtendWith(MockitoExtension.class)
class AiLockServiceTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private UserDisplayNameService userDisplayNameService;

    private ObjectMapper objectMapper;

    private AiLockServiceImpl lockService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lockService = new AiLockServiceImpl(cacheService, userDisplayNameService);
        ReflectionTestUtils.setField(lockService, "ttlSeconds", 1800L);
        ReflectionTestUtils.setField(lockService, "forceUnlockTimeoutSeconds", 60L);
    }

    @Test
    void tryAcquire_noExistingLock_shouldSucceed() {
        // setIfAbsent returns true → lock acquired
        when(cacheService.setIfAbsent(eq("ai-gen-lock:1"), any(), eq(Duration.ofSeconds(1800))))
                .thenReturn(true);
        when(userDisplayNameService.resolve("user1")).thenReturn("Test User");

        LockInfoResponse response = lockService.tryAcquire(1L, "user1");

        assertNotNull(response);
        assertEquals(1L, response.getFunctionUnitId());
        assertEquals("user1", response.getUserId());
        assertTrue(response.isLocked());
        assertEquals("Test User", response.getUserName());
    }

    @Test
    void tryAcquire_existingLockByOtherUser_shouldThrow() throws Exception {
        // setIfAbsent returns false → lock already held
        when(cacheService.setIfAbsent(eq("ai-gen-lock:1"), any(), any(Duration.class)))
                .thenReturn(false);
        when(userDisplayNameService.resolve("user1")).thenReturn("User One");

        // Existing lock held by different user
        stubExistingLock(cacheService, "ai-gen-lock:1", "other-user");

        AiLockConflictException ex = assertThrows(AiLockConflictException.class,
                () -> lockService.tryAcquire(1L, "user1"));

        assertNotNull(ex.getLockInfo());
        assertEquals("other-user", ex.getLockInfo().getUserId());
    }

    @Test
    void release_ownLock_shouldSucceed() throws Exception {
        stubExistingLock(cacheService, "ai-gen-lock:1", "user1");

        lockService.release(1L, "user1");

        verify(cacheService).delete("ai-gen-lock:1");
    }

    @Test
    void extendLock_shouldResetTtl() throws Exception {
        stubExistingLock(cacheService, "ai-gen-lock:1", "user1");

        lockService.extendLock(1L, "user1");

        verify(cacheService).expire("ai-gen-lock:1", Duration.ofSeconds(1800));
    }
}
