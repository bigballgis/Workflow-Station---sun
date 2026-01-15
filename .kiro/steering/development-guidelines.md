# 开发细则指南

## 概述
本指南包含项目开发的通用规则和最佳实践。

## 1. Flyway 迁移文件规则（重要！）

**每次修改数据库表结构时，必须同步更新 Flyway 迁移文件！**

### 迁移文件位置
| 表前缀 | 模块 | 迁移文件路径 |
|--------|------|-------------|
| `dw_*` | developer-workstation | `backend/developer-workstation/src/main/resources/db/migration/` |
| `sys_*` | platform-security | `backend/platform-security/src/main/resources/db/migration/` |
| `admin_*` | admin-center | `backend/admin-center/src/main/resources/db/migration/` |

### 关键规则
1. **初始版本（V1）**：直接修改 `V1__init_schema.sql` 文件
2. **后续版本**：创建新的迁移文件如 `V2__add_column.sql`
3. **手动修改数据库后**：必须同步更新对应的 Flyway 迁移文件
4. **Entity 类添加字段后**：必须在迁移文件中添加对应的列

### 常见表的迁移文件
- `sys_function_unit_contents` → `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`
- `sys_users`, `sys_roles`, `sys_permissions` → `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`
- `dw_function_units`, `dw_table_definitions`, `dw_form_definitions` → `backend/developer-workstation/src/main/resources/db/migration/`

### 示例：添加新列
```sql
-- 在 V1__init_schema.sql 中找到对应的 CREATE TABLE 语句，添加新列
CREATE TABLE IF NOT EXISTS sys_function_unit_contents (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    content_name VARCHAR(200) NOT NULL,
    content_path VARCHAR(500),
    content_data TEXT,
    checksum VARCHAR(64),
    source_id VARCHAR(64),  -- 新增列
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_func_unit FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id)
);
```

## 2. 数据库表归属

| 表前缀 | 模块 | 说明 |
|--------|------|------|
| `dw_*` | developer-workstation | 开发工作站相关表 |
| `sys_*` | platform-security | 系统安全相关表 |
| `admin_*` | admin-center | 管理中心相关表 |

## 3. 数据库连接信息

- 数据库名：`workflow_platform`
- 用户名：`platform`
- 密码：`platform123`
- Docker容器名：`platform-postgres`

### 常用命令
```powershell
# 查询数据库表结构
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "\d table_name"

# 执行SQL文件
Get-Content -Path "xxx.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 执行单条SQL
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT * FROM table_name"
```

## 4. 服务端口配置

| 服务 | 端口 | Context Path |
|------|------|--------------|
| Admin Center Backend | 8090 | `/api/v1/admin` |
| Developer Workstation Backend | 8083 | `/api/v1` |
| User Portal Backend | 8082 | `/api/portal` |
| Workflow Engine Core | 8081 | `/api/v1` |

## 5. ID 映射规则

### formId 映射
- BPMN 中的 `formId` 必须使用 `dw_form_definitions` 表中的实际数据库 ID
- 不要使用相对 ID（如 1, 2, 3），必须使用实际 ID（如 11, 13, 16）
- 原因：ID 可以保证一致性，formName 容易混淆

### sourceId 字段
- `sys_function_unit_contents.source_id` 用于存储原始表单 ID
- 用于 user-portal 通过 formId 匹配表单

## 6. JPA 查询最佳实践

### 加载关联数据
```java
// 使用 FETCH JOIN 加载关联数据
@Query("SELECT DISTINCT f FROM FormDefinition f " +
       "LEFT JOIN FETCH f.boundTable " +
       "LEFT JOIN FETCH f.tableBindings tb " +
       "LEFT JOIN FETCH tb.table " +
       "WHERE f.functionUnit.id = :functionUnitId")
List<FormDefinition> findByFunctionUnitIdWithBindings(@Param("functionUnitId") Long functionUnitId);
```

### 避免循环引用
```java
// 在双向关联的一侧添加 @JsonIgnore
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "table_id")
@JsonIgnore  // 避免序列化时的循环引用
private TableDefinition table;
```

## 7. 前端表单配置

### form-create 规则结构
```json
{
  "rule": [...],      // form-create 规则数组
  "options": {...}    // form-create 选项
}
```

### 多Tab页布局
```json
{
  "rule": [
    {
      "type": "el-tabs",
      "props": { "type": "border-card", "modelValue": "basic" },
      "children": [
        {
          "type": "el-tab-pane",
          "props": { "label": "Basic Info", "name": "basic" },
          "children": [...]
        }
      ]
    }
  ]
}
```

## 8. 常见问题检查清单

### 数据库修改后
- [ ] 更新 Flyway 迁移文件
- [ ] 更新 Entity 类
- [ ] 重启相关服务

### 添加新 API 后
- [ ] 检查 context-path 是否正确
- [ ] 检查前端代理配置
- [ ] 测试 API 响应

### BPMN 绑定问题
- [ ] 检查 formId 是否使用实际数据库 ID
- [ ] 检查 actionIds 是否使用实际数据库 ID
- [ ] 验证 BPMN XML 中的 ID 与数据库匹配

## 9. 参考文件

### 后端
- Entity 定义：`backend/*/src/main/java/com/*/entity/`
- Repository：`backend/*/src/main/java/com/*/repository/`
- Flyway 迁移：`backend/*/src/main/resources/db/migration/`

### 前端
- API 定义：`frontend/*/src/api/`
- 组件：`frontend/*/src/components/`
- 视图：`frontend/*/src/views/`
