# 运行中MI已办节点着色修复

用户任务：8ecceadf-d5ca-11f1-8c70-6e7849f291cc；流程64b21b78-d5ca-11f1-8c70-6e7849f291cc。

## 修复验证

- **复现**：真实浏览器打开原任务，Investigation为灰rgb(144,147,153)，Mark Completed为橙rgb(255,102,0)。历史中一笔Investigation为PENDING，另一笔已以REJECT动作办理后进入Mark Completed。
- **根因**：任务BPMN解析器只将completed历史计入已办理集合，遗漏rejected；My Request运行中MI分支也有相同条件。操作结果与节点是否已经办理混为一谈。
- **改动**：两个集合同时接受completed/rejected，原当前节点优先级及MI下游抑制规则保持。根据历史状态、BPMN结构和当前任务计算，无FU/业务表/节点名/ID硬编码；不修改动作类型、业务BPMN、历史REJECT审计或权限。
- **红→绿证据**：新增10项回归，改前4失败/6通过，改后10通过；相关21文件74项通过。build并重建Docker后，在同一原任务检查Investigation绿色rgb(0,166,81)、Mark Completed仍橙、未走Approval仍灰；My Request同一流程的Investigation也绿色。两次历史API的data逐值相等，代理未领取、释放或推进用户任务。
- **未验证项**：独立并发压力、远程环境、重新导入导出/回滚未执行。本次没有配置或接口格式变更；这些生命周期不涉及新字段透传。

## 上轮验收遗漏

上一轮完整流转及最终申请详情着色检查，没有覆盖流程仍在运行、REJECT动作转到下一节点的任务页。两个入口解析器不同，最终图正确不代表中途任务图正确。本轮新增每个MI任务阶段的实时颜色断言，不能再用结束图代替。

## 自动化与部署

- `pnpm test`基线：2613通过、1失败；修改后：2623通过、同1失败，新增10项全部通过。既有失败为mainTableView/viewDetailForm.test.ts的未配置显示列预期，未在本次修补。
- `pnpm run typecheck`：基线381条、最终381条，按文件/诊断内容比较无新增。两个原解析器ESLint基线8+1错误，最终同8+1；新增测试ESLint通过。
- `pnpm run build`成功，47.63秒；随后dev compose `up -d --no-deps --build user-portal-frontend`重建，才在浏览器验证。没有只测试源码而漏部署。
- 完整`pnpm run regression:mi`：250项单测+6浏览器场景通过。因本机浏览器网络问题，临时驱动以127.0.0.1:3101直连、禁用Chromium代理，使用正常密码登录会话；没有mock API。

## 截图

- 修前：frontend/user-portal/verification-screenshots/2026-11-01_live-investigation-before.png。
- 同任务修后：frontend/user-portal/verification-screenshots/2026-11-01_live-investigation-after.png。
- My Request修后：frontend/user-portal/verification-screenshots/2026-11-01_live-investigation-application-after.png。

已实际打开修前、修后截图核对。原任务仍由liam持有，另一笔Investigation仍在办理，不通过操作用户数据造绿色结果。

## 证据

`.codex-1101-live-node-before/after.json`记录真实API及computed stroke，`-red.log/-green.log`记录红绿，`-test-baseline.log/-test-final.log`、`-types-baseline.log/-types-final.log`及`-lint-compare.log`记录全量基线差异，`-build.log/-docker.log/-mi.log`记录构建、部署和MI门禁。临时脚本、日志、会话、构建产物和截图按仓库规则忽略不提交。

浏览器并行回归期间遇到空白页/加载超时及Claim/草稿重开超时；Kong日志确认429限流（含draft GET和claim POST），保留.codex-1101-live-node-kong-429.log。未调整或关闭限流，后续串行恢复同一草稿/任务，没有计作业务通过；保留失败截图和日志，随后串行重跑同一尚未办理的测试任务。未为此修改产品代码或降低断言。

## 六组新建流程的中途检查

- approve / direct：`b6d22a08-d5cb-11f1-8c70-6e7849f291cc`，6个办理阶段、9次交易Details隔离检查。
- reject / approve：`e5198bed-d5cb-11f1-8c70-6e7849f291cc`，6个办理阶段、9次交易Details隔离检查。
- direct / reject：`4a544fd4-d5cc-11f1-8c70-6e7849f291cc`，5个办理阶段、8次交易Details隔离检查。
- approve / approve：`705f1493-d5cc-11f1-8c70-6e7849f291cc`，7个办理阶段、10次交易Details隔离检查。
- reject / reject：`fdfb5e3e-d5cc-11f1-8c70-6e7849f291cc`，5个办理阶段、8次交易Details隔离检查。
- direct / direct：`29ebd85f-d5cd-11f1-8c70-6e7849f291cc`，5个办理阶段、8次交易Details隔离检查。

六组均从New Request、草稿重开、编辑/删除替换、Batch选人及逐行email核对，走到全部交易结束，再重开申请详情。共34个办理页面，含28个MI任务阶段的实时颜色断言：打开Investigation时自身橙、Approval/Mark Completed灰；进入Approval或Mark Completed时已办理的Investigation绿；深度优先先办完一笔再办另一笔，后者后续节点仍不得因兄弟历史提前变绿。34次Case和52次Transaction Details隔离检查、每次持久化数据与行版本不变、逐taskId历史动作/备注、最终节点和出口均通过，pageerror=0，引擎end_time存在且0活动任务。

此处新增中途断言，没有继续用结束后的申请详情代替活跃任务验收。六组包含三类路径及相同/混合分支，不宣称九种有序组合穷举。业务配置Mark Complete=REJECT保留，最终REJECTED与历史审计仍按真实动作显示。

## 既有FU兼容

- FU48 v1.0.133：`7bc356e9-d5cc-11f1-8c70-6e7849f291cc`，Batch人员分派VERIFIED、approve/direct混合完整流程6次办理及中途颜色断言通过，重开和引擎结束核对通过。
- FU50126 v1.0.2：`95ae0441-d5cc-11f1-8c70-6e7849f291cc`，Batch人员分派VERIFIED、approve/direct混合完整流程6次办理及中途颜色断言通过，重开和引擎结束核对通过。

FU48与50126的配置未改动。三份设计不同的样本分别为原始FU、普通双绑定副本、显式额外FK副本；旧冻结版本无需重新发布即可使用前端修复。独立双账号和并发压力未验，用户原单仅只读检查，不冒称代其走完。

地址核对：localhost:3000与127.0.0.1:3101实际返回同一入口bundle（index-DjIj5it-.js），截图所测为相同部署；非另搭模拟页面。
