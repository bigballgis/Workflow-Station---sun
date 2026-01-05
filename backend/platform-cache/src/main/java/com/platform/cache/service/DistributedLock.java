package com.platform.cache.service;

/**
 * Distributed lock interface for cross-service synchronization.
 * Validates: Requirements 7.5
 */
public interface DistributedLock extends AutoCloseable {
    
    /**
     * Get the lock key.
     * 
     * @return Lock key
     */
    String getLockKey();
    
    /**
     * Check if the lock is currently held.
     * 
     * @return true if locked, false otherwise
     */
    boolean isLocked();
    
    /**
     * Release the lock.
     */
    void unlock();
    
    /**
     * Extend the lock timeout.
     * 
     * @param additionalSeconds Additional seconds to extend
     * @return true if extended successfully, false otherwise
     */
    boolean extend(long additionalSeconds);
    
    /**
     * Get remaining lock time in seconds.
     * 
     * @return Remaining time in seconds, 0 if expired
     */
    long getRemainingTime();
    
    @Override
    default void close() {
        unlock();
    }
}
