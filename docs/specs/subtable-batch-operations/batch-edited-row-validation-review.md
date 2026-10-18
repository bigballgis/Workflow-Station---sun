# Code Review：服务端批量 Edit 完整行校验

## 结论

PASS（修复后独立 staged 重审），无剩余本批 Blocker／未接受 Major。独立关键36项、最终package277项、真实PG/H2 29项及部署后完整MI234+6通过。仅限 A09/A19/A21 的完整行数据校验；不是 New Request 工作区、P3/P4 全部完成或生产放行。

## Findings 与逐条处置

### Blocker

- Save/Complete 的共享 PayloadGuard 仅验证 Edit 字段权限、身份与声明，没有按冻结字段元数据和批量表单规则校验完整行。伪造第二行数值类型可与第一行合法改动一起保存。
  - 反例：新增 10 项真实 PayloadGuard 测试，修复前 8 项失败；旧部署在自有 synthetic FU50067 第二行 `amount=INVALID-NUMBER` 返回成功，数据库确实保存该字符串。不是仅由源码推断。
  - 处置：新增纯 `SubTableBatchEditedRowGuard`，在共享写前边界复用已有字段类型、必填、长度、精度、日期及表单规则；校验合并后的完整行，不仅 patch 列。不重建行，不丢弃合法未知原列。

### Major（独立首轮审查）

- 只依赖客户端 EDIT 声明仍允许删标记后绕过基础类型校验。
  - 反例：新无标记测试 21 项中 3 项失败；首版部署同一真实请求省略 changes 返回200。
  - 处置：增强实例 `TaskPermissions` 根据 canonical 与基线的真实行差异调用同一元数据校验；无客户端标记也生效。普通 single edit 只遵守冻结元数据和原权限，不套用批量表单列白名单/额外规则；旧实例仍走原路径。合法普通字段、批量表单未展示的字段继续可写。

### Minor / Question

独立复核提出“ScopeGuard 仅递增系统行版本也会误判业务变化”的 Question，补测后确认为回归（6项中1项失败）。已改为比较冻结元数据字段实际值，不比较 `_wsRowVersion` 或未知原列；同一测试恢复通过，不改变原版本递增算法、不重验未改动历史值，也不放宽真正业务改值。

共享字段错误提示从“Batch added”改为“Batch”，没有改变 Add 的类型判据。新异常沿统一 BusinessException/ApiResponse 返回 HTTP400/code4003，不对任意异常吞错或默认为合法。无业务方向分歧。

## 范围 / 验收映射

A09：全行校验失败整批拒绝；A19：省略 marker 不绕过基础数据规则；A21：沿原 Save/Complete 写前边界，不另存批量数据。旧实例/无能力路径不变，未知合法原列保留，0/false 不当作空。

## 触发矩阵

implementation-autonomy、bugfix-verification、code-review、secure-coding-sast；真实 TaskForm 写边界、MI 保存/完成、元数据驱动与事务验证。无实体/SQL/共享 jar/配置包/可见布局变化，不引入新依赖。运行脚本只用正常授权 API 和白名单限定的只读 SQL。

## 编译与静态检查

Maven 在隔离副本执行，先完成声明 Edit 修复的 package 271 项，再对无标记修复执行全部 Batch 测试 254 项，均成功。版本戳处置后最终 package 277 项，失败/错误/跳过均0，BUILD SUCCESS；最终 fat jar 已复制并重建 Portal，不能以 mvn test 替代。

## 测试与运行证据

- 声明 Edit 红→绿：7 种非法值 + 第二行失败，数值字符串、整数溢出、布尔字符串、非法日期、min、未声明列长度、未改字段缺必填均拒绝；0/false 与合法未知原列通过，输入深等。
- 无标记红→绿：两套配置 PK 的真实 TaskForm/H2 写路径，非法类型写入数0；普通合法文本保存一次。元数据独立测试证明不需要 batchForm 才能普通编辑合法字段。
- PostgreSQL/JPA：一次性隔离容器，最新 13 项 IT + 16 项真实 H2 事务，共29项通过。新增两套 PK 的声明/无标记非法编辑拒绝，整个 JSONB 和实体乐观版本均不变；合法普通编辑版本只增1。测试脚本 finally 核验随机名称/标签后清理自己的临时容器；平台卷和测试截图未删。
- Runtime 双样本：自有新建 FU50067（manual PK）/50068（UUID PK），表/字段名不同。脚本 `verify-batch-edited-row-runtime.mjs` 最终产物验证声明/不声明批量的 Save 和 Complete 均 HTTP400/code4003、整份数据库变量深等及真实任务仍活动；最后合法数值0/2通过正常 Save 保存。首轮旧部署确实将 synthetic50067 写入非法值；后续通过合法正常 API 保存恢复，不直接改库或掩盖 RED。
- 最终产物的 PostgreSQL/H2 29项再次通过；最终完整 MI 234单测+6浏览器场景通过。Portal image SHA256 `3338eb0e54a5aed99b6bb991421334242eacacaf6883a0b3958020648f848f7a` healthy，启动失败计数0；这些结果不是首版二进制证据。

## Function Unit 通用性与生命周期

字段类型、范围、PK、binding/store 来自冻结契约；不猜列名。后端事务覆盖两个不同 PK 名，运行覆盖两套不同字段/PK FU。Save/Complete 共用边界；Export/Import/Clone/Rollback/Admin 两条导入 N/A：未改变设计序列化或消费者，只验证已有冻结数据。完整生命周期门禁不由本批替代。

## 增量敏感信息

独立staged395行非空增量四类敏感候选零命中，diff check和runtime脚本语法通过。只输出 synthetic 技术标识，不输出凭证/用户资料；SQL process ID 从授权任务返回后再按 UUID 格式验证，不直接采用用户任意 SQL。无业务库直接写入。

## 性能

最多200条 Edit 声明按既有 canonical 行查找，字段校验无 DB/HTTP。普通增强写只检查实际变化的行，未变化/旧实例不新增校验。标记 Edit 会在声明规则与基础权限边界复用同一纯元数据函数，不复制判据。容量/延迟仍须单独量测，不由测试时长推导生产结论。

## 残余风险 / 豁免

A39/#1666 仍 OPEN/critical。New Request 批量工作区、设计控件显示属性、容量、多主体及生命周期全矩阵继续处理。没有把全量旧实例的普通保存改造成批量规则引擎；仅增强实例基础数据不允许绕过冻结元数据。

## 范围外问题

未修改全局异常处理、旧单行表单规则、流程编排算法或平台旧类型诊断。
