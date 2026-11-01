# DW Copilot — P0 spike

一次性验证工程，只回答 [设计 Plan](../../docs/plans/dw-vscode-copilot-extension.md) P0 的问题，**不是** P1 的实现。

| 文件 | 作用 |
|---|---|
| `package.json` | 声明 4 个 Copilot 工具（`languageModelTools`）、`@dw` 对话入口、3 个命令 |
| `extension.js` | 工具实现（直调 DW API）+ `@dw` 自带工具循环 + 自检命令 |
| `dw-copilot-spike-0.0.1.vsix` | 打好的安装包（`npx @vscode/vsce package --no-dependencies --allow-missing-repository --skip-license`） |

## 已在本机验证（无 Copilot）

- VS Code 1.133：扩展激活；4 个工具全部注册并出现在 `vscode.lm.tools`；3 个命令注册；未登录时调用工具返回明确错误。
- DW API（curl 经 Kong `:8000`）：登录 → 建 FU → `studio-chat/apply` 反例被拒（`INVALID_ENUM`，带 `fieldPath`）→ 正例写入 → 回读一致。

## 需要你用 **公司 Copilot 账号** 实测（问题 1，约 15 分钟）

前提：能访问 DW 的 dev（本地 docker 是 `http://localhost:8000/api/v1`；共享 dev 环境改设置 `dwCopilot.apiBaseUrl`）。

1. 安装：VS Code → 扩展面板 `…` → **Install from VSIX…** → 选 `dw-copilot-spike-0.0.1.vsix`。
2. 登录：命令面板 → **DW: Sign in (dev username/password)**（dev 账号即可）。
3. 自检：命令面板 → **DW: P0 self-check**，看输出面板 "DW Copilot"：

   | 输出行 | 说明 |
   |---|---|
   | `Q1 tools registered and visible to Copilot: 4/4` | 工具注册未被策略拦截 |
   | `Copilot models available to @dw fallback: N` | N ≥ 1 表示 `@dw` 退路可用 |
   | `DW API … /auth/me -> HTTP 200` | 网络与登录正常 |

4. **Agent 模式**（核心问题）：Copilot Chat 切到 **Agent**，点工具图标确认能看到并勾选 `DW: …` 四个工具，然后输入：

   > 用 DW 工具：先读格式说明，然后在 function unit 50150 里用 scope=TABLES 新建一张 MAIN 表 `travel_request`（字段：id BIGINT 主键、traveler VARCHAR(100)、destination VARCHAR(200)、start_date DATE、budget DECIMAL(12,2)），写完再读回核对。

   期望：Copilot 依次调 `dw_get_format_spec` → `dw_apply_design`（弹确认，点 Continue）→ `dw_get_tables`，最终表出现在 DW 设计器的 FU 50150 里。
   FU id 换成你环境里的一个测试 FU（50150 只存在于我本机 dev）。

5. **`@dw` 退路**：切回 **Ask** 模式，输入 `@dw 列出名字含 P0 的 function unit`。

## 结果怎么判

| 现象 | 结论 |
|---|---|
| 第 4 步跑通 | 问题 1 = 可行，P1 按 `registerTool` 方案做 |
| 第 4 步看不到工具 / 工具灰掉，但第 5 步能用 | 公司策略禁了扩展工具，P1 改走 `@dw` 入口 |
| 第 5 步也不行（自检 models = 0） | 公司策略禁了扩展调用 Copilot 模型，需重新评估方案 |

把输出面板 "DW Copilot" 的内容贴回来即可（不含 token）。
