# Design Document: User Management

## Overview

用户管理模块提供完整的用户 CRUD 功能，采用 Spring Boot 3.2 + JPA 实现后端服务，Vue 3 + Element Plus 实现前端界面。后端提供 RESTful API，前端通过 API Gateway 调用后端服务。

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Admin Center (Vue 3)                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ User List   │  │ User Form   │  │ User Import Dialog      │  │
│  │ Component   │  │ Component   │  │ Component               │  │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘  │
│         │                │                      │                │
│         └────────────────┼──────────────────────┘                │
│                          │                                       │
│                    ┌─────▼─────┐                                 │
│                    │ User API  │                                 │
│                    │ Service   │                                 │
│                    └─────┬─────┘                                 │
└──────────────────────────┼───────────────────────────────────────┘
                           │ HTTP
                    ┌──────▼──────┐
                    │ API Gateway │
                    │   :8080     │
                    └──────┬──────┘
                           │
┌──────────────────────────┼───────────────────────────────────────┐
│                   Admin Center Backend                           │
│                    ┌─────▼─────┐                                 │
│                    │   User    │                                 │
│                    │Controller │                                 │
│                    └─────┬─────┘                                 │
│                          │                                       │
│                    ┌─────▼─────┐                                 │
│                    │   User    │                                 │
│                    │  Service  │                                 │
│                    └─────┬─────┘                                 │
│                          │                                       │
│         ┌────────────────┼────────────────┐                      │
│         │                │                │                      │
│   ┌─────▼─────┐   ┌──────▼──────┐  ┌─────▼─────┐                │
│   │   User    │   │    Role     │  │  Audit    │                │
│   │Repository │   │ Repository  │  │Repository │                │
│   └─────┬─────┘   └──────┬──────┘  └─────┬─────┘                │
│         │                │               │                       │
└─────────┼────────────────┼───────────────┼───────────────────────┘
          │                │               │
          └────────────────┼───────────────┘
                           │
                    ┌──────▼──────┐
                    │ PostgreSQL  │
                    │   Database  │
                    └─────────────┘
```

## Components and Interfaces

### Backend Components

#### UserController
REST 控制器，处理用户管理 HTTP 请求。

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    // GET /api/v1/users - 分页查询用户列表
    // GET /api/v1/users/{id} - 获取用户详情
    // POST /api/v1/users - 创建用户
    // PUT /api/v1/users/{id} - 更新用户
    // DELETE /api/v1/users/{id} - 删除用户
    // PATCH /api/v1/users/{id}/status - 更新用户状态
    // POST /api/v1/users/{id}/reset-password - 重置密码
    // POST /api/v1/users/import - 批量导入
    // GET /api/v1/users/export-template - 下载导入模板
}
```

#### UserService
用户业务逻辑服务接口。

```java
public interface UserService {
    Page<UserDTO> findUsers(UserQueryRequest query, Pageable pageable);
    UserDetailDTO findById(UUID id);
    UserDTO create(CreateUserRequest request);
    UserDTO update(UUID id, UpdateUserRequest request);
    void delete(UUID id);
    void updateStatus(UUID id, UserStatus status);
    String resetPassword(UUID id);
    ImportResult importUsers(MultipartFile file);
    byte[] getImportTemplate();
}
```

#### UserRepository
用户数据访问层。

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Page<User> findByDeletedFalse(Pageable pageable);
    
    @Query("SELECT u FROM User u WHERE u.deleted = false " +
           "AND (:username IS NULL OR u.username LIKE %:username%) " +
           "AND (:email IS NULL OR u.email LIKE %:email%) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND (:departmentId IS NULL OR u.departmentId = :departmentId)")
    Page<User> findByFilters(String username, String email, 
                             UserStatus status, String departmentId, 
                             Pageable pageable);
}
```

### Frontend Components

#### UserListView
用户列表页面组件。

```typescript
interface UserListState {
  users: UserDTO[];
  total: number;
  loading: boolean;
  query: UserQueryRequest;
  pagination: { page: number; pageSize: number };
}
```

#### UserFormDialog
用户创建/编辑对话框组件。

```typescript
interface UserFormProps {
  visible: boolean;
  userId?: string;  // 编辑时传入
  onSuccess: () => void;
  onClose: () => void;
}
```

#### UserImportDialog
用户批量导入对话框组件。

```typescript
interface ImportResult {
  total: number;
  success: number;
  failed: number;
  errors: ImportError[];
}
```

## Data Models

### User Entity (Extended)

```java
@Entity
@Table(name = "sys_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(unique = true, length = 100)
    private String email;
    
    @Column(length = 50)
    private String displayName;
    
    @Column(length = 20)
    private String phone;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    
    @Column(length = 50)
    private String departmentId;
    
    @Column(length = 10)
    private String language = "zh_CN";
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sys_user_role", 
                     joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code")
    private Set<String> roles = new HashSet<>();
    
    @Column(nullable = false)
    private Boolean deleted = false;
    
    @Column
    private Boolean passwordExpired = false;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column
    private LocalDateTime deletedAt;
    
    @Column(length = 50)
    private String deletedBy;
}
```

### DTOs

```java
// 用户列表项
public record UserDTO(
    UUID id,
    String username,
    String displayName,
    String email,
    String phone,
    UserStatus status,
    String departmentId,
    String departmentName,
    Set<String> roles,
    LocalDateTime createdAt
) {}

// 用户详情
public record UserDetailDTO(
    UUID id,
    String username,
    String displayName,
    String email,
    String phone,
    UserStatus status,
    String departmentId,
    String departmentName,
    String language,
    Set<RoleDTO> roles,
    List<LoginHistoryDTO> loginHistory,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// 创建用户请求
public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank String password,
    @Email String email,
    String displayName,
    String phone,
    String departmentId,
    Set<String> roles
) {}

// 更新用户请求
public record UpdateUserRequest(
    @Email String email,
    String displayName,
    String phone,
    String departmentId,
    String language,
    Set<String> roles
) {}

// 查询请求
public record UserQueryRequest(
    String username,
    String email,
    UserStatus status,
    String departmentId
) {}

// 导入结果
public record ImportResult(
    int total,
    int success,
    int failed,
    List<ImportError> errors
) {}

public record ImportError(
    int row,
    String field,
    String message
) {}
```

## API Endpoints

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | /api/v1/users | 分页查询用户 | UserQueryRequest + Pageable | Page<UserDTO> |
| GET | /api/v1/users/{id} | 获取用户详情 | - | UserDetailDTO |
| POST | /api/v1/users | 创建用户 | CreateUserRequest | UserDTO |
| PUT | /api/v1/users/{id} | 更新用户 | UpdateUserRequest | UserDTO |
| DELETE | /api/v1/users/{id} | 删除用户 | - | void |
| PATCH | /api/v1/users/{id}/status | 更新状态 | {status: UserStatus} | UserDTO |
| POST | /api/v1/users/{id}/reset-password | 重置密码 | - | {password: string} |
| POST | /api/v1/users/import | 批量导入 | MultipartFile | ImportResult |
| GET | /api/v1/users/export-template | 下载模板 | - | byte[] |



## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Pagination Consistency

*For any* user query with pagination parameters, the returned page SHALL contain at most pageSize items, and the total count SHALL equal the actual number of matching users in the database.

**Validates: Requirements 1.1, 1.3**

### Property 2: Filter Correctness

*For any* user query with filter criteria, all returned users SHALL match every provided filter criterion (username contains, email contains, status equals, departmentId equals).

**Validates: Requirements 1.2**

### Property 3: Sort Order Correctness

*For any* user query with sort parameters, the returned users SHALL be ordered according to the specified field and direction.

**Validates: Requirements 1.5**

### Property 4: User Creation Round-Trip

*For any* valid CreateUserRequest, creating a user and then retrieving it by ID SHALL return a user with matching username, email, displayName, and roles.

**Validates: Requirements 2.1, 2.5**

### Property 5: Username Uniqueness

*For any* two CreateUserRequests with the same username, the second creation attempt SHALL fail with error code USER_001.

**Validates: Requirements 2.2**

### Property 6: Email Uniqueness

*For any* two CreateUserRequests with the same email, the second creation attempt SHALL fail with error code USER_002.

**Validates: Requirements 2.3**

### Property 7: Password Security

*For any* created user, the stored passwordHash SHALL NOT equal the plain text password from the CreateUserRequest.

**Validates: Requirements 2.4**

### Property 8: Update Persistence

*For any* valid UpdateUserRequest, updating a user and then retrieving it SHALL return a user with the updated field values.

**Validates: Requirements 3.1, 3.4**

### Property 9: Update Timestamp

*For any* user update operation, the updatedAt timestamp after update SHALL be greater than or equal to the updatedAt timestamp before update.

**Validates: Requirements 3.6**

### Property 10: Status Change Correctness

*For any* user and any target status (ACTIVE, INACTIVE, LOCKED), changing the user's status SHALL result in the user having that exact status when retrieved.

**Validates: Requirements 4.1, 4.2, 4.3**

### Property 11: Soft Delete Behavior

*For any* deleted user, the user SHALL have deleted=true, AND the user SHALL NOT appear in default user list queries.

**Validates: Requirements 5.1, 5.3**

### Property 12: Import Validation Consistency

*For any* Excel import file, each row SHALL be validated against the same rules as CreateUserRequest, and validation errors SHALL include row number and field name.

**Validates: Requirements 6.2, 6.3**

### Property 13: Import Atomicity

*For any* Excel import with at least one invalid row, no users SHALL be created (all-or-nothing transaction).

**Validates: Requirements 6.4**

### Property 14: Password Reset Behavior

*For any* password reset operation, the user SHALL have passwordExpired=true, AND the returned temporary password SHALL allow authentication.

**Validates: Requirements 8.1, 8.2, 8.3**

### Property 15: Audit Trail Completeness

*For any* user status change, deletion, or password reset operation, an audit log entry SHALL be created with the operation type and operator information.

**Validates: Requirements 4.4, 5.5, 8.4**

## Error Handling

### Error Codes

| Code | Message | HTTP Status | Description |
|------|---------|-------------|-------------|
| USER_001 | 用户名已存在 | 409 Conflict | Username already taken |
| USER_002 | 邮箱已被使用 | 409 Conflict | Email already in use |
| USER_003 | 用户不存在 | 404 Not Found | User not found by ID |
| USER_004 | 不能禁用最后一个管理员 | 400 Bad Request | Cannot disable last admin |
| USER_005 | 不能删除最后一个管理员 | 400 Bad Request | Cannot delete last admin |
| USER_006 | 导入文件格式错误 | 400 Bad Request | Invalid import file format |
| USER_007 | 导入数据验证失败 | 400 Bad Request | Import validation failed |

### Exception Classes

```java
public class UserException extends RuntimeException {
    private final UserErrorCode errorCode;
    private final Object[] args;
}

public enum UserErrorCode {
    USER_001("用户名已存在", HttpStatus.CONFLICT),
    USER_002("邮箱已被使用", HttpStatus.CONFLICT),
    USER_003("用户不存在", HttpStatus.NOT_FOUND),
    USER_004("不能禁用最后一个管理员", HttpStatus.BAD_REQUEST),
    USER_005("不能删除最后一个管理员", HttpStatus.BAD_REQUEST),
    USER_006("导入文件格式错误", HttpStatus.BAD_REQUEST),
    USER_007("导入数据验证失败", HttpStatus.BAD_REQUEST);
}
```

## Testing Strategy

### Unit Tests

- Test UserService methods with mocked repositories
- Test validation logic for CreateUserRequest and UpdateUserRequest
- Test Excel parsing logic with sample files
- Test password hashing and verification

### Property-Based Tests

Using JQwik for property-based testing:

- **Pagination Property Test**: Generate random page sizes and page numbers, verify pagination behavior
- **Filter Property Test**: Generate random filter combinations, verify all results match criteria
- **CRUD Round-Trip Test**: Generate random user data, verify create-read consistency
- **Uniqueness Property Test**: Generate duplicate usernames/emails, verify rejection
- **Soft Delete Property Test**: Delete users, verify they don't appear in queries

### Integration Tests

- Test complete API flows through UserController
- Test database constraints and cascading behavior
- Test import functionality with real Excel files

### Test Configuration

```java
@PropertyDefaults(tries = 100)
class UserPropertyTest {
    // Property tests with 100 iterations each
}
```
