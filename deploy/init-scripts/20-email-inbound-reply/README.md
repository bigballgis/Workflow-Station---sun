# 20 — Email Inbound Reply

**EXECUTE_ENV: UAT ONLY.** 本种子不进 DEV Docker 首次 init（`00-init-all.sh`），也不进 `init-database.ps1`。SIT / PROD 不要执行。

收邮件建案 → 复核 → 确认发送（直发或送 checker）→ SMTP 回复。

| 项 | 值 |
|---|---|
| Code | `fu-20260910-emlrep` |
| Name | Email Inbound Reply |
| Process Key | `Process_EmailInboundReply` |
| Designer version | `current_version` 1.0.5（种子 `status` 为 DRAFT） |
| Status | DRAFT（配邮箱凭证后在 DW Publish + Deploy） |

种子只写 Developer Workstation 的 `dw_*`。不写 `sys_function_units`、不写 `dw_versions` 快照、不提交真实 IMAP/SMTP 密文。

## 流程

```
Start (Email Monitor)
  → Review Inbound（主表只读；E2E_FINANCE + Department Manager）
  → Confirm & Send（E2E_FINANCE + Department Manager）
       ├ Send Email          → Send Reply Email → End
       └ Submit for Checker  → Checker Review（E2E_FINANCE + Department Manager）
            ├ Send Email                 → Send Reply Email → End
            └ Back to Confirm & Send     → 退回 Confirm & Send
```

**前置：** UAT 必须已有 `01-admin/05-e2e-test-users-and-business-units.sql` 的 `E2E_FINANCE` / `MANAGER`。没有这两条时，灌库后先在 Process Design 改办理人再 Publish + Deploy。

Confirm & Send 两个办理按钮：

1. **Send Email**：立刻走发信节点并结束。
2. **Submit for Checker Review**：交给 Department Manager。Checker Review 两个办理按钮：
   - **Send Email**：发信并结束。
   - **Back to Confirm & Send**：退回 Confirm & Send。

Reply Subject / Reply Body 建案时从入站 SUBJECT / 正文写入，可再改。

Email Monitor 分两层：**Email Monitors** 页是模板（`Start from inbound email`）；**Process Design → Start Event** 再绑定到 `StartEvent_1`。种子会同时写入这两行。

View Design 默认视图 **Inbound Email Case**（DRAFT，无 BU/Role access → 仅管理员可见，直到在 View Design 补规则并 Publish）。

## 灌库（仅 UAT，手工执行）

**破坏性重建：** 再跑一遍会按 `code`/`name` **删掉**该 Function Unit 已有 `dw_*`（含表、表单、BPMN、视图、Email Monitor、连接配置、`dw_versions`），再插入种子。不要对已有真实设计的同名 FU 重跑。

对 UAT PostgreSQL 手工执行（不要对 DEV / SIT / PROD）。PowerShell 管道会把文件开头的 `--` 注释吃掉，请用 `-f`：

```bash
psql -v ON_ERROR_STOP=1 -h <UAT_HOST> -U <USER> -d <DB> -f deploy/init-scripts/20-email-inbound-reply/init.sql
```

已配置过的 IMAP/SMTP 密文会按连接名称从 DW 回填；若 DW 已是占位账号，再按 `connection_uid` 从上次 Deploy 的 `sys_email_connections` 回填。

## 灌库后必做

1. Developer Workstation 打开 **Email Inbound Reply**
2. Email Connection：把 **Inbound Mailbox** / **Outbound Mailbox** 改成自己的账号并启用（仓库 SQL 只有 `demo@example.com` 占位）
3. **Publish**，再 **Deploy**（引擎才会用新 BPMN 与 `sys_email_*`）
4. 用新 inbound 邮件验证两条路径（旧在途实例仍走旧图）
