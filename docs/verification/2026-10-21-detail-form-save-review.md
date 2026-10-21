# Detail Form 保存修复及 changes 提交前审查

## 结论

PASS（下述有界提交范围）。无未处置 Blocker/Major。全仓类型检查按用户明确指示排除，不把 Vite build 当作类型检查通过。本文是最终提交审查；10 月 20 日报告保留当时结果，不覆盖历史失败证据。

## Findings 与逐条处置

- **Major，已修复**：`FormConfigJsonTableProvisioner.matchOrCreateMain` 对 DETAIL 按画布字段名重合度猜目标，忽略 `boundTable`。两个新 Input 字段不匹配已有表时，repair-config 返回 400 `FORM_BINDINGS_REQUIRED`。现在仅从当前 FU 的 SUB 表集合按显式目标 ID 取表；不存在或非 SUB 明确失败，不猜另一张表。
- **Major，已修复**：实际页面验证发现 repair 成功后，`useFormSave` 的最终 PUT 漏传 `boundTableId`，后端 update 会清空选择。现在透传当前表单目标，新增回归先失败后通过；页面与数据库双样本复验目标保留。
- **Minor，已修复**：repair 异常统一显示无关的 pasted config 提示。改用已有用户可读 HTTP 错误解析器；400 用例验证显示具体错误且不执行最终持久化。
- **Minor，已修复**：llms-full 中先前补充的 Link Form/折叠说明处于下一节标题之后，已移回相关章节，重新构建及验证 Help。
- **Minor，已修复**：折叠验证脚本的间距查询依赖修复后才存在的 wrapper，会使旧页面 before 模式提前报错；改从新旧页面都有的首分区定位字段，不改变间距断言。
- 重审修复增量及完整待提交产品差异后：Blocker 0，未处置 Major 0，Minor 0，Question 0。没有为通过检查降低校验或添加猜测兜底。

## 范围 / 验收映射

- Detail Form Save：遵循创建时选择的 SUB 表；控件字段不同名或空画布不影响绑定目标；非法目标拒绝；重复保存不重复创建 PRIMARY 或表。
- DW List View：Table Columns / Extend Action 独立上下折叠，保留原 × 功能与搜索；消除字段区下方大空白，短视口字段内部滚动。
- DW Link Form：同表重名绑定显示配置过滤外键与 binding ID，仅改变标签，不改变保存值。
- Portal Link Form：同表不同过滤作用域不冒认自身行；无唯一匹配不打开、不写入；合法共享记录能跨绑定打开、保存、刷新；修改身份和陈旧弹窗不能串写。
- 包含对应测试、可重复执行的合成数据验证脚本、三语帮助和验证报告；不修改业务 FU 50083，不包含日志、截图二进制、构建产物或凭证。

## 触发矩阵（命中项）

配置驱动、稳定行身份与过滤外键、异常处理、Portal MI、可见 UI、弹窗标签、三语 i18n、Help、后端输入归属、测试、Docker 部署、增量敏感信息。按 config-over-heuristics、bugfix-verification、UI 截图及 code-review 技能检查。

没有 Entity/schema/API DTO/导出包格式/快照格式或权限模型变更，没有新增 SQL、XML、LDAP、认证或远程 URL 构建逻辑。

## 编译与静态检查

- 后端：`mvn -o -pl backend/developer-workstation -am package -DskipTests` → BUILD SUCCESS。
- DW：`pnpm run build` → PASS；Help：`pnpm run build` → PASS；Portal 本批作用域修复 build → PASS（见前日作用域报告）。受影响服务均已重建。
- 新增 Portal scope/open helper、seed helper 及 scope 测试定向 ESLint → PASS。不宣称旧长文件全量 lint 通过。
- 七个本批验证脚本 `node --check` → PASS；`git diff --check` 及 staged diff 卫生检查 → PASS。

## 测试与运行证据

- 后端 Provisioner 红基线：5 项中 3 项失败；修复后 5/5。连同 FormDesignComponentImpl 与 Scene 测试最终 **22/22 PASS**。
- DW Save 专项 **13/13 PASS**，包括错误消息与 boundTableId 保留的红→绿回归；DW 全量 **184 文件、1158/1158 PASS**。
- Portal 全量 **318 文件、2401/2401 PASS**；完整 `pnpm run regression:mi` **234 单测 + 6 页面场景 PASS**，使用固定合成实例，不遍历业务任务。
- Help 页面 **153/153 PASS**。此前首次有一项不相关 Action-email 图片加载失败，重跑通过；最终 Help 构建/部署后再次 153/153，不将首次失败隐藏为通过。
- Detail Save 旧部署真实 UI：合成表单 50858 repair-config → 400；中间部署 repair 成功但最终 PUT 清空目标，断言再次失败。两个失败均保留，不用 exit 0 或单测代替页面成功。
- 最终已有合成 FU 50091 / 表单 50859 / 表 50541，新建独立合成 FU 50093 / 表单 50860 / 表 50545：真实点击 Save、服务端重读、页面重开、重复 Save 均 PASS；各一个 PRIMARY，没有新增表，pageerror 0。
- PostgreSQL 只读核对：50859 → PRIMARY 51329 / 表 50541；50860 → PRIMARY 51330 / 表 50545；业务 50857 仍选择 50524 且无 PRIMARY。两次脚本均对业务表单完整 API 结果做前后相等断言。
- 折叠脚本最终复跑：50084 因没有要求的 synthetic tag 被安全拒绝，没有设计写入；改为合格夹具 50088 后 PASS，间距 0px，折叠上移 99px，短视口内部滚动及独立开关通过。脚本输出的同名 after-spacing 截图现为本次复跑；前日报告的 487px 是其当时样本结果，不混作本次测量。
- 50858 是失败阶段保留的测试表单，不冒充最终成功样本；未删除测试或业务数据。

已人工查看截图（本地验证产物，不强制入库）：

- [修复前页面](../../frontend/developer-workstation/verification-screenshots/2026-10-21_detail-save-before-50858.png)
- [已有合成 FU 保存并重开](../../frontend/developer-workstation/verification-screenshots/2026-10-21_detail-save-after-50859.png)
- [新建合成 FU 保存并重开](../../frontend/developer-workstation/verification-screenshots/2026-10-21_detail-save-after-50860.png)
- [Link Form 作用域与共享行证据](2026-10-20-link-form-scope-fix.md)
- [分区折叠及间距证据](2026-10-20-list-view-collapse.md)
- [最初同表多绑定失败与标签验证](2026-10-20-link-form-dual-binding.md)

## Function Unit 通用性与生命周期

- 通用性：PASS。显式目标表 ID、过滤 FK/引用表和稳定行身份驱动，无业务 FU/表/字段名硬编码；已有与新建合成 FU 均实际验证。
- Save/Load：PASS。绑定建立、最终 PUT 保留目标、重复保存及重开均实测。
- Export/Import、Clone、Version/Rollback、Admin JSON/Deploy 两条 Import：本批不改变字段/包/快照契约；现有 boundTableId 与 binding/filter 配置继续传递，没有需要新增映射的配置。未执行全生命周期 round-trip，不宣称这些运行验证通过。
- Deploy/Activate：DW 后端、DW/Portal/Help 前端实际构建并重建对应容器；既有 FU 不需改业务配置即可采用修复。
- Portal runtime/parity：待办、My Requests、合法共享行及完整 MI 验证通过；Completed Tasks 历史专属入口没有单独实测，保持记录。

## 增量敏感信息

对完整 staged 新增行、新增脚本/测试/文档及拟提交 message 做高置信私钥、密钥、Bearer/JWT 和凭证字面量扫描，并人工复查测试登录路径。无新增真实凭证或个人信息；沿用已有登录 helper，不提交本地日志/截图/环境文件。

## 性能

非性能优化，不宣称性能提升。没有新增产品网络请求、缓存或监听器。跨作用域唯一匹配仅扫描目标绑定行；标签重复判断发生于设计器小型选项列表，不在 Portal 行渲染热路径。完整 MI 回归通过。

## 残余风险 / 豁免

- 全仓类型检查：用户明确排除，未执行；不修改无关历史类型问题。
- Completed Tasks 历史专属入口、同一实例多用户并发、导入/克隆/回滚 round-trip：未实测，本批无新增契约或该入口专属修改；需这些专项证明时另开覆盖，不以本报告替代。
- 最初 Docker 镜像构建早于 DW build 完成，造成 index.html 缺失及临时 500/403；已在完整构建后重新构建镜像恢复，随后双样本 UI 及重跑通过。不是产品修复证据，也未添加兜底代码掩盖部署问题。
- 未操作用户当前未保存画布；旧页面需要先备份未保存配置，再刷新加载新前端。普通 Input 保存成功不代表绑定数据字段，显示业务列仍应 Import Table Fields。
- 回退不需 schema 操作；可回退本批代码并重新构建对应服务，已有设计配置格式不变。合成 FU 保留供复验，未执行删除。

## 范围外问题

铅笔按钮偶发点击问题未实施修复；无关全仓类型错误及 `.kiro` 台账不改。用户仅授权提交，不执行 push。
