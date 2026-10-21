# Link Form 同表不同外键修复验证

## 修复验证

- 复现：A-only 行点击目标 B，原代码直接打开 A；B→A 和 My Requests 同样错行。旧部署运行 `verify-link-form-scope-fix.mjs` 未出现无匹配提示，断言失败，exit 1，保存了修复前截图。
- 根因：Link Form 把物理表身份当成绑定过滤作用域；打开、初始化、补数据、保存共用或重复了该判断。
- 红→绿：新增作用域测试最初 9 项中 7 项失败；修复后通过。审查补出的跨作用域修改主键用例也先失败后通过，最终新增 12 项全部通过。
- 页面红→绿：同一个旧任务、同一脚本，旧部署失败；构建并重建容器后通过。错行弹窗不再打开，提示无唯一匹配；自身保存、刷新、B 行不变、My Requests 只读均通过，pageerror 为 0。
- 未验证项：Completed Tasks 历史快照专属入口、同一实例内不同用户并发写入、FU 导入导出/回滚 round-trip 未实测；不以其他入口通过代替这些证据。全仓类型检查按用户此前确认不属于本次范围。

## 生效行为

- 相同 binding ID：仍是自身绑定。
- 不同 binding ID、同表且同过滤外键/引用表：允许跨表单或 MI 使用点击行，不因父外键尚未填入而清空未保存行。
- 同表但过滤关系不同：只接受目标 binding 数据中与点击行配置主键（缺失时平台 UUID）相同且唯一的记录；校验目标外键，引用同一父表时还需与点击行的父引用一致。
- 无匹配或多匹配：三语言提示，不打开弹窗、不应用修改；不取 B 的第一行、不借用 A 行。
- 保存重新检查目标范围和编辑后行身份，拒绝旧弹窗或主键变化造成的串写。合法共享记录写回原行，不增加嵌套副本。
- 补数据时不再把另一过滤作用域的同表 binding 合并到空目标。

## 修改文件

- `frontend/user-portal/src/composables/subTableField/linkFormBindingScope.ts`：区分物理表与过滤作用域，显式 table ID 不同不能被展示名相同覆盖。
- `sameTableLinkFormOpen.ts`：同表打开的集中决策；提取到短文件，避免扩大原本已超长的 open composable。
- `subTableLinkFormRowMatch.ts`：宿主传递过滤配置，复用作用域判断；显式目标失效时不按名称冒认自身。
- `subTableLinkFormSeed.ts`：唯一行身份与目标范围匹配，初始化/打开/保存复用。
- `useSubTableLinkFormOpen.ts`：调用集中决策，旧文件由 546 行缩为 544 行。
- `useSubTableLinkFormDialog.ts`：保存校验、无匹配提示及补数据范围限制。
- Portal 三语 `linkForm.noMatchingRow`、现有 Help Extend Action 三语说明与 llms-full 同步更新，无新增配置或帮助入口。
- 新增 `linkFormBindingScope.test.ts` 和两个可重复执行的合成实例验证脚本；旧夹具脚本增加 setup-only 和独立 Portal 测试账号选项。

## 测试与部署

- 修改前 Portal 全量基线：317 文件、2389/2389 PASS；当时 Portal 产品代码无未提交差异，没有 stash/reset 用户其他改动。
- 修复后 Portal 全量：318 文件、2401/2401 PASS。
- Link Form 专项：4 文件、27/27 PASS。
- 完整 `pnpm run regression:mi`：29 文件、234/234 单测，以及 6/6 Playwright 场景 PASS。使用明确固定的合成 MI 实例，未遍历或修改业务任务。
- Portal `pnpm run build` PASS，最终产物已重建 `user-portal-frontend`；Help build PASS，已重建 `platform-help-frontend`。两个容器均 Up，日志未显示启动失败。
- Help 页面验证 153/153 PASS。
- 新增作用域/open helper、seed helper、作用域单测的 ESLint PASS；旧长文件未宣称全量 lint 通过。
- `git diff --check` PASS；新增/修改行高置信私钥、AWS key、Bearer/JWT 模式增量扫描 0 命中，脚本人工核对沿用密码登录 helper，无新增硬编码凭证。

## 双样本、保存与查库证据

### 已有合成 FU 50091，账号 123456

- 实例 `8abe3258-cc70-11f1-89f2-0a31b3ca61b3`，任务 `8ac844c3-cc70-11f1-89f2-0a31b3ca61b3`。
- A→B、B→A、My Requests 两个方向均拒绝错行，没有保存请求或数据变化。
- A 自身保存后刷新一致；服务端重读确认 B 业务字段不变。
- 最终部署复验 A caption `SCOPE VERIFIED 1792499047740`；B 为 `B ONLY`。

### 修复中新建合成 FU 50092，账号 developer

- 实例 `74fcf544-cc79-11f1-89f2-0a31b3ca61b3`，任务 `75061d4f-cc79-11f1-89f2-0a31b3ca61b3`。
- 独立新表、独立 binding ID，重复上述双向拒绝、自身保存、刷新、B 不变及只读检查，通过，pageerror 为 0。
- 之后通过正常带 binding scope 的 submit API，为**已有** A 行补上 B 外键，建立合法共享记录；没有弱化后端新行外键守卫，也没有改业务 Demo。
- 共享 A 行从 A→B 打开并保存，再刷新从 B→A 打开，均定位同一主键；最终部署复验 caption `SHARED VERIFIED 1792499065227`。
- 服务端重读：仍然两条记录，没有新增嵌套/重复记录，无关 B 行业务字段与测试前一致；取消后没有额外保存。
- PostgreSQL 读取两实例 `variables.__subTables__`：50092 的 A 行两个外键均为 `LF-e96fb295`，B 行仍只有 B 外键、caption 为 B ONLY；50091 的 A/B 外键及最终 caption 仍与第二账号操作前一致。
- 这是两个账号各自实例的隔离验证，不冒充同一实例内多用户并发验证。

脚本：

- `LOGIN_USER=123456 LINK_FIXTURE_TASK_ID=8ac844c3-… node scripts/verify-link-form-scope-fix.mjs`
- 第二账号、新实例使用同一脚本；共享行阶段使用 `verify-link-form-shared-row.mjs`，显式校验合成 FU 名及配置里的 A binding ID。
- 共享阶段后该实例不再符合 A-only/B-only 脚本的前提；重跑负例应新建实例，不将共享行应能打开误判为回归。

## 截图（已查看）

- [修复前错误弹窗](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-scope-a-to-b-failed.png)
- [无唯一匹配提示](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-scope-8ac844c3-a-to-b-notice.png)
- [自身保存后刷新](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-scope-8ac844c3-self-reload.png)
- [只读入口不再打开错行](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-scope-8ac844c3-readonly-a-to-b.png)
- [合法共享记录跨绑定保存及刷新](../../frontend/user-portal/verification-screenshots/2026-10-20_link-form-shared-75061d4f.png)
- MI 截图同目录 `2026-10-20_app-788a3ae1-*`、`2026-10-20_task-83319626-*`。

## Code Review 结果

### 结论

针对本轮修复的工作区增量自检，不是全仓提交审查。功能实测通过；未实测边界如上，整体交付标 **CONDITIONAL**，不宣称所有历史入口和生命周期已验证。

### Findings 与逐条处置

- **Major（已修）**：跨作用域已匹配后若表单修改主键，仍可能把另一主键写回点击行。补回归先红，再在保存时检查编辑后数据仍匹配原身份；现在不 emit，数据不变。
- **Minor（已修）**：在既有超长 open composable 内增加分支会扩大超标文件。提取 25 行同表打开 helper，原文件净减 2 行，未重构不相关路径。
- **兼容性检查（已处理）**：同作用域 MI 尚未填父 FK 的行不能被新跨作用域校验误伤。增加正例，同作用域仍按原身份初始化。
- 重审上述增量及本轮完整产品差异后，无已知未处置 Blocker/Major。未提交的新文件仍保留在工作区，未执行 commit/push。

### 范围 / 验收映射

仅 Portal Link Form 的作用域、身份初始化、打开及保存；不改任务分派、权限模型、数据库结构、设计器配置格式或现有业务 FU。此前 DW 折叠/间距和目标标签改动保留，不混称为本轮重新实现。

### 触发矩阵（命中项）

配置判据、错误处理、稳定行身份、Portal MI、三语文案/帮助、构建部署和截图、保存隔离、增量敏感信息。没有新增 SQL/XML/LDAP/服务 URL/鉴权代码。

### 编译与静态检查 / 测试与运行证据

见上方真实命令结果。全仓类型检查是用户明确排除项，不把 build 当类型检查。

### Function Unit 通用性与生命周期

- 通用性：配置过滤 FK/引用表和稳定主键驱动；产品代码没有固定业务 FU、表名或字段名判据；已有与新建合成 FU 实测通过。
- Save/Load：现有、新建实例自身保存及共享记录跨绑定保存/刷新通过。
- Export/Import、Clone、Version/Rollback、Admin JSON/Deploy 两条 Import：无新字段、新枚举、包或快照格式修改，现有配置消费者契约不变；本轮未执行 round-trip。
- Deploy/Activate：50092 正常设计与部署创建，Portal 消费已有过滤配置；旧 50091 无需重部署 FU 即生效。
- Portal runtime/parity：待办和 My Requests 已验证，完整 MI 回归通过；Completed Tasks 专属入口未实测。

### 增量敏感信息

范围为本轮产品新增行和新增测试/脚本；扫描与人工检查未发现新增凭证。截图为本地验证产物，未强制入库。

### 性能

非性能优化；没有新增网络请求、缓存或监听器。同表唯一匹配按目标 binding 的行数组扫描，避免扩大到其他绑定；不宣称性能提升。

### 残余风险 / 范围外问题

未验证项保持显式记录，不扩大修复到其他子表全局 merge/旧启发式路径。保留合成 FU 50092 及实例供复验，无删除操作；原业务 FU 50083 未修改。没有自动提交，也没有修改 `.kiro` 台账。
