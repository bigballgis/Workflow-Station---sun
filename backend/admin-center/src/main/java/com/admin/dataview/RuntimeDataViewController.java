package com.admin.dataview;

import com.admin.config.InternalServiceAuthenticator;
import com.platform.common.dataview.RuntimeDataView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/data-views")
@RequiredArgsConstructor
public class RuntimeDataViewController {
    private final InternalServiceAuthenticator authentication;
    private final RuntimeDataViewCatalog catalog;

    @GetMapping("/administrator")
    public com.platform.common.dto.ApiResponse<Boolean> administrator(HttpServletRequest request) {
        return com.platform.common.dto.ApiResponse.success(
                catalog.isAdministrator(authentication.requireDelegatedUserId(request)));
    }

    @GetMapping
    public List<RuntimeDataView> list(HttpServletRequest request) {
        return catalog.list(authentication.requireDelegatedUserId(request), false);
    }
    @GetMapping("/{viewId}")
    public RuntimeDataView definition(HttpServletRequest request, @PathVariable Long viewId) {
        return catalog.require(authentication.requireDelegatedUserId(request), viewId);
    }
}
