# 批处理收口增量：2026-10-19

## 范围与结论

以合并提交 `9167894aa` 为本轮基线。闭合 #1684、#1685、#1686；不宣布整个 P4 放行，不更改用户暂缓的 A39/#1666。

## 修复与红→绿证据

- Record Note：原测试重复收到两个 processInstanceId，新增冲突身份反例也失败；修后 5 项通过。相同上下文只发送一次，单来源和无来源兼容，冲突身份在发请求前拒绝；附件仍为多值字段。不更改服务端授权或通知规则。
- 测试基线：Pinia 与 matchMedia 装配、bpmn-moddle v10 命名导出、AI Close 图标定位及限定文件注释翻译。没有忽略测试、减少属性迭代或修改产品行为来满足按钮数断言。
- 嵌套 FK/PK：装配修正后仍有主表/祖先池传递断言失败。补齐 FormRendererFields → Inline Form → PortalFormFields 及 Link Form 入口，布局容器继续传递同一宿主上下文。当前宿主字段池加入而不覆盖祖先。card/row/tabs/collapse 四种布局在临时移除传递时 4 项失败，恢复后同文件 7 项通过。

## 验证

- Portal 全量：314 文件、2378 项通过。
- Developer Workstation 全量：182 文件、1151 项通过。
- Record Note 与嵌套上下文专项最终 12 项通过。
- `pwsh -NoProfile -File scripts/verify-batch-postgres.ps1`：真实 PostgreSQL 15 项、H2 16 项，合计 31 项通过，无失败/错误/跳过，BUILD SUCCESS。临时容器清理后专用 verification label 查询为空；开发库和数据卷未删除。
- Portal 构建通过，user-portal-frontend 已重建。
- 部署后完整 `pnpm run regression:mi`：234 项单测、6 类 Playwright 界面场景通过，使用隔离实例 `788a3ae1-cb90-11f1-967e-82c0efc60df9` / 任务 `83319626-cb90-11f1-967e-82c0efc60df9`。
- 截图保留于 `frontend/user-portal/verification-screenshots/2026-10-19_task-83319626-{sex-before,sex-after,attachment-table,subtask-grid,subform2-people,people-inline-uuid,assignee-subtask-slice}.png`，及 `2026-10-19_app-788a3ae1-details-{unprocessed,completed}.png`。

## 未验证与继续工作条件

- Record Note 的完整上传/通知投递与“未保存祖先行→嵌套 Add”的真实 UI 写入，本轮未独立重跑；不以 MI 截图冒充这两个场景。
- Portal 全量类型检查仍失败；基线 worktree 也完成 Vite 构建以生成同等组件声明后，两边均为 378 条诊断，行号归一化后差异 0。不能把 Vitest/Vite 成功写成类型门禁成功。诊断保留在本机 TEMP 的 `batch-closure-*-typecheck.log`。
- 生命周期脚本在源合成 FU50037 的 Clone 入口收到403，未创建副本、导入或回滚，未改权限或绕过校验。用户指出该检查偏离本轮批处理范围，已停止，不再请求扩权或创建额外生命周期测试FU，不作为本轮收口阻塞项。
- #1672 保留严格性能指标的后续优化记录，但用户已明确本轮“能用即可”，不再阻塞可用性收口。完整 stash 保留，未 pop/drop/apply；主键解析、审计精确索引读取、参数化多值写入和宽表隐藏选项按需挂载的最终证据见 `batch-wide-render-budget-review.md` 末尾。已有库需执行96号索引脚本，不能只重启。
- A39/#1666 继续遵循用户之前的暂缓决定；多主体授权、跨库与容量性能等完整门禁不得由本轮结果推断为通过。
- 当前改动未自动 commit/push，避免与用户正在远程合并的操作混淆。

## 最终验证补充

- 后续全量 Portal 为314文件2381项，DW为182文件1151项；后端专项444项、common470项（21跳过）及真实PG/H2合计31项通过。
- 最后一次部署后完整MI仍为234项单测、6类界面场景通过；类型检查仍为相同378项基线诊断。
- 后端既有 pinned-form 测试的 Map.of 与实际可变 mapper 返回不符：独立HEAD基线同样失败，改为可变测试数据后通过，未修改生产表单加载逻辑。
- 保留早期测量以便追溯；最终正常保存约0.8–1.3秒，无业务改值约0.36–0.43秒，属于有限样本范围而非稳定SLO保证。
