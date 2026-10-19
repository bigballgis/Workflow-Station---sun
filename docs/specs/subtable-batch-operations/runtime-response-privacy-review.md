# Code Review 结果：运行态快照响应裁剪

## 结论

PASS，仅限本批 DTO 序列化边界修复，不代表 P1–P4 完成或功能可发布。

## Findings 与处置

### Blocker

- 首轮：TaskInfo / ProcessInstanceInfo 普通变量响应暴露整个实例的批量快照。
  - 反例：隔离新实例的任务详情、流程详情实际 HTTP body 均包含保留 key。
  - 影响：浏览器获得不属于当前表单阶段的批量设计。
  - 修复：七类响应 DTO 的八个变量/表单数据属性使用 Jackson Map 条目裁剪；保留 key 共用常量。
  - 重审：测试及真实 HTTP body 均不含快照；当前表单能力投影仍存在，数据库快照仍保留。

### Major / Minor / Question

本批无剩余 finding。其他批次的 MI / Preview / Assign / 生命周期门禁不得由本批结论替代。

## 范围 / 验收映射

设计运行态快照规则第 8 项：普通表单/变量响应不返回完整快照；仅返回当前表单投影。未改变捕获、授权、实例变量写入或业务数据。

## 触发矩阵

code-review、secure-coding-sast、bugfix-verification；后端 DTO、隐私和兼容边界。未修改 UI、MI 切片算法、配置包或数据库结构。

## 编译与静态检查

- Maven user-portal 连同依赖 package，指定 ResponsePrivacy / SnapshotMerge / HydratorOwnership / RuntimeSnapshot / CompletionGuard 五类测试：通过。
- staged 完整 diff 及 `git diff --cached --check`：通过。
- 无新增依赖、异常兜底或安全检查抑制。

## 测试与运行证据

- `SubTableBatchResponsePrivacyTest`：修复前失败，修复后通过；覆盖七类 DTO 并验证业务值保留、序列化不修改原变量 Map。
- Portal 后端已重新 package、Docker build、重建并健康。
- 相同隔离实例的任务/流程详情：修复前均暴露，修复后均不暴露。
- 相同实例的任务 form-data：不暴露完整快照，仍有一个当前表单能力投影。
- 只读数据库查询：原实例仍有服务器快照。

## Function Unit 通用性与生命周期

通用性：平台保留 key，与业务表名、字段名、FU 身份无关。Save/Load、Export/Import、Clone、Rollback、Admin 两条 Import 格式均未改变，本批 N/A；完整功能的生命周期运行验证仍未完成。

## 增量敏感信息

扫描 staged 新增/修改行及新增文件：私钥、凭证赋值及邮箱候选 0。未记录真实身份、凭证或快照正文。

## 性能

使用既有 Jackson Map 条目忽略机制，无额外数据库请求、深克隆或变量持久化。

## 残余风险 / 范围外问题

当前表单能力投影的实时授权及损坏快照拒绝门禁仍需另批闭合。完整 MI 页面回归仍失败，前端全量 typecheck 仍失败；它们不属于本 DTO-only 修复的放行范围，也未被宣称通过。
