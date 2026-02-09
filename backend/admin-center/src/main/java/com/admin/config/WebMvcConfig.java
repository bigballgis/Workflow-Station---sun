package com.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Web MVC configuration.
 * 
 * Disables static resource handling so all requests route to controllers.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOrigins;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 完全禁用资源处理器
        // 不添加任何资源处理器，这样所有请求都会路由到控制器
        // 注意：不调用 registry.addResourceHandler()，这样就不会注册任何资源处理器
    }
    
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 使用 PathPatternParser 而不是 AntPathMatcher
        // 这样可以更精确地匹配路径，避免静态资源处理器拦截
        configurer.setPatternParser(new PathPatternParser());
        // 禁用后缀模式匹配，避免 .FORM 被当作文件扩展名
        configurer.setUseTrailingSlashMatch(false);
    }
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
