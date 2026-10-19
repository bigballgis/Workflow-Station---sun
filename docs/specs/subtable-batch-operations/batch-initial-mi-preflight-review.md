# Code Review：开始节点直接进入 MI 的批量初次分派预检

## 结论 / 范围

CONDITIONAL。本批只覆盖当前 Start 绑定表单显式开启 Batch Assign、开始节点直接连接 MI 子流程的初次分派。不是全实例分派授权重构，也不宣称网关/服务任务/调用活动后的间接初始入口已闭合。旧请求及当前 source 未开启的新能力不变。

## 复现、Finding 与逐条处置

- Major（已修）：合成 FU50047 `batch_verify_initialmi1017`，PROCESS50747/binding51062、TASK50748/binding51064，Start直接连接AssignedRows、没有Intake或Review。DW validate0错误/警告并Deploy。旧镜像上无效canonical目标加单条伪造collection仍返回success，错误实例 `337aebd0-c97b-11f1-b5f7-36c1412e9ef7` 保留；未自动取消、补偿或改派。
- 根因：既有严格分派检查发生在人工任务 Complete 前，而该拓扑在 Flowable.start 内就已创建子任务。新增独立纯编排 helper，在 engine start 前复用冻结 source 的集合构造、PK/FK父scope、个人/角色资格校验；成功后才整体发布准备好的集合。无逐行请求、不创建任务、不传完整快照到引擎。
- ProcessStart仅一行接线，放在 engine调用的try外：无效配置/目标不触发原“启动失败清部署缓存”逻辑，避免每次录入错误都迫使重新部署。实际部署仍由上一批真实ID pin固定，预检的XML与本次确定的发布定义同源。
- 独立首审 Major（已修）：prototype对增强但无Assign、Start缺formId的directMI也尝试投影，会误拦旧预置集合。最终与Complete契约一致：只用当前Start实际formId的controls；当前未开启或没有该source的能力时不改变旧集合、无目录调用。其他表单的Assign既不能授权当前Start，也不能反过来封锁其原单行路径。
- 独立首审 Major（已修）：Assign与非Assign直接MI若共用集合名会覆盖后者原集合。所有直接目标先统计集合名，任何实际Assign集合重叠都明确拒绝；不同集合的legacy准备数据保持原对象。
- 多Start事件不能猜本次实际起点。只有需要执行source新增能力时才明确拒绝歧义；不选第一个，不把多个Start表单权限OR合并。网关等间接路径不由该窄helper遍历。
- prototype跨表单仅任务ID检测会误将不同表的同名节点视为保护目标。最终移除整个跨表单推断，依当前source精确投影；其自己开启Assign时仍校验完整冻结目标与当前BPMN，漂移/坏配置不回落。跨表单原集合保留、零目录调用的反例已覆盖。

## 红→绿与构建

- 实际运行脚本 `verify-batch-initial-mi-runtime.mjs` 在旧镜像先FAIL（无效初始目标仍success），新镜像同脚本PASS，不用单测代替真实启动结果。
- 临时关闭helper：24项中2失败0错误；关闭审查边界保护：30项中2失败1错误；prototype跨表单误拦反例32项中1错误。最终删掉跨表单推断后32项全部通过，不保留临时禁用代码。
- 最终隔离副本Portal package：42类511项0失败/错误/跳过，日志 batch-initial-mi-package-source-bound.log。独立reviewer同源SHA核验并重跑集合32+Start pin3+首Task3，共38项全部通过。新helper约80行，最长方法约40行；原大文件只有一行接线。
- Portal jar SHA256 `8900765D49AC3FCABA81372115BF54D4EC36673329C0C0C1C1193DF0DD0238D4`，重建镜像 `sha256:952d5599c28c49bcc21489d13dc4019f2176b5d595b5ab385103de18c6eb4855`，healthy后真实测试。Engine仍为6282ee1b，前端仍为07be7a65，未用旧服务冒充新产物。

## 真实新镜像证据

- 无效目标请求使用唯一合成businessKey，固定只读psql查询ACT_HI_PROCINST，拒绝前后均0；不只依赖HTTP状态码。日志/断言不输出人员标识。
- 合法请求保留两行canonical、同时提交一条伪造collection，新实例 `ef648ead-c9c9-11f1-b5f7-36c1412e9ef7` 实际生成 `ef6662b4-c9c9-11f1-b5f7-36c1412e9ef7` / `ef64dc0f-c9c9-11f1-b5f7-36c1412e9ef7`。两份冻结行身份正确、actual assignee为已验证目录目标、无候选池、无本地快照进入Flowable。
- 先前新增UUID主键样本FU50046 `batch_verify_assignuuid1017`（PROCESS50745/binding51058、TASK50746/binding51060），采用entry_token/owner_ref/item_label/units等不同字段：实例 `c97c0fa0-c97a-11f1-b5f7-36c1412e9ef7` 的角色→个人→角色真实UI往返通过，最后 `dbfdc3db-c97a-11f1-b5f7-36c1412e9ef7` / `dbf70d16-c97a-11f1-b5f7-36c1412e9ef7` 各为未领取的单人候选池、两份UUID身份正确。该样本运行于上一批1ac3d418后端；它证明配置身份通用性，不冒充本批新预检镜像复测。

## 生命周期 / 安全 / 性能

产品无包格式、schema、共享jar、UI/i18n或生命周期写出端变化；Config/Clone/Import/回滚沿既有冻结契约，不由本批推导完整生命周期通过。新增fixture旗标仅测试脚本，必须显式STARTS_IN_MI且DIRECT_MI/assignment模式，默认图不变。SQL只在合成脚本中固定容器+严格数字businessKey做只读计数，不写产品SQL。

预检在完整副本上只新增集合根键，不修改原canonical行；没有成功前部分发布。按源表单映射，不按列名猜身份或分派。目录查询按目标集合批查，非Assign/普通Start无目录调用；没有压测结论。

## 最终门禁 / 未验证项

新镜像完整 `pnpm -C frontend run regression:mi`：234项单测及6个浏览器场景全部通过，截图保留在 `frontend/user-portal/verification-screenshots/2026-10-17_*`。普通首人工任务直接MI的同脚本复验通过：无效目标实例 `92125e1d-c9ca-11f1-b5f7-36c1412e9ef7` 保持Intake任务；合法实例 `92429602-c9ca-11f1-b5f7-36c1412e9ef7` 实际生成两条对应身份的任务，快照未进入引擎。

独立reviewer最终重审全部staged6文件及源码SHA，无Blocker/未接受Major，窄批CONDITIONAL可提交；diff检查及两脚本node检查通过，291条非空新增行的4类增量敏感扫描0命中。报告仅补本签核结果，无产品再改。

仍须闭合间接初始路由/多Start、无JWT生产者、双补偿失败隔离、真实创建后分派异常可见性、New Request/Preview、多用户与全部A01-A41。当前source未开启Assign的普通Start资格不属于本批新增门禁，不能宣称全实例所有分派值都已统一校验。383既有前端类型错误及邮件调度表缺失仍开放，不报告整体P4完成。
