# Code Review：个人初次分派的实时资格

## 结论 / 范围

CONDITIONAL。本批只闭合个人目标的存在、ACTIVE与未删除检查，分别用于增强实例实际分派变化保存和拟生成MI集合的Complete。不是完整BU/Role资格、跨服务竞态或P4完成；旧无快照保存/普通分派路径不变。

## Findings / 处置

- 新 `SubTableBatchUserEligibility`：对去重后的真实用户ID做一次参数化目录查询，只读id，要求 `status='ACTIVE' AND deleted=false`；结果不完整拒绝。没有默认人、全字段用户对象、口令读取、资格缓存、逐行查询或吞错。目录故障保留DataAccessException，不当作合法空结果。
- 保存接线位于 `TaskFormComponent` 既有写事务、scope和阶段校验之后、业务写入之前；`changedPersonalTargets`根据服务器旧canonical与最终入站行的冻结映射及稳定身份算差异，不依赖客户端batch标记。普通字段变化和省略store不新增目录查询。
- Complete接线在严格collection所有行构造完成后、variables.put之前；检查该集合全部个人目标，包括已经自动保存、没有本次UI批次标记的值。角色行不转为个人、不为无个人集合发起目录查询。
- Major（已修）：只比较person字符串会漏掉“清旧role/BU后激活同一个个人值”的模式生效变化。现在该行任一冻结分派字段发生delta、最终person非空均检查资格。纯差异测试覆盖该反例；不能把它写成真实运行模式切换证据。
- 新checker5项在空实现上先RED4失败0错误；实现后通过。既有Save正例最初因旧夹具无目录而报错，只为该测试补真实H2用户目录，保留原业务行断言；未放宽产品检查。
- 现有严格collection测试改用独立H2目录，验证可用人员与角色路径；只有目录查询，不读live DW。新增保存后个人停用→Complete拒绝且collection不publish；新增无batch标记Save停用目标→零持久化写入、完整canonical不变。

## 编译 / 测试 / 独立复审

- 同源隔离副本 `mvn -pl backend/user-portal -am package`，所选Batch/MiCollection/TaskProcess/Scope/Isolation/Complete/Client共454项，失败/错误/跳过0。
- 独立reviewer实际重跑UserEligibility5、Differences8、Persistence4、MiCollection16，共33项0失败/错误/跳过，核对6份产品/事务测试源码SHA256一致。模式生效finding已闭合，暂无新增Blocker/Major，待最终staged签核。
- H2真实SQL覆盖ACTIVE成功、INACTIVE/已删除/不存在/SQL形状ID拒绝、同人重复一次批查、保存后状态变化复核、目录表不可用异常、无个人零查询；未在真实用户上做停用试验。
- 未更改前端产品TS/Vue，无新增前端build产物；原383项全量类型错误仍未关闭，不报告typecheck PASS。

## 部署与真实运行

- Portal jar SHA256 `C238F6B916ACAC6BA1D5B5A929D0146C6E1BF898B6EF5A8AADA91B89F2893C52`，镜像 `sha256:6803c8aabeceef6bc1ebf0831ca101e76a28c98abf763030bd8ab8fb9c74f8e9`；重建healthy，启动失败/无效字节码检测0。
- 新合成FU50036实例 `15714af8-c932-11f1-8f36-4ee612046d7e`，Review `1581c606-c932-11f1-8f36-4ee612046d7e`：`verify-batch-person-eligibility-runtime.mjs`全链通过。虚构个人两行无batch marker Save403/Complete409均拒绝，GET完整tables不变，宿主仍Review。
- 随后缺目标Complete仍拒绝；真实目录选人工作区ASSIGN/Save不增加任务；正常Save→Complete生成2项真实AssignedWork `3e54f936-c932-11f1-8f36-4ee612046d7e` / `3e5435e2-c932-11f1-8f36-4ee612046d7e`，办理人、两份身份、UI各1行、生成后Assign关闭及伪造修改拒绝全部通过，pageErrors0。没有目录写入、停用/删除用户、改派、重建或取消任务。
- 本轮截图使用独立tag，不覆盖前批child证据；workspace脚本增加可选安全tag，缺省原行为保持。已目视脱敏错误态工作区 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_person-eligibility-workspace-only.png`，同前缀only-narrow及child-1/child-2保留；workspace图是故意清空目标的错误态，不用它冒充保存成功截图。
- 新镜像完整 `pnpm -C frontend run regression:mi`：29文件234单测及6浏览器场景通过，最终 `OK — unit + screenshots`，未用unit-only替代。

## 通用性 / 安全 / 性能 / 未完成项

- 判据是冻结分派字段、服务器canonical身份和平台固定用户目录schema，不按业务FU/列名猜测；纯个人新增校验最多一次去重SQL。未压测200行/大集合，不宣称全性能门禁通过。
- 字段只读、行scope、任务阶段仍由既有服务端校验处理；此批不增加个人业务BU限制，也不把目录可见性等同操作授权。集合构造后的资格检查不会提前执行引擎任务动作。
- FU包、Entity/schema、共享jar、API请求/响应、配置项与部署路由本批不变；Save/Load、Export/Import、Clone、Rollback、两条Admin导入的包格式N/A。整体真实生命周期往返门禁仍开放。
- BU/Role存在/准入/当前池成员、无buField时权威BU来源、引擎单人角色池及失败结果、跨服务TOCTOU、父scope支持限制、Preview/New Request、启动失败隔离等仍未完成。当前资格在保存和Complete时重新读取，不承诺在引擎创建前后永远不变。
- 最终staged13文件完整复审无新增Blocker/未接受Major，个人资格窄批CONDITIONAL；255条新增非空行四类增量secret0命中，cached whitespace及3脚本语法检查通过。
