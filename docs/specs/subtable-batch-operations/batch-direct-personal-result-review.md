# 直接初始 MI 复用逐行个人结果核对

## 结论 / 范围

CONDITIONAL。本批在 `e23b543b9` 已审核对器基础上，把直接 Start→MI 的严格初始计划也交给同一观察器。原先只有轻量任务列表而一直 UNCONFIRMED 的入口，现在具备相同的逐行个人核对；角色/混合模式仍不冒称 VERIFIED。不是 P4 全部放行，不改 FU50007 或其业务实例。

`ProcessStartComponent` 仅在原 TX1/TX2 结束后，将已成功预校验的初始集合包装成同一个服务器 Plan，使用本次 pin 的真实 definitionId；没有初始集合时继续用原首人工 Complete 计划。没有任何新能力的旧流程仍 null，零新增观察查询。删除不再使用的轻量 verify、Supplier/DTO imports，不保留两个判据。

## Findings 与处置

- Blocker / Major：独立首轮静态审查无新增。
- Minor 验证建议（已处置）：错误目标脚本仅 `issues.every(reason)` 可能空集为真。两个验证脚本均新增 issues 的 taskId 集合与实际任务集合精确相等断言，确保数量及定位完整；固定 synthetic 故障目标和 reasonCode 仍逐项核实。
- 测试迁移：旧轻量方法的 8 项测试替换为 8 项真实计划构造测试；旧隔离、分页、角色和异常契约由共享观察器覆盖，另补空白办理人及 live 查询异常用例，不通过删负例降低门禁。

## 修复验证

- 真实红：已部署旧版同一 UI 验证脚本请求 VERIFIED，实际 UNCONFIRMED；实例 `fa4d7d4c-ca07-11f1-b00d-96c2314bbe84` 保留，截图 `2026-10-17_direct-personal-before.png`。并未拿未知接口或虚构 rowKey 的 mock 代替真实接口。
- 修后相同脚本/正常既有 FU50047 新实例 `a41f267a-ca08-11f1-b00d-96c2314bbe84`，两条真实任务、实际目标正确、VERIFIED/空 issues，原正常成功提示，pageerror=0。
- 新合成 FU50053 `batch_verify_directwrong1017`，仅新合成设计显式故障注入。实例 `aea11f9c-ca08-11f1-b00d-96c2314bbe84`，实际两条非空错误目标，响应 FAILED 两条 PERSON_TASK_TARGET_MISMATCH，issues taskId 精确等于两条 live task，页面警告，pageerror=0。
- 既有缺主合成 FU50048 新实例 `b93456ce-ca08-11f1-b00d-96c2314bbe84`，真实两条缺主任务，FAILED 两条 PERSON_TASK_UNASSIGNED，页面警告，pageerror=0。
- 固定以上三个 pid 的 PostgreSQL 只读事务确认各恰好两条 AssignedWork；正常样本无空/故障目标，错误样本恰好两条固定故障目标，缺主样本恰好两条空目标。未操作已有任务的领取、改派、补偿或取消，未修改目录。
- `mvn -pl backend/user-portal -am package "-Dtest=SubTableBatch*Test,TaskCompletionObservationTest,ProcessStartBatch*Test" "-Dsurefire.failIfNoSpecifiedTests=false"`：227 项通过，0 失败/错误/跳过，BUILD SUCCESS，25.51 秒。仍在隔离源副本构建，从真实仓库同步本批 4 份 Java 源。
- jar SHA256 `7B19C6FFF3EB2532479D024CE4474F3482C840DD06BC356E302C9CFEA8FA64D3`。只以 `--no-deps` 重建 Portal 后端，健康镜像 `sha256:83e87cbd34cdfe39ed04d290fb5a3947838ca1953f7eabaab8aa986586a8abf2`；前端未改产品代码，继续使用上一批已部署产物。
- 完整 MI、独立最终 staged 重审与增量 secret 扫描等待真实结果后补记，不能提前填 PASS。

## 截图

已目视修前未确认、修后正常及窄屏错误告警，无 PII/遮挡，全部保留在：

`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`

- `2026-10-17_direct-personal-before.png`
- `2026-10-17_direct-personal-verified.png` 及 `-narrow.png`
- `2026-10-17_direct-personal-wrong.png` 及 `-narrow.png`
- `2026-10-17_direct-personal-missing.png` 及 `-narrow.png`

## 触发矩阵 / 通用性 / 生命周期

命中实现自主、代码审查、配置驱动、修复验证、安全/隐私、MI 与性能规则。冻结源预校验和部署 definition pin 不变；观察结果取 live pid/definition/node/table/完整行键，不猜业务列名。新合成错误目标 FU 与已有正常/缺主 FU 都通过。无 schema/配置格式/共享 jar/部署配置变更；Save/Load、Export/Import、Clone、Rollback、Admin 两条导入及设计产物生命周期无新增消费者，N/A，不取代这些路径的全功能验收。

直接初始分支由一条轻量查询改为两条既有 bulk 查询，任务/身份上限与完整性检查保持共享判据，不引入逐行 HTTP/SQL。其他入口不增查询；尚未压测，不宣称性能提升或 200 行全结果已核实。

## 未验证项 / 范围外

本批闭合直接初始个人入口，不闭合角色候选池的精确完整结果、混合模式全行结果、刷新后的持续 GET 展示、预校验至创建间资格竞态及所有 A01–A41。没有给生产 FU 开启能力，没有迁移旧实例。Portal 既有全类型 383 项问题不夹带修复；本批没有前端产品类型变化，不能报告全仓类型 PASS。

## 最终签核

最终镜像完整 `pnpm -C frontend run regression:mi` 234 项单测、6 个真实浏览器场景全部通过，不是 unit-only。独立最终 staged 7 文件完整重审无新增 Blocker/未接受 Major；隔离源 SHA 同源，独立 Maven 8+20=28 项测试 0 失败/错误/跳过。cached diffcheck、两个脚本语法检查通过；105 条非空新增行四类敏感检查 0 命中。独立目视修前、修后及窄屏错误截图，无 PII/遮挡。仅追加实际门禁，产品代码未再改；允许本窄批提交，整体仍 CONDITIONAL。
