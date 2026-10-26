# 新完成任务 Change History 只读复核

对象：任务 `253104a7-d10f-11f1-b750-c640d8456ecf`，流程实例 `902cac9c-d10e-11f1-b750-c640d8456ecf`，快照截止本地时间 2026-10-26 15:30:45.688。

## 验证结果

- 数据库和真实 API 均为48条明细，页面显示48/48、7个分组；48条全部具有 batch_id/batch_time，无记录晚于快照截止时间。
- 40条初始变更（1条主表、39条子表新增）共享一个批次，3条子表更新共享另一个批次；5次Claim各自独立。每个批次内用户和批次时间一致。
- 页面分组时间：15:26:31、15:27:52、15:28:36、15:29:38、15:29:52、15:30:30、15:30:43。
- 正序/倒序切换后时间列表互为逆序；搜索实际字段返回1/48，搜索无关字符串显示无匹配。
- 清空搜索恢复48/48；排序、搜索及真实API刷新后，原先收起的组仍保持收起。
- 浏览器pageerror为0，Change History及其敏感值配置请求无HTTP错误。
- 仅进行GET请求、查库和浏览器展示操作，没有修改业务数据或产品代码。

截图/机器证据位于 `frontend/user-portal/verification-screenshots/`：

- `2026-10-26_change-history-new-task-expanded.png`
- `2026-10-26_change-history-new-task-collapsed.png`
- `2026-10-26_change-history-new-task-evidence.json`

## 单独发现的日志告警

流程引擎对该已完成实例的查询返回 `PROCESS_QUERY_ERROR: Failed to query process instances: null`，并有 `INSTANCE_BPMN_UNAVAILABLE: Instance deployed BPMN cannot be verified`。Portal的Change History及敏感值配置接口仍返回200，页面相关操作通过。此处仅记录另一条调用链的告警，未据此宣称整个任务详情或流程图链路没有问题，也未在只读复核中修改其实现。

本次未通过写操作重新验证保存幂等或制造快照后的新增历史；快照检查针对现有48条数据完成，前两项仍依赖此前专项测试证据。
