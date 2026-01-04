package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 缓存操作请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheOperationRequest {
    
    /**
     * 缓存键
     */
    private String key;
    
    /**
     * 缓存键列表（批量操作）
     */
    private List<String> keys;
    
    /**
     * 缓存值
     */
    private Object value;
    
    /**
     * 过期时间（秒）
     */
    private Long ttlSeconds;
    
    /**
     * 缓存类型
     */
    private CacheType cacheType;
    
    /**
     * 缓存类型枚举
     */
    public enum CacheType {
        PROCESS_DEFINITION,
        PROCESS_INSTANCE,
        TASK,
        VARIABLE,
        USER,
        STATISTICS
    }
}
