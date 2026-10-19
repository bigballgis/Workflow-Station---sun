# Code Review：DW 批量预览共用 Portal 工作区

## 结论

CONDITIONAL。本批补 DW Form Preview 缺失的批量入口与共享渲染，不能代表完整 A22 或 P4 完成。最终45文件独立 staged 重审无新增 Blocker/未接受 Major；完整 MI 通过，允许提交本窄批。

## Findings / 逐条处置

- Major（已修）：旧 DW Preview 只有 Export/Import/Add，没有批量入口。真实红截图 `2026-10-17_batch-preview-before.png`；新实现投影当前放置控件、当前尚未保存的 batchForm 和 BPMN 分派配置，不覆盖单行 subForms。
- 早审 Major（已修）：预览新增若全部生成 UUID，会抹去配置的序列前缀/位数。`batchPreviewKeyAllocator.ts` 改为按明确 PK strategy 使用现有格式器，仅在本地生成样例；3 项测试覆盖格式与拒绝路径，真实合成序列样本保留 SYNTHETIC 前缀及六位序号。
- 早审 Major（已修）：普通字段未投影设计只读权限。`batchSubTablePreview.ts` 只消费当前 binding 的 scoped fieldPermissions，不读取别的 binding 或裸列名扩大权限；测试证明 READONLY 保持只读。
- 独立预审：新增 Blocker 0、未接受 Major 0。reviewer 独立 Portal 8 文件52项、DW 5文件36项测试全绿，审阅全部8个共享新文件和 DW host/目录/allocator/投影/测试/脚本。最终结论仍须结合部署、MI、staged 和敏感扫描。

## 范围 / 验收映射 / 触发矩阵

对应 A01/A03/A20/A22 的预览边界。Portal 原工作区、分派编辑器、草稿、字段解析和纯校验移到现有 shared 区域，原模块保留兼容导出与薄适配器；DW 和 Portal 当下共用同一组件，不复制第二套业务判断。

命中实现自主、修复验证、配置驱动、代码审查、SAST、前端 parity、弹窗标签、截图、MI 完整回归和帮助指南。三语及原 Sub-Table Help 同步，没有新增站点。无后端、API 路由、实体、配置格式、依赖版本或部署变量变更。

## 编译与静态检查

- DW `pnpm -C frontend/developer-workstation run build`：最终构建23.31秒通过；Portal 对应 build26.01秒通过。既有 Sass/chunk warnings 未屏蔽。
- Help `pnpm -C frontend/help test`：22项通过；typecheck通过，build通过。
- 全量 vue-tsc 不能记为通过。Portal 当前383项，与本次改动前383项一致；DW 用 HEAD 8a4f7f86c 的 git archive 临时副本复现253项，新增 shared Vue 的宿主模块解析 paths 后当前128项。未降低 strict 或增加忽略；新增 batch 文件无类型错误。既有 FormDesigner/useFormPreviewBuild/SubTableField 错误在基线已存在，未夹带整改。
- 新共享函数按 AST 检查无超过40行的函数；共享 SFC 各355/196行，无增加管理包体系。

## 测试 / 红→绿

- DW 7文件42项通过：16开关组合、严格 true、原CRUD、未知schema/控件ID、Assign-only、scoped只读、配置格式、Preview取消/私有草稿/保留隐藏字段、同 binding 两控件独立配置与重复ID、实时未保存定义。
- 为同 binding 第二控件作反例：临时移除 fullBindingMap 查询后，真实 preview builder 4项中2失败；恢复后4项全绿，非伪造成功断言。
- Portal 9文件59项通过，保留旧草稿、Add/Delete、Assign、校验、范围、变更声明和组件行为断言；只有测试访问共享内部实例的路径调整。
- DW/Portal 都复用共享 renderer，但 Portal 仍只接受服务端冻结投影、真实目录和 PK prepare；DW 注入明确的样例目录、本地序号和 private parent context，不触及真实保存/任务动作。

## 部署与真实页面

最终 build 后仅 `--no-deps` 重建 developer-workstation-frontend、user-portal-frontend、platform-help-frontend，三个实际容器 running。

- DW 镜像 `sha256:ab8a8269da0995ce3415344469e5453427e42a3b0bac9760a49f6017c563ab73`。
- Portal 镜像 `sha256:a6a9f2765f1b10a7a6e99e6c0d85370634dcffbf9492aaef599b82f7e2f25e7f`。
- Help 镜像 `sha256:ead78745b65568012366d0bf0732be874dd12828e3b39674eacb845f9d5ee940`。
- `verify-batch-designer-preview.mjs` 在最终镜像以 FU50034 data、50035 person、50037 role 三组通过：真实 Form→Edit→Preview→Batch Edit，取消零应用、两行不同值/统一0、多行新增/删空、个人/角色样例、桌面/600窄屏。每组 pageerror0、非GET请求0、保存的 configJson 精确不变；没有 route interception 或脚本注入假工作区。
- Portal 新建隔离合成实例 `cfea97d5-c9d9-11f1-a0ed-d2843fd6a5be`、宿主任务 `cff5baac-c9d9-11f1-a0ed-d2843fd6a5be`：真实混合 Edit/Assign 工作区，非法目标与普通必填失败均保留草稿且未持久化，成功 Apply 沿原自动保存发一次 EDIT+ASSIGN；未选行不变、实际任务集合不变、pageerror0。不推进 MI 目标，不修改目录成员或运行中归属。
- Help 部署后真实页面验证152/152通过，含新增 preview hint DOM 断言。

截图绝对目录：`F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\`，保留 `2026-10-17_batch-preview-before.png`、`2026-10-17_batch-preview-data-50034.png`、`-added.png`、`-narrow.png`、`2026-10-17_batch-preview-assign-50035.png`/`-narrow.png`、`2026-10-17_batch-preview-role-50037.png`/`-narrow.png`。人工检查数据新增及窄屏标签/确认按钮可见，表格自身滚动。

Portal 脱敏截图 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_batch-shared-preview1017-workspace-mixed.png`（既有脚本日期前缀保留，实际本轮拍摄）；目录选人显示已遮罩。该图是错误反馈/保留草稿态，不作为成功保存截图；成功依据来自随后真实 submit 声明、重读 fieldValues 和未变化的任务集合断言。

## Function Unit 通用性与生命周期

通用性：绑定元数据、配置 PK、稳定控件ID驱动，无特定 FU/字段名业务分支；三套既有隔离合成 FU 和一个新实例有运行证据。新建 FU 完整设计、跨库生命周期本批未重跑，不冒称通过。

Save/Load：Preview 读取实时未保存设计但不写回持久化，测试和三次重读证明。Export/Import、Clone、Rollback、Admin两导入、Deploy：无格式/引用/序列化变化，本批 N/A 于迁移实现；既有全部生命周期验收仍是 P4 全局门禁。Portal 服务端快照和实际授权未改变；不是用 Preview 权限代替服务器权限。

## 性能 / 安全 / 残余风险

复用原20行分页/200行操作上限，DW样例目录不发 HTTP，Portal保留远程目录去抖/缓存/有限错误处理。无新增 SQL/内部URL/XML/凭证/用户日志，Preview零写请求。不声称200行压测通过。

本批只建立共享基础表格与专用 Assignment 的真实预览，不声称任意字段宽度/全部控件props/options、嵌套多父PK/FK、错误行定位等完整 A22 已通过。已有校验以受影响子集索引提示行号的问题仍需单独反例修复。完整 A38 的角色候选精确核对、错误非空办理人、持久结果可见性，以及启动失败持久隔离/hydration 等历史 P4 门禁未因此闭合。

## 最终门禁记录

首次完整 MI 因同时运行多组登录验证，命中 API 登录限流后中止；不是 PASS，也不以已通过的部分场景替代完整命令。保留该日志，等限流窗口结束后不改限流、不改测试标准，独占重跑完整 `pnpm -C frontend run regression:mi`：234项单测、6个真实浏览器场景全部通过，截图保留。既有 Node shell deprecation 提示未压制。

最终45文件独立 staged 复审：新增 Blocker0、未接受 Major0，早审两项按反例修复并重审闭合。reviewer 抽看 DW data-added桌面/role窄屏、Portal脱敏错误态；cached diffcheck、两脚本 nodecheck通过，1609条非空新增行四类敏感检查零命中。主审1708条新增总行同样零命中（含空行口径不同），文件名和拟提交消息未含敏感值。报告本节只补验证记录，产品代码未再改。

本机 TEMP 日志保留 `batch-preview-{dw-build,portal-build,portal-test,dw-typecheck,portal-typecheck,dw-baseline-typecheck,projection-red,help-runtime,mi,mi-final}.log`。P4全局缺口仍如实开放，不启用生产配置。
