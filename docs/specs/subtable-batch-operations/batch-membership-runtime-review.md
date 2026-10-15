# Code Review：P3 新增/删除真实运行补证

## 结论

CONDITIONAL。本批仅增强验收脚本、记录新增/删除运行证据，没有产品代码变化；不宣称 P3/P4 全部通过。

## 范围 / Findings

新增验收缺口：原脚本只证明录入前不保存，没有验证“新增草稿后 Cancel 不分配主键”。现在真实点击 Add→Cancel→确认丢弃，监控任务 submit/primary-keys allocate 请求均为零，并读回完整原行相等，重新打开只有原两行。任务必须显式 UUID 且任务 FU 名属于合成 fixture；脚本不自动选择真实业务任务。未增加未使用的自动推进分支。无新增产品缺陷或业务方向变更。

## 真实运行

当前部署为前两批产品补丁（只读提交投影、Complete 冻结 binding），此前 build/package 和完整 MI234+6通过。本批不修改可部署产物，无重复重建必要。

- 新建合成 FU50030，UUID 策略，字段为 entry_token/owner_ref/item_label/units；过程 `6ad11bd4-c86f-11f1-a63d-c6848797bef7`，任务 `6adba36b-c86f-11f1-a63d-c6848797bef7`。
- 新建合成 FU50031，人工 PK/只读数值默认0，字段为 line_key/case_ref/caption/amount；过程 `82c35425-c86f-11f1-a63d-c6848797bef7`，任务 `82cddabc-c86f-11f1-a63d-c6848797bef7`。
- 两次 `FIXTURE_TASK_ID=... node frontend/scripts/verify-batch-membership-runtime.mjs`：PASS，浏览器 pageerror/console.error 均零。真实取消零分配/零保存；新增草稿未持久化；Apply/自动保存后3行及PK/FK/版本正确；reload仍3行；重复Add409且伴随的第一行改动未保存；伪造控件403且行未变；人工样本无batch标记的只读修改403，默认0保留；本地Delete不持久化，Apply后明确删除到空且reload仍空。
- 两次 `FIXTURE_TASK_ID=... node frontend/scripts/complete-batch-verification-fixture.mjs`：PASS。真实 Complete 后过程状态 COMPLETED、canonical仍空、未暴露快照/删除保留元数据。不是只看HTTP或成功toast。
- 同批重跑 `pwsh -NoProfile -File scripts/verify-batch-postgres.ps1`：PostgreSQL/JPA 7项 + H2保存链12项，共19项0失败/错误/跳过、BUILD SUCCESS；仅创建并清理专用临时容器，不触及平台卷。
- 四张目视检查截图绝对目录：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`，文件 `2026-10-15_batch-uuid-add-persisted.png`、`2026-10-15_batch-manual-readonly-add-persisted.png`、`2026-10-15_batch-uuid-delete-empty-persisted.png`、`2026-10-15_batch-manual-readonly-narrow.png`。其余 initial/delete/narrow 图也保留，不删除；裁剪弹窗仅合成数据，无姓名/邮箱。窄屏表格内部横向滚动，footer可见；不将空表截图当作满表响应式验收。

## 安全 / 通用性 / 生命周期 / 未验证

仅合成 FU，新建两种字段/PK形态；不写用户业务数据，不删除测试截图、平台卷或旧实例。脚本读回行数据验证业务结果，错误只输出字段位置/断言，不输出凭证。无包或设计态格式变化，Export/Import/Clone/Rollback/Admin消费者本批 N/A；本批两个都是新建fixture，不冒充导入往返验收。旧实例与导入/Clone样本的成对门禁沿前批记录，完整生命周期和两操作者仍未完成。

Cancel 请求监控仅覆盖分配/保存接口，数据回读覆盖本地实例业务写入；不宣称证明了所有外部副作用。序列号策略、跨父行、Add-only/Delete-only、真实目录分派、Preview、New Request及完整P4仍需补齐。已有类型检查383错误未解决，未降低严格度。变更为脚本/文档，无性能结论。最终diff/脚本语法/敏感增量在staged复审中核验。
