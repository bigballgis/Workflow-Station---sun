---
name: verify-ui-fix-with-screenshot
description: >-
  After any UI or visual change/bug fix, build the affected app, rebuild its Docker
  service, run Playwright to capture screenshots into verification-screenshots/, and
  cite the paths. Use when fixing frontend bugs, layout/parity issues, modal/form empty
  states, or when the user asks for screenshot verification. Triggers: "verify ui",
  "screenshot", "截图验证", "/verify-ui".
---

# Verify UI Fixes With Screenshots

Project skill for **Workflow Station**（本目录 `.cursor/skills/` 为唯一真源，`.claude/skills/`
由 `sync-cursor-rules.mjs` 镜像生成）。配套规则见 `.cursor/rules/frontend-screenshot-verification.mdc`。

凡改动 **可见 UI**（布局、表单、子表、卡片、弹窗、列表、设计 parity），在 build + 部署对应
frontend 服务之后 **必须** 用 Playwright 截图验证，不得仅凭单元测试或「应该对了」收尾。

## 快速路径

1. 改代码 → 在对应 app 跑 `pnpm run build` → 重建 `*-frontend` Docker 服务
   （见根规则 `debug-mode-docker-workflow`）。
2. 截图（仓库标准脚本 `frontend/scripts/verify-page-screenshot.mjs`）：

   ```bash
   cd frontend
   pnpm install                       # 首次：安装 playwright devDep
   pnpm exec playwright install chromium   # 首次：下载浏览器

   # 全页
   pnpm run verify:screenshot -- --app portal --url "http://localhost:3000/portal/tasks/<taskId>" --name task-detail
   # 指定区域（parity：card 内 subTable）
   pnpm run verify:screenshot -- --app portal --url "http://localhost:3000/portal/tasks/<taskId>" --selector ".form-layout-card" --name task-title-card
   # admin / dw
   pnpm run verify:screenshot -- --app admin --url "http://localhost:3000/admin/..." --name dashboard
   pnpm run verify:screenshot -- --app dw    --url "http://localhost:3000/dev/..."   --name form-preview
   ```

   登录变量（可选）：`LOGIN_USER`、`LOGIN_PASS`（默认 `developer` / `password`）。

### 登录方式：一律用 developer/password 直连，**不要走 SSO**

自己写临时截图脚本时，用 `scripts/playwright-login.mjs` 里的**密码直连**函数，
不要用 `loginViaUnifiedSso`——统一 SSO 登录页在 headless 下不可靠（会走到
`/sso/callback` 后又弹回 `/login`，会话建不起来，白跑一轮 build）。

```js
import { loginViaPortalPassword, loginViaDwPassword } from './scripts/playwright-login.mjs'
// portal：默认 developer / password
await loginViaPortalPassword(page)
// 多 UBR 用户要落在指定 workspace（否则默认取第一条，可能不是复现问题那条）
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
// developer workstation
await loginViaDwPassword(page)
```

登录端点各不相同，别混用：

| app | 端点 | localStorage key |
|---|---|---|
| portal | `POST /api/portal/auth/login` | `ws_up_user` / `ws_up_user_id` |
| dw | `POST /api/v1/auth/login` | `ws_dw_user` / `ws_dw_user_id` |

两者 token 都走 httpOnly cookie，脚本只需写 user 记录。

- 端点是 `POST /api/portal/auth/login`（**不是** `/portal/api/v1/auth/login`，那个会 401）。
- 多 UBR 会先返回 `WORKSPACE_CONTEXT_REQUIRED` + `workspaceContexts`，helper 已自动带
  `workspaceBusinessUnitId`/`workspaceRoleId` 重试；`buCode`/`roleCode` 用来挑具体那条。
- 查可用组合：`select bu.code, r.code from sys_user_business_unit_roles ubr
  join sys_users u on u.id=ubr.user_id join sys_business_units bu on bu.id=ubr.business_unit_id
  join sys_roles r on r.id=ubr.role_id where u.username='developer';`
- DW（`/dev`）headless SSO 同样不可靠，见规则 `dw-headless-sso-login-blocked`。

3. PNG 落在 `frontend/<app>/verification-screenshots/`，命名 `{YYYY-MM-DD}_{slug}.png` —— **禁止验证后删除**。
4. 在对话 / issue / PR 中写明截图绝对路径，便于人工对照 Designer Form Preview。

## MI 回归截图（与单元测试绑定）

触达 MI 热路径（`detail.vue` / `shared.ts` / `SubTableField.vue` 等）时，跑完整回归：

```bash
cd frontend && pnpm run regression:mi          # 99 unit tests + 6 截图场景
cd frontend && pnpm run regression:mi:screenshots   # 仅 Playwright
```

仅 `--unit-only` 为 portal 未启动时的临时手段，**不算**通过完整回归。
场景映射见 `frontend/user-portal/MI_REGRESSION.md`、`frontend/scripts/mi-regression-scenarios.mjs`。

## 自检清单

- [ ] 已 `pnpm run build` 并重建对应 `*-frontend` Docker 服务
- [ ] 已跑 `pnpm run verify:screenshot`，截图落在 `verification-screenshots/` 且未删除
- [ ] parity 改动：目标 selector 的 DOM 断言通过（`--expect-selector` / `CARDS REPORT`）
- [ ] 对话中引用了截图路径；与 DW Preview 对比时说明一致点

## 禁止

- 验证完成后删除 `verification-screenshots/` 内 PNG（与临时 `scripts/_*.png` 不同）
- 仅用「我修了代码」代替截图（parity / 布局类缺陷）
