# Code Review：批量分派模式往返的个人展示缓存

## 结论 / 范围

CONDITIONAL。本批只处理 Batch Assign 更换目标时遗留的个人展示缓存，并补角色→个人→角色真实往返；不是整体 P2/P4 放行。不修改普通单行分派、已有任务归属或目录数据。

## Finding 与修复验证

- Major（已修）：新合成实例 `8d4acf81-c976-11f1-8f36-4ee612046d7e` 在 Review 执行两行角色配置→第一行个人→统一恢复角色。个人字段已为 null，但第一行多出 `assignee_display_name`，主表仍出现1个 `.assignee-name`。完整行比较失败，未执行 Complete；失败实例保留。
- 根因：`applyBatchAssignment` 复制完整原行，只清理配置映射的互斥字段，保留了先前页面个人名称缓存；列表优先显示该平台缓存。它是已有平台行展示元数据（`tasks/internal.ts`、现有单行 `applyAssigneeDisplayNameToRow` 契约），不是根据业务列名猜测分派语义。
- 最小修复3行：个人目标变化时删除旧展示缓存，角色目标替换时删除个人展示缓存；配置字段仍按原映射赋值，不改基线行，未修改字段权限、行身份、PK/FK 或任务操作。
- 同一 pure 测试红→绿：新增用例首次7项中1失败，明确旧 display 仍存在；修复后草稿7项、Assignment草稿8项、Workspace17项，共32项通过。相同个人目标保留缓存、原对象不变和隐藏业务字段保留也有断言。相同目标的无改动操作不额外 emit，未扩大成平台缓存治理。

## 构建与运行证据

- `pnpm -C frontend/user-portal run build`：Vite 构建成功（4183模块）；存在既有 Sass/大chunk警告，未屏蔽规则。独立 `typecheck` 仍383错误，本次两份产品/测试文件相关错误0；不能用 Vite 成功冒充类型门禁通过。
- 重建 `user-portal-frontend` 并确认运行镜像 `sha256:07be7a65b04f4c2345bac6bd711df795f5fc7118eb06e578f5860d857bdd87ac`。后端仍为上一提交镜像 f9ba105a，未改后端源码/jar。
- 新实例 `5fd101da-c977-11f1-8f36-4ee612046d7e`、Review `5fdc99e1-c977-11f1-8f36-4ee612046d7e` 在新前端重跑同一往返全链成功：第一次单行个人 ASSIGN1补丁，第二次统一选两行但只生成实际改变的1补丁。保存后完整字段与原角色行相等，仅各行按既有两次 Save 递增版本2；没有忽略任意业务字段差异。
- 页面无旧 `.assignee-name`，保存/重开/关闭不改数据；每次 Save 引擎任务集合不变。最后 Complete 创建 `6e65ea85-c977-11f1-8f36-4ee612046d7e` / `6e643cd0-c977-11f1-8f36-4ee612046d7e`，均未领取、真实候选集合各1人、BU/Role及两份身份正确，pageErrors=0、目录写入0。
- 修复前截图 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_role-person-role-stale-before.png`；修复后 `2026-10-16_role-person-role-fixed-grid.png`、`2026-10-16_role-person-role-fixed-workspace.png`、`2026-10-16_role-person-role-fixed-workspace-narrow.png` 均保留在同目录。个人名称脱敏；图像不作为未覆盖的完整窄屏 parity 证明。

## 触发矩阵 / 通用性 / 性能

命中实现自主、bugfix-verification、config-over-heuristics、可见UI截图及MI热路径、code-review。纯数据补丁无查询、远程调用或新增依赖；固定平台缓存失效不替代冻结 assignmentConfig 的业务字段映射。没有包格式、持久化实体或配置更改，Export/Import/Clone/版本回滚/Admin消费者本批写出端 N/A，不宣称全部生命周期复验。

## 最终门禁与残余风险

- 第二套合成 FU50035（普通 Edit+Assign，而首例FU50036为Assign-only）也完成相同 UI 往返：实例 `e34161ee-c977-11f1-8f36-4ee612046d7e`、Review `e34b7455-c977-11f1-8f36-4ee612046d7e`，实际角色任务 `f28a0a39-c977-11f1-8f36-4ee612046d7e` / `f286fcf4-c977-11f1-8f36-4ee612046d7e`。全部业务字段、模式互斥、两份身份及真实候选池校验通过，pageErrors=0。脚本等待两条 `.assignee-role-pool` 可见后才断言个人名称0，避免空表瞬间假通过。截图 `2026-10-16_role-person-role-edit-assign-grid.png` 及对应 workspace/窄屏图保留于上述绝对目录。
- 新前端上的完整 `pnpm -C frontend run regression:mi`：234单测及6浏览器场景全部通过，最终 OK — unit + screenshots；没有复用旧前端的运行结论。
- 独立 reviewer 重跑32项全部通过；staged4文件完整复审无新增 Blocker/Major，node --check、cached diff --check 通过，68条非空新增行四类敏感扫描0命中。独立目视已脱敏 grid 图，不把遮盖文字的截图单独当作无旧人名证明；依据上述 DOM 和完整行校验。
- 383既有类型错误、无首人工任务启动、引擎执行资格竞态/异常可见性、New Request/Preview及多主体验收仍开放，不能由本批推导 P4 完成。两套FU仍同测试操作者，不冒充第二主体或导入FU验收。
