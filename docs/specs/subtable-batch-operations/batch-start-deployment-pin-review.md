# Code Review：增强实例启动的部署一致性

## 结论 / 范围

CONDITIONAL。本批闭合服务端批量快照与本次启动实际 BPMN 的部署竞态前提，不代表无首人工任务的 collection 校验或整体 P4 完成。旧无快照流程保留原启动接口、请求字段和 ByKey 行为。

## Findings 与逐条处置

- Major（已修）：Portal 的 BPMN 内容摘要缓存只保存 process key，而引擎再次按 latest key 启动；同键部署发生在缓存命中或启动之间时，快照可能对应另一份实际 BPMN。新缓存保存部署响应的 key+真实 definition ID，只有服务端已捕获批量快照的新实例附带期望 ID。
- Engine 仍先解析当前 latest、检查原有存在/暂停规则，再精确比较期望 ID；不匹配拒绝，匹配后仅按已校验 ID 启动，消除校验后再次 ByKey 的竞态。不是任意历史版本选择功能，旧 ID 即使合法存在也不能绕过 latest 检查。
- 旧引擎可能忽略新 DTO 字段：增强 client 使用独立 `/api/v1/processes/instances/pinned`，旧程序404发生在创建实例前，禁止回落普通 endpoint。新端点要求非空 pin，并保持既有 anyRequest.authenticated；未扩大原 permitAll 范围。
- Major（新镜像真实验收发现，已修）：既有部署响应将真实 raw UUID 拼成显示用 `key:version:uuid`，不等于 ACT_RE_PROCDEF.id_。首轮 warm 启动因真实 ID 不匹配明确失败，没有创建实例。兼容补充 `engineProcessDefinitionId`，由本次 deployment 的实际 ProcessDefinition.getId() 原样返回，旧 processDefinitionId 展示字段不变。Portal 只消费真实字段，缺失时在启动前拒绝，绝不拆字符串、猜 ID 或回落 latest。
- 新增 client fixture 首轮错误地省略统一 success envelope 导致2项失败，已按真实 API 格式补 success=true；未修改产品 unwrap 规则去适配错误测试。

## 红→绿、构建、独立重审

- Engine pin 测试在旧实现上3项2失败0错误：过期 pin 仍启动、匹配 pin 仍调用 ByKey。隔离副本重跑同样失败；初次误在工作区执行的测试不作部署产物来源。随后所有 package 均来自隔离副本，复制确切 jar 后才 build Docker。
- 新 producer 身份测试原实现1项失败（期望 raw ID、实际 null），补真实字段后通过，旧显示 ID 仍逐值校验。
- 最终 Portal/Engine Maven package：Engine9类40项、Portal42类500项，共540项0失败/错误/跳过；日志 batch-deployment-pin-package-final.log 留在隔离副本。独立 reviewer 核对同源 SHA 并重跑 producer1+engine pin4+真实security chain2+Portal pin3+client3，共13项全部通过。
- 测试覆盖有/无 startUser 分支、清除线程身份、过期 pin 零 RuntimeService 交互、空 pin 零 component 交互、匿名403、旧引擎404无第二请求、旧请求 endpoint/shape 不变、快照不进入 engine variables。
- 最终 jar SHA256：Engine `295396A9BFE5F075E7A487DBBE0CFBACDEA3848F1F3A5C9529E4F2C38E55A3DA`，Portal `BB55AA3BA12D3DDE586CC702A5B7F501D8373FCA2B695CDE99A88486CCA69522`。
- 先部署 Engine `sha256:6282ee1ba056f5e9cc5a91eb41663de3d6f0524032dc50a99ae5b7b0993b1663` healthy，再部署 Portal `sha256:1ac3d4189ce70d254c3ba0309851157304077301c1c172f1cb4de943824ae328` healthy。没有共享 jar 或前端产品改动。

## 真实同键部署竞态

- `verify-batch-start-deployment-pin-runtime.mjs` 只接受显式 `batch_verify_*` 与合成 catalog 名称。FU50036正常 Portal Start 先暖缓存；读取该实际实例的 BPMN 后原样重新部署同键，未修改 DW/catalog 或旧定义。
- 显式用旧 pin 直调新引擎返回400；Portal 使用旧缓存也明确拒绝，没有自动重试普通接口。针对本次唯一 `SYNTHETIC-PIN-<数字>` businessKey 通过固定容器 psql 查询 ACT_HI_PROCINST，操作前及两次拒绝后都为0，非只看状态码。
- 下一次显式 Portal 请求清除旧缓存后成功部署/启动，正常到 Review；新实例具备服务端投影的 Assign 能力，旧实例实际定义 ID 保持不变。
- 最终真实结果：warm实例 `5de97520-c97a-11f1-b5f7-36c1412e9ef7`，重试新实例 `5ea1a1e5-c97a-11f1-b5f7-36c1412e9ef7`，Review `5eb6feec-c97a-11f1-b5f7-36c1412e9ef7`，rejectedInstances=0。没有取消/改派或自动修复这些实例。

## 触发矩阵 / 生命周期 / 性能

命中实现自主、bugfix-verification、config-over-heuristics、secure-coding-sast、MI热路径、code-review。新请求为专用 DTO 的可选字段，只作一致性收紧；不改变用户、租户、工作台权限来源。SQL仅运行验收脚本中对严格数字合成businessKey的固定只读计数，不引入产品SQL。

新响应字段只增、旧字段不改；唯一 deployment producer 与 Portal consumer 同步，其他 DW/Admin 部署消费者继续读取旧字段。FU包/Clone/Import/Rollback格式及业务存储未改，本批对应写出端 N/A。真实运行仍仅本测试操作者，不冒充所有启动生产者或完整生命周期验收。启动没有新增逐行查询或额外服务请求；部署缓存仍按原摘要复用，不宣称压测通过。

## 待补验证与残余风险

- 最终两服务新镜像上的完整 MI 回归：29文件234单测及6浏览器场景全部通过，最终 OK — unit + screenshots。独立 staged15文件完整复审无新增 Blocker/未接受 Major，node --check、cached diff --check 通过，363条非空新增行四类敏感模式0命中；可提交本窄批 CONDITIONAL，未重复制造 runtime 部署竞态。
- 邮件/内部API无可转发JWT的增强启动会显式失败，不称已适配；需按原设计补服务端生产者契约，不能扩大新端点匿名授权。无首人工任务的collection预校验、启动持久化且取消/清理双失败的隔离、引擎分派异常可见性、New Request/Preview及多主体仍开放。日志另观察到既有 `we_action_email_deliveries` 表缺失的定时任务错误，本批未触碰邮件/schema，记录为范围外环境债务，不用容器healthy宣称所有运行链无错误。383既有前端类型错误未解决，整体P4不放行。
