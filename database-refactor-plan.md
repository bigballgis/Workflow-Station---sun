# 数据库Flyway重构计划

## 重构目标

1. **统一Flyway管理** - 所有服务使用Flyway进行数据库迁移
2. **统一ID策略** - 所有表使用VARCHAR(64)作为主键
3. **修复Schema差异** - 同步Flyway脚本与实际数据库结构
4. **优化迁移顺序** - 确保依赖关系正确

## 当前问题分析

### 1. Flyway配置问题
- 只有workflow-engine-core启用Flyway
- 其他服务使用Hibernate ddl-auto: update
- 导致Schema管理混乱

### 2. ID类型不一致
- sys_* 表: VARCHAR(64) 
- dw_* 表: BIGSERIAL
- admin_* 表: VARCHAR(64)
- wf_* 表: VARCHAR(64)
- up_* 表: VARCHAR(64)

### 3. 服务依赖关系
```
platform-security (sys_* 基础表)
    ↓
    ├─→ admin-center (依赖sys_users)
    ├─→ developer-workstation (依赖sys_users)
    ├─→ workflow-engine-core (依赖sys_users)
    └─→ user-portal (依赖sys_users)
```

## 重构方案

### 阶段1: 统一Flyway配置
1. 启用所有服务的Flyway
2. 禁用Hibernate ddl-auto
3. 设置正确的迁移顺序

### 阶段2: 统一ID策略
1. 修改dw_*表的ID类型为VARCHAR(64)
2. 创建数据迁移脚本
3. 更新外键引用

### 阶段3: 修复Schema差异
1. 同步现有数据库结构到Flyway脚本
2. 创建补丁迁移脚本
3. 验证数据完整性

### 阶段4: 优化和清理
1. 移除重复的迁移脚本
2. 优化索引和约束
3. 更新文档

## 实施步骤

### 步骤1: 备份现有数据库
```bash
pg_dump -h localhost -U platform -d workflow_platform > backup_$(date +%Y%m%d_%H%M%S).sql
```

### 步骤2: 更新服务配置
- 启用所有服务的Flyway
- 设置baseline-on-migrate: true
- 禁用ddl-auto

### 步骤3: 重新组织迁移脚本
- 按服务和依赖关系重新编号
- 确保迁移顺序正确

### 步骤4: 执行迁移
- 逐步执行迁移脚本
- 验证每个阶段的结果

## 风险评估

### 高风险
- ID类型变更可能影响现有数据
- 外键约束可能导致迁移失败

### 中风险
- 服务启动顺序需要调整
- 可能需要停机维护

### 低风险
- 配置文件更新
- 文档同步

## 回滚计划

1. 保留完整数据库备份
2. 准备回滚脚本
3. 测试回滚流程