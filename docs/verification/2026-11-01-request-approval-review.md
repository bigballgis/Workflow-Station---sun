# Request Approval 追修 Code Review

## 结论

本次相对 ef0dd8d1a 的增量审查 PASS：无 Blocker / Major / Minor；用户已接受既有失败基线，“没有新增问题即可提交”。仓库全量门禁仍 CONDITIONAL，不能把本结论扩展为全仓全绿。审查由当前代理只读复核，未使用第二 reviewer。

## Findings 与处置

- Blocker：无。
- Major：无。本轮原缺陷已在开发阶段修复，原失败页面与两份修复后新建实例提供红→绿证据；没有靠忽略异常或撤掉 guard 处理。
- Minor：无。
- Question：Mark Complete 在冻结设计中配置为 REJECT，终态按此记录；未按按钮名称改平台语义。业务动作设计调整不在本修复范围。

## 范围 / 验收映射

MiSubTaskSubTableRowMerger 无 scope 的完全相同 store 保持原顺序，避免恢复的只读表被合成一次写入。keyScopes 非空分支先执行，显式清空不会进入新 no-op 条件；真实变化仍经原 MI 合并和 versioned scope guard。新增测试覆盖未变、省略、恶意实际修改三种边界。产品代码仅一个分支判据变化，未引入 FU/表/字段硬编码。

## 触发矩阵

后端 bugfix、MI 数据隔离、配置驱动身份、完整流程浏览器验证、增量敏感信息和最少代码检查。无 DTO/schema/配置格式/导入导出契约变化，无前端代码变化。

## 编译与静态检查

- backend/user-portal 及依赖 Maven package：BUILD SUCCESS，633 项、0 失败/错误、20 条件跳过。
- review 阶段重新运行 SubTableUnchangedMiStoreTest：3 项通过、0 失败/错误，BUILD SUCCESS。
- git diff --cached --check：通过。完整 staged diff / status / 未跟踪文件已检查，必要测试及文档全部纳入。

## 测试与运行证据

- 修复前新增测试 2 失败/3；修复后全 3 通过；真实 Request Approval 500 → 同一实例 200。
- 新部署后的普通双绑定实例 2a037e33-d5a8-11f1-a759-6acd226d917b：新建/草稿重开/编辑删除/提交/分派/Request Approval/直接 Mark Complete/Approve/两个最终节点/实例结束，全链浏览器办理。
- 显式额外 FK 实例 7697390a-d5a8-11f1-a759-6acd226d917b：同链到结束。两份实例均无活动任务、有引擎结束时间；每步 Correspondence 行值/顺序/版本均未意外变化，pageerror=0。
- 完整 MI：250 单测、6 浏览器场景通过；使用已有 FU 50029，非仅新建样本。
- 各步及结束页截图路径见同目录 2026-11-01-subtable-binding-and-completion.md。

## Function Unit 通用性与生命周期

- 通用性：新建普通/显式额外 FK 克隆样本与既有 MI FU 已运行；测试使用重命名字段。运行时代码没有业务名称判据。
- Save/Load、Clone 后发布、Portal runtime：本轮实际执行。
- Export/Import、Version/Rollback、Admin 两条 Import：本轮未重跑；没有变更其消费的数据模型或持久化配置，不能宣称全生命周期验收。

## 增量敏感信息

新增行检查无凭证、Cookie、JWT、私钥候选。未跟踪会话/临时脚本/运行日志/截图均未 stage。只有代码、测试、验收/审查记录与同源 issue 续记。

## 性能

没有新增 DB/HTTP 或逐行远程请求。新 equality 检查线性比较原 payload；后续原合并本来也线性遍历行。未作性能提升声明，MI 热路径完整门禁通过。

## 残余风险 / 范围外

全量前端测试 1 项、TypeScript 381 诊断、PostgreSQL opt-in IT 2 项为此前已对照基线；本轮无前端修改，不重复声明这些门禁全绿。未穷举全部业务分支/远程部署/双账号，权限及行版本校验保留。部署回退可恢复 ef0dd8d1a Portal jar 并重建服务，无 schema 迁移，但原 Request Approval 问题会重新出现。
