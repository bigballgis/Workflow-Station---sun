# Code Review 结果：损坏快照不可当作旧实例

## 结论

PASS，仅限投影入口的格式拒绝修复，不代表运行态快照全部安全门禁或 P1 完成。

## Findings 与处置

### Blocker

- 首轮：`projectForForm` 对未知版本、损坏根结构及当前表单 controls 返回空集合或跳过错误。
  - 反例：两个新增测试在修复前均失败；存在快照却被当成旧实例关闭能力。
  - 影响：掩盖新实例元数据故障，违反设计的显式报错约束。
  - 修复：只在保留 key 不存在时接受旧实例兼容；存在时验证格式版本、forms、当前 controls 和唯一控件标识，错误返回 409。
  - 重审：当前表单未配置批量仍返回空；无快照旧实例仍关闭；格式损坏明确拒绝。

### Major / Minor / Question

本增量无剩余 finding。摘要校验、字段保护和实时授权的完整性不在本批通过范围，仍待实施/验证。

## 范围 / 验收映射

设计快照读取规则第 2、3 项；不读取最新 DW，不回填旧实例，不修改实例变量。

## 触发矩阵

code-review、implementation-autonomy、bugfix-verification、secure-coding-sast；服务端格式边界和旧实例兼容。没有新增 UI、数据库结构或配置包格式。

## 编译与静态检查

Maven user-portal 与依赖 package：通过。指定 RuntimeSnapshot / ResponsePrivacy / SnapshotMerge / HydratorOwnership / CompletionGuard 五类测试通过。staged 完整 diff 与 check 通过。

## 测试与运行证据

- RuntimeSnapshot 20 个测试，0 失败；新增两项测试有修复前失败证据，覆盖非 Map、未知版本、forms 类型错误和 controls 错误。
- 新产物已重建 Portal Docker 服务并健康。
- 新的隔离批量实例启动及真实 Add/Delete 保存/重开成功，未影响合法投影。
- 数据库故意破坏快照的 HTTP 故障注入未执行：未通过修改已存快照制造验收数据；坏格式拒绝由上述真实服务端单测覆盖。

## Function Unit 通用性与生命周期

格式判据只使用平台契约，与 FU、表或业务字段名无关。格式与实例存储未改变，Export/Import、Clone、Rollback、Admin 导入本批 N/A；完整功能仍需运行往返验收。

## 增量敏感信息

新增/修改行及新增文档扫描：私钥、凭证赋值及邮箱候选 0。错误信息不暴露快照正文或跨节点配置。

## 性能

线性遍历当前表单控件，无额外查询、全实例深复制或持久化。

## 残余风险 / 范围外问题

完整快照的规范摘要、冻结字段/绑定约束及有效操作投影尚未全部闭合；此提交不是对整个既有快照实现的 PASS。完整前端类型门禁与严格 MI 运行门禁也仍待闭合，未用本批测试替代。
