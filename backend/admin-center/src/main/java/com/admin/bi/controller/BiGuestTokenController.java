package com.admin.bi.controller;

import com.admin.bi.dto.request.GuestTokenRequest;
import com.admin.bi.dto.response.GuestTokenResponse;
import com.admin.bi.service.BiGuestTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Guest Token 控制器
 * 负责获取 Superset Guest Token 以支持前端嵌入式 Dashboard 渲染
 */
@RestController
@RequestMapping("/bi/guest-token")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Guest Token 管理", description = "获取 Superset Guest Token 接口")
public class BiGuestTokenController {

    private final BiGuestTokenService guestTokenService;

    @PostMapping
    @Operation(summary = "获取 Guest Token", description = "验证用户 Dashboard 分配权限并获取 Superset Guest Token")
    public ResponseEntity<GuestTokenResponse> getGuestToken(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid GuestTokenRequest request) {
        log.info("User {} requesting guest token for dashboard {}", userId, request.getDashboardId());
        GuestTokenResponse response = guestTokenService.getGuestToken(userId, request);
        return ResponseEntity.ok(response);
    }
}
