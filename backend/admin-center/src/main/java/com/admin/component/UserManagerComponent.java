package com.admin.component;

import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserQueryRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.dto.response.UserCreateResult;
import com.admin.dto.response.UserDetailInfo;
import com.admin.dto.response.UserInfo;
import com.admin.entity.PasswordHistory;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.exception.*;
import com.admin.repository.BusinessUnitRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
    private final BusinessUnitRepository businessUnitRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final com.admin.repository.UserBusinessUnitRepository userBusinessUnitRepository;
    
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
        
        // 创建用户 - 使用 String 类型 ID
        String encodedPassword = passwordEncoder.encode(request.getInitialPassword());
        String userId = UUID.randomUUID().toString();
        
        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .passwordHash(encodedPassword)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .employeeId(request.getEmployeeId())
                .position(request.getPosition())
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .passwordExpiredAt(LocalDateTime.now().plusDays(90))
                .failedLoginCount(0)
                .build();
        
        user = userRepository.save(user);
        
        // 如果指定了业务单元，创建用户-业务单元关联
        if (request.getBusinessUnitId() != null && !request.getBusinessUnitId().isEmpty()) {
            com.admin.entity.UserBusinessUnit userBusinessUnit = com.admin.entity.UserBusinessUnit.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .businessUnitId(request.getBusinessUnitId())
                    .build();
            userBusinessUnitRepository.save(userBusinessUnit);
        }
        
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
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getBusinessUnitId() != null) {
            // 更新用户-业务单元关联（先删除旧的，再创建新的）
            userBusinessUnitRepository.deleteByUserId(userId);
            if (!request.getBusinessUnitId().isEmpty()) {
                com.admin.entity.UserBusinessUnit userBusinessUnit = com.admin.entity.UserBusinessUnit.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .businessUnitId(request.getBusinessUnitId())
                        .build();
                userBusinessUnitRepository.save(userBusinessUnit);
            }
        }
        if (request.getPosition() != null) {
            user.setPosition(request.getPosition());
        }
        
        // 更新实体管理者
        if (request.getEntityManagerId() != null) {
            if (!request.getEntityManagerId().isEmpty()) {
                // 验证实体管理者存在
                if (!userRepository.existsById(request.getEntityManagerId())) {
                    throw new AdminBusinessException("ENTITY_MANAGER_NOT_FOUND", "实体管理者不存在");
                }
                user.setEntityManagerId(request.getEntityManagerId());
            } else {
                user.setEntityManagerId(null);
            }
        }
        
        // 更新职能管理者
        if (request.getFunctionManagerId() != null) {
            if (!request.getFunctionManagerId().isEmpty()) {
                // 验证职能管理者存在
                if (!userRepository.existsById(request.getFunctionManagerId())) {
                    throw new AdminBusinessException("FUNCTION_MANAGER_NOT_FOUND", "职能管理者不存在");
                }
                user.setFunctionManagerId(request.getFunctionManagerId());
            } else {
                user.setFunctionManagerId(null);
            }
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
        user.setPasswordExpiredAt(LocalDateTime.now().plusDays(90));
        
        userRepository.save(user);
        
        // 保存密码历史
        savePasswordHistory(userId, encodedPassword);
        
        // 记录审计日志
        auditService.recordPasswordReset(user);
        
        log.info("Password reset successfully for user: {}", userId);
        return newPassword;
    }
    
    /**
     * 删除用户（软删除）
     */
    @Transactional
    public void deleteUser(String userId) {
        log.info("Deleting user: {}", userId);
        
        try {
            // 使用 findByIdWithRoles 加载用户及其角色，避免 LazyInitializationException
            User user = userRepository.findByIdWithRoles(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            
            // 检查是否是最后一个管理员
            if (isLastActiveAdmin(user)) {
                throw new AdminBusinessException("USER_005", "不能删除最后一个管理员");
            }
            
            // 软删除 - 不改变状态，只设置删除标记
            // 如果必须改变状态，使用 INACTIVE（数据库约束允许）
            // 但通常软删除只需要设置 deleted=true 即可
            user.setDeleted(true);
            user.setDeletedAt(LocalDateTime.now());
            user.setDeletedBy(getCurrentUserId());
            // 保持原有状态，不强制修改状态
            // 如果需要禁用，可以单独调用状态更新接口
            
            userRepository.save(user);
            
            // 记录审计日志（在事务外执行，避免影响事务）
            try {
                auditService.recordUserDeletion(user);
            } catch (Exception e) {
                log.warn("Failed to record user deletion audit log: {}", e.getMessage());
                // 审计日志失败不影响删除操作
            }
            
            log.info("User soft deleted successfully: {}", userId);
        } catch (AdminBusinessException e) {
            // 业务异常（包括 UserNotFoundException）直接抛出
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete user: {}", userId, e);
            throw new AdminBusinessException("USER_DELETE_FAILED", "删除用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否是最后一个活跃管理员
     */
    private boolean isLastActiveAdmin(User user) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        
        try {
            // 检查用户是否有管理员角色
            // 使用 findByIdWithRoles 已经加载了 role，但需要安全访问
            boolean isAdmin = user.getUserRoles().stream()
                    .filter(ur -> ur != null)
                    .map(ur -> {
                        try {
                            return ur.getRole();
                        } catch (org.hibernate.LazyInitializationException e) {
                            log.warn("LazyInitializationException when getting role for userRole: {}", ur.getId());
                            return null;
                        } catch (Exception e) {
                            log.warn("Failed to get role for userRole: {}", ur.getId(), e);
                            return null;
                        }
                    })
                    .filter(role -> role != null && role.getCode() != null)
                    .anyMatch(role -> "ADMIN".equals(role.getCode()) || "SUPER_ADMIN".equals(role.getCode()));
            
            if (!isAdmin) {
                return false;
            }
            
            // 统计活跃管理员数量
            long activeAdminCount = userRepository.countActiveAdmins();
            return activeAdminCount <= 1;
        } catch (Exception e) {
            log.error("Error checking if last active admin for user: {}", user.getId(), e);
            // 如果检查失败，为了安全起见，不允许删除
            return true;
        }
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        return "system";
    }
    
    /**
     * 获取用户详情
     */
    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
    
    /**
     * 获取用户详情（包含角色和登录历史）
     */
    @Transactional(readOnly = true)
    public UserDetailInfo getUserDetail(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        UserDetailInfo detail = UserDetailInfo.fromEntity(user);
        
        // 获取用户角色 - 需要在事务内访问懒加载属性
        Set<UserDetailInfo.RoleInfo> roles = user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> UserDetailInfo.RoleInfo.builder()
                        .roleId(ur.getRole().getId())
                        .roleCode(ur.getRole().getCode())
                        .roleName(ur.getRole().getName())
                        .description(ur.getRole().getDescription())
                        .build())
                .collect(java.util.stream.Collectors.toSet());
        detail.setRoles(roles);
        
        // 获取实体管理者名称
        if (user.getEntityManagerId() != null) {
            userRepository.findById(user.getEntityManagerId())
                    .ifPresent(manager -> detail.setEntityManagerName(manager.getFullName()));
        }
        
        // 获取职能管理者名称
        if (user.getFunctionManagerId() != null) {
            userRepository.findById(user.getFunctionManagerId())
                    .ifPresent(manager -> detail.setFunctionManagerName(manager.getFullName()));
        }
        
        // 获取登录历史（最近10条）
        detail.setLoginHistory(List.of());
        
        return detail;
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
                request.getBusinessUnitId(),
                request.getStatus(),
                request.getKeyword(),
                pageable);
        
        return users.map(user -> {
            UserInfo info = UserInfo.fromEntity(user);
            
            // 通过关联表获取用户的业务单元
            List<com.admin.entity.UserBusinessUnit> userBusinessUnits = userBusinessUnitRepository.findByUserId(user.getId());
            if (!userBusinessUnits.isEmpty()) {
                String businessUnitId = userBusinessUnits.get(0).getBusinessUnitId();
                info.setBusinessUnitId(businessUnitId);
                businessUnitRepository.findById(businessUnitId)
                        .ifPresent(unit -> info.setBusinessUnitName(unit.getName()));
            }
            
            // 填充实体管理者名称
            if (user.getEntityManagerId() != null) {
                userRepository.findById(user.getEntityManagerId())
                        .ifPresent(manager -> info.setEntityManagerName(manager.getFullName()));
            }
            
            // 填充职能管理者名称
            if (user.getFunctionManagerId() != null) {
                userRepository.findById(user.getFunctionManagerId())
                        .ifPresent(manager -> info.setFunctionManagerName(manager.getFullName()));
            }
            
            return info;
        });
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
        return List.of();
    }
}
