# Code Review：批量画布卸载缓存与回调归属

## 结论

CONDITIONAL。最终独立6文件staged重审无新增Blocker/未接受Major，允许提交本窄批。正常UI保存/退出/重开已真实验证，故障卸载仅有实际composable及保存边界的测试证据，不宣称完整浏览器故障验收或P4完成。

## Findings / 逐条处置

- Major（已修）：原 `FormDesigner.setBatchDesignerRef` 卸载读取失败后吞错，后续可能用旧cache/saved保存。新状态按binding持有画布，卸载失败记录failed并显示原configNotReady，Save按钮和实际Save入口均阻断；Preview取批量定义也拒绝旧缓存。失败保持到显式状态reset/重开，不因简单重挂载悄悄清除。
- 红→绿：临时恢复“只报告、不标失败”的旧卸载策略，生命周期失败阻断和实际useFormSave集成两个用例失败（2失败/13通过），actualSave错误返回true。恢复失败标记后通过。此为逻辑回归证据，不冒充真实UI故障。
- 独立审查Major（已修）：初版只按form generation隔离，同代次旧ref回调null会清掉新实例。reviewer只读运行真实composable复现 `currentCanvasSurvives=false, collects=1, cacheField=current`。新增同form/binding替换回调反例，每次注册回调拥有独立owner Symbol，只有最新owner能清理；reset清owner使旧表单/同表单重开旧回调失效。独立重审结果 `currentCanvasSurvives=true, oldCleanupCollects=0, currentCleanupCollects=1`，该Major闭合。
- 正常未挂载画布仍缓存；绑定重排时按binding而非旧数组下标归属；当前数组仅为旧load/save接口的顺序投影。切换表单同步清理cache/visited/failed/owner，避免旧表单状态阻断新表单。普通subForms生命周期未修改。

## 范围 / 触发矩阵

FormDesigner少量接线并移走旧卸载处理，新 `useBatchDesignerDrafts` 和其测试、原actualSave测试的一项失败集成、正常UI脚本与本报告。新模块使用unknown/Record与显式返回类型，不新增依赖、配置格式、API、字段或环境变量。适用实现自主、修复验证、错误显式性、配置身份、前端截图/弹窗和代码审查；没有修改FU50007或任何现有业务实例。

## 构建 / 类型 / 单测

- 最终DW `pnpm -C frontend/developer-workstation run build`：20.90秒通过，原chunk/Sass提示未压制。
- vue-tsc仍128项既有错误，新helper/测试无新增；中间收窄Canvas接口产生的9项错误已补真实方法形状和边界检查，最终回到原128，不用any/忽略或降低strict掩盖。
- 最终5文件29项通过：draft生命周期5、actualSave11、hydration4、batch config5、membership4。独立reviewer同29项全绿，并独立复现/核对owner反例。
- 新helper物理函数长度在40行以内；FormDesigner净缩小，只迁出本次批量状态，不进行全文件重构。脚本nodecheck、diffcheck通过。

## 最终部署 / 真实UI兼容

只 `--no-deps` 重建DW前端，实际镜像 `sha256:52a13191d986f97c631c1d022c8a24fb0e008fd10be8e90a3bccc64bfc08c35b`，容器running。Portal/后端/共享工作区未修改，本窄批未重新运行完整MI；此前输入控件批的234+6完整MI证据保留，不冒充本次新执行。

`verify-batch-canvas-save.mjs` 在正常DW登录、明确synthetic tag保护下，对新合成FU50051/TASK50756与既有合成FU50034/TASK50719各跑完整脚本：未访问batch保存、挂载batch保存、退出后重开同表单保存，各3次实际PUT成功，GET投影比较schema/binding/字段类型/min/max/maxlength未丢，随后真实Preview批量工作区可用，pageerror0。只改这两份隔离合成设计，不发布新流程、不修改任务或目录。

初次退出后等待列表超时，未作为新产品故障或PASS；脚本补充等待“列表可见/既有离开确认框可见”，若出现确认框则走正常Save，绝不强行跳过或丢弃草稿。最终两组实际计数均为3次，不假称额外的导航保存已经发生。截图等真实dialog-fade完成后捕获，避免透明过渡包含宿主身份，无固定sleep/force click。

截图绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\`：保留 `2026-10-17_batch-canvas-save-50051.png`/`-narrow.png`、`2026-10-17_batch-canvas-save-50034.png`/`-narrow.png`。主审人工查看桌面/600窄屏，无姓名邮箱，字段/底部按钮可见。绿色Save提示只证明正常保存，不当故障路径证据。

## FU生命周期 / 安全 / 性能

form/binding与回调owner明确归属，不按字段名、表名或数组历史位置猜身份。Save/Load含两套不同binding/配置样本的正常实际证据。Export/Import、Clone、Rollback、Admin两导入、Deploy格式未变化，本批迁移实现N/A，不冒称完整生命周期已重测。旧无批量表单配置仍不自动新增批量定义或实例能力。

无新HTTP/SQL、逐行查询、凭证或用户日志；原缓存仅当前表单，切换即释放，无deep watch或无限重试。catch将异常变成阻断状态和可见反馈，不是静默成功；原状态未完整读取时不持久化旧值。复用既有三语configNotReady（提示重开），无需新i18n或帮助文章。

## 未验证 / 残余风险

真实浏览器画布卸载时getRule/getOption故障未验证；测试通过同一实际composable及Save边界证明failed阻断，不能报运行PASS。真实绑定增删中的全部组合、200行压力、完整A22/A38、启动失败持久隔离/hydration等P4门禁仍开放。既有全量TS错误不夹带整改。失败后的恢复入口是重开已保存设计，不声称可找回已无法读取的画布内容。

## 最终门禁

独立6文件完整staged重审确认原same-generation owner Major闭合，无新增Blocker/未接受Major；独立29项测试与实际composable反例修后输出均保留。cached diffcheck、脚本nodecheck通过，259条非空新增行四类敏感扫描0命中；文件名/拟提交消息无敏感值。reviewer独立查看50051桌面、50034窄屏截图，无PII或确认按钮遮挡，不把Saved提示当故障运行证据。报告只追加真实记录，产品代码未再改。TEMP `batch-unmount-{red,green-final,typecheck,build-final,deploy,runtime,runtime-existing}.log` 保留。
