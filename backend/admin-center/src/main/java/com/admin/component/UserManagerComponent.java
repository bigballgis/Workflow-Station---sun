package com.admin.component;

import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserQueryRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.dto.response.UserCreateResult;
import com.admin.dto.response.UserInfo;
import com.admin.entity.PasswordHistory;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.exception.*;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserRepository;
import com.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户管理组件
 * 负责用户的创建、更新、状态管理、批量导入等核心功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserManagerComponent {
    
    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    
    /**
     * 创建用户 - 立即激活，无需邮件验证
     */
    @Transactional
    public UserCreateResult createUser(UserCreateRequest request) {
        log.info("Creating user: {}", request.getUsername());
        
        // 验证用户名唯一性
        validateUsernameUnique(request.getUsername());
        
        // 验证邮箱格式
        validateEmailFormat(request.getEmail());
        
        // 验证邮箱唯一性
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AdminBusinessException("EMAIL_EXISTS", "邮箱已被使用: " + request.getEmail());
        }
        
        // 创建用户
        String userId = UUID.randomUUID().toString();
        String encodedPassword = passwordEncoder.encode(request.getInitialPassword());
        
        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .passwordHash(encodedPassword)
                .email(request.getEmail())
                .phone(request.getPhone())
                .fullName(request.getFullName())
                .employeeId(request.getEmployeeId())
                .departmentId(request.getDepartmentId())
                .position(request.getPosition())
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .passwordExpiredAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .failedLoginCount(0)
                .build();
        
        userRepository.save(user);
        
        // 保存密码历史
        savePasswordHistory(userId, encodedPassword);
        
        // 记录审计日志
        auditService.recordUserCreation(user);
        
        log.info("User created successfully: {}", userId);
        return UserCreateResult.success(userId, request.getUsername());
    }
    
    /**
     * 更新用户信息
     */
    @Transactional
    public void updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        User oldUser = User.builder()
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .departmentId(user.getDepartmentId())
                .position(user.getPosition())
                .build();
        
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            validateEmailFormat(request.getEmail());
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new AdminBusinessException("EMAIL_EXISTS", "邮箱已被使用: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getDepartmentId() != null) {
            user.setDepartmentId(request.getDepartmentId());
        }
        if (request.getPosition() != null) {
            user.setPosition(request.getPosition());
        }
        
        userRepository.save(user);
        
        // 记录审计日志
        auditService.recordUserUpdate(user, oldUser);
        
        log.info("User updated successfully: {}", userId);
    }
    
    /**
     * 更新用户状态
     */
    @Transactional
    public void updateUserStatus(String userId, UserStatus newStatus, String reason) {
        log.info("Updating user status: {} -> {}", userId, newStatus);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        UserStatus oldStatus = user.getStatus();
        
        // 验证状态转换
        validateStatusTransition(oldStatus, newStatus);
        
        user.setStatus(newStatus);
        
        // 如果是解锁操作，重置锁定相关字段
        if (newStatus == UserStatus.ACTIVE && oldStatus == UserStatus.LOCKED) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        
        userRepository.save(user);
        
        // 记录审计日志
        auditService.recordStatusChange(user, oldStatus, newStatus, reason);
        
        log.info("User status updated: {} from {} to {}", userId, oldStatus, newStatus);
    }
    
    /**
     * 重置用户密码
     */
    @Transactional
    public String resetPassword(String userId) {
        log.info("Resetting password for user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        // 生成新密码
        String newPassword = generateRandomPassword();
        String encodedPassword = passwordEncoder.encode(newPassword);
        
        user.setPasswordHash(encodedPassword);
        user.setMustChangePassword(true);
        user.setPasswordExpiredAt(Instant.now().plus(90, ChronoUnit.DAYS));
        
        userRepository.save(user);
        
        // 保存密码历史
        savePasswordHistory(userId, encodedPassword);
        
        // 记录审计日志
        auditService.recordPasswordReset(user);
        
        log.info("Password reset successfully for user: {}", userId);
        return newPassword;
    }
    
    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(String userId) {
        log.info("Deleting user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        // 删除密码历史
        passwordHistoryRepository.deleteByUserId(userId);
        
        // 删除用户
        userRepository.delete(user);
        
        // 记录审计日志
        auditService.recordUserDeletion(user);
        
        log.info("User deleted successfully: {}", userId);
    }
    
    /**
     * 获取用户详情
     */
    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
    
    /**
     * 根据用户名获取用户
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }
    
    /**
     * 分页查询用户
     */
    public Page<UserInfo> listUsers(UserQueryRequest request) {
        Pageable pageable = PageRequest.of(
                request.getPage(), 
                request.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<User> users = userRepository.findByConditions(
                request.getDepartmentId(),
                request.getStatus(),
                request.getKeyword(),
                pageable);
        
        return users.map(UserInfo::fromEntity);
    }
    
    /**
     * 批量导入用户
     */
    @Transactional
    public BatchImportResult batchImportUsers(MultipartFile file) {
        log.info("Starting batch import from file: {}", file.getOriginalFilename());
        
        BatchImportResult.BatchImportResultBuilder resultBuilder = BatchImportResult.builder()
                .fileName(file.getOriginalFilename())
                .startTime(Instant.now());
        
        try {
            // 解析文件
            List<UserCreateRequest> users = parseImportFile(file);
            
            int successCount = 0;
            int failureCount = 0;
            StringBuilder errors = new StringBuilder();
            
            for (int i = 0; i < users.size(); i++) {
                UserCreateRequest userRequest = users.get(i);
                try {
                    createUser(userRequest);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.append(String.format("行 %d: %s\n", i + 2, e.getMessage()));
                }
            }
            
            BatchImportResult result = resultBuilder
                    .totalCount(users.size())
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .errors(errors.toString())
                    .endTime(Instant.now())
                    .success(failureCount == 0)
                    .build();
            
            // 记录审计日志
            auditService.recordBatchImport(result);
            
            log.info("Batch import completed: {} success, {} failed", successCount, failureCount);
            return result;
            
        } catch (Exception e) {
            log.error("Batch import failed", e);
            return resultBuilder
                    .success(false)
                    .errors("文件解析失败: " + e.getMessage())
                    .endTime(Instant.now())
                    .build();
        }
    }
    
    /**
     * 验证用户名唯一性
     */
    public void validateUsernameUnique(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
    }
    
    /**
     * 验证邮箱格式
     */
    public void validateEmailFormat(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailException(email);
        }
    }
    
    /**
     * 验证状态转换
     */
    private void validateStatusTransition(UserStatus from, UserStatus to) {
        // 定义有效的状态转换
        boolean valid = switch (from) {
            case ACTIVE -> to == UserStatus.DISABLED || to == UserStatus.LOCKED;
            case DISABLED -> to == UserStatus.ACTIVE;
            case LOCKED -> to == UserStatus.ACTIVE;
            case PENDING -> to == UserStatus.ACTIVE || to == UserStatus.DISABLED;
        };
        
        if (!valid) {
            throw new AdminBusinessException("INVALID_STATUS_TRANSITION",
                    String.format("无效的状态转换: %s -> %s", from, to));
        }
    }
    
    /**
     * 保存密码历史
     */
    private void savePasswordHistory(String userId, String passwordHash) {
        PasswordHistory history = PasswordHistory.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .passwordHash(passwordHash)
                .createdAt(Instant.now())
                .build();
        passwordHistoryRepository.save(history);
    }
    
    /**
     * 生成随机密码
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder password = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }
    
    /**
     * 解析导入文件
     */
    private List<UserCreateRequest> parseImportFile(MultipartFile file) {
        // 简化实现，实际应该使用 Apache POI 或 OpenCSV
        // 这里返回空列表，实际实现需要解析 Excel/CSV 文件
        return List.of();
    }
}
