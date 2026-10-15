# Code Review：新实例快照规范化摘要

## 结论 / 范围

CONDITIONAL。后续新捕获快照为runtime schema v2，增加definitionDigest；嵌入batchForm schema仍为1，未改变FU包格式或前端batch规则协议。仅增强批量快照消费边界的完整性检查，不宣称全实例所有普通写入口或完整P4已闭合。

## Findings 与逐条处置

- 已修：快照没有规范化摘要，改变catalogId后仍可投影。两条新增测试先真实失败（缺摘要/未抛异常），再实现capture生成与project校验。
- 已修（复审版本边界）：doubleValue舍入高精度小数为1/2，或v2只改版本为1而保留digest，可能跳过校验。补测试先FAIL，改精确BigDecimal版本比较，并拒绝v1带digest混合格式；缺摘要/坏格式/不一致/未知版本均409。
- 既有v1快照从未带摘要，保持原读取，不回填、不重写。没有快照的旧实例依然不获得批量能力。不得通过修改版本/删变量把新实例降级成旧实例。

## 实现与边界

SHA-256输入为除root definitionDigest外的完整冻结定义：嵌套对象key排序、数值BigDecimal去无意义零、数组顺序保留，含catalog/version及规则/PK/FK/权限。规范化不修改原Map，不落业务行。摘要不进入浏览器响应或Flowable；它是完整性checksum，不是签名/鉴权凭证，不能防有权改存储者删digest并降版本或重算摘要；原服务端元数据保护与任务权限仍必须执行。

校验入口为projectForForm，因此覆盖该投影及使用它的批量读/写/分配路径；不把它宣传成所有无表普通保存、同步任务的统一完整性门禁。没有服务端返回整份快照、没有新路由/环境变量/共享jar源码变更。每次投影增加整份冻结定义规范化/哈希成本，不逐行业务HTTP；未压测，不声称性能提升。

## 编译与测试

- 最终 `mvn -pl backend/user-portal -am package "-Dtest=SubTableBatch*Test,SubTableWriteIsolationTest,TaskApprovalCompletionSubTableGuardTest,SubTableBindingScopeGuardTest,MiSubTaskSubTableRowMergerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：190项0失败/错误/跳过，BUILD SUCCESS。
- 最终 `pwsh -NoProfile -File scripts/verify-batch-postgres.ps1`：PostgreSQL/JPA8项+H2保存链12项，共20项全绿。新增真实JSONB规范化/新持久化上下文读回/真实任务保存后摘要仍一致；只创建并清理专用临时容器，不动平台卷。
- 摘要单测覆盖nested key顺序、1/1.00、1000/1e3、null、数组顺序差异；Snapshot覆盖新v2、旧v1不回填、损坏与未知版本、混合降级及权限元数据变更。
- package后重建user-portal（--no-deps），最终镜像manifest list `sha256:8935886c0ad7e2132674c02d9bb21da52eacaebca8c2e6bc12337457a13387cd`。旧后端不能消费v2：产生v2实例后不可直接回退到仅识别v1的Portal；须保留兼容后端并禁用新增入口，不删快照/改版本回退。
- 未改前端产品，不重复前端build；全量类型检查383既有错误仍未闭合。完整MI恢复限流窗口后重跑234单测+6浏览器全部通过，输出 `OK — unit + screenshots`。

## 实际运行

新v2合成FU50034进程 `ca36b856-c892-11f1-a63d-c6848797bef7` / 任务 `ca4361cd-c892-11f1-a63d-c6848797bef7`：完整membership脚本PASS，两行序列分配、第二行必填失败零写/零分配、合法一次count2、保存/重开、伪造拒绝、删空全通过。只读SQL核对runtime schema=2、摘要长度64；引擎act_ru_variable同实例该runtime key数量0。旧v1合成任务 `d37b2afb-c871-11f1-a63d-c6848797bef7` 真实HTTP表单投影仍可用，SQL仍为schema1且无摘要。初次旧v1 probe从根目录node缺playwright模块，改在frontend目录后通过，非产品失败。

最后Complete脚本首次登录遭API rate limit exceeded，未禁用认证/限流；窗口恢复后同一任务重试PASS，真实过程COMPLETED、空集合未复活、响应无保留元数据。首次完整MI也在第5浏览器场景登录限流失败，按失败记录，恢复后完整重跑（不是unit-only）。截图仍存于 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-sequence-*.png`，均合成数据，保留不删除。

## 生命周期 / 未验证

DW/Admin/FU包消费者不接收实例runtime，未改保存/导入/Clone/Rollback格式，本批有依据N/A；完整往返门禁仍保留。v1信任策略为只读兼容，不宣称旧v1有摘要保护。未执行真实运行库损坏注入；坏快照拒绝为单测证据，不冒充故障演练。两用户隔离、Assign同工作区及完整实际分派链、Preview/New Request摘要、启动本地失败/取消失败隔离、hydration不可变来源、上限压力和所有普通写入口仍未闭合。增量敏感扫描、完整diff及最终运行结果纳入staged重审。
