## Summary

<!-- 1–3 句：解决什么问题、为什么这样改 -->

-

## 变更类型

- [ ] **fix** — bug 修复
- [ ] **feat** — 新功能
- [ ] **perf** — 性能优化（须与 fix/feat 分 PR；默认只动取数层）
- [ ] **refactor** — 重构（行为不变）

## 影响面

| 项 | 说明 |
|----|------|
| **模块** | portal / dw / admin / backend-* / platform-common |
| **MI 热路径** | 是 / 否（是则下方 regression 必填） |
| **platform-common** | 是 / 否（是则列出需重建的后端 service） |
| **可见 UI** | 是 / 否（是则必填截图路径） |

## 验证

<!-- 粘贴命令 + 结果摘要（pass / 截图路径 / logs 无 ERROR） -->

```bash
# 示例
cd frontend && npm run regression:mi
# 或
mvn -pl backend/user-portal -am package -DskipTests
cd deploy/environments/dev && docker compose -f docker-compose.dev.yml --env-file .env up -d --build user-portal-frontend
```

- [ ] build 通过
- [ ] 单测 / regression 通过
- [ ] Docker 重建 + logs 无启动失败
- [ ] 截图（如有 UI）：`frontend/*/verification-screenshots/…`

## MI Invariant（仅 MI 热路径改动时勾选）

对照 [performance-change-safety.mdc](../.cursor/rules/performance-change-safety.mdc)：

- [ ] I1 发起人 My Request 未误用 allSlices merge
- [ ] I2 file-only 子表未 global MI merge
- [ ] I3 shared attachment Save merge 全量 snapshot
- [ ] I4 nested `__subTables__` 仅 scope 本 parent
- [ ] I5 Link Form Details id vs sub_task_id 映射正确
- [ ] I6 form-below-table 按参与者选行
- [ ] I7 缓存 key 含 viewContext + binding 类型
- [ ] 不适用（非 MI 改动）

## Issue 闭环

- **关联 Issue：** #<!-- 编号 -->
- [ ] 已补单测 / verify 脚本 / 专项 rule（至少一项）
- [ ] `.kiro/issues` 已更新（验证通过后一条 `fixed`）

## 参考

- 协作流程：[docs/ai-rules/ai-development-playbook.md](../docs/ai-rules/ai-development-playbook.md)
- MI 回归：[frontend/user-portal/MI_REGRESSION.md](../frontend/user-portal/MI_REGRESSION.md)
