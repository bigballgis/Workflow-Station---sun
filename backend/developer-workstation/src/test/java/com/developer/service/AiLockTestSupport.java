package com.developer.service;

import com.developer.service.impl.AiLockServiceImpl;
import com.platform.cache.service.CacheService;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Shared stubbing helpers for the {@link AiLockServiceImpl} tests.
 *
 * <p>Why this exists: the AI-lock tests used to stub {@code cacheService.getString(key)}, but the
 * service reads its lock through {@code cacheService.get(key, LockValue.class)}. The stub therefore
 * never matched, {@code readLockInfo} returned {@code null}, and the service bailed out before the
 * behaviour under test — every release/extend/force-unlock assertion failed for a reason that had
 * nothing to do with the property being checked.
 *
 * <p>{@code LockValue} is a private record inside {@link AiLockServiceImpl}, so it is built
 * reflectively here rather than duplicated as a test double that could silently drift from the
 * real shape.
 *
 * <p>Note the force-unlock path needs no equivalent helper: it is gated on
 * {@code cacheService.exists(forceUnlockKey)}, not on a typed read.
 */
final class AiLockTestSupport {

    private AiLockTestSupport() {
    }

    /**
     * Stub the lock read so {@code readLockInfo} finds a lock held by {@code userId}.
     *
     * <p>Deliberately does NOT stub {@code exists(key)}: several tests depend on
     * {@code exists} answering false (e.g. asserting that releasing a non-existent lock is a
     * no-op). Tests that need the {@code requestForceUnlock} guard to pass stub it themselves.
     */
    static void stubExistingLock(CacheService cacheService, String lockKey, String userId) {
        Object lockValue = newRecord(
                "com.developer.service.impl.AiLockServiceImpl$LockValue",
                userId, "User_" + userId, Instant.now().toString());
        stubGet(cacheService, lockKey, lockValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubGet(CacheService cacheService, String key, Object value) {
        when(cacheService.get(eq(key), any(Class.class))).thenReturn((Optional) Optional.of(value));
    }

    private static Object newRecord(String binaryName, String... components) {
        try {
            Class<?> type = Class.forName(binaryName);
            Class<?>[] paramTypes = new Class<?>[components.length];
            java.util.Arrays.fill(paramTypes, String.class);
            var ctor = type.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance((Object[]) components);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    binaryName + " no longer has an all-String constructor of "
                            + components.length + " components — update AiLockTestSupport "
                            + "alongside the record in AiLockServiceImpl.", e);
        }
    }
}
