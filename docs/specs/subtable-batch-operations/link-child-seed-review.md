# Code Review 结果：MI Details 子行播种

## 结论

CONDITIONAL。不放行 P3/P4。修复范围无未处置代码 finding；完整类型/lint 门禁仍失败，双 FU、第二身份运行验收未完成。

## Findings 与逐条处置

- 已修：`useSubTableLinkFormDialog.buildLinkedFormData` 用子表主键读取父行，再与子行主键比较。父子主键不同的合法 Details 显示为空；若父行普通字段恰与子主键同名同值，则可能误播种无关子行。改为同表链接按完整配置身份匹配，跨表链接按指向宿主表的已声明 FK 与引用主键匹配。
- 已修：不能 OR 合并多个指向同表的不同角色关系。多个关系必须由 `filterFkFieldName` 明确选择；匹配多个子行不任选第一行。缺失/不完整关系不播种，保持新行空模型行为，禁止继承兄弟行嵌套数据。
- 重审完整范围：辅助函数仅用于原 Details 模型构造，不改保存、权限、任务操作或持久化协议。无新增代码 finding；全量类型/lint 失败继续列为门禁，不宣称 PASS。

## 范围 / 验收映射 / 触发矩阵

A01/A16/A17/A18/A20 的旧 MI 路径保护。命中 config-over-heuristics、bugfix-verification、verify-ui-fix-with-screenshot、code-review；不修改产品文案、布局或配置格式。仅从 binding/fieldDefinitions/引用主键取关系，不使用业务列名白名单。

## 编译与静态检查

- `pnpm -C frontend/user-portal exec vite build`：PASS；重建部署 user-portal-frontend，未改数据卷。
- `pnpm -C frontend/user-portal exec vue-tsc --noEmit`：FAIL，407 项错误，本次三个源码/测试文件无类型诊断。不是完整类型 PASS。
- 定向 ESLint：新 helper、新测试、MI config PASS；存量 dialog 文件仍有 24 项 no-explicit-any。相同命令 stdin 审查 HEAD 版本为 26 项，没有删除规则或 suppress。
- diff check、增量 secret 扫描在提交前执行；不纳入构建产物/截图二进制。

## 修复验证

- 复现：同一个合成 MI 实例 API 已有 UUID/age，My Requests Details 两条均显示空 id，完整 MI 浏览器门禁失败。
- 根因：将自链接身份匹配误用于父子链接。
- RED：实际 composable 初始四测试中，合法子行、外来子行同名字段、显式过滤 FK 三项断言失败；未复制算法代替真实调用。
- GREEN：补齐复合引用、不完整引用、歧义关系、自链接、平台行身份后，相关四文件 20 测试 PASS。
- 最新产物完整 `pnpm -C frontend run regression:mi`：28 文件 226 单测及全部六项浏览器场景 PASS。随后将八个新测试接入固定清单，`pnpm -C frontend/user-portal run test:regression:mi` 为 29 文件 234 项 PASS；清单变更不影响部署代码。
- 同实例桌面 1366×768、窄屏 480×800：UUID 和 age=666 正确，pageerror=0；前后截图已打开检查。标签单行且不遮挡，未变布局。
- 未验证项：第二 FU、第二身份运行；完整类型/lint 仍失败；批量 Preview、同工作区 Assign、引擎实际结果和其他 P3/P4 门禁未完成。

截图均保留于绝对目录 `F:/hermes1.0/Workflow-Station---sun/frontend/user-portal/verification-screenshots/`：

- `2026-10-15_link-child-seed-before.png`
- `2026-10-15_link-child-seed-after-desktop.png`
- `2026-10-15_link-child-seed-after-narrow.png`

## Function Unit 通用性与生命周期

静态通用性 PASS；测试使用不同 PK/FK、复合引用及平台身份。运行仅合成 Clone 样本，不改源 FU。Save/Load 的 Details 读取链已验证；Export/Import、Clone、Version/Rollback、两条 Admin Import、Deploy 序列化均 N/A（没有新增配置字段或改变传输），完整批量生命周期另行验收。DW Preview 不使用此 Portal composable，批量 Preview parity 仍未完成。

## 增量敏感信息 / 性能 / 范围外问题

新增 fixture 仅任意标识与合成数据，无凭证/真实 PII；截图显示测试账号及合成实例。解析本地行集 O(n)，无新增 HTTP、监听器或持久化；不宣称性能提升。存量链接匹配模块的列名猜测和全量类型/lint 债务不在此次修复中扩改。
