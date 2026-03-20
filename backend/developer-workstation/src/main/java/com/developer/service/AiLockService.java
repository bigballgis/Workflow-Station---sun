package com.developer.service;

import com.developer.dto.LockInfoResponse;

/**
 * AI 生成功能分布式锁服务
 * 基于 Redis 实现，确保同一功能单元同一时间仅有一个用户使用 AI 生成功能
 */
public interface AiLockService {

    /**
     * 尝试获取锁
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     * @return 锁信息
     * @throws com.developer.exception.AiLockConflictException 如果锁已被其他用户持有
     */
    LockInfoResponse tryAcquire(Long functionUnitId, String userId);

    /**
     * 释放锁
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     */
    void release(Long functionUnitId, String userId);

    /**
     * 续期锁（重置 TTL）
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         用户 ID
     */
    void extendLock(Long functionUnitId, String userId);

    /**
     * 获取锁信息
     *
     * @param functionUnitId 功能单元 ID
     * @return 锁信息
     */
    LockInfoResponse getLockInfo(Long functionUnitId);

    /**
     * 请求强制解锁
     *
     * @param functionUnitId 功能单元 ID
     * @param requesterId    请求者用户 ID
     */
    void requestForceUnlock(Long functionUnitId, String requesterId);

    /**
     * 响应强制解锁请求
     *
     * @param functionUnitId 功能单元 ID
     * @param userId         响应者用户 ID（当前锁持有者）
     * @param accept         是否同意解锁
     */
    void respondForceUnlock(Long functionUnitId, String userId, boolean accept);
}
