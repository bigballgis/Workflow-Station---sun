# Code Review：批量保存审计吞吐

## 结论

PASS（仅审计吞吐窄批，独立staged重审无新增Blocker/未接受Major）。不改变业务保存/切片/权限/流程启动顺序。A25完整性能与P4仍未通过，A39/#1666仍延期。

## Findings 与处置

- Major：200×51列修改生成约9800个字段审计，原实现逐cell查最新记录；自有50069三次真实保存45415/59341/88050ms，默认30秒曾超时但服务端随后成功。查询次数测试修前3项中2红。
  - 处置：按process/table/row/field/changeType一次读取最新记录，SQL值全参数化、DISTINCT ON只返回每组最新一条，不加载全部历史。原timestamp相同未定义次序，现在以id降序确定选一条。
  - 独立审查提出普通Import无200行界限；按行身份每200个分片查询，401行3次查询，不退回逐字段。只新增查找的Map键，不改变现有in-batch去重或old/new比较。
- PostgreSQL only-null集合实测报varchar=bytea：混合null与非null最初可运行，全null集合会被Hibernate绑定为bytea；专用IS NULL查询处理全null，混合集合去掉null后使用原条件。不伪造占位身份。
- 查询优化后同一宽表5424/5498/5753ms，IDENTITY仍导致逐条insert；新批插测试4项中2红。
  - 处置：仅子表审计记录数>200时用同DataSource JdbcTemplate参数化batchUpdate，每批200；<=200保持原JPA saveAll。12个持久列逐项对应实体，ID由数据库生成且调用方不读取；普通FIELD_UPDATE未改。
  - 保持原REQUIRES_NEW审计事务、best-effort失败策略；不把它改成业务/引擎跨服务事务，也不以异步丢记录提速。
- 原catch异常正文对JDBC错误可能输出绑定字段值；日志负例修前1/1红，改为仅异常类型，保留技术process/table上下文，不输出原异常对象/SQL值。

## 验证

- 新lookup测试：200行×48列9600条审计只1次查询、去重不串行身份、空输入零I/O、401行3次有界查询；大批插保留全部记录。最终独立5项lookup+旧property15=20项通过，0失败/错误/跳过；关键源SHA与隔离副本一致。
- PostgreSQL/JPA：最终15项PG+16项H2共31项通过，包括实际native latest不同field/type分组、进程与表隔离、timestamp tie、混合null、only-null；401实际批插的old/new/actor/task/stage/type/时间/ID完整，重复保存不新增，后续chunk末行SQL错误让整个审计事务回滚（之前chunk也未留下）。
- 广集首次368项有1项失败：`ChangeHistoryStatusFieldFilterTest#approvalAuditUsesBpmnTaskFormWhenStageBindingMissing:124`。独立审查把隔离副本组件恢复HEAD后同单项仍1失败/0错误，且该test直接new未改SubmissionFilter；基线证据摘要TEMP/batch-audit-baseline-reviewer.log。没有修改该存量测试或产品逻辑。最终package显式排除此一既有method、保留该类其他tests；不能宣称全平台全绿。
- 最终部署前一次JDBC产物：image SHA256 cef8cca55849a1ca56bafd3c1ace3e6e20de33bf857eac915f7d8b7f7f6beed6 healthy。运行脚本恢复30秒请求超时，不再以120秒放宽测量。
- 同50069、同字段/数据/三次逐行改值并全部GET读回：2229/1940/1852ms；补普通未改值Save对照后批量1766/1763/1890ms、同数据普通Save1168/1342/1152ms。均不满足1秒底线；对照仅帮助定位固定链路成本，不构成豁免或P95承诺。
- manual50067三次1259/1236/1239ms，UUID50068（不同字段名）1550/1317/1269ms；每组200行读回正确，201请求400且整variables不变，Cancel无保存、pageErrors0。截图已重拍回表格可见状态，桌面/390窄屏页脚与内部横滚可用；宽表打开1689ms仍不合格，下一批继续处理UI挂载数。

最终日志净化产物package369项0失败/错误/跳过，BUILD SUCCESS（显式排除上文已证实的1个HEAD基线method，非全平台全绿）。镜像SHA256 583944e3d05cfe972f475779a6eea9c2d63bf92c1ace25a2804fdaf1211bbdc1 healthy；同wide再测2192/1848/1929ms，无业务改值普通Save1164/1121/1105ms，打开1180ms，数据/201拒绝/取消/pageErrors0保持。最终完整MI234单测+6浏览器场景通过，输出OK — unit + screenshots；独立staged复审认可窄批，不将1672当作已通过。

## 范围 / 影响链

仅Portal审计组件/Repository及回归测试、合成验证脚本。没有Entity/SQL迁移/共享jar/设计配置格式变化；FU Save/Load、Import/Clone/Rollback/Admin消费者N/A：序列化未改。所有FU仍由原Diff提供身份，查询键不按具体列名猜测。正常单行/Import也调用审计writer，所以加401分片兼容和原property测试，而非只测新批量UI。

安全SQL固定12列，值绑定；无动态SQL标识符，无新接口或授权改变。仅自有合成FU经正常API保存，没有业务库直接写入；未动用户FU/现网任务。独立294非空新增行四类敏感扫描零命中，diffcheck与脚本语法通过；完整staged复审无新增Blocker/未接受Major。

## 残余风险

性能显著改善不等于A25完成；API>1秒、宽表打开延迟、New Request工作区、完整多主体/生命周期/配置属性/紧急禁用仍需处理。独立HEAD基线红保留，A39仍延期，不宣布P4或生产放行。
