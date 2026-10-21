# Link Form 同表不同外键验证 — 2026-10-20

> 本文保留修复前的失败证据；后续实现与红→绿结果见 [作用域修复报告](2026-10-20-link-form-scope-fix.md)。

## 结论

**FAIL：同表多绑定不能判定为全部正常。** 自身绑定的打开、保存、刷新通过；不同过滤外键的跨绑定打开错误，My Requests 只读入口也复现。没有保存错误的跨绑定弹窗，没有改动业务 Demo 的配置或数据。

显示优化已完成并部署：重名选项显示为 `Same lines [case_ref · #51317]` / `Same lines [case_ref_2 · #51318]`，普通唯一名称不加后缀。标签从明确配置的 `filterFkFieldId` 解析字段名，保存值仍是原 binding ID，不改表名、外键或 Link Form 配置契约。

## 验证思路与期望

同一表分别通过 A/B 两个明确配置的过滤外键绑定，制造 A-only/B-only 数据，防止用相同内容误判。先确认两个列表过滤正确，再检查 Link Form 是否遵循选择的目标 binding，而不是仅凭 table ID 认作自身绑定。自身绑定保存后从服务端重读并刷新；跨绑定发现错行后仅取消，不冒险测试保存。

跨绑定的最低期望是不把 A-only 行当成 B binding 的已有数据。本次单行夹具将 A→B 预期设为 B ONLY；若产品定义要求按行关系匹配而非直接定位 B 行，则仍需明确匹配规则，不能静默打开 A-only 行。无匹配时应明确提示/按配置处理。

## 样本与实际输出

- 业务 FU 50083 仅检查配置：PROCESS 50827 的绑定 51248/51307 指向同表 50524，过滤外键分别为 `case_ref` / `case_ref_2`。
- 独立合成 FU 50091：`Link Form verification 1792489920942`，同表 `lf_lines_mvgi7uts`，PROCESS A/B 51317/51318，TASK A/B 51320/51321。
- 流程实例 `8abe3258-cc70-11f1-89f2-0a31b3ca61b3`，任务 `8ac844c3-cc70-11f1-89f2-0a31b3ca61b3`。
- A-only：`case_ref=LF-a71ab15f`，`case_ref_2=null`，初始 caption `A ONLY`。
- B-only：`case_ref=null`，`case_ref_2=LF-a71ab15f`，caption `B ONLY`。

结果：

- 列表隔离：A 区只显示 A-only，B 区只显示 B-only，通过。
- A 自身绑定：打开 A ONLY，改为 A UPDATED VIA SELF，正常保存成功；刷新后值一致，通过。
- B 未受影响：服务端重读 B 的业务字段与保存前一致（比较排除内部 `_wsRowVersion`），通过。
- A→B：仍打开 A UPDATED VIA SELF，失败。
- B→A：仍打开 B ONLY，失败。
- My Requests 只读 A→B：仍打开 A UPDATED VIA SELF，失败；没有 Save 按钮。
- 跨绑定弹窗全部取消，服务端数据与自身绑定保存后的快照一致。
- 数据库读取实例 `variables.__subTables__` 再确认：A caption 已更新且只有 case_ref，B caption 仍 B ONLY 且只有 case_ref_2。

## 原因定位与处置

`frontend/user-portal/src/composables/subTableField/subTableLinkFormRowMatch.ts:23` 的 `subTableBindingMatches` 在 binding ID 不同时，仍把相同 table ID 判为同绑定，没有区分过滤外键。`useSubTableLinkFormOpen.ts:104` 因此走自身行快捷路径；`useSubTableLinkFormDialog.ts:277` 的保存路径也依赖相同判据。

这属于实际运行逻辑缺陷，不是下拉名称重复造成的误选。本次仅验证并优化显示，**未修复 Portal 判断，也未执行错误跨绑定保存**。后续应按显式 binding/filter-FK 配置区分作用域，同时回归跨表单合法的同表 MI 别名场景，不能简单禁止所有不同 binding ID。

## 显示改动与检查

- `FormDesigner.vue` 透传配置过滤外键对应的字段名。
- `SubTableBindingOption` 增加可选显示元数据；`SubTableColumnConfigDialog.vue` 仅对重名选项附加外键与 binding 编号。
- 英文、简中、繁中现有 Extend Action 帮助和 llms-full 同步说明；没有新增帮助入口。
- 新增标签单测：2/2 PASS（18:40 再跑通过），覆盖不同外键、旧配置缺元数据和唯一名称。
- DW build PASS；Help build PASS；对应 Docker 服务已重新构建部署。
- Help 校验：153/153 PASS。
- 标签 UI 自动化确认两个真实选项不同；取消后服务端 list-view 配置不变。
- `git diff --check` PASS；验证脚本语法检查 PASS。
- 初始跨绑定复现脚本断言失败，exit 1。后续保存/只读脚本曾输出 FAIL_CROSS_BINDING 但没有返回非零退出码，旧 exit 0 不是成功证据。修复阶段已由 `verify-link-form-scope-fix.mjs` 替代，旧 existing 入口转调新脚本；夹具脚本同步采用“无唯一匹配不打开”的正确断言。本文保留当时失败结果，不将旧脚本的错误预期当验收标准。
- 全仓类型检查未执行，依用户此前确认不纳入本次范围；不宣称全仓检查通过。

## 截图

- [DW 下拉区分外键与 binding](../../frontend/developer-workstation/verification-screenshots/2026-10-20_link-form-binding-labels.png)
- [自身绑定保存后刷新](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-dual-key-self-saved.png)
- [跨绑定错误打开自身行](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-dual-key-cross-after-self.png)
- [My Requests 只读入口同样错行](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-dual-key-cross-readonly.png)

## 未验证、夹具与残余风险

- 双外键共享同一条记录、跨绑定保存、completed-task 入口、多用户隔离未验证。
- 构造共享行时，非所属绑定外键的写入被现有后端守卫以 403 拒绝；没有降低守卫权限，未把该失败当运行通过证据。
- 新建 FU 50091 用于隔离验证；业务 FU 仅静态读取，并未在已有业务实例上做写入回归。
- 保留测试 FU 50090（早期克隆，DRAFT，因不可编辑 workspace 未继续）和 50091 及合成测试实例；未删除任何 FU 或业务数据。
- 早期夹具的启动作用域和配置存储位置问题已在验证脚本中调整，不作为产品修复或通过证据。
- 本次没有修改 Portal 产品运行代码；前一轮 Table Columns/Extend Action 折叠和间距改动保持原样；未 commit/push。
