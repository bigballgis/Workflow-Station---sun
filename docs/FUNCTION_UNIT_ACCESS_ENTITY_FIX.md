# Function Unit Access Entity 修复

## 日期
2026-02-03

## 问题描述

Admin Center 在导入功能单元包时报错：

```
ERROR: column fua1_0.role_id does not exist
Position: 78
```

SQL查询：
```sql
select fua1_0.id,fua1_0.created_at,fua1_0.created_by,fua1_0.function_unit_id,
       fua1_0.role_id,fua1_0.role_name 
from sys_function_unit_access fua1_0 
left join sys_function_units fu1_0 on fu1_0.id=fua1_0.function_unit_id 
where fu1_0.id=?
```

## 根本原因

**实体定义与数据库schema不匹配**：

### 数据库 Schema (正确)
```sql
CREATE TABLE sys_function_unit_access (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    access_type VARCHAR(20) NOT NULL,      -- DEVELOPER, USER
    target_type VARCHAR(20) NOT NULL,      -- ROLE, USER, VIRTUAL_GROUP
    target_id VARCHAR(64) NOT NULL,        -- 目标ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64)
);
```

### 实体定义 (错误 - 修复前)
```java
@Entity
@Table(name = "sys_function_unit_access")
public class FunctionUnitAccess {
    private String roleId;      // ❌ 数据库中不存在
    private String roleName;    // ❌ 数据库中不存在
}
```

## 修复方案

### 1. 更新实体定义 ✅

**文件**: `backend/admin-center/src/main/java/com/admin/entity/FunctionUnitAccess.java`

```java
@Entity
@Table(name = "sys_function_unit_access")
public class FunctionUnitAccess {
    @Id
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    /** 访问类型：DEVELOPER, USER */
    @Column(name = "access_type", nullable = false, length = 20)
    private String accessType;
    
    /** 目标类型：ROLE, USER, VIRTUAL_GROUP */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    /** 目标ID（角色ID、用户ID或虚拟组ID） */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
```

### 2. 更新 Repository ✅

**文件**: `backend/admin-center/src/main/java/com/admin/repository/FunctionUnitAccessRepository.java`

所有查询方法更新为使用 `targetType` 和 `targetId`：

```java
@Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM FunctionUnitAccess a " +
       "WHERE a.functionUnit.id = :functionUnitId AND a.targetType = 'ROLE' AND a.targetId = :roleId")
boolean existsByFunctionUnitIdAndRoleId(@Param("functionUnitId") String functionUnitId, 
                                       @Param("roleId") String roleId);

@Query("SELECT DISTINCT a.functionUnit.id FROM FunctionUnitAccess a " +
       "WHERE a.targetType = 'ROLE' AND a.targetId IN :roleIds")
List<String> findAccessibleFunctionUnitIdsByRoles(@Param("roleIds") List<String> roleIds);
```

### 3. 更新 Service ✅

**文件**: `backend/admin-center/src/main/java/com/admin/service/FunctionUnitAccessService.java`

创建访问配置时使用新字段：

```java
FunctionUnitAccess access = FunctionUnitAccess.builder()
        .functionUnit(functionUnit)
        .accessType("USER")      // 用户访问类型
        .targetType("ROLE")      // 目标类型为角色
        .targetId(request.getRoleId())  // 角色ID
        .build();
```

检查权限时：

```java
for (FunctionUnitAccess config : configs) {
    // 只检查角色类型的访问配置
    if ("ROLE".equals(config.getTargetType()) && 
        userBusinessRoleIds.contains(config.getTargetId())) {
        return true;
    }
}
```

### 4. 更新 DTO ✅

**文件**: `backend/admin-center/src/main/java/com/admin/dto/response/FunctionUnitAccessInfo.java`

添加新字段并保持向后兼容：

```java
@Data
@Builder
public class FunctionUnitAccessInfo {
    private String id;
    private String functionUnitId;
    private String functionUnitName;
    private String accessType;  // DEVELOPER, USER
    private String targetType;  // ROLE, USER, VIRTUAL_GROUP
    private String targetId;    // 目标ID
    private String targetName;  // 目标名称（用于显示）
    private Instant createdAt;
    private String createdBy;
    
    // 为了向后兼容，保留 roleId 和 roleName 字段
    public String getRoleId() {
        return "ROLE".equals(targetType) ? targetId : null;
    }
    
    public String getRoleName() {
        return "ROLE".equals(targetType) ? targetName : null;
    }
}
```

## 设计说明

### 通用访问控制模型

数据库使用通用的访问控制模型，支持多种目标类型：

1. **access_type**: 访问类型
   - `DEVELOPER`: 开发者访问（用于开发工作站）
   - `USER`: 用户访问（用于业务用户）

2. **target_type**: 目标类型
   - `ROLE`: 角色
   - `USER`: 用户
   - `VIRTUAL_GROUP`: 虚拟组

3. **target_id**: 目标ID（根据 target_type 解释）

### 当前实现

当前实现简化为只支持角色分配：
- `accessType = "USER"`
- `targetType = "ROLE"`
- `targetId = roleId`

未来可以扩展支持其他目标类型而无需修改数据库schema。

## 验证

### 编译
```bash
mvn clean package -DskipTests -pl backend/admin-center -am -T 2
```
**结果**: ✅ BUILD SUCCESS

### 部署
```bash
docker-compose -f deploy/environments/dev/docker-compose.dev.yml up -d --build admin-center
```
**结果**: ✅ 容器重新创建成功

### 测试
- 启动 Admin Center 服务
- 导入功能单元包
- 验证不再出现 `role_id does not exist` 错误

## 影响范围

### 修改的文件 (4个)
1. `backend/admin-center/src/main/java/com/admin/entity/FunctionUnitAccess.java`
2. `backend/admin-center/src/main/java/com/admin/repository/FunctionUnitAccessRepository.java`
3. `backend/admin-center/src/main/java/com/admin/service/FunctionUnitAccessService.java`
4. `backend/admin-center/src/main/java/com/admin/dto/response/FunctionUnitAccessInfo.java`

### 向后兼容性
- DTO 保留了 `getRoleId()` 和 `getRoleName()` 方法
- 前端代码无需修改
- API 响应格式保持兼容

## 相关问题

这是实体-数据库schema对齐工作的一部分，类似的问题已在以下文档中记录：
- `docs/ENTITY_SCHEMA_ALIGNMENT_COMPLETE.md` - Permission, Role, User 实体对齐
- `docs/ADMIN_CENTER_STARTUP_ISSUES.md` - Repository 查询修复

## 总结

修复了 FunctionUnitAccess 实体与数据库schema不匹配的问题。实体现在正确映射到数据库的通用访问控制模型（access_type, target_type, target_id），同时保持了向后兼容性。
