# Code Review 结果 — Change History 工具栏、搜索和排序

## 结论

CONDITIONAL。功能相关单元测试、构建、部署及真实页面/隔离响应截图验证通过。全量 TypeScript 检查仍存在存量错误，本轮历史文件没有类型报错，不宣称全仓 PASS。

## Findings 与逐条处置

### Blocker / Major

本轮无未解决项。

### Minor

首轮截图发现面板内边距与外层 Tab 内容边距叠加。初步缩为 10px 后仍有重复留白；最终 `ChangeHistoryPanel.vue` 仅在 headerless 模式将内部 content padding 设为 0，沿用 Tab 原有 gutter。有标题的面板仍使用原样式。最后一轮真实 DOM 证据为 paddingTop=0px、工具栏高32px、工具栏到首组间距12px；任务详情与 My Request 均验证。

### Question

无。

## 范围 / 验收映射

- `ChangeHistoryPanel.vue`：顶部改为搜索、时间排序、结果数量及帮助入口组成的紧凑工具栏；无匹配时显示独立空状态。
- `useChangeHistoryPresentation.ts`：在原有授权/快照过滤及分组完成后筛选，保留稳定 key，避免过滤独立事件后重新合并保存分组。
- 默认正序；倒序同时调整分组及组内明细的时间顺序，同一时间的字段保留原顺序。排序不修改原数组。
- 搜索不区分大小写，去掉两端空白；标题命中时显示整组，字段/事件/值命中时显示匹配行。
- 搜索只使用显示标签和同一格式化函数处理后的值；不检索遮罩明文、Lookup 隐藏载荷。显示截断长度由共享常量保持一致。
- 已收起分组在搜索、排序及清空搜索后保持状态。
- 原明细表抽为 `ChangeHistoryDetails.vue`，保持事件、字段、新旧值及遮罩语义；父组件没有继续膨胀。
- 产品三语言及现有任务指南、llms 索引同步。

## 触发矩阵

UI 布局、前端只读行为、i18n 三语、敏感值展示、历史兼容、最小 diff。未修改 backend、schema、FU 设计产物、MI 行过滤或权限链。

## 编译与静态检查

- Portal `pnpm run build`：PASS；Help `pnpm run build`：PASS。
- Portal/Help Frontend 均使用现有本地构建产物重建部署，Portal 后端保持 healthy。
- 本轮前端文件 ESLint：0 errors；父组件仍有5条原有可选 prop 默认值 warning。
- 全量 `pnpm run typecheck`：未通过，386项错误；上轮未修改 HEAD 同样存在386项错误。本轮 ChangeHistoryPanel/Details、history composables/tests 均无报错。
- `git diff --check`、验证脚本 `node --check`：PASS。

## 测试与运行证据

- `pnpm exec vitest run src/composables/changeHistoryPanel/__tests__`：38 tests PASS。
- 临时禁用搜索谓词：新增6个测试中4个失败、2个通过；恢复后6个全部通过。覆盖排序、不改原数组、相同时间、标题/明细命中、空结果、事件边界、遮罩、Lookup、流程发起及重载。
- 布局修前：headerless 内 paddingTop=20px，顶部只有16px高帮助图标，搜索/排序控件不存在；保存 before PNG/JSON。
- 真实完成任务：76条可见明细、13组；时间正倒序切换、关键字搜索、无匹配、清空恢复、收起状态均通过。
- 隔离响应 fixture：7条可见记录/4组，未来记录按快照排除；搜索 Amount 后仍是2组，未跨隐藏 Claim 合并。
- 隔离响应 fixture：卡号隐藏前缀和 Lookup 内部字段搜索结果为0；卡号可见尾号、Lookup显示标签及大小写混合关键字可正常命中。
- 1366×768：输入框与排序控件无重叠，截图已人工检查。
- My Request：既有另一实例11条历史，工具栏及事件标签正常显示，内部 paddingTop=0px。
- 帮助入口定位 Change History 小节，并能看到新排序/搜索说明。
- 专项浏览器脚本无 pageerror；未写业务数据。

截图与证据：`frontend/user-portal/verification-screenshots/2026-10-26_change-history-toolbar-*`，包括 before、live、search-fixture、1366、application PNG，以及 before/evidence JSON。

复跑：从 `frontend` 设置 `CH_TASK_URL` 为带 processInstanceId 的已完成任务 URL，执行 `node scripts/verify-change-history-toolbar.mjs`。脚本先验证真实记录，随后只拦截当前页面响应验证边界，不修改服务端数据。

## Function Unit 通用性与生命周期

- 通用性：静态 PASS，产品代码按已有历史字段、展示值和稳定事件类型工作，无 FU/表/流程 ID 硬编码。
- Export/Import、Clone、Version/Rollback、Admin两条Import：N/A，未改变设计产物或存储契约。
- Portal runtime：任务详情、My Request 两入口均验证；新建FU全流程与完整MI截图套件本轮未运行，未触及相关热路径或行过滤。

## 增量敏感信息与性能

新增代码和文件扫描无凭证、token、私钥。搜索不新增网络请求、不把关键字发往服务端；索引由 computed 根据可见分组及遮罩生成，输入变化时复用已格式化文本。未做大数据性能基准。

## 残余风险 / 未验证项

- 全仓类型检查未通过，限于存量问题，本轮没有降低标准。
- 实际运行截图以英文为主；三语言文案均已编译。
- 旧历史、行可见性及快照约束继续由原 loader/后端处理，未重写历史记录。

## 范围外问题

无新增范围外实现。前轮已暂存的 Change History 批次及 BI Schema 修复内容保留，未提交或推送。
