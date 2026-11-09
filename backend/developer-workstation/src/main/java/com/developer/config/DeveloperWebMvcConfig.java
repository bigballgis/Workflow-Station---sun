package com.developer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class DeveloperWebMvcConfig implements WebMvcConfigurer {

    /** Routes whose function unit (in the path, or reached through a sub-resource) is authorized per team. */
    public static final String[] WORKSPACE_PATHS = {
            "/function-units/**",
            "/export-import/function-units/**",
            "/api/forms/**",
            "/actions/**",
            "/ai-generation/**"
    };

    private final FunctionUnitWorkspaceAccessInterceptor functionUnitWorkspaceAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(functionUnitWorkspaceAccessInterceptor).addPathPatterns(WORKSPACE_PATHS);
    }
}
