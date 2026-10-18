# Code Review：New Request 发布批量上下文

## 结论

PASS（最终 staged 独立重审）。39 文件无新增 Blocker／未接受 Major；独立关键 26 测试、API 3 测试、651 行非空增量敏感扫描及 8 个脚本语法检查通过。范围仅为新申请读取/提交的发布一致性，不代表全部 P4 通过，也不解决延期 A39。

## Findings 与逐条处置

### Blocker

- New Request 未带发布上下文，页面打开后同 catalog 覆盖仍可按另一份设计启动。新增类型化 `BatchStartContext`，服务端从权威发布内容计算摘要，在部署、表单增强和引擎调用之前校验。缺失、伪造、变更、开启后关闭均返回 VERSION_CONFLICT；不把摘要当授权。
- Portal 与 Admin 都有五分钟内容缓存。真实复验发现只绕过 Portal 缓存仍读到旧 Admin 内容；增加已有 content 接口的 `forStart` 分支，绕过而不覆盖旧组装缓存，并要求 Admin 返回 authoritative 确认。旧 Admin 不识别该协议时显式拒绝，不回退缓存。

### Major

- `PortalException(409, ...)` 被通用处理器映射为 500。改用既有 `BusinessException(VERSION_CONFLICT)`，真实浏览器验证 HTTP 409 和可读刷新提示；非法 task/instance + forStart 使用 IllegalArgumentException 400。
- CSV 是服务端生产者，但不能借用 internal=true 绕过普通 FU/workspace 权限。专用 server-produced 委托仅豁免浏览器 echo，保留原普通鉴权；内部邮件仍走其原私有服务端路径。

### Minor

- 新 API 测试 hook 返回 VitestUtils 导致新增类型诊断。改为 void block，重跑三项测试并比较类型基线。

### Question

无剩余本批业务方向问题。启动顺序与 A39 方案均未修改。

## 范围 / 验收映射

设计运行态快照规则 7、A05/A06/A19/A26：New Request 使用当前发布内容，提交时复核同一摘要；开启后关闭不能绕过；已启动增强实例不漂移。上下文是可选 DTO 外层字段，不进入 formData、实例业务字段或 Flowable。旧设计无增强时不要求新字段。

## 触发矩阵

implementation-autonomy、bugfix-verification、code-review、secure-coding-sast、verify-ui-fix-with-screenshot、extract-help-guideline；跨服务发布契约、输入鉴权、缓存、三语帮助、共享 jar 下游与 MI 回归。没有实体/SQL/环境变量/路由变更。

## 编译与静态检查

- Maven 在隔离副本执行，避免 IDE classfile 污染；四个后端 clean package 成功，随后 Admin authoritative 协议 package 成功，最终 Portal HTTP 错误映射 package 成功。Portal 全选择回归 290 项，Admin 新缓存边界 1 项；最后关键组 25 项。独立 reviewer 复跑六组 26 项。
- 发现 DW 增量 jar 仍是旧资源后，未接受该产物：clean package 四服务，逐个检查 fat jar 内 platform-common 英文新 key，再重建四个后端。
- Portal build 成功（23.07s），Help build 成功（1.29s）。前端本批相关六文件 23 项通过；新 API 三项再次独立通过。
- vue-tsc 存量 383 项诊断，不作为全类型检查 PASS；新增测试诊断修正后与基线逐条比较差异为零。未降低严格度或屏蔽告警。

## 测试与运行证据

- 旧部署真实 RED：隔离 clone 50062 New Request 缺服务端上下文。第二次实际失败 clone 50063：Portal 绕缓存后摘要仍未变化，定位 Admin 缓存。
- 关闭校验的隔离副本 mutation：8 项中 4 项失败；恢复后通过。没有将 mutant 部署到运行环境。
- 最终运行脚本 `frontend/scripts/verify-batch-start-context-runtime.mjs`：隔离新 clone 50066 / catalog `fce062d8-a4fd-40ca-ad77-601d11d167ee`，先 DW Deploy，再同 catalog 正常 Export/Admin Import 两次覆盖。缺上下文及过期页面均 HTTP409、引擎实例为零；刷新后增强启动成功。关闭四项后旧增强实例能力不变、旧页面拒绝；刷新后普通启动成功，第二实例无批量能力。最终两实例、每实例一个 Review 任务、浏览器错误零。只操作 tagged synthetic clone，不改用户 FU 或真实业务任务。
- 引擎流程变量没有 batchStartContext。完整 MI：234 单测 + 6 浏览器场景；Help 实际 DOM 153/153。
- 截图（均已部署后采集）：
  - `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-18_batch-start-context-opened.png`
  - `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-18_batch-start-context-stale-rejected.png`
  - `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-18_batch-start-context-disabled-narrow.png`
  - 窄屏 600px 错误提示完整可见且边界不越界，实际查看确认；正文为通用刷新说明，不含业务身份。

## Function Unit 通用性与生命周期

使用发布产物、catalog、快照规则及规范化摘要，不硬编码 FU/table/field。运行覆盖已有 synthetic FU 的多个新 clone、正常 Deploy 以及同版本 Import 覆盖；纯协议测试同时覆盖旧配置、启用、关闭、其他 catalog、格式错误。Save/Load、Export、Clone、Rollback 序列化格式未更改；Admin 两条导入仍使用同一原解析器，本批仅增加读取分支。跨库生命周期全矩阵不由本批声明完成。

## 增量敏感信息

提交前扫描完整新增/修改行、测试、脚本、文档；不输出登录凭证或 token。runtime 脚本复用既有测试登录工具，日志仅 synthetic 技术 ID。测试读取计数 SQL 限定唯一 synthetic process key，且 key 先验证格式；不直接修改业务数据库。

## 性能

每次 Start 必须向 Admin 获取一次权威发布内容，增加一次无缓存组装及对 Admin 可用性的依赖，这是避免普通→增强变化被缓存遗漏的正确性成本，不宣称性能优化。普通 Task GET/Save 缓存不变；无逐行新增 HTTP。容量压测尚由后续验收闭合。

## 残余风险 / 豁免

A39 用户明确延期，#1666 OPEN/critical，仍阻塞完整 P4 放行。本批不调整引擎先启动、本地后持久化的顺序。New Request 批量工作区本身尚未接线；本批只是发布上下文一致性，不能宣称该 UI 已完成。全生命周期、容量、多主体及所有元数据写入口的证据继续处理。服务端先升级 Admin authoritative 协议，再升级 Portal/UI；旧消费者不能被当成已确认支持。

## 范围外问题

存量 383 项 TypeScript 诊断不在批量增强修复范围；没有顺手改其他平台逻辑。

## 部署产物

四个后端均 healthy，两前端 running。Docker image SHA256（顺序为 Portal、Admin、DW、Engine、Portal UI、Help）：

```
60c013f187d1f93f4ddc814cff22e22d66ad343c047975d08fe37727164bfcf9
4fa4bf88e8447e2c7b6f79a05c22642affc6bc956c6ffbf822856fb690807ca1
90a2ccab75e4883c16e1594c990f179db955a77e561f0fa1a858fb9f3d17f847
73811dd444cf44b62816edc59a4e841e3025f326f852769b886d3e2dc7848bdb
b205539f56f08f008783a4e9028fdec7a5feb063da57ba3d8c71302f9d7ab039
05b7038564dfc084088659e3d488eb8dee8caddbf984baa769ae924e14f75ec4
```
