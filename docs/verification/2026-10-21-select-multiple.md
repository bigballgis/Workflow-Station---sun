# Portal Select 多选修复验证

## 2026-10-22 提交前补充

- 按用户要求，在已有 FU50094（testmultiselect）的 selectform 中从组件库拖入 Select，通过属性面板配置多选、苹果/橙子/香蕉选项及前两项默认值；通过表设计界面添加对应 VARCHAR(1000) 字段。
- 全程通过界面配置，保存、设计校验及部署 v1.0.0 成功；未直接通过 API 修改此 FU。
- 设计器截图：`frontend/developer-workstation/verification-screenshots/2026-10-21_fu50094-select-drag-config.png`。
- 用户于2026-10-22反馈“好的，没问题了，你提交吧”。此前新建FU50097与此次已有FU50094均提供了实际配置/部署样本；用户测试通过，自动化仍未覆盖数据库保存回显和完整MI套件，不将审查结论提升为全门禁PASS。

## 用户手工测试 FU

- 名称：`Select Multiple Test 20261021`，DW ID：`50097`，工作区：Public。
- Portal发起：`http://localhost:3000/portal/processes/start/select-multiple-test-20261021-20261021-cuxkir`。
- DW：`http://localhost:3000/dev/function-units/50097`。
- 独立主表：`select_verify_mvhykwyu`（50546）；表单50861发起、50862待办、50863我的申请。
- 已通过设计校验并真实部署、启用；本地developer当前角色已获该测试FU访问权。
- 四个Select：单选对照、多选无默认值、多选默认一项、多选默认两项。选项为苹果/橙子/香蕉。
- 工作流：发起填写 → 发起人待办验证 → 完成。测试编号由UUID策略自动分配。
- 无响应拦截的实际部署页验证：默认1项/2项正确，空多选可同时选择2项，pageerror为0。
- 实际FU截图：`frontend/user-portal/verification-screenshots/2026-10-21_select-multiple-real-fu-50097.png`。
- 留给用户提交与保存验证，自动化没有提交此FU的业务申请。

## 范围与根因

流程发起、待办、我的申请的规则转换器未复制 `rule.props.multiple`，而 FieldRenderer 读取 `field.multiple`。
三个入口调用 `applySelectPropsFromRule`；不改变保存格式、后端、部署包或设计器。
`coerceDefaultValueForRuleType` 对多选逐元素匹配选项，避免 `[1]` → `1` 或 `[1,2]` 命中值为 `"1,2"` 的另一个选项。
FormField 默认值类型补齐数组。

## 红 → 绿

- 专项命令：在 `frontend/user-portal` 执行 `pnpm exec vitest run src/components/formRendererHelpers/__tests__/selectMultipleEntryPoints.test.ts src/utils/__tests__/formCreateRuleDefaults.test.ts`。
- 修前测试证明三个真实提取入口丢失多选，并把单元素数组压成标量。
- 修前全量基线（仅新增测试，产品代码尚未修改）：318文件，8失败、2396通过；失败均为本次新增用例。
- 修后全量：`pnpm exec vitest run --maxWorkers 4 --minWorkers 1`，318文件、2406项全部通过。比基线额外补充两个逐元素匹配用例。
- 页面命令：在 `frontend` 执行 `node scripts/verify-select-multiple.mjs --before`（修前）、`node scripts/verify-select-multiple.mjs`（重建后）。
- 修前同一配置选第二项后标签数为0（期望2），断言失败；修后两项均保留，删除/清空/空初值/单选/刷新后的数组默认值全部通过，console error/pageerror 均为0。
- 截图：`frontend/user-portal/verification-screenshots/2026-10-21_select-multiple-before.png` 与 `2026-10-21_select-multiple-after.png`，已逐张检查。
- 页面脚本通过 Playwright 替换内容响应中的规则，使用实际部署的 Portal 前端；**未修改后端配置，未提交业务数据，不能证明数据库保存回显或FU部署链路**。
- MI额外回归：`cd frontend; pnpm run regression:mi`。29文件、234项单测通过；前两个页面脚本通过（其中性别隔离页面没有对应控件，只由单测覆盖实际切换）。第三个脚本 `verify-mi-attachment-rows.mjs` 未找到带附件行的待办/申请数据，失败退出，其余三个页面脚本未执行。**完整MI门禁未通过**，不能以unit通过代替。

## 构建与静态检查

- `pnpm run build`：PASS，Vite 24.34秒；有既存 Sass/chunk 警告。
- `docker compose -f docker-compose.dev.yml --env-file .env up -d --build --no-deps user-portal-frontend`：镜像重建、容器重新创建并启动；nginx日志确认加载新前端资源。
- 新增helper、测试和默认值工具的 ESLint：PASS。
- 三个转换器 ESLint：原有2/15/21项错误；用 ESLint API 比较 HEAD 与工作区消息签名完全一致。
- FormField类型文件 ESLint：原有6项错误，HEAD与工作区消息签名一致。
- `pnpm run typecheck`：修前、修后均382项存量TS错误；两次完整输出一致。本轮未降低检查标准或修复范围外错误。
- `git diff --check`：PASS。

## Code Review 结果

### 结论

CONDITIONAL。审查本轮工作区完整修改（含新增文件），不提交、不推送。静态未发现新增Blocker/Major；保留以下运行证据缺口，不宣称完整端到端验收。

### Findings 与处置

- Blocker：无新增发现。
- Major：未发现新增实现问题。实际数据库保存回显、新建/已有或导入FU双样本运行证据未覆盖，列为验证缺口。
- Minor：默认值类型原先只声明标量；已补数组类型，并移除默认值工具中不必要的标量强转。重审确认与实际返回值一致。
- Question：无。

### 验收映射与触发矩阵

- Select多选：三类真实提取入口及嵌套card规则回归覆盖；未设置/false仍为单选。
- 数组默认值：单项、多项、空数组、值/标签匹配、逗号值碰撞覆盖。
- 命中：前端规则转换、Portal parity、bug验证、UI截图、测试、可部署代码、增量敏感信息。
- N/A：API、schema、鉴权、i18n、配置、业务分派和性能优化，均无改动。

### Function Unit 通用性与生命周期

- 通用性：静态PASS，仅按组件类型与设计器布尔配置判断，无业务FU/表/字段名判据。运行双样本未验证。
- Save/Load：规则格式不变；默认值提取单测及刷新验证通过，数据库写入回显未验证。
- Export/Import：N/A，无序列化契约变更；前期只读检查导出携带完整configJson、Admin保存完整表单JSON。
- Clone：N/A，读取原有配置，不新增持久化字段。
- Version/Rollback：N/A，同上。
- Admin两条Import：N/A，同上。
- Portal runtime/parity：实际部署发起页响应样本红→绿；待办/我的申请使用实际提取入口单测，未单独做Select页面截图。

### 增量敏感信息与性能

- 检查本轮新增代码、脚本、测试、台账和说明：未引入凭证/token/个人业务数据；截图使用本地合成验证流程。
- 增量凭证模式扫描（新增diff行与4个新增文本文件）：0命中。
- 未增加API请求、缓存或遍历业务记录；仅规则转换时读取布尔属性，默认值逐项匹配选项。

### 残余风险 / 范围外问题

- 类型检查和三个旧转换器的lint不能整体PASS，已对比基线，无新增报错。
- 完整MI截图套件因缺附件样本未通过；本轮不修改范围外的MI数据与验证脚本。
- 浏览器响应样本不替代真实业务保存与双FU验收；未覆盖历史单选标量业务值切换多选后的兼容行为。
