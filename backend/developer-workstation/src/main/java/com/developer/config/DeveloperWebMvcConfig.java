package com.developer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class DeveloperWebMvcConfig implements WebMvcConfigurer {

    private final FunctionUnitWorkspaceAccessInterceptor functionUnitWorkspaceAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(functionUnitWorkspaceAccessInterceptor)
                .addPathPatterns(
                        "/function-units/**",
                        "/export-import/function-units/**",
                        "/ai-generation/**"
                );
    }
}
