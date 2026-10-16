# Code Review：增强实例 Complete 绑定实际部署 BPMN

## 结论

CONDITIONAL。本批解决增强实例普通 Complete 的 MI collection 读取最新版 BPMN 的隔离缺口；不代表冻结字段映射、目标资格、启动失败隔离或 P2/P4 已完成。未启用实例继续原 key 查询。没有运行中任务改派、取消、迁移或旧数据回填。

## Findings 与逐条处置

- Major（已修）：`ProcessController` 新增实例参数分支继承 `/definitions/**` 的匿名 permitAll，可泄露实例与历史部署关联。`SecurityConfig` 在宽规则前仅对带实例参数的 BPMN 路径要求认证；真实 FilterChainProxy/JWT 测试先 RED（期望403实际200），后通过。第二轮发现 HEAD 同样执行 GetMapping，移除 matcher 的 GET 限制并增加 HEAD、空参数负例，HEAD 反例先 RED 后绿。旧匿名无参数查询仍200。
- Major（已修）：旧引擎可能忽略 query 后返回最新 XML。Portal 严格分支要求响应含匹配 instance/key 和非空部署 definition ID，缺少来源证明、空 XML、HTTP错误或不可用均拒绝，不重试无实例查询。单元测试覆盖混部。
- Minor（已修）：运行脚本 finally 恢复不能只看部署HTTP成功。现在重部署原XML后GET latest精确比较，成功后才输出 restored。
- 扩大测试暴露旧 `TaskProcessProperties` Complete 夹具没有本地实例；仅补无快照实例夹具，保留原两行分派断言，新增旧1参数/never实例2参数调用断言，没有放宽产品逻辑。
- 完整增量静态复审未发现新增 Blocker/未接受 Major。独立 reviewer 实跑关键6组17项并核对7份源码哈希一致。

## 范围 / 影响链 / 兼容

- Engine 新 `InstanceBpmnReader`：active processInstance→实际 processDefinitionId→deployment/resource；核对实例、定义、key，关闭资源流，不查 latest。既有 `/definitions/{key}/bpmn` 只增可选参数与来源字段。
- Portal facade/client→`MiCollectionVariableBuilder`→`TaskApprovalCompletionComponent`：有增强上下文走实例绑定查询；旧4参数入口保留。BFS拆分保留原callActivity/look-ahead顺序，未改旧选行/分派算法。原超长文件只接线或拆出等价短方法。
- XML沿用安全解析器；URL沿用 SafeUrlInput；没有依赖、schema、环境变量、共享jar、路由或UI产品源码变化。部署顺序Engine→Portal；若回退Engine，严格客户端拒绝旧响应，不静默读最新。
- FU Save/Load、Export/Import、Clone、Rollback、Admin两导入的包格式本批不变，N/A；整体生命周期验收仍开放。运行来源按实际实例，不硬编码业务FU/表/字段。

## 编译与测试

- 隔离源副本构建，规避IDE ECJ覆写仓库target。`mvn -pl backend/workflow-engine-core,backend/user-portal -am package`，所选 InstanceBpmn/Client/MiCollection/TaskProcess/Deployment/Assignment/TaskInfo/Batch/Isolation/Scope/Complete 测试：530项，失败/错误/跳过均0；原 TaskProcessProperties 203项包含其中。
- 独立 reviewer 再跑 Reader4、HTTP1、安全链1、Client7、Builder3、原callActivity1，共17项全绿。
- Node语法及 whitespace 检查通过。未更改前端产品源码，不重新宣称既有383项类型错误已修复。

## 部署与真实运行

- Engine jar SHA256 `3DA16281BB37EB5A03B411FA4B3B67B186FBE7B980CBBD855FD1A58C41D54C27`，镜像 `sha256:6b38be811725133793be66efba53033f0313ab2eb1076ac4685f7323f105418a`。
- Portal jar SHA256 `6FD95FFCB1CAB2AFD2B8624CFA6D4A598E894997113B810CA80EE7D576645348`，镜像 `sha256:f0e29f45c4b19c06d7cc03633b035a2c41c366214c2f350b85f7aef1b0afd6c8`。两者重建后healthy，启动失败/无效字节码检测0。
- 合成FU50036，新实例 `5e70f72d-c90f-11f1-8f36-4ee612046d7e`，Review `5e98559b-c90f-11f1-8f36-4ee612046d7e`。工作区真实ASSIGN保存且任务数量不变。
- `verify-batch-instance-bpmn-runtime.mjs`：匿名GET/HEAD403、旧匿名latest200；将该合成key的最新collection改为不同变量，bound仍返回创建时definition与原XML，latest确实不同。
- 在最新定义漂移期间，原Save→Complete生成2项AssignedWork `de97ab81-c90f-11f1-8f36-4ee612046d7e` / `de9365bd-c90f-11f1-8f36-4ee612046d7e`；实际办理人正确、两份不同rowKey、UI各1行、初次分派关闭、伪造修改拒绝且canonical不变，pageErrors0。没有修补/改派/取消任务。finally以新deployment恢复原XML并GET核对一致。
- 脱敏截图已目视：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_instance-bpmn-child-1.png`，同目录child-2；姓名遮盖，旧三个按钮保留，不删除截图。
- 新镜像完整 `pnpm -C frontend run regression:mi`：29文件234单测及6浏览器场景通过，最终 `OK — unit + screenshots`，未用unit-only替代。

## 性能 / 安全 / 残余门禁

- 每次增强Complete增加按实例读取部署定义，不逐行查询；200行/长历史压力未验证，不宣称性能PASS。响应只含当前定义来源，不返回实例变量/完整快照。脚本凭正常httpOnly Cookie鉴权，不提取、持久化或打印token。
- 冻结 `assigneeField/roleField/buField`、canonical store/PK 全链严格消费仍待实现；当前旧 collection 选行/候选列逻辑并未由本批修复。`ProcessStartComponent` 首任务自动推进仍旧入口，不宣称所有来源已绑定。
- 目标资格、单人角色池、执行分派失败可见性、Preview/New Request、启动失败隔离、全生命周期及多主体压力仍开放；总体不可宣称P2/P3/P4完成。
- 最终staged15文件完整复审无未闭合Blocker/未接受Major；555条新增非空diff行四类增量secret扫描0命中，脚本语法与cached whitespace检查通过。独立复审结论仅为本窄批次CONDITIONAL。
