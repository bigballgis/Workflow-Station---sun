# P1-1 拆分 platform-common god module — 设计方案

> platform-common = 110 类 / 17 包，被**所有**模块依赖（platform-cache/security/messaging + 4 业务服务），
> 改一个类→全部重编。全局**爆炸半径最大**的改动，故先出设计、评审后再动代码。

---

## 1. 现状：内部包依赖图（实测）

```
纯叶子（不依赖 platform-common 任何其它包）：
  enums            (6)   ← 谁都不依赖
  audit            (8)
  saga             (6)
  jdbc             (4)
  functionunit     (12)
  i18n             (3)
  health           (3)
  util             (4)
  constant/sql/version (3)

叶子之上：
  dto       (10)  → enums
  exception (11)  → dto, enums

耦合簇（互相依赖，最难拆）：
  config    (25)  → audit, resource, security
  resource  (7)   → config, dto, exception
  security  (5)   → config
  fk        (3)   → dto, jdbc
```

**观察**：`config ↔ resource ↔ security` 三者环状耦合（config→resource→config），是拆分的硬骨头；
`enums/dto/exception` 是干净的自含三角（互相只单向依赖、不碰其它包）。

---

## 2. 目标模块划分

| 新模块 | 收纳包 | 类数 | 依赖 | 说明 |
|---|---|---|---|---|
| **platform-core** | enums, dto, exception, constant, version | ~28 | 无 | 最稳定、人人需要的基础类型；改动频率最低 |
| **platform-audit** | audit | 8 | core | 审计框架 |
| **platform-jdbc** | jdbc, fk, sql | ~8 | core | JdbcTemplate/JSONB/FK helper |
| **platform-saga** | saga | 6 | core | Saga 工具（实际用得少，隔离避免误依赖） |
| **platform-functionunit** | functionunit | 12 | core | FunctionUnit 元数据 |
| **platform-common**（瘦身后保留） | config, resource, security, i18n, health, util | ~47 | core, audit | 耦合簇暂留一起（config↔resource↔security 环需先解环才能再拆） |

**爆炸半径收益**：改 audit/saga/functionunit/jdbc 不再触发只用 core 的消费者重编；
core 本身改动频率最低，等于把"高频改动"与"广依赖"解耦。

---

## 3. 包名取舍（决定 diff 规模，关键）

两条路：

| | A. 保留包名 `com.platform.common.*` | B. 改包名 `com.platform.core.*` 等 |
|---|---|---|
| 消费者 import 改动 | **零**（类还在原包，只换 jar 来源） | **全量重写**（所有 import 该包的文件） |
| diff 规模 | 小 | 巨大、跨全仓 |
| 隐患 | **split-package**：同一包 `com.platform.common.dto` 跨两个 jar，破坏 JPMS、IDE/工具告警；Maven 编译期可工作但属反模式 | 干净、无 split-package |
| 可逆性 | 高 | 低 |

**建议：A（保留包名）**。理由：本仓库无 JPMS（无 module-info），split-package 在传统 classpath 下编译/运行正常；
换来零 import 改动、最小 diff、最低回归。代价是工具告警，可接受。
（若日后上 JPMS 再走 B 彻底改名。）

---

## 4. 拆分顺序（每步独立可编译、可回滚）

1. **建 platform-core**，移入 enums/dto/exception/constant/version（保留包名）。
   platform-common 依赖 platform-core。**全 reactor 编译验证**。
2. **建 platform-audit**（移 audit）→ platform-common 依赖它。编译验证。
3. **建 platform-jdbc**（移 jdbc/fk/sql）。编译验证。
4. **建 platform-saga / platform-functionunit**（各自独立叶子）。编译验证。
5. config↔resource↔security 环：**本轮不拆**，留在瘦身后的 platform-common。
   解环是独立子任务（要先打破 config→resource→config 循环）。

每步：移动文件 + 新模块 pom + 根 pom 加 module + platform-common 加依赖 + `mvn -am compile` 全量验证 + 跑测试基线对比。

---

## 5. 风险与回滚

| 风险 | 缓解 |
|---|---|
| split-package 工具告警/未来 JPMS 不兼容 | 记录为已知取舍；上 JPMS 时走方案 B 改名 |
| 移动后某消费者依赖断裂 | 每步全 reactor `mvn -am compile` + 测试基线对比；断裂即回滚该步 |
| 新模块间出现循环依赖 | 严格按 §1 依赖图自底向上拆；core 永不依赖他人 |
| 构建需 `-Dspring-security.version` | 沿用现有构建参数 |
| 测试遗留基线（admin 590/36F/24E 等） | 拆分零行为变更，失败数应与基线一致，不一致才是回归 |

**回滚**：每步是一次 commit；任一步出问题 `git revert` 即可。不删任何类，只移动 + 加模块。

---

## 6. 工作量与建议

- 本轮已完成 P1-3/P1-4/P1-2/P2-2 + P2-1 首切；P0-4 与 P2-1 全量已记待办。
- P1-1 即便走最稳的方案 A，也涉及**移动 ~60 类 + 6 个新模块 pom + 反复全量编译**，是独立一轮的体量。
- **建议**：先按本文档评审确认（尤其包名取舍 A/B），再单独一轮执行第 1 步（platform-core）作为试点，
  跑通全量编译 + 测试基线后再继续 2~4 步。

---

## 7. 一句话总结

> platform-common 的拆分按"自底向上、保留包名、逐模块验证"做：先抽最稳的 platform-core（enums/dto/exception），
> 再剥 audit/jdbc/saga/functionunit 等叶子；config↔resource↔security 环留待解环后再拆。
> 保留包名换零 import 改动与最小回归，代价是 split-package（无 JPMS 下可接受）。
