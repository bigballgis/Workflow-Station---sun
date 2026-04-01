# Issue 跟踪仪表盘

> **详细信息已迁移到 `.kiro/issues/index.yaml`**
> 本文件仅保留统计摘要和当前待处理清单。

## 统计 (截至 2026-03-31)

| 状态 | 数量 |
|------|------|
| ✅ Fixed | **112** |
| 🔓 Open | **13** |
| **总计** | **125** |

### 按严重度

| 严重度 | Fixed | Open |
|--------|-------|------|
| 🔴 Critical | 18 | 1 |
| 🟡 Major | 59 | 6 |
| 🟢 Minor | 35 | 6 |

---

## 当前待处理 (Open)

### 🔴 Critical
| ID | 分类 | 描述 |
|----|------|------|
| 022 | security | workflow-engine 任务 API 信任调用方自报 userId (IDOR) |

### 🟡 Major
| ID | 分类 | 描述 |
|----|------|------|
| 008 | architecture | user-portal Controllers 使用 X-User-Id header 而非 SecurityContext |
| 009 | bug | developer-workstation user.ts changePassword 调用不存在的端点 |
| 028 | security | Redis 黑名单故障放行 — 已撤销 token 可能被接受 |
| 031 | security | Refresh token 未轮换，泄露后可反复换发 access token |
| 043 | quality | user-portal user.ts changePassword 每次创建新 axios 实例 |
| 113 | architecture | RelationTable/Ai ExceptionHandler 与 GlobalExceptionHandler 响应格式不一致 |
| 114 | architecture | developer-workstation BusinessException 与 platform-common 同名类冲突 |

### 🟢 Minor
| ID | 分类 | 描述 |
|----|------|------|
| 011 | quality | platform-security RoleEntityPropertyTest 缺少嵌入式数据库 |
| 012 | quality | developer-workstation 集成测试 ApplicationContext 失败 |
| 068 | performance | FormRenderer.vue deep watcher 性能隐患 |
| 088 | security | UserController.resetPassword 返回明文密码 |
| 095 | quality | auth.ts 三个前端应用大量重复 |
| 117 | quality | process store startProcess 参数类型与 API 不一致 |

---

## 跟踪系统说明

issue 详情存储在 `.kiro/issues/index.yaml`，格式为机器可读的 YAML。

查看所有未修复问题：
```bash
grep "status: open" .kiro/issues/index.yaml
```

查看特定分类：
```bash
grep -A2 "category: security" .kiro/issues/index.yaml | grep "status: open"
```
