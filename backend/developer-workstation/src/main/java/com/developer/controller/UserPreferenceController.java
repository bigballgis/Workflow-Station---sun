package com.developer.controller;

import com.developer.component.UserPreferenceComponent;
import com.platform.common.dto.ApiResponse;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

/**
 * 用户 UI 偏好：跨设备/跨浏览器跟随账号的前端布局类设置
 * （如 FU 列表 Launchpad 的排序与分组）。value 为前端自定义 JSON，后端只存取不解析。
 */
@RestController
@RequestMapping("/user-preferences")
@RequiredArgsConstructor
@Tag(name = "User Preferences", description = "Per-user UI preferences (layout, view settings)")
public class UserPreferenceController {

    /** key 白名单模式：小写字母/数字/连字符，防参数篡改类告警 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9-]{1,64}$");
    /** 布局 JSON 上限（64KB），防滥用 */
    private static final int MAX_VALUE_LENGTH = 64 * 1024;
    /** 共享作用域的占位 user_id（真实 userId 均为业务 id，不会冲突） */
    private static final String SHARED_SCOPE_ID = "__shared__";

    private final UserPreferenceComponent userPreferenceComponent;

    @GetMapping("/{key}")
    @Operation(summary = "Get preference value (null when absent); scope=shared reads the platform-wide value")
    public ResponseEntity<ApiResponse<String>> get(
            @PathVariable("key") String key,
            @RequestParam(name = "scope", defaultValue = "user") String scope) {
        String userId = SecurityContextUtils.getCurrentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String ownerId = resolveOwnerId(scope, userId);
        if (ownerId == null || !KEY_PATTERN.matcher(key).matches()) {
            return ResponseEntity.badRequest().build();
        }
        String value = userPreferenceComponent.get(ownerId, key).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(value));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Save preference value; scope=shared writes the platform-wide value (last write wins)")
    public ResponseEntity<ApiResponse<Void>> save(
            @PathVariable("key") String key,
            @RequestParam(name = "scope", defaultValue = "user") String scope,
            @RequestBody PreferenceValueRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String ownerId = resolveOwnerId(scope, userId);
        if (ownerId == null || !KEY_PATTERN.matcher(key).matches()) {
            return ResponseEntity.badRequest().build();
        }
        String value = request.getValue();
        if (value == null || value.length() > MAX_VALUE_LENGTH) {
            return ResponseEntity.badRequest().build();
        }
        userPreferenceComponent.save(ownerId, key, value);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** scope=user → 当前用户 id；scope=shared → 平台共享行；其余 → null（400） */
    private String resolveOwnerId(String scope, String userId) {
        if ("shared".equals(scope)) {
            return SHARED_SCOPE_ID;
        }
        if ("user".equals(scope)) {
            return userId;
        }
        return null;
    }

    @Data
    public static class PreferenceValueRequest {
        private String value;
    }
}
