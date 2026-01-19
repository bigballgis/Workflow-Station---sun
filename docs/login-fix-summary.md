# 登录问题修复总结

生成时间: 2026-01-18

## 问题描述

只有 Kevin Huang (core.lead) 和 Robert Sun (tech.director) 可以登录，其他用户登录时返回 400 Bad Request 错误。

## 问题原因

1. **缺失用户数据**: 数据库中缺少以下测试用户：
   - `purchase.requester` (Tom Wilson)
   - `dept.reviewer` (Alice Johnson)
   - `countersign.approver1` (Daniel Brown)
   - `countersign.approver2` (Eva Martinez)
   - `finance.reviewer` (Carol Davis)
   - `parent.reviewer` (Bob Smith)

2. **密码哈希不一致**: `core.lead` 和 `tech.director` 的密码哈希与其他用户不同，虽然能登录，但为了统一性也进行了修复。

## 解决方案

### 1. 插入缺失的用户

执行了以下 SQL 插入语句：

```sql
INSERT INTO sys_users (id, username, password_hash, email, full_name, display_name, status, language, created_at, updated_at) 
VALUES 
    ('countersign-001', 'countersign.approver1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'daniel.brown@example.com', 'Daniel Brown', 'Daniel', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('countersign-002', 'countersign.approver2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'eva.martinez@example.com', 'Eva Martinez', 'Eva', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('dept-reviewer-001', 'dept.reviewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'alice.johnson@example.com', 'Alice Johnson', 'Alice', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('finance-reviewer-001', 'finance.reviewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'carol.davis@example.com', 'Carol Davis', 'Carol', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('parent-reviewer-001', 'parent.reviewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'bob.smith@example.com', 'Bob Smith', 'Bob', 'ACTIVE', 'zh_CN', NOW(), NOW()),
    ('purchase-requester-001', 'purchase.requester', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'tom.wilson@example.com', 'Tom Wilson', 'Tom', 'ACTIVE', 'zh_CN', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;
```

### 2. 统一密码哈希

更新了 `core.lead` 和 `tech.director` 的密码哈希，使其与其他用户一致。

**所有测试用户的密码**: `admin123`

**密码哈希**: `$2a$10$U8RY1nXkphRLpUyqzy1fOe3W64/nfRmG3ara8YHK2yrWfYMugCKxK`

**注意**: 之前的哈希值 `$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH` 不匹配 `admin123`，已更新为正确的哈希值。

## 修复后的用户列表

| 用户名 | 全名 | 状态 | 密码 |
|--------|------|------|------|
| `purchase.requester` | Tom Wilson | ACTIVE | admin123 |
| `dept.reviewer` | Alice Johnson | ACTIVE | admin123 |
| `countersign.approver1` | Daniel Brown | ACTIVE | admin123 |
| `countersign.approver2` | Eva Martinez | ACTIVE | admin123 |
| `finance.reviewer` | Carol Davis | ACTIVE | admin123 |
| `parent.reviewer` | Bob Smith | ACTIVE | admin123 |
| `core.lead` | Kevin Huang | ACTIVE | admin123 |
| `tech.director` | Robert Sun | ACTIVE | admin123 |

## 验证

所有用户现在都应该能够使用 `admin123` 作为密码成功登录。

**密码哈希验证**: 已通过单元测试验证，新的密码哈希 `$2a$10$U8RY1nXkphRLpUyqzy1fOe3W64/nfRmG3ara8YHK2yrWfYMugCKxK` 确实匹配密码 `admin123`。

## 注意事项

1. **密码哈希**: 所有用户现在使用相同的 BCrypt 哈希值，对应密码 `admin123`
2. **Flyway 迁移**: 这些用户应该在 `V2__init_data.sql` 迁移脚本中，但可能由于 `ON CONFLICT DO NOTHING` 导致未插入
3. **未来修复**: 建议检查 Flyway 迁移脚本的执行情况，确保所有测试用户都能正确初始化
