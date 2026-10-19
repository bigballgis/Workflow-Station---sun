# Code Review：必填只读默认值与 DW 保存接线

## 结论

CONDITIONAL，不放行阶段或生产。当前批次补齐只读必填默认值校验、真实保存接线及加载期间 Save 保护；DW/导入服务端的发布前统一校验与全量类型门禁仍未闭合。

## Findings 与逐条处置

### Blocker（本批已修）

- `SubTableBatchRowDesign.validateAddForm` 仅检查字段是否出现在表单，未证明必填只读字段可获得值。
  - 新反例先失败：必填只读 INTEGER 无默认值被接受；`1.5` 也应拒绝。
  - 处置：只读必填业务字段要求已验证的字面默认值，复用新增完整行类型/范围校验；手工 PK 不能借固定默认值复制多行身份。PK/FK/计算字段原专用规则保留。
- `useFormSave` 的局部画布 `options` 遮蔽传入参数，`options.getBatchTableFields` 取不到字段。
  - 真实 composable 测试：恢复旧调用后 2 用例失败，恢复注入回调后通过。
  - 处置：直接解构字段读取函数；把当前 TASK 字段权限传入 membership 设计验证。

### Major（未闭合的阶段门禁）

- `FunctionUnitValidator` 和 Admin 包导入尚无同一服务端批量配置校验，直接 API 仍可发布不完整配置。本批只证明 DW Save 与 Portal 启动捕获拒绝，不能宣称发布端全覆盖。下一批补共享纯契约，禁止生产启用。
- 真实测试在画布异步初始化前点 Save，得到成功且 synthetic 主/单行规则被清空。已恢复本次自建 fixture 原定义。处置：新增按选择代次追踪的 hydration 锁，手动和自动保存均在读取画布前检查；等待实际挂载，取消过期/卸载回调，失败保持禁用。2026-10-15 真页面延迟 bindings 请求验证锁定，释放后才允许保存；不是仅让脚本等待。
- DW 全量 vue-tsc 仍 FAIL；当前 `useFormSave` 尚有 HEAD 既存未使用解构变量，不通过删检查或忽略规避。

### Minor / Question

前端检查排除 JS 归一化非法日期、非法时间和数字表达式；服务端最终校验使用字段类型/长度/精度。大整数超出安全 Number 范围不作为前端可验证默认值。批量首版支持范围仍需整套契约统一，不能靠字段名称决定语义。

## 范围 / 验收映射与触发矩阵

A13/A19/A23，必填完整性和保存无损；不是 P3/P4 最终验收。命中 code-review、implementation-autonomy、bugfix-verification、config-over-heuristics、verify-ui-fix-with-screenshot、i18n/兼容门禁。

## 编译与静态检查

- Portal Maven package 成功，默认值/新增行/快照组合通过；原编译与 JaCoCo warning 保留。
- DW Vite build 成功；5 文件 34 测试通过（包括真实 Save composable、metadata/canvas 加载锁、取消旧回调、权限和事件序列化）。Portal RowDesign 4、AddedRowGuard 6 测试通过；其他指定类须以本次实际测试报告为准，不能把未匹配类计入结果。
- DW 全量 vue-tsc FAIL；改动 membership/新测试无新增类型错误，未声明完整类型 PASS。
- 三语既有错误文案同步说明只读默认值/手工 PK；diff 和增量敏感信息按提交范围检查。

## 运行与截图

重建 Portal 与 DW frontend 后验证：

- Synthetic FU 50027 无必填只读默认值：旧运行产物允许启动（反例）；本批产物拒绝新启动。原反例实例保留，拒绝没有新增本地实例，未操作真实 FU/业务数据。
- DW 50027 TASK 的 Save 明确错误且 0 PUT；50025 合法默认 0 的 Save 为 1 PUT，规则/control ID/Batch Add/独立 batchForms 均保留。
- 2026-10-15 对 50025（人工 PK）、50026（UUID）与 50027 重跑延迟加载路径：加载中 Save disabled；两种合法设计各 1 PUT 且 GET 重新读取 rule/batchForms 与请求一致；非法设计 0 PUT；浏览器 pageerror 为 0。
- 本次恢复原 PostgreSQL/Redis 容器，未重建数据卷。DW 初次增量打包产物出现 `ClassFormatError`，清理 target 后 Maven clean package 并重建 DW 后恢复健康；原 canModify=false 在重新编译部署后恢复 true，未修改账号角色或产品权限。
- 截图目录：`F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots`，`2026-10-14_batch-designer-invalid-default-50027.png`、`batch-designer-invalid-default-message-50027.png`、`batch-designer-valid-default-50025.png`。截图保留，内容为 synthetic 设计。
- 最新同目录 `2026-10-15_batch-designer-valid-default-50025.png`、`2026-10-15_batch-designer-valid-default-50026.png`、`2026-10-15_batch-designer-invalid-default-50027.png` 与 `2026-10-15_batch-designer-invalid-default-message-50027.png`。已查看 50025 画布和 50027 错误图片；不能据此宣称整个设计器布局或 DW/Portal Preview parity 通过。

## FU 通用性与生命周期 / 性能

只读来自 binding 复合权限，类型来自字段元数据；无 FU/列名硬编码。共享包格式不变；新配置发布与 Admin 两条导入、Clone/Rollback 等完整矩阵未通过。没有每行查询或新依赖，默认值校验有界于设计字段集，负载压测未执行。

## 增量敏感信息 / 残余风险

脚本仅接受显式、带 synthetic tag 的 FU；登录助手不改，未输出 token/凭证，未加入真实数据。当前代码批次不能抵消前述未闭合门禁；后續继续修正服务端发布和加载期间保存保护，不做生产放行。
