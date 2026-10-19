# Code Review：批量校验定位当前草稿行

## 结论

CONDITIONAL。最终完整 MI 通过、独立 staged 重审无新增 Blocker/未接受 Major，允许提交本窄批。本窄批对应 A09/A22 的错误定位，不代表 P4 完成。

## Findings / 逐条处置

- Major（已修）：原工作区只把实际变化的行传入校验器，直接把子集索引作为页面行号。仅第21行非法时提示第1行并跳第一页；删除后新增的实际第2行也提示第1行。新测试在旧实现得到2失败/17通过。
- 修复：保持纯校验函数和整批拒绝不变，既有行按配置 rowIdentity 映射回当前 draftRows；新增行按临时草稿顺序映射回删除后的当前位置。分页索引连续编号，提示21、表格序号21和第二页保持一致。不使用列名或数组旧下标识别业务行。
- 独立预审无新增 Blocker/未接受 Major，23项测试通过；删除、混合新旧、分页路径的映射一致。最终 staged 结果见末节。

## 范围 / 触发矩阵

只改共享 BatchWorkspace、Portal 组件回归测试、新增真实 DW 验证脚本和本报告。DW/Portal 同用该组件，同时生效；无字段契约、服务端权限、保存时机、PK/FK、设计/导入格式或任务动作变化。适用修复验证、配置驱动、前端截图/弹窗/MI完整回归和代码审查。原帮助语义已经说明全批错误，不新增用户操作，无需新帮助文章或i18n键。

## 编译 / 测试

- 旧实现 workspace19项中2失败、17通过，期望21/2实际均1。修后 workspace19+validation4共23项全通过；独立reviewer同23项通过。
- DW build24.77秒、Portal build27.29秒通过。DW全量vue-tsc仍128项既有错误，改动共享组件0错误；不冒称全量类型检查PASS，不降低strict或压制警告。
- nodecheck新脚本及diffcheck通过。

## 部署 / 真实红绿 / 截图

旧镜像运行 `FIXTURE_UNIT_ID=50034 node scripts/verify-batch-row-error-preview.mjs`，在真实表单Preview创建21条本地样例，应用后重开，只把第21行置空，返回第一页点击Apply：断言期望row21，实际 `Check Caption in row 1`，失败日志保留。

重新构建并仅 `--no-deps` 部署两端前端后同一脚本通过：既有第21行错误定位第二页，删除一行再新增时错误仍定位当前第21行；原预览21行未被失败批次覆盖。pageerror0、非GET请求0、持久化configJson精确不变。未修改真实业务数据、目录或任务。

DW镜像 `sha256:f0f4b0c5350bf6a1516bde69b837e388ed89af333740bf8d7c8e50292fdb1792`，Portal镜像 `sha256:4b14c91e1b3baa8774681cab7c419384f7ab2494a09cd28913df43abec1166dd`；实际容器均running。

截图绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\`：`2026-10-17_batch-error-row-before.png` 证明错误跳第一页，`2026-10-17_batch-error-row-existing.png` 证明第21行/第二页，`2026-10-17_batch-error-row-added-narrow.png` 证明新增错误提示与600窄屏。截图保留；最终脚本按弹窗边界裁剪，排除宿主身份区域，不以宿主遮罩挡住提示或关闭按钮。人工检查输入/标签/底部确认可见，不用截图代替零写请求断言。

## FU生命周期 / 性能 / 安全

通用性：运行态配置身份及本地临时草稿顺序驱动，无FU/列名分支。Save/Load、Export/Import、Clone、Rollback、Admin两导入、Deploy包格式均未变化，本批迁移实现N/A；未声称其完整生命周期重测。新建/导入FU全场景仍属P4全局门禁。

每次失败校验至多两次有界行遍历，分页20/操作上限200不变；无新增HTTP/SQL/敏感日志/凭证，零业务写入。压力测试与完整A22控件props、A38结果核对和历史启动隔离门禁仍未闭合。没有范围外修复。

## 最终门禁

最终镜像完整 `pnpm -C frontend run regression:mi`：234项单测、6个真实浏览器场景全通过。MI结束后独占重跑最终按弹窗边界裁剪的同一DW脚本，再次通过；人工查看桌面及600窄屏无姓名/邮箱，窄屏提示为当前row21，桌面/窄屏第二页表格序号也是21，底部按钮可用。原before图仅弹窗区域，没有宿主身份。

独立4文件 staged 重审无新增 Blocker/未接受 Major；cached diffcheck、nodecheck、148条非空新增行四类secret扫描0命中。只在报告追加实际门禁，不再改产品代码。TEMP日志 `batch-error-row-{red,green,runtime-red,runtime-green,dw-build,portal-build,dw-typecheck,deploy,mi}.log` 保留。P4全局未完成项保持开放，不启用生产配置。
