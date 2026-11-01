# Code Review：节点表单主表身份传递

## 结论

相对80b9c24b9的本次增量 PASS，无剩余Blocker或未接受Major；全仓门禁仍CONDITIONAL，保留已确认的测试及类型诊断基线。本报告由当前代理只读复核，不冒称独立第二reviewer。

## Findings

### Blocker

无。

### Major

已解决并重审：useApplicationDetailNodeFormMap未携带主表主键元数据，导致点击已完成节点时声明FK的子表被投影为空。反例为原请求Case note=3已保存、原节点No Data。最终只补primaryKeyFields/fieldDefinitions并扩充对应状态类型，现有安全过滤不变。2项测试先红后绿、原单同路径截图转绿，普通和显式重叠样本均符合声明。

### Minor

无剩余问题。注释同步说明主表身份还用于FK投影，没有引入第二份投影规则或业务名兜底。

### Question

Batch最新1.0.2已开启，本轮未改配置。旧1.0.0 Input配置错误和已结束请求只读需要解释，不能通过绕过版本冻结、权限或强开全部控件解决。

## 范围 / 验收映射

原请求Case数据可见、交易行不混入；显式额外FK仍可合法重叠；新请求Batch人员分派经VERIFIED到引擎结束，重开Case节点仍可见。原用户单仅只读，无完成、Claim或行编辑。

## 触发矩阵（命中项）

Portal可见bugfix、FK/PK配置传递、My Request节点只读路径、MI热路径、测试和增量隐私检查。按bugfix-verification、config-over-heuristics、verify-ui-fix-with-screenshot及code-review门禁复核。无API/schema/SQL/i18n/依赖或权限规则变化。

## 编译与静态检查

Portal build及Compose重建PASS；最终staged diff和空白检查通过。类型381→381，排除源码行号平移后无新增诊断，未改strict或抑制诊断。

三个受影响文件定向ESLint：新测试无诊断，存量两个模块37条no-explicit-any；对HEAD和最终源码分别重跑、逐条按源码文本核对一致，无新增。此门禁保留基线失败，不冒称lint通过。

## 测试与运行证据

新增2项先失败后通过；现有投影组连跑22通过。全量2611→2613通过，剩余同1基线失败。完整MI250单测+6浏览器场景PASS。3份既有请求只读检查、新请求从Draft/Batch到结束及重开验证通过，截图和命令见同名验收报告。

## Function Unit 通用性与生命周期

通用性PASS：运行时新增行只读发布元数据，无MCY、表名、字段名或ID判断。测试使用不同业务名；已存在冻结1.0.2和新请求均实测，既有FU50029通过MI。

Save/Load及Portal runtime/parity：PASS；旧任务Batch保持旧版本保护。Clone样本正常，未重新执行Clone操作；Export/Import/Version Rollback/Admin两条Import未重跑，未引入任何新设计字段或消费格式。没有宣称全生命周期重验。

## 增量敏感信息

全部staged新增行及文件清单扫描，未包含凭证、Cookie、JWT、私钥；会话、临时脚本、日志、构建产物与截图不提交。报告中的用户输入仅保留定位所需的测试note及实例ID。

## 性能

只传递既有元数据引用，无新增DB/HTTP查询、全表扫描、缓存或额外逐行处理。不作性能优化声明，完整MI门禁通过。

## 残余风险 / 豁免

既有前端全量测试1失败、381类型诊断和定向ESLint37条基线仍在，按用户此前“无新增问题即可提交”的授权执行。没有独立双账号、并发、远程版本或全部生命周期验证。默认Sub Case没有Case控件仍按设计不显示，需通过Case Submission查看；没有擅自重排表单。

## 范围外问题

旧冻结Batch配置和既有全仓基线问题不在本次改动中修补。FU48没有被修改。
