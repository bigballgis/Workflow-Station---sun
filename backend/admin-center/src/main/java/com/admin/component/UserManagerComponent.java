package com.admin.component;

import com.admin.dto.request.UserCreateRequest;
import com.admin.dto.request.UserQueryRequest;
import com.admin.dto.request.UserUpdateRequest;
import com.admin.dto.response.BatchImportResult;
import com.admin.dto.response.UserCreateResult;
import com.admin.dto.response.UserDetailInfo;
import com.admin.dto.response.UserInfo;
import com.admin.entity.PasswordHistory;
import com.platform.security.entity.User;
import com.platform.security.entity.UserBusinessUnit;
import com.platform.security.model.UserStatus;
import com.admin.exception.*;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.PasswordHistoryRepository;
import com.admin.repository.UserRepository;
import com.platform.common.audit.Audited;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User provisioning: CRUD lifecycle, auditing hooks, batch import scaffolding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserManagerComponent {
    
    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.admin.repository.UserBusinessUnitRepository userBusinessUnitRepository;
    private final I18nService i18nService;

    /** From env USER_RESET_PASSWORD (see application.yml); not logged or returned in API. */
    @Value("${admin.user.reset-password}")
    private String userResetPassword;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    
    /**
     * Create user (active immediately; no outbound email handshake in this pathway).
     */
    @Transactional
    @Audited(action = "USER_CREATE", resourceType = "USER", resourceId = "#result.userId")
    public UserCreateResult createUser(UserCreateRequest request) {
        log.info("Creating user: {}", request.getUsername());
        
        validateEmailFormat(request.getEmail());

        // Re-enable soft-deleted row in place (unique username constraint forbids inserting a second profile)
        Optional<User> existingByUsername = userRepository.findByUsername(request.getUsername());
        if (existingByUsername.isPresent()) {
            User existing = existingByUsername.get();
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                return reactivateSoftDeletedUser(existing, request);
            }
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        validateUsernameUnique(request.getUsername());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AdminBusinessException("EMAIL_EXISTS",
                    i18nService.getMessage("admin.user.email_already_in_use", request.getEmail()));
        }
        
        String encodedPassword = passwordEncoder.encode(request.getInitialPassword());
        String userId = UUID.randomUUID().toString();
        
        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .passwordHash(encodedPassword)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .employeeId(request.getEmployeeId())
                .entityManagerId(request.getEntityManagerId())
                .functionManagerId(request.getFunctionManagerId())
                .position(request.getPosition())
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .passwordExpiredAt(LocalDateTime.now().plusDays(90))
                .failedLoginCount(0)
                .deleted(false)
                .build();
        
        user = userRepository.save(user);
        applyBusinessUnitOnCreate(user.getId(), request.getBusinessUnitId());
        savePasswordHistory(userId, encodedPassword);
        
        log.info("User created successfully: {}", userId);
        return UserCreateResult.success(userId, request.getUsername());
    }

    /**
     * Hydrate a tombstoned username row instead of allocating a conflicting insert.
     */
    private UserCreateResult reactivateSoftDeletedUser(User user, UserCreateRequest request) {
        log.info("Reactivating soft-deleted user: {} ({})", user.getId(), user.getUsername());

        if (userRepository.existsByEmailExcludingUser(request.getEmail(), user.getId())) {
            throw new AdminBusinessException("EMAIL_EXISTS", i18nService.getMessage("admin.user.email_already_in_use", request.getEmail()));
        }

        String encodedPassword = passwordEncoder.encode(request.getInitialPassword());
        user.setPasswordHash(encodedPassword);
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setEmployeeId(request.getEmployeeId());
        user.setPosition(request.getPosition());
        user.setEntityManagerId(request.getEntityManagerId());
        user.setFunctionManagerId(request.getFunctionManagerId());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setPasswordExpiredAt(LocalDateTime.now().plusDays(90));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setDeleted(false);
        user.setDeletedAt(null);
        user.setDeletedBy(null);

        userRepository.save(user);
        applyBusinessUnitOnCreate(user.getId(), request.getBusinessUnitId());
        savePasswordHistory(user.getId(), encodedPassword);

        log.info("User reactivated successfully: {}", user.getId());
        return UserCreateResult.success(user.getId(), user.getUsername());
    }

    private void applyBusinessUnitOnCreate(String userId, String businessUnitId) {
        if (businessUnitId != null && !businessUnitId.isEmpty()) {
            userBusinessUnitRepository.deleteByUserId(userId);
            UserBusinessUnit userBusinessUnit = UserBusinessUnit.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .businessUnitId(businessUnitId)
                    .build();
            userBusinessUnitRepository.save(userBusinessUnit);
        }
    }
    
    /**
     * Persist profile updates originating from administrators.
     */
    @Transactional
    @Audited(action = "USER_UPDATE", resourceType = "USER", resourceId = "#userId")
    public void updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            validateEmailFormat(request.getEmail());
            if (userRepository.existsByEmailExcludingUser(request.getEmail(), userId)) {
                throw new AdminBusinessException("EMAIL_EXISTS", i18nService.getMessage("admin.user.email_already_in_use", request.getEmail()));
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
            userBusinessUnitRepository.deleteByUserId(userId);
            if (!request.getBusinessUnitId().isEmpty()) {
                UserBusinessUnit userBusinessUnit = UserBusinessUnit.builder()
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
        
        if (request.getEntityManagerId() != null) {
            if (!request.getEntityManagerId().isEmpty()) {
                if (!userRepository.existsById(request.getEntityManagerId())) {
                    throw new AdminBusinessException("ENTITY_MANAGER_NOT_FOUND", i18nService.getMessage("admin.user.entity_manager_not_found"));
                }
                user.setEntityManagerId(request.getEntityManagerId());
            } else {
                user.setEntityManagerId(null);
            }
        }
        
        if (request.getFunctionManagerId() != null) {
            if (!request.getFunctionManagerId().isEmpty()) {
                if (!userRepository.existsById(request.getFunctionManagerId())) {
                    throw new AdminBusinessException("FUNCTION_MANAGER_NOT_FOUND", i18nService.getMessage("admin.user.function_manager_not_found"));
                }
                user.setFunctionManagerId(request.getFunctionManagerId());
            } else {
                user.setFunctionManagerId(null);
            }
        }
        
        userRepository.save(user);
        
        log.info("User updated successfully: {}", userId);
    }
    
    /**
     * Transition platform user status respecting allowed lifecycles (active/locked/inactive).
     */
    @Transactional
    @Audited(action = "USER_STATUS_CHANGE", resourceType = "USER", resourceId = "#userId")
    public void updateUserStatus(String userId, UserStatus newStatus, String reason) {
        log.info("Updating user status: {} -> {}", userId, newStatus);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        UserStatus oldStatus = user.getStatus();
        
        validateStatusTransition(oldStatus, newStatus);

        user.setStatus(newStatus);

        if (newStatus == UserStatus.ACTIVE && oldStatus == UserStatus.LOCKED) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        
        userRepository.save(user);
        
        log.info("User status updated: {} from {} to {}", userId, oldStatus, newStatus);
    }
    
    /**
     * Reset credential to administrator-configured rotating secret.
     */
    @Transactional
    @Audited(action = "PASSWORD_RESET", resourceType = "USER", resourceId = "#userId")
    public void resetPassword(String userId) {
        log.info("Resetting password for user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        if (userResetPassword == null || userResetPassword.isBlank()) {
            throw new AdminBusinessException("USER_RESET_PASSWORD_NOT_CONFIGURED",
                    i18nService.getMessage("admin.user.reset_password_not_configured"));
        }
        String encodedPassword = passwordEncoder.encode(userResetPassword);
        
        user.setPasswordHash(encodedPassword);
        user.setMustChangePassword(true);
        user.setPasswordExpiredAt(LocalDateTime.now().plusDays(90));
        
        userRepository.save(user);
        
        savePasswordHistory(userId, encodedPassword);
        
        log.info("Password reset successfully for user: {} (plaintext not returned in API response)", userId);
    }
    
    /**
     * Soft-delete semantics to retain audit trail yet free uniqueness slots.
     */
    @Transactional
    @Audited(action = "USER_DELETE", resourceType = "USER", resourceId = "#userId")
    public void deleteUser(String userId) {
        log.info("Deleting user: {}", userId);
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));

            if (isLastActiveAdmin(user)) {
                throw new AdminBusinessException("USER_005", i18nService.getMessage("admin.user.cannot_delete_last_admin"));
            }
            
            user.setDeleted(true);
            user.setDeletedAt(LocalDateTime.now());
            user.setDeletedBy(getCurrentUserId());
            user.setStatus(UserStatus.INACTIVE);
            user.setUsername(buildDeletedUsername(userId));
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                user.setEmail(buildDeletedEmail(userId));
            }
            
            userRepository.save(user);
            
            log.info("User soft deleted successfully: {}", userId);
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete user: {}", userId, e);
            throw new AdminBusinessException("USER_DELETE_FAILED", i18nService.getMessage("admin.user.delete_failed", e.getMessage()));
        }
    }
    
    /**
     * Guardrail deleting the solitary active SUPER_ADMIN-equivalent boundary.
     */
    private boolean isLastActiveAdmin(User user) {
        if (user == null) {
            return false;
        }
        
        try {
            boolean isAdmin = userRepository.isUserAdmin(user.getId());
            if (!isAdmin) {
                return false;
            }

            long activeAdminCount = userRepository.countActiveAdmins();
            return activeAdminCount <= 1;
        } catch (Exception e) {
            log.error("Error checking if last active admin for user: {}", user.getId(), e);
            return true;
        }
    }

    private String buildDeletedUsername(String userId) {
        return "__deleted__" + userId;
    }

    private String buildDeletedEmail(String userId) {
        return "__deleted__" + userId + "@deleted.local";
    }
    
    /**
     * Request-scoped operator placeholder (future: propagate security principal).
     */
    private String getCurrentUserId() {
        return "system";
    }
    
    /** Fetch persisted {@link User} aggregate. */
    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
    
    /**
     * Enriched projection for admin UI (delegations + managers + roles placeholder hook).
     */
    @Transactional(readOnly = true)
    public UserDetailInfo getUserDetail(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        UserDetailInfo detail = UserDetailInfo.fromEntity(user);

        // Role assignments are queried separately in future iterations (User entity exposes no eager role bag).
        detail.setRoles(java.util.Set.of());

        if (user.getEntityManagerId() != null) {
            userRepository.findById(user.getEntityManagerId())
                    .ifPresentOrElse(
                    manager -> detail.setEntityManagerName(manager.getFullName()),
                    () -> detail.setEntityManagerName(user.getEntityManagerId()));
        }

        if (user.getFunctionManagerId() != null) {
            userRepository.findById(user.getFunctionManagerId())
                    .ifPresentOrElse(
                    manager -> detail.setFunctionManagerName(manager.getFullName()),
                    () -> detail.setFunctionManagerName(user.getFunctionManagerId()));
        }

        // Recent login auditing will plug in via dedicated projections.
        detail.setLoginHistory(List.of());
        
        return detail;
    }
    
    /** Resolve user by immutable login handle. */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }
    
    /** Filtered, paginated directory view for admin grid. */
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
        
        List<User> userList = users.getContent();
        if (userList.isEmpty()) {
            return users.map(UserInfo::fromEntity);
        }
        
        List<String> userIds = userList.stream().map(User::getId).toList();
        
        Map<String, UserBusinessUnit> userBuMap = new HashMap<>();
        List<UserBusinessUnit> allUserBus = userBusinessUnitRepository.findByUserIdIn(userIds);
        for (UserBusinessUnit ubu : allUserBus) {
            userBuMap.putIfAbsent(ubu.getUserId(), ubu);
        }
        
        Set<String> buIds = allUserBus.stream()
                .map(UserBusinessUnit::getBusinessUnitId)
                .collect(Collectors.toSet());
        Map<String, com.platform.security.entity.BusinessUnit> buMap = buIds.isEmpty() 
                ? Collections.emptyMap()
                : businessUnitRepository.findAllById(buIds).stream()
                    .collect(Collectors.toMap(com.platform.security.entity.BusinessUnit::getId, Function.identity()));
        
        Set<String> managerIds = new HashSet<>();
        for (User u : userList) {
            if (u.getEntityManagerId() != null) managerIds.add(u.getEntityManagerId());
            if (u.getFunctionManagerId() != null) managerIds.add(u.getFunctionManagerId());
        }
        Map<String, User> managerMap = managerIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(managerIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
        
        return users.map(user -> {
            UserInfo info = UserInfo.fromEntity(user);
            
            UserBusinessUnit ubu = userBuMap.get(user.getId());
            if (ubu != null) {
                info.setBusinessUnitId(ubu.getBusinessUnitId());
                com.platform.security.entity.BusinessUnit bu = buMap.get(ubu.getBusinessUnitId());
                if (bu != null) {
                    info.setBusinessUnitName(bu.getName());
                }
            }
            
            if (user.getEntityManagerId() != null) {
                User manager = managerMap.get(user.getEntityManagerId());
                if (manager != null) {
                    info.setEntityManagerName(manager.getFullName());
                } else {
                    info.setEntityManagerName(user.getEntityManagerId());
                    info.setEntityManagerId(null);
                }
            }
            
            if (user.getFunctionManagerId() != null) {
                User manager = managerMap.get(user.getFunctionManagerId());
                if (manager != null) {
                    info.setFunctionManagerName(manager.getFullName());
                } else {
                    info.setFunctionManagerName(user.getFunctionManagerId());
                    info.setFunctionManagerId(null);
                }
            }
            
            return info;
        });
    }
    
    /**
     * Batch import driver (file parsing currently stubbed; errors aggregate per logical row).
     */
    @Transactional
    @Audited(action = "BATCH_IMPORT", resourceType = "USER", logResponse = true)
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
                    errors.append(i18nService.getMessage("admin.user.import_line_failed", i + 2));
                    errors.append('\n');
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
            
            log.info("Batch import completed: {} success, {} failed", successCount, failureCount);
            return result;
            
        } catch (Exception e) {
            log.error("Batch import failed", e);
            return resultBuilder
                    .success(false)
                    .errors(i18nService.getMessage("admin.user.import_file_parse_failed"))
                    .endTime(Instant.now())
                    .build();
        }
    }
    
    /** Username uniqueness guard before insert. */
    public void validateUsernameUnique(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
    }
    
    /** RFC5322-lightweight email guard. */
    public void validateEmailFormat(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailException(email);
        }
    }
    
    private void validateStatusTransition(UserStatus from, UserStatus to) {
        boolean valid = switch (from) {
            case ACTIVE -> to == UserStatus.INACTIVE || to == UserStatus.LOCKED;
            case INACTIVE -> to == UserStatus.ACTIVE;
            case LOCKED -> to == UserStatus.ACTIVE;
        };

        if (!valid) {
            throw new AdminBusinessException("INVALID_STATUS_TRANSITION",
                    i18nService.getMessage("admin.user.invalid_status_transition", from, to));
        }
    }

    /** Append-only password lineage for auditing / replay resistance policies. */
    private void savePasswordHistory(String userId, String passwordHash) {
        PasswordHistory history = PasswordHistory.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .passwordHash(passwordHash)
                .createdAt(Instant.now())
                .build();
        passwordHistoryRepository.save(history);
    }
    
    /** Materialize parsed CSV/XLS payload (currently returns empty sentinel). */
    private List<UserCreateRequest> parseImportFile(MultipartFile file) {
        return List.of();
    }
}
