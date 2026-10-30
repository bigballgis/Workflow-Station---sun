# 1179 互斥 Assign 分批提交终审

日期：2026-10-30。固定审查基线：`811a7c80b`。用户确认当前 Changes 均属于本功能，并授权审核、修复和本地 commit；未执行 push。

## 审查范围与结论

基础协议、SQL 100～108、Engine、Admin/DW 发布及设计器、Portal 后端和前端、Help、升级脚本、验证脚本与交付文档均纳入审查。已展开新增文件，而非只检查原 tracked diff。共享协议、鉴权、数据库、发布链路和 Portal 另经独立审查；修复后重审整个固定基线范围。

结论：**PASS（本功能变更范围）**。已发现的阻断项均已关闭，相关构建及回归通过。全仓类型检查的既有失败另列，不宣称全产品所有测试通过。

原始 Changes 已在本地归档 `tmp/assignment-pre-commit-20261030-184520.zip`，含 tracked 二进制补丁、全部新增文件及 SHA-256 清单。截图、日志和测试凭证保持本地；不纳入源码提交。截图忽略规则补齐嵌套目录，保留两张原 manual-diagnostics 图片。

## Findings 与逐条处置

### Major：行改派未递增版本，旧页面可覆盖新经办人

- 位置：`backend/user-portal/src/main/java/com/portal/component/AssignmentSubTableWriter.java`。
- 反例：页面读取 v5；另一请求完成改派；原页面仍提交 v5。原 writer 不改行版本，真实 `SubTableBindingScopeGuard` 接受旧页面，覆盖经办人。
- 修复：在既有事务中校验并递增 `_wsRowVersion`；未版本化行从 1 开始；非法值、负数和溢出拒绝。返回前后版本、配置 store、完整主键及经办字段作为窄变更回执。
- 证据：`tmp/assignment-final-row-version-red.log` 旧保存未被拒绝；最终 PG 用例同路径返回 409，确认经办人和版本保留。未只凭 HTTP 状态或编译结果宣称修复。
- 依据：并发隔离、配置驱动身份及 bugfix 红绿证据门。独立重审已关闭。

### Major：仅更新平铺行会保留嵌套副本的旧人员和版本

- 反例：flat 行已改派，而父行 `__subTables__` 内同一子行仍为旧人员/v5；打开链接表单会优先读取嵌套副本，后续保存冲突或重新带回旧值。
- 修复：仅遍历实时 `__subTables__` 树，按同 store 和完整类型化主键更新经办人与版本。其他字段、主键、表和归档不变。
- 证据：`tmp/assignment-final-writer-nested-red.log` 预期 target、实际 old；最终数据库测试同时核对新人员/v6、未保存字段、兄弟行、其他 store 和归档。
- 依据：MI 双份数据契约、最小写范围。独立重审已关闭。

### Major：Boolean 主键在登记和办理间不一致

- 位置：Engine `TaskWriteContextResolver`、Portal `SubTableBatchTaskWriteContext`、`AssignmentSubTableWriter`。
- 反例：设计器支持 Boolean 复合主键，slot 登记接受 Boolean；后续写上下文及改派却仅接受 String/Number，使合法任务无法办理。
- 修复：三处保持 Boolean 原值；空值、对象、数组等既有限制不变；不转换为文本。
- 证据：`tmp/assignment-final-context-boolean-red.log` 两服务 true/false 各两例失败；该诊断为同时执行两服务使用 failure-ignore，日志中的 BUILD SUCCESS 不代表测试通过。`tmp/assignment-final-writer-boolean-red.log` 数据库改派同样失败。最终不使用 failure-ignore 的四服务 package 中全部通过。
- 依据：完整配置主键和跨服务合同一致性。独立重审已关闭。

### 前端配套：安全接收改派回执

暂停并等待在途 autosave 后发起改派；回执的旧版本必须同时匹配请求捕获行、当前行、baseline 和所有目标副本，才更新该行人员与版本。其他草稿、兄弟行和 Lookup 缓存保留。不执行全量 GET 提升旧业务值版本；版本冲突保留草稿并阻止自动保存，提示明确说明改派已经生效。任务切换、缺元数据和 Boolean/text 主键混淆均拒绝。

`tmp/assignment-rowversion-frontend-red.log` 原实现两例失败；`tmp/assignment-rowversion-frontend-green.log` 四文件 47 项通过，其中 17 项回执测试包含下一次真实 autosave 负载。四处新测试类型错误通过结构收窄修正，专项再次 17 项通过。新实现由另一审查者独立核对，无未关闭阻断项。

## 验证

- 最终四服务 package：`mvn -pl backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal -am package -Dtest=Assignment*Test,Task*Test,SubTable*Test,InstanceBpmnSecurityTest,ProcessStart*Test -Dsurefire.failIfNoSpecifiedTests=false`。设置隔离测试库及本机 Byte Buddy 测试兼容参数。Common 99、Engine 537、Admin 53、DW 21、Portal 699，共 **1409 项，失败/错误/跳过均为 0**。日志 `tmp/assignment-final-six-batches-package.log`；writer 数据库 14 项确实执行。
- Designer、Help、Portal 正常 Vite 构建通过；日志 `tmp/assignment-final-{designer,help,portal}-build.log`。未降低类型或编译配置。
- Engine、Portal 后端及 Portal 前端正常 Docker 镜像重建，开发容器与最新镜像 ID 一致且健康。隔离验收环境也更新 Engine/Portal 默认生产入口，无 fixture Java 配置。
- 更新后的完整 `pnpm -C frontend run regression:mi`：29 文件 236 项单测、全部 6 个浏览器截图场景通过。日志 `tmp/assignment-final-rebuilt-mi.log`。使用独占隔离 fixture，不办理用户正在分析的最新单据。
- 正常 3000 入口新页面脚本 `index-zLgJB9ZL.js`；完成测试单仍为 Completed，参与行保留。截图 `frontend/user-portal/verification-screenshots/2026-10-30_assignment-final-commits-completed.png`。这是读取已有完成结果，未冒充重新办理全流程。先前完整双人办理证据见同目录文档 `2026-10-30-assignment-50122.md`。
- 两个升级 PowerShell 脚本 AST 解析通过；不运行会清理业务数据的完整初始化。原缺迁移的实际复现、100～108 幂等补齐和页面重新部署证据见上述 50122 文档。
- 每批 staged 文件与显式清单匹配，检查新增文件的 EOF 和 diff 卫生；原已超长文件无净增长。固定基线至最终工作树及新增 commit message 的高置信凭证扫描零命中，不把测试凭证或本地运行环境文件加入提交。

## 保留的限制

全量 Portal `vue-tsc` 仍失败，不能写全仓 PASS。终审在隔离目录提取原始基线，沿用同一依赖并正常构建以生成组件类型后比较：原始与当前均为 **382 项**，按文件和错误代码比较无新增诊断。本轮新增测试的四项类型错误已修正。具体原始/当前诊断保留于 `tmp/assignment-final-typecheck-original-built-base.log`、`tmp/assignment-final-typecheck-fixed.log`，对比结果为 `tmp/assignment-final-typecheck-comparison.json`。

原有非本功能的管理员 force-unclaim 权限治理、FU 自定义事件中未定义变量 `a` 的警告未顺带改写；不据此放宽本功能鉴权。最终包和专项回归不等于所有外部依赖组合、全部 FU 和整个仓库所有测试均已验收。

## 提交顺序

1. 基础协议、消息和协调 schema。
2. Engine 冻结策略、资格守卫及持久化完成/重开操作。
3. Admin/DW 校验与发布、Designer 配置。
4. Portal 后端写范围、账本与行改派版本。
5. Portal 前端操作、恢复及安全行版本同步。
6. Help、升级脚本、交付说明及本报告。

最终完整性检查补充一笔前端接线提交：`useSubTableAssignment.ts` 为本轮审查修复新增修改的文件，未在原始分批清单内。该文件已经参与上述前端构建、47 项测试及独立复审；补充提交使实际代码集合与已验证工作树一致。

这些提交形成一个跨服务功能集合；部署时遵循 BUILD_GUIDE 的迁移和四服务兼容要求。
