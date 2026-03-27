package com.admin.bi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Guest Token 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTokenResponse {

    /** Superset Guest Token */
    private String token;

    /** Dashboard Embed ID */
    private String dashboardEmbedId;

    /** Superset domain for frontend embedding */
    private String supersetDomain;
}
