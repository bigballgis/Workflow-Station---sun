# @workflow-station/core — 前端共享代码（P2-1 首切）

消除三个前端 app（admin-center / user-portal / developer-workstation）的复制粘贴（ISSUE-095）。

## 现状（本次首切，低风险）

- 建立 pnpm workspace（`frontend/pnpm-workspace.yaml`）+ 本共享包脚手架。
- **只抽了三 app 完全一致的工具**：`languageLabelFor`（三份 0 行差异，可肉眼核对）。
- **未抽**分叉严重的文件（见下「待调和」），避免高风险大重构。

## ⚠️ 需验证（本环境无法构建三 Vite app）

本仓库前端是 **host/CI 侧 `npm run build` 产出 dist、Docker 只 COPY dist**（见各 app `Dockerfile.local`）。
切换 pnpm workspace 改变本地/CI 的依赖解析，**上线前必须验证**：

```bash
cd frontend
pnpm install                          # 用 pnpm 而非 npm 安装（workspace 解析）
pnpm --filter admin-center build      # 三 app 各构建一次，确认能解析 @workflow-station/core
pnpm --filter user-portal build
pnpm --filter developer-workstation build
```

各 app 改用共享包的方式：

1. `package.json` 加依赖：`"@workflow-station/core": "workspace:*"`
2. 删除本地 `src/utils/languageLabel.ts`，import 改为：
   `import { languageLabelFor } from '@workflow-station/core/languageLabel'`
3. `vite.config.ts` / `tsconfig.json` 确认能解析 workspace 包（pnpm 装好后通常自动）。

> 本次脚手架**未**改动三 app 的 import（保持现状可运行）；切换由上述步骤在能构建/截图验证的环境里逐 app 做，
> 每改一个 app 跑 `/verify-ui` 截图确认 Profile 语言标签显示正常。

## 待调和（分叉严重，留专项，勿盲并）

实测三份差异（admin vs portal diff 行数）：

| 文件 | 差异 | 说明 |
|---|---|---|
| `auth.ts` | 332 行 | 三份实现已大幅分叉；token key 前缀（`ws_ac_`/`ws_up_`/`ws_dw_`）**必须保持各 app 独立**防 localStorage 会话冲突——抽共享时参数化，**别合并 key** |
| `httpErrorMessage.ts` | 180 行 | 错误文案/分支已分叉，需逐条对齐 |
| `sso.ts` | 63 行 | SSO 交换逻辑分叉 |
| `changePasswordError.ts` | 2 行 | 仅一个 i18n key 不同（`common.failed` vs `common.error`），参数化即可并 |

调和这些需在能构建 + 截图回归的环境里逐 app 做，且每个 app 的 login/SSO 流程要回归验证（改错=全员登不进）。
建议作为独立前端专项，不与后端改动混在一起。
