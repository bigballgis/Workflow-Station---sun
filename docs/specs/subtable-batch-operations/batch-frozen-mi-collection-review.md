# Code Review：冻结映射构造初次分派 MI collection

## 结论 / 范围

CONDITIONAL。本批只替换增强实例当前表单已开启 Batch Assign 的 MI collection 构造；不是完整目标资格、角色池、P2/P3/P4验收。无快照旧实例、当前表单无Assign的控件保留旧路径，不修改旧评分/候选列算法。未新增任务操作接口、schema、共享jar、配置包字段或UI产品代码。

## Findings 与逐条处置

- 原构建器即使读到已部署XML，仍从live DW取PK、对所有子表评分、尝试assignee候选列并跳过坏行。新增 `SubTableBatchMiCollection` 按冻结table/PK/分派映射严格读取canonical store，完整批次验证后才写collection；不丢弃缺目标/身份行、不猜列、不按可见字段重建业务行。首次6项测试在旧转发实现上1失败2错误；后续扩展至15项全绿。
- Major（已修）：最初allControls按全实例扁平ID索引，可能混淆不同表单相同控件ID。撤掉该实现，不改Snapshot/TaskForm加载器；直接从实例绑定XML的当前userTask读取formId，再用既有projectForForm验证版本/digest并取本表单控制。跨表单同ID/不同binding/不同目标测试通过。缺部署formId明确拒绝；非空且不在快照的表单保持协议中的“无批量配置”，不宣称检测了所有来源丢失。
- Major（已修）：仅按当前可达subProcess匹配可能在目标及表名都漂移时落旧路径。遍历前校验当前表单所有冻结Assign目标、表名及模式/字段与整份bound BPMN中的对应任务一致；双漂移测试明确拒绝且零JDBC/零collection写入。只解析关联目标，不扫描/拒绝其他未启用节点的任意分派配置。
- Major（已修）：当前form有Edit-only等控件并不代表有Assign。新增显式Assign集合判断；非Assign控制对无关错误分派配置不新增校验，反例通过。
- Major（已修）：直接读整个canonical store可能把其他父行数据纳入任务编排。新增 `SubTableBatchMiScope`，复用冻结binding的filter FK/ref PK与服务器已持久化父身份；非本父行不纳入，缺FK或权威身份报错。Contract包含scope，歧义不合并。两父范围共享store仅生成当前一行、缺父权威拒绝的测试通过。
- Major（已修）：任意深度后代不能等同当前MI目标。要求目标最近MI祖先就是当前subProcess；内层行不能注入外层collection。嵌套反例中outer/inner collection均无写入。
- 兼容边界（已修）：buField维持既有可选语义；声明时角色必须有BU，未声明时不发明BU；角色值不在Portal构造阶段转为个人。无BU字段正例通过。不代表后续引擎单人角色池规则已修复。
- 脚本证据修正：首次真实任务检查读取Portal15秒Mine缓存，看到已结束Review而失败。核查引擎实际存在2项AssignedWork；不改平台缓存产品代码。stage脚本现先直接断言实际引擎2项，再有界轮询要求Portal列表收敛，之后仍核对办理人、行身份、UI与拒绝写入；没有放宽数量或实际结果要求。

## 影响链与通用性

- `TaskApprovalCompletionComponent` 增强分支向builder传服务器repository baseline，不采信客户端保留字段；旧分支仍4参数。
- `MiCollectionVariableBuilder` 从bound XML当前节点读取formId，验证快照与对应目标，按当前可达MI选择严格构建或明确未启用的旧路径。`BpmnMiXmlSupport`只增加专用任务契约解析小方法。
- `SubTableBatchScopeDesign.binding`从private改包可见以复用原冻结FK/父约束，无SQL或写规则改变。新scope与collection类纯数据处理，不加外部请求、缓存或逐行DB查询。
- 由BPMN/冻结元数据驱动，不按FU/表/字段示例硬编码。包的Save/Load、Export/Import/Clone/Rollback、两条Admin导入和Deploy结构本批不变，N/A；整体真实生命周期验收仍未完成。

## 编译 / 测试 / 独立复核

- 同源隔离副本 `mvn -pl backend/user-portal -am package`，所选Batch/MiCollection/TaskProcess/Isolation/Scope/Complete/Client测试445项，失败/错误/跳过0。新冻结集合15项在其中。
- 独立reviewer比对8份关键源码/测试SHA256，重跑新15+MiCollection4+旧TaskProcess203，共222项全绿。
- Node语法及whitespace检查通过；本批未改前端产品源码，383项既有全量类型错误未关闭。

## 部署 / 真实运行

- Portal jar SHA256 `6C286B7AB5FD3286C38CCB6E6CF4425A52DAC29C97C8FBF86ECB42BF1A23D5F6`，镜像 `sha256:a73d2355cc1396ddef8e3e4d32f574bb9b3007c94753bc21c68e0670a2e7de17`；重建healthy，启动失败/无效字节码检测0。引擎沿用前批实例绑定版本。
- 首轮合成实例 `39baf0e0-c92d-11f1-8f36-4ee612046d7e`：缺目标Complete拒绝且规范数据不变；合法保存后实际生成 `5835d49e-c92d-11f1-8f36-4ee612046d7e` / `5833ffda-c92d-11f1-8f36-4ee612046d7e`。首次脚本因缓存失败如上记录，随后只读/拒绝写入复验两任务、两身份、正确目标、pageErrors0；没有重复Complete、重建或改派。
- 最终完整重跑新实例 `5b0aaa11-c92e-11f1-8f36-4ee612046d7e`，Review `5b1531af-c92e-11f1-8f36-4ee612046d7e`：`verify-batch-mi-frozen-runtime.mjs` 全链通过。先拒缺目标Complete且GET完整tables不变；工作区ASSIGN保存不增加任务；正常Save→Complete引擎确有2项AssignedWork `774512af-c92e-11f1-8f36-4ee612046d7e` / `7744766b-c92e-11f1-8f36-4ee612046d7e`，两任务各自不同rowKey、真实目标正确、UI各1行、生成后Assign关闭，伪造修改拒绝且完整canonical不变，pageErrors0。
- 已目视脱敏截图：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_frozen-mi-child-1.png`及child-2；首轮frozen-mi-recheck两张也保留。截图不代替引擎/数据断言，不删除。
- 本轮新镜像完整 `pnpm -C frontend run regression:mi`：29文件234单测及6浏览器场景通过，最终 `OK — unit + screenshots`，不是unit-only。

## 安全 / 性能 / 残余门禁

- 新分支全行校验后单次publish；无客户端任意字段名、评分别的表、相近列名或目标空值的兼容猜测。主记录父身份取repository baseline，不取提交payload中的父字段；未改选择行以外业务数据。
- 性能为本form控制数与source行数的纯遍历，不加逐行I/O。200行/大FU压力未测，不宣称性能PASS。
- 当前只实现MAIN单父键；SUB/嵌套父上下文、复合父键和嵌套MI显式拒绝，仍属后续实现门禁，不宣称完整父scope支持。子行复合PK可按冻结keys构造，但复合父关系不猜测。
- 实时人员启用/角色准入/BU资格、引擎单人角色池、执行失败可见性、首次自动推进、Preview/New Request、启动失败隔离及全生命周期仍未闭合。当前标识结构合法不等于目标真实可分派。
- 最终staged10文件完整复审无新增Blocker/未接受Major，本窄批次CONDITIONAL；494条新增非空行四类增量secret0命中，cached whitespace与两脚本语法检查通过。
