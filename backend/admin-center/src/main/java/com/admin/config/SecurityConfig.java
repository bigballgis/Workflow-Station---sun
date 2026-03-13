package com.admin.config;

import com.admin.audit.AuditRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置
 *
 * <p>当前所有端点使用 permitAll 模式。生产环境中，以下 N8N 相关端点需要特别注意：
 * <ul>
 *   <li>{@code /api/admin/n8n-config/**} - N8N 连接配置 CRUD，需要管理员权限。</li>
 *   <li>{@code GET /api/admin/n8n-config/{id}/internal} - 内部 API，返回解密后的 apiKey，
 *       仅允许 workflow-engine-core 通过 RestTemplate 内部调用。</li>
 *   <li>{@code GET /api/admin/n8n-config/{configId}/workflows} - N8N 工作流列表代理 API。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/data-api/**").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }

    /** Register AuditRequestFilter before all other filters so context is always populated */
    @Bean
    public FilterRegistrationBean<AuditRequestFilter> auditRequestFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<AuditRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuditRequestFilter(objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("auditRequestFilter");
        return registration;
    }
}
