# Code Review：批量字段权限与只读默认值

## 结论

CONDITIONAL。本批权限/默认值反例已修复并有真实运行证据，可保存为实现批次；不是生产放行，也不表示 P2/P3/P4 完成。完整类型基线、MI 服务端权威 scope 和配置生命周期验收仍是阶段门禁。

## Findings 与逐条处置

### Blocker（本批已修复）

- `SubTableBatchRowDesign.capture` 只捕获字段，没有设计字段权限；普通 schema 中的只读字段可被批量写入。
  - 处置：按发布包中 binding 复合 key 展开有限字段权限；冻结 `fieldWriteModes`，Add/Edit 的服务端校验和工作区共用此来源。PK/FK/计算字段仍受原专用规则保护。
- Admin `FunctionUnitContentComponent.assembleFormContent` 解包 configJson 后遗漏发布权限。
  - 反例：两份真实新 FU 启动报 `Published batch field permissions are missing`，数据库发布 wrapper 明确含权限。
  - 处置：DTO 显式携带 `publishedFieldPermissions`，从 catalog wrapper 解析，不查询 live DW 补权限；旧包 absent/null 保持缺失，新批量捕获拒绝缺失来源。两份 FU 重建后启动成功。
- 保存/Complete 仅有批量标记时校验字段，省略标记不应提高当前任务权限。
  - 处置：新启用实例加载当前发布权限；无标记的原始提交在旧过滤/PK 准备之前校验，事务内再按当前基线复核。Complete 也不依赖标记。H2 JDBC 的真实组件用例先失败后通过；实际接口使用最新版本、移除覆盖副本，拒绝修改只读值，重读不变。
- 原 Add 前置字段权限检查把合法只读默认值当成 Edit；工作区数值默认值又保留为字符串。
  - 处置：Add 在完整 canonical 值边界校验，只读值必须等于已声明的字面默认值；Edit 仍禁止。UI 复用既有类型转换，保留 `0`/`false`。默认值单测与真实只读数值 Add 均通过。
- 新增两处 TypeScript 错误：未知值直接传 Object.values；测试向身份字段定义写未声明 dataType。
  - 处置：先窄化对象；测试按真实类型组织字段。再次全量 vue-tsc 的这三个改动文件不再出现错误，不能据此宣称全量类型通过。

### Major（阶段门禁，未放行）

- 全量 Portal vue-tsc 仍 FAIL；没有降 strict、加忽略或用 Vite build 冒充 PASS。
- Edit/Assign-only 早期契约没有完整 rowDesign；本批捕获针对 Add/Delete membership，不能宣称全能力字段授权已冻结。
- 服务端 MI 身份/切片、所有普通无标记写入的版本/身份保护、跨用户矩阵、DW Preview parity、发布前只读必填字段默认值完整性尚需闭合。禁止启用生产配置。

### Minor / Question

- 测试攻击请求起初仅修改 formData，未改 subTableData，Controller 的覆盖副本使请求实际无该非法差异；另一次复用了过期版本，409 不能证明 readonly 拒绝。
  - 处置：使用最新 fieldValues，删除 subTableData/批量声明/scope，明确断言 403 和持久化数据不变。以上错误测试不计为产品 RED/PASS。
- 未知/重复/缺失配置主键不再转换成字符串 `null` 并匹配；两个更名主键用例通过。

## 范围 / 验收映射

A04/A08/A13/A19/A21/A26：设计只读字段、当前任务权限、类型化默认值、无标记负例、完整/部分 canonical 保留。不是 D7 Assignment 完整验收。

## 触发矩阵

implementation-autonomy、code-review、bugfix-verification、config-over-heuristics、function-unit-portability、secure-coding-sast、verify-ui-fix-with-screenshot、portal-dialog-form-labels；Portal MI、性能与兼容规则。

## 编译与静态检查

- Admin + Portal Maven package/test 成功；原编译/JaCoCo 数据不匹配警告保留，不声称覆盖率通过。
- Portal Vite build 成功，工作区/能力解析 17 测试通过。
- 全量 vue-tsc FAIL，本批文件已修复新增错误；完整类型门禁保留。
- 完整 diff、git diff --check 和新增行/文件增量敏感信息检查随提交核查。

## 测试与运行证据

- Admin CatalogFormSnapshot/Content：15 测试通过，包含发布权限传递、旧 null、非法模式拒绝。
- Portal 新增字段/当前任务校验、发布权限、真实 JDBC 事务、scope/保存/Complete 与快照测试组合通过；真实事务类为 12 参数化测试，H2 JDBC 不替代 PostgreSQL/JPA 故障注入。
- Docker 重建 Admin/Portal/Portal frontend，健康后实际操作。
- 新建 FU 50025/50026，分别人工/UUID PK，不同字段/binding；只读数字初始 1/2，新增默认 0；非法无标记变更 403 且重读不变。Add/Delete-all→reload→真实 Complete 后数据库均 COMPLETED、0 行，能力快照和删除身份保留。
- 完整 MI 门禁：28 文件/226 单测及 6 个固定 synthetic 真实页面场景通过，非 unit-only。
- 截图目录：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots`。
  - `2026-10-14_batch-manual-readonly-add-persisted.png` 已实际核看：默认 0、只读无输入、统一赋值禁止、单行 label 完整对齐。
  - `2026-10-14_batch-manual-readonly-*` / `batch-uuid-readonly-*` 保留桌面、重开、删空与窄屏截图，数据为 synthetic。

## Function Unit 通用性与生命周期

由配置主键、binding、元数据和发布权限决定，不写死 FU/业务字段。两种新建 FU 运行通过；既有 MI 克隆回归通过。原 ZIP/两个 Admin 导入保存 wrapper 的结构不改，新增仅运行内容 DTO 投影；启动以该投影捕获，当前任务读取同 catalog wrapper。完整 Export/Import/Clone/Rollback 成对验收仍未验证，不能以本次 DW Deploy 成功替代全部生命周期。

## 增量敏感信息

检查本次新增/修改行、未跟踪源码/测试、文档和脚本。无新凭证或真实业务数据；脚本凭证仍由既有登录助手提供，未输出 token。

## 性能

字段按冻结 schema 校验，原行配置身份建立索引，无逐行 DB/HTTP。新增实例权限查询为每次任务操作的固定查询，不宣称性能提升；负载压测未执行。部分提交只复制遗漏表，避免后续 scope 变更基线引用。

## 残余风险 / 豁免

仅实现批次保存；生产开启必须等待设计文件全部必需门禁。缺少 rowDesign 的 Edit/Assign-only、PK/FK/版本/MI 授权以及启动来源边界继续跟进，无风险放行豁免。

## 范围外问题

全量前端旧类型错误与历史 JaCoCo 累积数据警告不在本批顺手清理；保留其失败状态。
