package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 锁信息响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockInfoResponse {

    private Long functionUnitId;

    private String userId;

    private String userName;

    private Instant lockedAt;

    private boolean locked;
}
