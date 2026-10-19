# Code Review 结果：部署配置驱动的 MI 循环变量

## 结论

CONDITIONAL。本批修复真实 Complete 暴露的循环变量读取问题，不代表 D7/P4 整体验收完成。严格冻结分派字段、目标资格、单人角色池、生成失败结果 UI、Preview/New Request、启动失败隔离及完整生命周期仍待闭合。

## Findings / 逐条处置

### Major（本批已修）

- `MultiInstanceTaskWriter` 的 ELEMENT_VARIABLE 与预分派元数据路径固定读 `currentItem/_currentItem`。实际 BPMN 配置 `item` 时创建了两个无主子任务；若旧变量恰好非空，还会错分派到另一记录。新增 listener 两条反例在原实现下稳定 9项2失败0错误，明确实际使用 `u-wrong-legacy` 而非配置目标。
- 新增 `MultiInstanceTaskItem`，按实际 processDefinitionId/node 查部署模型、最近自身/祖先 MI loop；显式 loop 只读其 elementVariable 的 execution-scoped VariableInstance，拒流程根、异实例、缺失变量和非行值。没有显式 loop 的旧 ELEMENT_VARIABLE 定义保留已标注迁移路径；未知部署模型不按旧变量猜测。`TaskWriteContextResolver` 只提取共享 loop 遍历，原严格身份校验未放宽。
- 缺配置异常交由 listener 既有 failure recorder，写 task-local failure；预分派元数据异常也接同一留痕。没有宣称所有旧 writer 早退/角色失败均已可见，A38 仍开放。
- 后续读取链同样固定读 `currentItem`。`TaskInfoAssemblerParticipantTest` 在原实现先失败：响应沿用流程级 foreign row；修复为使用同一部署解析，复制响应 Map 后清理旧键，只注入当前执行行。缺配置时抛错，不吞掉后返回全案上下文。原 runtime Map 不被修改，低变量列表入口不增加解析。

### Review 建议及处置

- 独立 reviewer 建议锁住第二接入点：已补预分派任务 metadata 使用配置 rowKey、保留原 assignee 且不调用 setAssignee 的测试。
- 建议锁住读取失败边界：已补 assembler 缺配置明确抛错的测试。
- 初轮真实脚本将 form-data canonical 数组长度误作切片判据，记录为失败，不记 PASS。已核查既有接口返回规范行集及 `_currentItem`；改为核对配置主键对应的任务身份、页面实际切片和失败写后完整 canonical 不变。未以客户端身份作为授权来源。

### Blocker / Minor / Question

- 独立 reviewer 对 staged 10文件完整重审无新增 Blocker/未接受 Major，允许本窄工程批次 CONDITIONAL 提交。其结论不能扩为整体发布 PASS。

## 范围 / 验收映射 / 触发矩阵

- D6/D7、A16/A30/A37：正常 Complete 时配置变量生效，实际办理人和不同行身份一致；生成后不能借普通无标记 Save 改分派。保留旧样本，不重派、不取消、不修复既有任务。
- implementation-autonomy、code-review、bugfix-verification、config-over-heuristics、secure-coding-sast。无新 endpoint、Entity、共享 jar、环境变量或业务物理表；不调整任务分派算法。

## 编译与静态检查

- 原实现红测试：listener 9项2失败0错误；assembler 1项1失败0错误。另有 IDE 生成字节码 VerifyError，不能当业务红测试。
- 最终同源隔离构建：`mvn -pl backend/workflow-engine-core -am package "-Dtest=TaskAssignmentListener*Test,TaskWriteContextResolverTest,MultiInstanceTaskItemTest,TaskInfoAssembler*Test,SubTableAssignmentHandlerTest,TaskClaimSupportReassignTest" "-Dsurefire.failIfNoSpecifiedTests=false" -q`，79项0失败/错误/跳过，jar生成成功。
- IDE 持续覆写工作区 target，曾出现测试绿但镜像 TaskActionService 构造失败，明确不计运行通过。改用临时目录 `C:\Users\LILONG~1\AppData\Local\Temp\hermes-batch-engine-837f62d98a67446da73f347622d79d5e` 的同一 pom/src 编译，独立 reviewer 核对源码哈希一致。
- 验证 jar 与复制回目标 jar 的 SHA256 均为 `E355C7AC57E1EDFB17801C92A28EA7D35867AEFB998316959D3F6BB5AC6BAD9C`，没有改编译级别、关闭校验或覆盖产品配置。
- 独立 review 第一轮21项通过；新增读取链最终在隔离目录独立13项通过。污染目录中的一次 VerifyError 如实记失败，不计通过。
- Node验证脚本 syntax 与 diff whitespace 检查通过。没有改前端产品源码；全量前端383项既有类型错误未关闭，不报告 typecheck PASS。

## 部署 / 运行证据

- 重建 workflow-engine（--no-deps），最终镜像 `sha256:70e931de4fc1a3000dc455feaf32b98172121c0144f842309fe5cb101a0e2d24`，healthy，日志确认正常启动。数据库/Redis原停止，按原 Compose 启动恢复，未删卷或业务数据。
- 原失败流程 `6b77ff15-c89e-11f1-a63d-c6848797bef7` 仍2个未分派任务，未自动修补。
- 新合成实例 `e13ec1a2-c902-11f1-a615-4efaf416e537`：工作区 EDIT+ASSIGN 保存通过、无新增任务、pageErrors0；后续正常 Save/Complete 实际生成2个 AssignedWork，SQL确认2个 assignee 非空、2份不同 rowKey 元数据。任务详情/表单 `_currentItem` 各自对应配置主键，不按未来办理人拆分相同用户的两行。
- Assign-only FU 的全新实例 `8f1c7e89-c903-11f1-a615-4efaf416e537` / 宿主 `8f2ad5b7-c903-11f1-a615-4efaf416e537`：独立工作区只提交 ASSIGN，随后脚本正常 Save→Complete→实际2个子任务→2份不同当前身份→页面各1行→两个无标记修改均明确 already started 拒绝→GET完整canonical不变，pageErrors0。SQL独立确认2任务都有assignee、2不同rowKey；混合工作区实例也经生成后复查通过。没有调用 Assign/Reassign 或修补既有任务。
- 最终完整 `pnpm -C frontend run regression:mi`：29文件234单测 + 6浏览器场景 PASS，输出 `OK — unit + screenshots`。旧独立克隆 FU 的参与者/People/附件回归正常，不以unit-only替代。
- 子任务截图已目视核验单行和姓名列遮盖，保留于 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_batch-assigned-child-1.png` / `2026-10-16_batch-assigned-child-2.png`；既有MI本轮截图同目录日期2026-10-16，不删除。

## Function Unit 通用性与生命周期

- 新合成 FU 使用非 currentItem 变量及自定义字段；旧独立克隆 FU 使用原配置。解析只依赖部署模型和 execution，不写死 FU/表/字段/流程名。
- 未改设计态格式和导入重写，Save/Export/Import/Clone/Rollback/Admin两条导入的本增量处理 N/A；整体 A23/A24 真实 round-trip 仍开放，不借本次免除。

## 增量敏感信息 / 性能 / 残余风险

- 本次 staged 471新增行、四类增量secret扫描0命中，独立复审同样通过；脚本不输出人员目录值。截图截子表并遮盖选择控件及办理人显示列。
- 部署模型读取复用 Flowable 本地 repository 缓存，无外部 HTTP、授权 TTL 或逐行目录查询新增；压力测试未执行，不声称性能提升。
- 现有接口仍返回 canonical + execution 身份，并非服务器行裁剪改造；不将本批扩为全平台响应契约重构。
- 严格冻结字段映射、Complete无差异预校验、实时目标资格、角色池和失败结果核对仍是原设计发布门禁。不能开启生产配置或宣布 P2/P3/P4 完成。
- 新生成的 Assign-only 子任务仍显示 Batch Edit 入口，虽服务端已经拒绝阶段越界写入，UI有效能力投影尚未收紧；这是紧接着的原 P2/A18 实现项，不能当作允许已有任务改派。本批不改动原单行入口。
