package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 强制解锁响应请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForceUnlockResponseRequest {

    private boolean accept;
}
