# Cursor User Rules — 粘贴块（可选）

本仓库**已内置**项目规则 [`.cursor/rules/ai-development-playbook.mdc`](../../.cursor/rules/ai-development-playbook.mdc)（`alwaysApply: true`），在本项目开 Agent 会话时会自动加载，**一般不必**再配 User Rules。

若希望在**所有项目**的 Cursor 里都沿用同一习惯，打开 **Cursor Settings → Rules → User Rules**，粘贴下方内容。

---

## 粘贴内容（User Rules）

```
Workflow Station：用户消息含「按 playbook」/「/playbook」时，必须先输出「## 任务整理（playbook）」并等待用户回复「确认」后，才能 Edit/Write/改代码的 Shell。确认前仅允许 Read/Grep 只读探查。用户说「无需确认直接执行」才可跳过。
```

---

## 在本项目里怎么用（推荐）

1. **不用配 User Rules** — 打开本仓库即可，规则已 always-on  
2. **开任务时**只填 playbook §5 模板（模块/类型/验收/范围/验证）  
3. **提 PR** — GitHub 自动加载 `.github/pull_request_template.md`

## 验证规则已生效

- Cursor：**Settings → Rules → Project Rules**，应能看到 `ai-development-playbook`  
- 或新开会话问：「当前会话加载了 ai-development-playbook 规则吗？」
