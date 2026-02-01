# 数据库分析总结

生成时间：2026-01-31

## 📊 数据库现状

### 表统计

| 模块 | 表前缀 | 表数量 | Flyway 状态 |
|------|--------|--------|------------|
| Platform Security | sys_* | 30 | ⚠️ 未启用 |
| Developer Workstation | dw_* | 11 | ⚠️ 未启用 |
| Admin Center | admin_* | 14 | ⚠️ 未启用 |
| User Portal | up_* | 10 | ⚠️ 未启用 |
| Workflow Engine | wf_* | 4 | ✅ 已启用 |
| Flowable Engine | act_*, flw_* | 70 | ✅ 自动管理 |
| **总计** | | **139** | |

### Flyway 执行状态

```
✅ workflow-engine-core: V1, V2 已执行
❌ platform-security: 未执行（有 V1-V9 脚本）
❌ developer-workstation: 未执行（有 V1-V5 脚本）
❌ admin-center: 未执行（有 V1 脚本）
❌ user-portal: 未执行（有 V1 脚本）
```

## ✅ 好消息

1. **表结构完全匹配**
   - 所有模块的数据库表与 Flyway V1 脚本定义完全一致
   - 没有遗漏或额外的表
   - JPA `ddl-auto=update` 正确创建了所有表

2. **Flyway 脚本质量良好**
   - V1 脚本定义完整
   - 表结构设计合理
   - 外键约束正确

## 🔴 问题

### 1. Flyway 未全面启用

**当前状态：**
- 只有 `workflow-engine-core` 启用了 Flyway
- 其他 4 个模块依赖 JPA `ddl-auto=update`

**风险：**
- 生产环境不推荐使用 `ddl-auto=update`
- 缺少数据库变更的版本控制
- 团队协作时可能出现结构不一致
- 回滚困难

### 2. 后续迁移脚本未执行

**Platform Security (V2-V9):**
- V2__fix_user_status_constraint.sql
- V2__init_data.sql
- V3__ensure_sys_login_audit.sql
- V4__add_developer_function_unit_create.sql
- V5__assign_developer_roles_to_dev_users.sql
- V6__assign_developer_role_to_adam.sql
- V7__add_developer_function_unit_delete.sql
- V8__sync_developers_vg_to_sys_user_roles.sql
- V9__add_developer_function_unit_publish.sql

**Developer Workstation (V2-V5):**
- V2__fix_form_table_bindings_constraint.sql
- V2__init_data.sql
- V3__init_process.sql
- V4__assign_adam_developer_role.sql
- V5__sync_developers_vg_to_sys_user_roles.sql

**影响：**
- 数据初始化可能不完整
- 约束修复未应用
- 权限配置可能缺失

## 💡 解决方案

### 方案 A：全面启用 Flyway（推荐）

#### 步骤 1：备份当前数据库

```bash
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform > backup_$(date +%Y%m%d_%H%M%S).sql
```

#### 步骤 2：启用所有模块的 Flyway

修改各模块的 `application.yml`：

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
  
  jpa:
    hibernate:
      ddl-auto: validate  # 改为 validate
```

#### 步骤 3：重启服务并验证

```bash
# 重启各服务
# 检查 flyway_schema_history 表
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT installed_rank, version, description, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
"
```

#### 步骤 4：验证表结构

```bash
# 确保没有错误
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "\dt"
```

### 方案 B：保持现状（不推荐）

如果选择保持现状：

1. **记录风险**：在文档中明确记录使用 JPA `ddl-auto=update` 的风险
2. **定期同步**：定期将数据库结构导出并更新 Flyway 脚本
3. **生产环境**：生产环境必须使用 Flyway

## 📋 检查清单

### 启用 Flyway 前

- [ ] 备份当前数据库
- [ ] 检查所有 Flyway 脚本语法
- [ ] 确认 baseline-version 设置正确
- [ ] 测试环境先验证

### 启用 Flyway 后

- [ ] 检查 flyway_schema_history 表
- [ ] 验证所有表结构正确
- [ ] 运行应用测试
- [ ] 检查日志无错误

### 生产环境部署

- [ ] 使用 Flyway 管理所有数据库变更
- [ ] 禁用 JPA `ddl-auto`（设为 validate 或 none）
- [ ] 建立数据库变更审查流程
- [ ] 定期备份数据库

## 🎯 推荐配置

### 开发环境

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    clean-disabled: false  # 允许清理（仅开发环境）
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
```

### 生产环境

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false
    clean-disabled: true  # 禁止清理
    validate-on-migrate: true
  
  jpa:
    hibernate:
      ddl-auto: none  # 完全禁用
    show-sql: false
```

## 📚 相关文档

- [数据库与 Flyway 对比报告](./DATABASE_FLYWAY_COMPARISON_REPORT.md)
- [Schema 切换指南](./SCHEMA_MIGRATION_GUIDE.md)
- [开发细则指南](./development-guidelines.md)

## 🔗 有用命令

### 查看 Flyway 历史

```bash
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
"
```

### 导出表结构

```bash
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform --schema-only > schema.sql
```

### 检查表数量

```bash
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT 
    schemaname,
    COUNT(*) as table_count
FROM pg_tables 
WHERE schemaname = 'public'
GROUP BY schemaname;
"
```

### 按前缀统计表

```bash
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT 
    SUBSTRING(tablename FROM '^[^_]+') as prefix,
    COUNT(*) as count
FROM pg_tables 
WHERE schemaname = 'public'
GROUP BY prefix
ORDER BY count DESC;
"
```

## ⚡ 快速行动

如果你决定启用 Flyway，执行以下命令：

```bash
# 1. 备份
docker exec -i platform-postgres pg_dump -U platform -d workflow_platform > backup.sql

# 2. 修改配置（手动编辑各模块的 application.yml）

# 3. 重启服务
# 停止所有服务
# 启动所有服务

# 4. 验证
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT installed_rank, version, description, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
"
```

## 📞 需要帮助？

如果在启用 Flyway 过程中遇到问题：

1. 检查日志文件：`logs/*.log`
2. 查看 Flyway 错误信息
3. 恢复备份：`docker exec -i platform-postgres psql -U platform -d workflow_platform < backup.sql`
4. 参考文档：`docs/development-guidelines.md`
