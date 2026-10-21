# List View 左侧分区上下折叠

## 后续间距调整

用户指出展开状态字段区仍撑满高度，Extend Action 被推到底部。首分区改用 `flex: 0 1 auto`（不主动增长，但内容多时可缩小），固定标题/表名/搜索和 Extend Action 不被压缩，剩余字段列表内部滚动。折叠状态与原 × 不变，不改变设计配置。

- 同一合成 FU、同一脚本加 `VERIFY_SPACING=1` 的 before/after：字段末尾到 Extend Action 的空白 58px → 0px（验收上限 12px），不是减少字段内容来凑结果。
- 1366×720 窗口断言字段列表 scrollHeight 大于 clientHeight，且 Extend Action 底部在面板范围内；独立折叠、搜索保留、整面板关闭/恢复均再次通过，无设计写入和浏览器错误。
- 组件测试 3/3；DW build 18.26s 成功，已重建并启动 DW frontend；`git diff --check` 通过。
- 人工检查截图：`frontend/developer-workstation/verification-screenshots/2026-10-20_list-view-after-spacing-expanded.png` 和 `2026-10-20_list-view-after-spacing-short-viewport.png`；before 截图为 `2026-10-20_list-view-before-spacing-expanded.png`。
- 增量复查没有发现新的产品问题；仅改样式及验收断言，不新增文案、依赖或持久化。沿用下文工程级未验证边界，不宣称全仓检查 PASS。尚未提交。

## 交付

- Table Columns 和 Extend Action 默认展开，可分别点击标题箭头上下折叠。
- 字段分区收起后释放高度，Extend Action 上移；保留搜索输入与已配置列。
- 原 × 继续隐藏整个面板；重新打开时保留本次编辑会话的分区状态。
- 状态只存在页面组件内，不保存到 FU 设计、用户偏好或后端。没有改列配置或 Link Form/Lookup 行为。
- 按帮助规范更新现有 Sub-Table 文章三语说明与 llms-full；不新增文章或配置项。

## 验证

- DW `pnpm exec vitest run SubTableListView.collapse --reporter=dot`：3/3，通过独立折叠、搜索保留、无保存事件和原整面板开关。
- DW `pnpm run build`：成功，19.67s；Help build：成功，1.27s。
- 两个 frontend 镜像已重建、容器已重新启动。
- `LOGIN_USER=<有编辑权限的账号> FIXTURE_UNIT_ID=<已有合成 FU> VERIFY_STAGE=before/after node scripts/verify-list-view-collapse.mjs`：同一合成设计前后验证。
- before：确认没有垂直折叠按钮并截图。after：实际点击后 Extend Action 标题 y 从 802 移到 315，上移 487px；Link Form 可见。两区独立收起、再次展开搜索不变、×/重新打开及 Lookup 可见断言均通过。
- 浏览器 console error/pageerror 为零，未保存设计或修改业务数据；打开 FU 产生的 Recent 用户偏好更新不计作设计写入。
- 默认测试账号仅有只读权限的首次编辑器超时不算验收证据；后续用已有编辑权限账号完成，未修改权限。
- Help 验证：`node scripts/verify-help-portal.mjs`，153/153 通过。

截图目录：`frontend/developer-workstation/verification-screenshots/`

- `2026-10-20_list-view-before-expanded.png`
- `2026-10-20_list-view-after-expanded.png`
- `2026-10-20_list-view-after-columns-collapsed.png`
- `2026-10-20_list-view-after-both-collapsed.png`

## Code Review 结果

### 结论

本轮交互已验证；工程级结论 CONDITIONAL（未重跑全仓类型检查和 DW 全量单测，不宣称全仓 PASS）。用户此前明确全仓类型问题不属于此次改动，未扩大治理。

### Findings 与处置

- 本轮实现检查、验证后增量复查未发现需修复的产品 Blocker/Major/Minor，Question 无。
- 重点复查：仅隐藏字段内容而不移除首分区的 flex:1 会留空白。实现以 is-collapsed 改为 flex:0 0 auto，实际 487px 上移验证通过。
- 重点复查：v-if 会移除搜索控件；使用 v-show 保留内容和搜索，独立折叠状态不影响配置数据。
- 验证脚本最初把 Recent 偏好更新误算为设计写入，已排除该路径；没有放宽对设计 PUT/PATCH/DELETE 的断言。

### 范围 / 验收与触发矩阵

- DW SubTableListView、三语展开/收起标签、组件测试、只读 UI 脚本及现有帮助文章。
- 命中可见 UI、i18n、测试与帮助维护；没有 Portal MI 热路径、后端、权限、数据库或持久化 schema 变化。

### 编译、测试与敏感信息

- 构建、部署、三条组件测试、实际页面断言、Help 153 项和截图均已执行；截图已人工查看。
- `git diff --check` 通过；新增源码/脚本无凭证、私钥、业务个人信息或新依赖。

### FU 生命周期 / 性能

- Save/Load、Export/Import、Clone、Version/Rollback、Admin 两条 Import、Portal runtime：N/A，不修改 FU 设计配置或运行时契约，只新增 DW 页面临时 UI 状态。
- 通用性：不按 FU、表或业务字段名决定折叠行为；没有新增网络请求或逐行计算，不宣称性能提升。

### 残余风险 / 范围外

- 未做跨浏览器、手机视口及 DW 全量回归；已验证 Chromium 1366×900、现有合成 FU 编辑器。
- 本轮未提交或推送代码；前次提交保持不变。
