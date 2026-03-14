package com.admin.bi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 同步结果响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultResponse {

    /** 新增数量 */
    private int created;

    /** 更新数量 */
    private int updated;

    /** 自动失效数量 */
    private int autoInactivated;

    /** 同步时间 */
    private LocalDateTime syncedAt;
}
