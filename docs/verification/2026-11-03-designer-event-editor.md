# Form Design / Batch Form 事件编辑器优化

## 修复验证

- 复现：FU50125 → Transaction Request Form - Transaction Assignment → HMDC Transaction → Batch Form → Assignee。旧版首次显示10项，X关闭后1项，重新选中恢复10项。实际只有change回填脚本；其余是设计器补出的空模板。
- 根因：空模板参与列表和角标计数，关闭watcher又应用草稿并清理空模板。组件重选再次补齐。X与页脚Cancel语义不一致；OK还会被关闭watcher重复应用。
- 红证据：新增eventEditors.test.ts在改动前5项中4项失败，覆盖空模板计数、X丢弃、OK仅应用一次、Form event空包装函数计数；Cancel原本通过。日志为本地`.codex-event-editors-red.log`。
- 绿证据：同一组5项全部通过；事件序列化7项、默认事件18项，共30项通过。真实页面在原FU50125和新克隆FU50135两份设计上通过下述矩阵。
- 未验证项：全量测试与类型检查存在已核对的基线失败；未重跑Portal运行时、MI全流程或完整FU导入导出/回滚，本次只改DW编辑器与帮助文案，不改运行时或配置格式。

## 最终行为

- 组件Event仅加载非空脚本；角标统计实际处理函数数量，支持数组及Hook，不计算空模板。
- 添加事件菜单继续提供组件事件、blur/focus、七个Hook及Custom。空的新建项显示在New event drafts；保存函数后进入Configured scripts。
- Form event将Configured scripts和Unconfigured events分组，角标与绿点只统计有内容的函数；空包装函数不再计数或重复持久化。
- 函数Save只更新弹窗草稿，底部OK才应用到设计器；X、Esc、页脚Cancel都丢弃整份弹窗草稿。仍须保存外层表单才会持久化。
- 添加新事件前先保留当前编辑器内容到弹窗草稿，避免切换丢字。
- 三语文案、现有`/help/form-events`及llms索引同改，保留原有红色问号入口。

## 真实页面矩阵

执行：`pnpm -C frontend exec node scripts/verify-designer-event-editor.mjs`。
复用专用测试副本：设置`EVENT_EDITOR_FIXTURE_ID=50135`后执行同一命令。脚本会校验副本名称前缀；默认从50125新建克隆，不向50125提交保存。

原FU50125、克隆FU50135，分别进入Form Design和Batch Form，四组组合均检查：

1. 空模板不进入已配置列表；普通Assignee初始0，Batch Assignee初始1且仅change。
2. 新增Hook并在弹窗内Save后，X / Cancel / Esc分别丢弃；再次打开列表和角标均恢复。
3. 点击另一个组件后重选Assignee，角标保持稳定。
4. Form event的Configured scripts数量等于角标，空模板在Unconfigured events分组；新增函数并Save后X关闭不应用。

克隆副本额外检查：

1. 新增Hook → OK，角标+1，重开可见。
2. 删除Hook → OK，角标恢复，重开已删除；重新添加后，删除 → Cancel则保留。
3. Batch change未点函数Save就添加另一个事件，原编辑内容仍保留在弹窗草稿；最终保存后重新读取配置可见标记。
4. Form event新增函数 → OK，进入已配置分组且角标+1。
5. 外层表单真实PUT保存，随后GET重新读取，subForms/batchForms均保留组件Hook和Form event函数。
6. 最后重新读取原FU50125的configJson，与运行前逐值一致；浏览器pageerror为0。

最终运行日志：本地`.codex-event-editor-ui-final.log`及`.codex-event-editor-ui-final-rerun.log`；首轮新副本50135、form50926。

截图（均在`frontend/developer-workstation/verification-screenshots/`）：

- `2026-11-03-event-editor-50125-batch-form.png`：原问题组件仅列出change。
- `2026-11-03-event-editor-50125-form-design.png`：空组件无已配置脚本。
- `2026-11-03-event-editor-new-event-draft.png`：新增空事件草稿分组。
- `2026-11-03-event-editor-50135-form-design-form-events.png`：普通Form配置与空事件分组。
- `2026-11-03-event-editor-50135-batch-form-form-events.png`：Batch Form配置与空事件分组。

## Code Review 结果

### 结论

CONDITIONAL：本次功能专项与真实页面通过；全量测试及类型检查保留基线错误，不能报告全项目PASS。范围为本轮相对实现前HEAD的完整diff与新增测试/脚本。

### Findings

- Blocker：无。
- Major（实现中自审，已修）：HermesEventConfig.add直接切换activeData会丢掉当前编辑器尚未Save的内容。反例是编辑change后直接添加Hook，event.change仍是旧字符串。最小修复是在add开头调用commitActiveEditor，校验失败则保持原编辑器；此步骤只写弹窗草稿。依据implementation-autonomy的业务正确性与不丢数据要求。真实Batch页面最终保存后重新GET，确认`current-editor-draft`标记仍在，验证缺口已补。
- Minor：无。
- Question：无。
- 处置与重审：上述Major已按最小修复处理；重新检查完整diff，未发现新增未处置正确性问题。全局基线使总体结论继续为CONDITIONAL。

### 范围 / 验收映射

组件列表、添加入口、角标、草稿关闭、Form event分组、三语文案、帮助页、序列化及UI回归脚本均对应本轮需求。无后端或数据库结构改动，无FU/字段名控制产品行为。

### 触发矩阵

UI / i18n / 弹窗草稿 / 配置Save-Load / 截图 / 增量敏感信息检查命中。MI运行时、SQL、鉴权、部署配置变更N/A。Help复用既有文章与问号，不新增文章。

### 编译与静态检查

- DW `pnpm run build`：PASS，最终20.88秒；Help同命令PASS。
- 两个frontend镜像均重建并拉起；最终DW/Help使用`up -d --no-deps --build`，运行日志为正常静态资源请求，无启动失败。
- `git diff --check`：PASS。
- DW `pnpm run typecheck`：FAIL，164条；与上一轮`.codex-batch-typecheck.log`按文件/诊断正文去掉行号对比，没有新增诊断。本次编辑器/序列化/新增测试无类型报错。

### 测试与运行证据

- 专项：`pnpm exec vitest run src/components/designer/__tests__/eventEditors.test.ts src/composables/hermesEventConfig/__tests__/eventSerialization.test.ts src/utils/__tests__/formCreateDefaultEvents.test.ts` → 3文件30项PASS。
- DW全量`pnpm test` → 193文件通过；1265项通过、1项失败，另1个测试文件加载失败。失败位置为decision-i18n.test.ts及i18n-locale-key-consistency.test.ts。
- 基线核对：对HEAD三份locale直接运行这两个测试使用的对象提取/new Function解析方法，均复现`assignmentExclusion is not defined`。没有为本次修改放宽测试或修改原解析器。
- 真实UI脚本 → PASS；不以构建或HTTP状态码代替行为和返回配置断言。截图已查看。

### Function Unit通用性与生命周期

- 通用性：PASS，原FU50125+本轮新建克隆50135；产品逻辑只读组件事件声明和规则，无FU/表/字段白名单。
- Save/Load：真实PUT+GET检查，普通子表和Batch配置均通过；已有change脚本保留。
- Export/Import、Clone配置格式、Version/Rollback、Admin两条Import、Deploy/Activate：消费者代码N/A，本次未增加配置键或改变非空脚本格式；克隆接口实际用于创建新样本，但未声称完成全套往返验证。
- Portal runtime/parity：无运行时变更，未重跑；序列化仍用原有非空$FNX/$GLOBAL格式，数组和编译函数保留有专项证据。

### 增量敏感信息

新增行和新增源文件/脚本扫描私钥、云访问密钥及凭证赋值模式，结果PASS；只有localhost测试地址和合成标记，无新增凭证。

### 性能

仅活动组件及打开弹窗内的少量事件遍历；无新增请求、全量FU遍历、缓存或监听器。N/A：无性能目标或运行时热路径变更。

### 残余风险 / 豁免

全量测试及类型检查基线未修，保持原有门禁状态。未执行的Portal/MI/FU完整生命周期测试及原因见上文。

### 范围外问题

两处国际化测试的locale解析方式不能处理现有import变量，及164条已有类型诊断；本轮不扩大修改范围。

## 提交前复审

用户要求code review后无问题则commit。重新审查全部产品代码、测试、三语文案、帮助页及本报告，并检查最终暂存范围包含新增回归测试和UI脚本。

- 新增Blocker / Major / Minor / Question：均无。实现阶段发现的草稿切换问题已修，处置与证据见上文。
- 重新执行30项专项：全部通过；重新执行DW构建：通过。抽查既有最终UI日志：四组合、真实保存读取、原FU未改、浏览器无运行错误均通过。
- 最终暂存diff卫生与增量敏感信息检查通过，无无关文件或生成产物。
- 本轮改动无阻碍提交的问题，按用户本轮指令提交；全项目总体结论仍为CONDITIONAL，保留已核实的全量测试及类型检查基线，不声称全项目门禁全绿。
