package com.platform.security.config;

import com.platform.security.model.User;
import com.platform.security.model.UserStatus;
import com.platform.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

/**
 * Data initializer for test users in development environment.
 * Validates: Requirements 6.5
 */
@Slf4j
@Configuration
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initTestUsers() {
        return args -> {
            log.info("Initializing test users for development environment...");
            
            // Admin Center users (password: admin123)
            createUserIfNotExists("super_admin", "admin123", "超级管理员", "super_admin@example.com", 
                    Set.of("SUPER_ADMIN"));
            createUserIfNotExists("system_admin", "admin123", "系统管理员", "system_admin@example.com", 
                    Set.of("SYSTEM_ADMIN"));
            createUserIfNotExists("tenant_admin", "admin123", "租户管理员", "tenant_admin@example.com", 
                    Set.of("TENANT_ADMIN"));
            createUserIfNotExists("auditor", "admin123", "审计员", "auditor@example.com", 
                    Set.of("AUDITOR"));

            // Developer Workstation users (password: dev123)
            createUserIfNotExists("dev_lead", "dev123", "开发组长", "dev_lead@example.com", 
                    Set.of("DEV_LEAD", "DEVELOPER"));
            createUserIfNotExists("senior_dev", "dev123", "高级开发", "senior_dev@example.com", 
                    Set.of("DEVELOPER"));
            createUserIfNotExists("developer", "dev123", "开发人员", "developer@example.com", 
                    Set.of("DEVELOPER"));
            createUserIfNotExists("designer", "dev123", "流程设计师", "designer@example.com", 
                    Set.of("DESIGNER"));
            createUserIfNotExists("tester", "dev123", "测试人员", "tester@example.com", 
                    Set.of("TESTER"));

            // User Portal users (password: user123)
            createUserIfNotExists("manager", "user123", "部门经理", "manager@example.com", 
                    Set.of("MANAGER", "USER"));
            createUserIfNotExists("team_lead", "user123", "团队主管", "team_lead@example.com", 
                    Set.of("TEAM_LEAD", "USER"));
            createUserIfNotExists("employee_a", "user123", "员工张三", "employee_a@example.com", 
                    Set.of("USER"));
            createUserIfNotExists("employee_b", "user123", "员工李四", "employee_b@example.com", 
                    Set.of("USER"));
            createUserIfNotExists("hr_staff", "user123", "HR专员", "hr_staff@example.com", 
                    Set.of("HR", "USER"));
            createUserIfNotExists("finance", "user123", "财务人员", "finance@example.com", 
                    Set.of("FINANCE", "USER"));

            log.info("Test users initialization completed. Total users: {}", userRepository.count());
        };
    }

    private void createUserIfNotExists(String username, String password, String displayName, 
                                        String email, Set<String> roles) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(username)
                    .passwordHash(passwordEncoder.encode(password))
                    .displayName(displayName)
                    .fullName(displayName)
                    .email(email)
                    .status(UserStatus.ACTIVE)
                    .language("zh_CN")
                    // Don't set roles here - the sys_user_roles table has a different structure
                    .build();
            userRepository.save(user);
            log.debug("Created test user: {} (roles should be assigned separately)", username);
        } else {
            log.debug("User {} already exists, skipping", username);
        }
    }
}
