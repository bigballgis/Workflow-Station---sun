# Code Review：DW 批量画布收集失败不保存旧版本

## 结论

CONDITIONAL。异常分支只有实际保存composable的单测证据，尚无真实浏览器画布读取故障复现；不宣称完整保存故障验收或P4完成。最终独立staged结果见末节。

## Findings / 逐条处置

- Major（已修）：`useFormSave` 读取挂载批量画布getRule/getOption异常被内部catch吞掉，随后采用旧cache/saved并报告保存成功；开发者离开时可能丢失以为已保存的编辑。挂载ref返回非数组也会走旧数据。另有已保存未知schema被硬改为1的问题。
- 红阶段：实际useFormSave的“读取抛错”“挂载ref返回undefined”“保存schema2”三个用例均期望false，旧实现均返回true，3失败/3通过；不是只测试新辅助函数。
- 修复：移除仅批量收集分支的吞错catch，挂载ref无数组显式拒绝，保留原最外层手动失败/自动保存一次warning处理。已保存batch定义要求schema1+rule数组后才序列化，未知格式不静默降级。没有改变单行subForms或通用保存策略。
- 兼容正例：未挂载但已访问的批量页签仍可保存合法cache；健康live优先于旧cache；未知schema原对象不被修改；失败时不调用store.updateForm；重复自动保存失败不会反复toast或报告成功。
- 独立初审无新增Blocker/未接受Major，独立19项测试绿，异常运行时证据不足如实标未验证。

## 范围 / 触发矩阵 / 编译

两处生产边界在useFormSave，现有实际保存测试扩充、新正常UI兼容脚本和本报告。适用实现自主、修复验证、错误显式性、FU可移植/版本格式及代码审查；无新格式/字段/接口/SQL/任务操作，无普通保存链全面重构。

- DW 7文件50项通过：实际Save10、config5、membership4、Preview builder4、Preview host3、投影21、allocator3。
- 独立reviewer实际重跑Save/config/membership共19项全绿。测试保留预期错误日志，不用吞日志冒充无错误。
- DW build22.18秒通过；vue-tsc仍128项既有错误，其中useFormSave原unused参数错误仍存在，本次无新增。无strict降级或抑制注释。
- 仅重建developer-workstation-frontend（--no-deps），镜像 `sha256:773371d610f222a0c28404f170088aaac9512187f64e3c1befb84704b96014a3`，实际容器running。Portal/共享renderer/backend/MI热路径均未改，本窄批不额外冒称又跑过MI；此前输入约束提交的最终完整MI234+6证据仍保留。

## 真实兼容运行 / 截图

正常DW登录，仅对有synthetic tag的FU50051/TASK50756执行UI Save。第一次未打开Batch Form，第二次通过Sub Table导航打开并挂载Batch Form后保存；两个实际PUT均成功，GET重读比较batch schema、binding、字段类型及min/max/maxlength完整保留，随后真实Preview/Batch Edit可用、pageerror0。脚本不注入画布错误、不拦截响应、不改真实业务FU，不将此正常保存当异常分支证明。

初次脚本在下拉动画完成前直接count得到0，增加等待真实选项可见后重新完整通过，没有固定sleep或force click。截图等待dialog-fade-enter-active消失，避免透明过渡泄露宿主身份；只截工作区，人工查看无姓名邮箱，字段与确认按钮可见。保留绝对路径 `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_batch-canvas-save.png`。绿色Save提示仅证明正常保存，不代表任何故障被假定成功。

## FU生命周期 / 安全 / 性能

旧FU无batchForms不进入新增校验；schema1正常保存不改格式，未知新schema显式拒绝符合设计。Save/Load有正常合成FU实际证据；Export/Import、Clone、Rollback、Admin两导入、Deploy格式/引用消费者未变，本批迁移实现N/A，不冒称这些路径已重测。新加校验不查询live运行实例，不回填旧数据。

无新增请求/缓存/循环/凭证/PII日志，原批量定义遍历保持不变。没有序号/业务写入补偿或任务动作。正常Save仅变更明确隔离的合成设计，未部署新的业务流程或改运行实例快照。

## 未验证 / 残余风险

真实浏览器“挂载画布getRule/getOption故障”与损坏schema载入未验证；目前通过actual composable注入失败的单测证明不persist，不能写运行PASS。`FormDesigner.setBatchDesignerRef` 的卸载缓存捕获失败路径尚未闭合，本批不声称所有批量草稿生命周期已保护，将继续处理；此前P4的完整A22/A38、启动隔离/hydration等门禁仍开放。既有全量TS错误不夹带整改。

## 最终门禁

独立4文件完整staged复审无新增Blocker/未接受Major；独立19项测试、cached diffcheck、脚本nodecheck通过，160条非空新增行四类敏感扫描0命中，文件名与拟提交消息无敏感值。reviewer独立目视正常保存工作区图，无宿主身份/PII，明确不把绿色Save提示当故障证据。本窄批允许CONDITIONAL提交，生产启用与完整P4仍不放行。TEMP `batch-canvas-save-{red,green,tests,build,typecheck,deploy,runtime}.log` 保留。
