# 普通 Form / Batch Form 字段校验一致性验收

2026-11-04。本地修复与浏览器验收；未访问或发布 DEV / UAT。本文记录本轮字段校验增量，前一轮计算字段文本类型修复另见 [验收记录](2026-11-04-computed-field-text-types.md)。

## 修复范围

- Switch 使用设计的 `activeValue` / `inactiveValue`，支持布尔、0/1、Y/N；DW 与 Portal 普通表单和 Batch 默认值转换复用同一个实现。
- 多选 Select 按数组逐项检查当前选项；必填空数组不通过，`allowCreate` 保留新建选项语义。DW 的单项数组默认值不再坍缩成标量。
- 日期范围保留二元数组；绑定字段必须具有相应存储能力，本次使用 JSON，未放宽 VARCHAR 的数组写入限制。
- 日期时间的 `type: date` 校验使用与普通 Form 相同的 async-validator 内置校验器。
- 普通日期控件与 Batch 共用默认 valueFormat：日期 `YYYY-MM-DD`，日期时间 `YYYY-MM-DD HH:mm:ss`；显式配置的格式保留。
- 扩展声明式规则契约，支持邮箱、正则、数组、枚举等类型与列出的 designer mode；前后端均检查支持范围，保留设计的错误提示。
- 普通 Form 与 Batch 共用必填标志、最大长度解析；支持 `$required`、`mode: required`、maxlength/maxLength 及数值字符串。
- Batch 校验读取每行会话的隐藏/显示、必填和选项状态；`hidden(false)` 后重新执行必填检查。Batch Add 的第二次校验同样读取会话，避免重新套用旧选项。
- 后端区分展示选项与值域约束：`api.setOptions` 可以改变当前选项；要求固定值域时使用显式 `enum`。分派候选人、BU/Role、权限与行身份守卫未改动。
- DECIMAL/NUMERIC 校验先无损去除尾零再判断能否存储，包含 DECIMAL(p,p) 的零值；真正超出小数位或整数位仍拒绝，不自动舍入。

未扩展附件、Checkbox 等控件、多选 Lookup、自定义 validator 脚本；不承诺完整原生 form-create API 兼容。未修改源 MCY FU 或既有流程冻结配置；不新增 schema 或保存接口。

## 红 → 绿证据

### 字段规则

- `batchFieldParity.test.ts` 初次运行 7/7 失败；同一组测试修复后 7/7 通过。
- 后端从任务开始时的 Git 暂存区独立编译旧守卫和旧 Batch 契约，以相同 `BatchFieldParityTest` 方法执行，7/7 失败；当前实现同组 7/7 通过。使用独立 classpath，未把混合旧依赖的初次探针计作最终红证据。日志 `.codex-batch-parity-isolated-red.log` / `.codex-batch-parity-isolated-green.log`。另补 maxlength 为 3.0 / 数字字符串的第八项回归，修前 NumberFormatException，修后通过。
- DECIMAL 直接执行真实计算器得到 `0.5000000000`（scale=10），旧守卫拒绝、相同数值 `0.50` 通过。新实现均接受；`0.501` 和超整数位值仍拒绝，并验证 DECIMAL(2,2) 的 0。
- Batch Add 动态选项新增集成测试，临时关闭本次工作区修复时失败，恢复后通过。日志 `.codex-batch-parity-add-red.log` / `.codex-batch-parity-focused-final.log`。
- 日期默认格式集成测试：修前原生规则 valueFormat 为 undefined，修后日期/日期时间均为与普通 Form 相同的默认格式；测试包含事件会话投影。

### 同一旧任务：数字开关阻塞整批 Apply

- 合成 FU50141，任务 `dcdeea19-d847-11f1-a1af-aeb2e07f8cc8`，在修改前创建并冻结配置。
- 修前只修改 Caption，Apply 报 `Batch event validation failed: Numeric switch`。
- 更新运行时后，对同一任务再次修改 Caption：保存成功；直接读取 `up_process_instance.variables`，确认选中行改变、未选中行未变。没有重新发布或替换这个任务的冻结配置。
- 截图：`frontend/user-portal/verification-screenshots/2026-11-04-batch-field-parity-red.png`、`2026-11-04-batch-field-parity-legacy.png`。

### 同一新任务：缺省日期格式保存 400 → 成功

- 合成 FU50144，任务 `03c0e2f1-d84d-11f1-9ac7-f647935ef758`，日期范围与日期时间均未配置 valueFormat。
- 修前同一操作得到 HTTP 400 / 4003：`Batch field has invalid timestamp: occurred`，trace `3d38bae8`。页面保留未保存草稿。
- 部署默认格式修复后，同一任务、同一操作保存成功。
- API 刷新及直接查库同时核对：switch_num=1、switch_text=Y、choices=[a,b]、period=[2026-11-02,2026-11-06]、occurred=2026-11-05 13:14:15、邮箱与正则字段正确、category=dynamic；未选中行原字段不变。
- 同一表含 DECIMAL(18,2) 计算字段 `1 / 2`，保存后两行结果仍为数值 0.5。此项为浏览器成功路径证据；尾零反例的红→绿另由真实 Java 守卫测试证明。
- 截图：`frontend/user-portal/verification-screenshots/2026-11-04-batch-field-parity-date-default-red.png`、`2026-11-04-batch-field-parity-date-default-green.png`。

## 浏览器实际覆盖

入口脚本 `frontend/scripts/verify-batch-field-parity.mjs`，使用 Playwright 操作真实控件，不通过组件内部状态代替 DOM 及持久化检查。

- Portal Batch Edit：非法邮箱阻止 Apply 并显示配置提示；修正后继续；正则不匹配阻止 Apply，修正后继续。
- 实际操作两种自定义 Switch、多选 Alpha/Beta、日期范围、日期时间，修改 Caption 触发脚本动态选项，Apply 后检查保存响应、API 刷新及数据库值。
- 隐藏必填字段未显示，不阻塞可见字段保存；表级非空和字段权限仍由原守卫负责。
- 普通 Form：打开刚保存行的 Edit，核对开关选中状态、多选标签、范围输入 DOM；非法邮箱/正则均阻止 Save，修正后 Save 成功。
- DW Preview：Batch Add 填入同类字段；非法邮箱、正则校验；动态选项、日期范围和日期时间经 Apply 成功加入预览表格。API 重读设计与测试前完全一致，没有保存设计或业务数据。
- FU50143 覆盖显式日期格式，FU50144 覆盖缺省格式；FU50141 覆盖旧冻结配置。各轮页面异常计数均为 0。
- 最终 Portal/普通 Form/DW 复验日志 `.codex-batch-field-parity-portal-last.log` / `.codex-batch-field-parity-dw-last.log` 均通过，含真实数据库断言；普通 Form 截图等待失焦校验完成后采集，另保留修正过程中尚未失焦的截图。
- 截图补充：`frontend/user-portal/verification-screenshots/2026-11-04-batch-field-parity-email-error.png`、`2026-11-04-ordinary-field-parity.png`、`2026-11-04-batch-field-parity-portal-controls.png`；`frontend/developer-workstation/verification-screenshots/2026-11-04-batch-field-parity.png`。

复跑：

```powershell
# frontend 目录；prepare 系列只克隆带 batch-verification-synthetic 标记的本地测试 FU
node scripts/verify-batch-field-parity.mjs prepare-defaults
node scripts/verify-batch-field-parity.mjs portal
node scripts/verify-batch-field-parity.mjs dw
# 旧任务单独复验，入口会检查 synthetic FU 名称
node scripts/verify-batch-field-parity.mjs legacy dcdeea19-d847-11f1-a1af-aeb2e07f8cc8
```

## 构建、回归与环境

- 后端 Portal/DW/Admin/Engine reactor package 成功；部署了包含更新 common 的四个本地后端，Portal 最终变更再次 package/rebuild。
- Portal、DW、Admin、Help 前端 build 成功，已重建对应 Docker 服务。浏览器检查使用重建后的资源；已比对四个前端入口与 Portal 两个守卫的容器字节码 SHA。Help 的已部署 /help/form-events#batch 也已截图核对。
- 后端字段与计算专项 48 项通过；最终更广 Batch 后端专项 400 项通过，包含第八项长度规则回归。标准 Maven testCompile 的既有缺陷见末节。
- Portal 字段/工作区/普通规则/默认值专项 81 项通过；DW 默认值与原生状态专项通过。新字段测试已纳入 `regression:batch-events`。
- Portal 最终全量 2781 项通过、保留同一 Lookup 显示列既有失败；包含新增字段和日期默认格式测试。
- DW 全量 1277 项通过，保留 decision-i18n / locale-key-consistency 两个既有失败文件。资源竞争导致的一轮原生组件超时，单独复跑通过，最终全量不再出现。
- 类型诊断 Portal380 / DW162 / Admin135，与此前记录的干净基线比较无新增，不称为 typecheck 全绿。
- `regression:mi`：250 单测及六组浏览器全部通过。显式固定此前使用的 TASK_ID / APP_ID，避免自动选择本轮新建的非目标流程。
- 完整 `regression:batch-events` 最终串行 PASS：DW121 / Portal286 单测，以及同步/异步事件矩阵、BU/Role/人员切换、原文和 guarded 邮箱脚本的 DW/Portal 浏览器组合全部通过。此前一轮与容器重建重叠出现 502，另一轮与 MI 并行触发登录限流；均保留失败日志，不计作通过，不修改限流配置。最终日志 `.codex-batch-parity-regression-serial.log`。
- 本地升级服务时发现 Admin 缺少仓库现有 109 迁移、启动重试导致权限接口不可用，已在本地事务执行现有迁移并重启。该迁移不是本修复新增的 schema 变更。

## Code Review 结果

### 结论

CONDITIONAL。目标场景已有函数、DOM、同一任务保存及查库证据；完整 Batch 和 MI 组合通过。无未处置的本轮 Blocker/Major，既有全量/类型基线失败及独立 FU 生命周期浏览器矩阵缺口保留。审查范围是本轮字段校验增量，前一轮暂存的计算字段类型修复见其独立审查记录。上述 findings 处置后已重审完整增量；最终 Portal 重新 package/rebuild 后再次浏览器验收通过。

### Findings 与处置

- Major：静态 Select 选项阻止 `api.setOptions` 产生的合法值保存。`SubTableBatchFormRules.validateRow` 已区分选项与显式 enum；浏览器脚本联动、保存与查库通过，原分派授权守卫保留。
- Major：Batch Add 第二次校验使用旧选项，编辑时通过而 Apply 失败。`BatchWorkspace.prepareAdded` 已使用对应行会话校验；新增集成测试关闭修复会红，DW 浏览器通过。
- Major：缺省日期格式产生 Date/ISO 值，普通 Form 与 Batch 的输出不同。`BatchDesignedForm` 与 `DesignedDateTimeField` 复用同一默认格式；同一任务保存 400→成功。
- Minor：DECIMAL 去尾零后零值的整数位不能计为 1，否则误拒 DECIMAL(p,p)。已单独处理零并补边界断言。
- Minor：共享源文件新增 async-validator 导入，不能只依赖某一宿主的解析。三个宿主显式声明/解析依赖；DW/Admin frozen-lockfile 离线安装校验通过，三宿主 build 通过。
- Minor：长度解析误用 Integer.parseInt，会让合法的 3.0 或数字字符串变成保存异常。已按数值阈值解析，兼容空/非正值不启用长度限制；同一新增测试修前失败、修后通过。

### 范围与触发矩阵

- 业务规则以字段元数据和控件配置为依据，无产品 FU/表名/业务字段名分支。
- 命中共享前端、保存校验、异步行会话、隐私/安全、批量性能、UI/三语帮助、FU 冻结配置兼容检查。
- 本轮不改变分派权限、主外键、ownership、MI 范围、提交声明或 FU 序列化格式；不以放宽 VARCHAR/数值存储类型校验换通过。

### FU 通用性与生命周期

- 新克隆双样本及旧任务运行时通过；设计 Save/Load/部署与任务冻结读取由隔离样本验证。
- Export/Import、Rollback、Admin 两条导入未做本轮独立浏览器全矩阵，未宣称覆盖；原有复用的 Batch 契约消费者继续使用同一 common 类。
- 增量敏感信息扫描：本轮 tracked diff 与六个新增源/测试/文档文件中，private key、AWS access key、literal bearer、literal secret 均 0；diff 卫生检查通过。测试只使用合成值，脚本不读取/打印 token。
- 性能：按现有最多 200 行逐行校验，无新增 HTTP/SQL 查询或跨会话缓存；动态选项及可见状态保留原行会话隔离。

### 未验证项与范围外问题

- 未访问或部署 DEV/UAT；未修复既有 locale、Lookup、类型诊断及后端两处缺少 Testcontainers 的测试编译基线。
- 标准 Portal `mvn test` 的既有 testCompile 阻碍保留，专项测试使用实际 javac 编译后执行 Surefire，不能称为后端全量通过。
- 不承诺所有控件和所有自定义校验脚本已与普通 Form 完全等价；上文未扩展能力继续保留明确边界。
