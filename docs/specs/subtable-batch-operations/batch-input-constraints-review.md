# Code Review：批量输入控件使用已保存的边界

## 结论

CONDITIONAL。最终独立 staged 重审无新增 Blocker/未接受 Major，完整 MI 通过，允许提交本窄批。本批只闭合 A22 中已有 min/max/maxlength 的输入投影，不代表全部控件属性或 P4 完成。

## Findings / 逐条处置

- Major（已修）：规则解析与整批校验已保留 min/max/maxlength，但共享 BatchFieldInput 没有把这些值传给 Element Plus。输入阶段没有设计的上下限或长度限制，DW/Portal 均受影响。
- 两个新组件反例在旧实现均失败：数字的 min0/max10 为 undefined，文本 maxlength12 为 undefined。旧镜像真实 DW 验证同样失败，input maxlength 实际 null，而不是12。
- 最小修复：数值控件只传已解析的 min/max，文本控件只传 maxlength；未配置仍是 undefined，不发明默认限制，0 不经 truthy 判断丢失。逐行与统一赋值复用同一个控件，DW/Portal 两个真实调用方同时生效。服务端最终校验、保存和草稿事务没有变化。
- 独立初审无新增 Blocker/未接受 Major；独立25项测试及脚本语法/diffcheck通过。最终复审见末节。

## 范围 / 触发矩阵

共享组件两处 props 接线、一个组件测试、新真实验证脚本、既有合成 fixture creator 的可选输入限制和本报告。适用修复验证、代码审查、配置驱动、前端 parity/截图/弹窗标签/完整MI、安全规范。没有新schema、权限、字段、API、数据库或发布配置；原帮助对字段约束的含义不变，无新增操作或文案，不需新增i18n键或文章。

## 编译 / 单元测试

- 新2项红→绿；连 workspace19、validation4 共25项通过。断言逐行/统一值各2个实际子控件，不以零个控件的空循环冒充成功；另断言无配置时不设置maxlength。
- DW build21.83秒、Portal build22.59秒通过，原chunk/Sass提示不屏蔽。DW/Portal全量vue-tsc仍128/383项既有错误、本次改动文件0；不冒称全量类型检查PASS。
- 两脚本 nodecheck、diffcheck通过。未变更依赖或降低strict。

## 真实红绿 / 部署 / 截图

正常DW API新建并部署合成FU50051 `batch_verify_inputlimits1017`，tag明确为批量验证样本，PROCESS50755/binding51078、TASK50756/binding51080，序列PK，Caption maxlength12，Amount min0/max10。原creator不设可选flag时保持原配置，未修改其他FU。

旧DW镜像在真实 Form→Edit→Preview→Batch Edit 的maxlength断言失败（null≠12）。新build后仅 `--no-deps` 重建两端前端，实际容器running：DW `sha256:9b31ac193fbcd5bae2486d869609b5d0cda9fa6e33174fcc5ef7ebecf33ba1bc`；Portal `sha256:f954fd917121445019c413892d91788c18216ec071746ca13ba8aac3d8b096a0`。

新合成实例 `d2290396-c9db-11f1-a0ed-d2843fd6a5be`、宿主 `d234c3ad-c9db-11f1-a0ed-d2843fd6a5be` 只用于Portal草稿验证，不推进目标任务。最终同一脚本双端通过：真实输入16字符截为12字符，数字0和10时相应减/增按钮disabled；逐行与统一输入控件都有0/10/12属性；取消后DW整份表单和Portal fieldValues精确未变，pageerror0、工作区打开后非GET请求0。不是仅看DOM属性，没有Apply/Save、真实PK分配或任务动作。

初次脚本连续两个下拉框误定位正在关闭的旧浮层，后续改为从当前combobox读取aria-controls并点对应listbox；又确认点击原生只读input被placeholder拦截，应点可见select外壳。两次属于脚本失败，不记运行PASS；未force click、未固定sleep或放宽业务断言，最终完整脚本重跑通过。

截图保留，均只截工作区、无姓名邮箱；人工核600窄屏标签、输入与确认按钮无覆盖。绝对路径：

- `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_batch-input-constraints.png` 及 `-narrow.png`。
- `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-17_batch-input-constraints.png` 及 `-narrow.png`。

## FU / 安全 / 性能 / 兼容

通用性：只读已有字段定义，不按字段名或FU分支；新建隔离FU双端有真实证据，旧无配置的控件通过组件断言不加默认限制。Save/Load、Export/Import、Clone、Rollback、Admin两导入、Deploy格式未变，本批迁移实现N/A；不冒称完整生命周期已重测。

无新增请求、循环、缓存、SQL、凭证或用户日志；原20分页/200行上限不变。既有校验仍拒绝不合格提交；不把输入框约束当作服务端鉴权。未声称200行压测/全部控件props/options通过，日期格式/列宽等完整A22与A38、启动隔离/hydration全局门禁仍开放。

## 最终门禁

最终镜像完整 `pnpm -C frontend run regression:mi`：234项单测、6个真实浏览器场景全通过。独立5文件 staged 重审无新增 Blocker/未接受 Major；25项独立测试、cached diffcheck、两脚本nodecheck通过；165条非空新增行四类敏感扫描0命中，文件名与拟提交消息不含敏感值。reviewer独立查看双端600窄屏截图，无PII/标签遮挡，未把脚本失败计为产品PASS。

报告只追加实际门禁，产品代码未再改。TEMP日志 `batch-input-constraints-{red,green,fixture,start,runtime-red,dw-runtime,portal-runtime,dw-build,portal-build,dw-typecheck,portal-typecheck,deploy,mi}.log` 保留。全局P4门禁保持开放，不启用生产配置。
