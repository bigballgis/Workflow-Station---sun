# 架构决策记录（ADR）— Activepieces 集成

> **全局约束来源**。本文件是 Q/D 系列裁决的**唯一事实来源**，其余文档只做引用。
> 推翻任何一条须回到本文件修订并重新评审。日期标注为拍板日。
>
> ⚠️ **读路径时注意**：Q1–D13 是 0.84 时期的裁决，文中大量写 `activepieces/`（仓库根的
> 0.84.0 硬分叉树）。**该目录已于 2026-08-14 删除**——0.88 重构（`automation/`）交付验证后，
> 它作为参考实现的使命结束。**这些条目是历史记录，路径按拍板当时的事实原样保留**；
> 除非另有说明，凡 `activepieces/<path>` 请读作今天的 `automation/<path>`
> （少数目录已挪位，最常见的是 `packages/shared` → `packages/core/shared`）。
> 要看当年的源码走 git 历史：`git show de4f6469:activepieces/<path>`（0.84 vendor 基线）
> 或 `git show 4635f7950:activepieces/<path>`（删除前最后一个提交）。
> 0.88 的现行需求/实施真源是 [REQUIREMENTS_0.88.md](REQUIREMENTS_0.88.md) 与
> [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md)。

---

## 0. 项目性质与不可变约束（Context）

这些是所有决策的前提，**不由架构讨论产生，也不可被架构讨论推翻**：

| # | 约束 |
|---|---|
| X-1 | HERMES 是**公司内部系统** |
| X-2 | Activepieces 0.84.0 集成**只有一次正式集成机会** |
| X-3 | 集成完成后**生产环境与外界完全断开**（air-gapped）：不依赖公网、不接受在线拉取或动态安装 |
| X-4 | **公司明令禁止 Bun**（构建/CI/运行时/锁文件全面禁止） |
| X-5 | HERMES 前端栈是 **Vue 3 + TypeScript**，不是 React |
| X-6 | AP 原始 Web Builder 是 React ⇒ **Builder 不得以"React SPA 直接并入 HERMES"的方式处理** |
| X-7 | 目标**不是持续跟随上游**，而是把 AP 0.84.0 的目标能力稳定纳入 HERMES，在封闭环境长期自主维护 |

---

## 1. 决策索引

| ID | 主题 | 裁决 | 日期 |
|---|---|---|---|
| [Q1](#q1) | Vendor 边界与位置 | 全量 vendor（D1 升级为 frozen vendor + controlled fork，**经 D12 再改为硬分叉 + 深度裁剪**；**位置经 D5 修订为仓库根 `activepieces/`**） | 07-22 |
| [Q2](#q2) | Bun 策略 | 全面禁止，AP 子树整体迁 pnpm + Node | 07-22 |
| [Q3](#q3) | 画布裁剪深度 | 抽"纯 builder 组件"（否决胖挂载） | 07-22 |
| [Q4](#q4) | 身份/租户 | 废除共享账号承载人的会话，per-user 映射 | 07-22 |
| [Q4a](#q4a) | 共享 project 放行方式 | **CE core 层 RBAC 小补丁**（两处切点） | 07-22 |
| [Q5](#q5) | 数据库 | 独立 schema `activepieces`（同实例） | 07-22 |
| [Q6](#q6) | 网关与桥的终局 | Kong 收编，:8085 桥并行一版后退役 | 07-22 |
| [Q7](#q7) | flowId 治理 | flow 注册表 + **部署期解析**（✅ 已实施，见正文） | 07-22 / 07-26 |
| [Q8](#q8) | AP 版本演进 | D1 重定义为 Frozen Baseline + Controlled Fork，**已由 [D12](#d12) 取代（2026-07-30）** | 07-22 |
| [Q9](#q9) | approval/todos piece | 从 v1 白名单移除 | 07-22 |
| [D1](#d1) | EE 发现对 Q1/Q8 成本模型的冲击 | 承认性质变化：**受控 fork**，Q1/Q8 保留但重定义（**"受控"部分经 [D12](#d12) 作废**，成本重估结论仍有效） | 07-23 |
| [D2](#d2) | AG-02 的正确目标 | **不保留 AP 独立 workspace**，裁剪 AP shared 并入 HERMES frontend；升为最高优先级 Gate | 07-23 |
| [D3](#d3) | 是否需要 EE 剥离专项文档 | **需要** — 新增 Document 3.5 | 07-23 |
| [D4](#d4) | 合规评估（R-B）状态 | **尚未启动** | 07-23 |
| [D5](#d5) | **AP 代码树位置与 workspace 归属**（Q1 位置部分的修订） | AP 子树 → 仓库根 `activepieces/`（自有 workspace）；前端只留 `ap-contracts` 裁剪层 | 07-23 |
| [D6](#d6) | **沙箱基线降级**（安全策略不允许提权） | 候选基线 → **`SANDBOX_CODE_ONLY` + `STRICT`**（降级阶梯第 3 级）；**内核级出网管控丧失，须由 NetworkPolicy + 桥加固补偿** | 07-23 |

| [D11](#d11) | **signing-key 是否自动供给** | **不自动化** — 只读检测 + fail-loud；私钥只返回一次、写 Secret 需 RBAC、重跑会轮换活密钥；且 k8s 尚未接 `ACTIVEPIECES_MANAGED_*` | 07-29 |
| [D12](#d12) | **controlled fork 是否还成立** | **不成立，终止** → **硬分叉 + 深度裁剪**（取代 Q8、修订 Q1/D1）。VT-11 证明内网 FOSS Guard 隔离的正是上游 pin 的精确版本，"冻结基线"做不到；且 rebase 在 X-2/X-3 下不会发生。删功能面 = 消依赖，是 VT-12 的解法 | 07-30 |
| [D13](#d13) | **D12 说了但没做的那部分** | **执行令**，非新方向：D12 的作废项逐条落地（删可重放脚本 / 删死配置 / 停止给新改动挂台账编号），并把同一原则**延伸到代码层**——不为上游契约保留 schema、默认值或分支。另**修订 D12 保留项 #1**：`activepieces/LICENSE` 已删，合规结论移交 [D4](#d4) | 08-07 |

---

> **D7** — HERMES 侧命名改为 ServiceTask（含明确不改清单）｜**D8** — admin-center 接受 DW 平台 JWT cookie
> ｜**D10** — sync webhook 终态释放

## 2. 决策正文

### <a id="d1"></a>D1 — EE 发现动摇 Q1/Q8 成本模型（2026-07-23）

**触发**：DEPENDENCY_MAP §2.2.4 实测——CE 有 105 处 import `app/ee/**`（37 文件），排除 ee 即
**编译期失败**；13 条 EE-4 硬依赖打在主干（每个 project 级 HTTP 请求、每次读连接、每次入队 job、
`/v1/projects` 唯一实现）；重实现工作量约 1000–1300 行。

**结论：是，成本模型必须重估。但 Q8 方向不因此变错——变的是项目的"性质定义"。**

原决策模型：
```
Vendor Activepieces → 少量适配 → 长期维护
```
实际模型：
```
Vendor AP 0.84.0 → EE/Core 耦合处理 → Bun→pnpm 迁移 → React Builder→Vue 宿主适配
→ ~1000–1300 行重实现 → 安全/离线改造 → 长期自主维护
```

⇒ **我们面对的不是简单 vendor，而是一个基于 AP 0.84.0 的受控 fork / internal derivative。**
这一点必须在所有文档中明确表述。

**Q1 重新表述**：仍成立，但不得再理解为"复制进来以后基本不动"，而是
> **Vendor 0.84.0 作为 frozen upstream baseline，在其上建立 HERMES 专属维护分支。**

随之而来的强制要求：保留 0.84.0 原始基线；建立明确的 HERMES patch 层；所有修改可追踪；
禁止无记录修改 AP 核心代码；建立 baseline → fork 的差异管理机制；生产不依赖 AP 官方在线服务；
运行时禁止访问公网 registry、禁止动态安装 piece/npm package；禁止 Bun；禁止依赖 EE 商业代码；禁止依赖 AP Cloud。

**Q8 重新表述**（见 [Q8](#q8)）。

**D1 决策表**：

| 项 | 决策 |
|---|---|
| AP 基线 | 固定 **0.84.0** |
| 持续跟随上游 | **否** |
| 保留 upstream baseline | **是** |
| 允许无记录修改 | **否** |
| 建立 HERMES patch 层 | **是** |
| 接受 1000–1300 行重实现 | **需要** |
| 继续做 EE 剥离 | **需要** |
| 追求未来 AP 升级兼容 | **非目标** |
| 保留未来升级可能性 | 保留，但**不作为架构约束** |
| 生产依赖公网 | **否** |
| 运行时安装依赖 | **禁止** |
| Bun | **禁止** |

---

### <a id="d2"></a>D2 — AG-02 重定义并升为最高优先级 Gate（2026-07-23）

**旧假设（作废）**：AP `shared` 继续作为独立 workspace package，经 `workspace:*` 被 HERMES frontend 消费。

**作废理由**：HERMES 前端是 Vue 3；已有自己的 frontend workspace；公司禁 Bun；AP 原始
workspace/build 链路不能作为 HERMES 前端基础设施；只有一次集成机会；集成后完全离线；
长期维护必须降低 workspace/package/runtime 复杂度。

**新目标**：
> **将 Activepieces 必要的 shared 类型契约能力裁剪后并入 HERMES frontend workspace，
> 而不是保留 AP 独立 workspace。**

目标形态：
```
HERMES Frontend Workspace（Vue3 / TS / Pinia / Element Plus / Vite / pnpm）
├── HERMES Shared
└── Activepieces Integration（HERMES 拥有）
      ├── Flow Schema
      ├── TypeBox Contracts
      ├── 必要类型
      └── 必要运行时契约
```
**而不是** `HERMES frontend → AP 独立 React workspace → AP shared → Bun/AP 原始构建体系`。

**AG-02 须验证**：
1. ~~`@sinclair/typebox` 版本统一~~ → **AG-02 实测推翻：AP shared 用 zod 4.3.6，typebox 零使用；HERMES 无 zod ⇒ 无冲突**；
2. AP shared 中**哪些是真正必要的**——
   Required：Flow schema / Step schema / Trigger schema / Action schema / Piece metadata contracts / API DTO types；
   Not Required：AP 前端 runtime / React 依赖 / AP Router / AP Query 基础设施 / AP 构建工具链；
3. 能否**完全脱离 AP workspace**（`import from HERMES-owned AP shared layer`，而非 `workspace:* → AP shared`）；
4. pnpm 能否成为唯一包管理与构建工具（NO BUN：install / runtime / build / lockfile 全无）。

**优先级**：**提升为 Document 4 之前的最高优先级 Gate**。未通过前不得冻结：
frontend integration layer、builder packaging strategy、frontend dependency architecture；
不得把 `workspace:*` 当作最终架构；不得假设 AP shared 可原样 vendor。

### D2 附加裁决 — Schema Drift 防护为 AG-02 强制 Gate

**禁止人工维护两套独立 Schema。**

- **Canonical = vendored AP `shared`**（服务端消费的那一份）。这不是偏好：
  AP server 用 **TypeBox 在运行时校验 flow JSON**，前端定义与之不一致则**服务端直接拒绝数据**
  ⇒ 前端层只能是**派生物**。
- **机制优先级**：**Codegen 首选** ／ Re-export 为简化方案（⚠️ **代价是保留
  `frontend → AP shared` 依赖边，与 AG-02.3「完全脱离 AP workspace」冲突**，须显式取舍）
  ／ Contract Test 单用仅作兜底（只能**发现**漂移，不能**防止**）。
- **🔒 无条件强制**：无论选哪种机制，都必须有 **CI 新鲜度校验**——因为 Codegen 的失效是
  **静默的**（生成器没重跑 / 产物过期 / 有人手改生成物，三者都不报错）。
  正确表述是「**Codegen + CI 强制新鲜度校验**」，不是「Codegen 或 Contract Test 二选一」。

详见 [OPEN_GATES.md · AG-02.7](OPEN_GATES.md)。

> **AG-02 实测补充（2026-07-23）**：① **frontend 实际不是 workspace**（无 pnpm-lock，4 个独立 npm 项目，
> `pnpm-workspace.yaml`/`packages/core` 为零引用遗留物）⇒ 本决策中"workspace 成员"应理解为
> **`file:` 依赖交付**（已验证可行）；② **ap-contracts 必须产出 ESM**——CJS 的 `__exportStar`
> 无法被 Rollup/Vite 静态分析具名导出；③ **`lib/ee` 应从 ap-contracts 剥离**（实测可干净剥离，
> 仅损失 5 个已裁定删除的 EE 符号），彻底消除许可歧义。

---

### <a id="d3"></a>D3 — 新增 Document 3.5（EE 剥离与 CE 边界实施方案）（2026-07-23）

**理由**：EE 剥离已不是"模块处置"，而是独立工程——含许可边界、代码依赖分析、Core→EE 反向引用、
EE API 替代、数据库模型替代、前端/运行时/构建期依赖替代、测试替代、CI Guard。

**文档**：`EE_REMOVAL_PLAN.md` — *Document 3.5 — Activepieces EE 剥离与 CE 边界实施方案*

**必须包含**：
1. EE 依赖清单（EE→Core / Core→EE / Web→EE / Build→EE / Test→EE / Runtime→EE）；
2. 每条依赖的替代方案（Delete / Stub / Replace with CE / Reimplement / HERMES-native）；
3. **1000–1300 行的精确组成拆分**——多少属 EE removal、多少属 Bun→pnpm、多少属 Vue 集成、
   多少属 offline、多少属 RBAC、多少属 HERMES API、多少属安全修复。
   *（当前的"1000–1300 行"只是总量，不足以支撑 Q8 的成本判断。）*
4. **EE 剥离是否真的需要全部重实现**——逐条区分处置。

### D3 附加裁决 — §3 升格为 Integration Phase 0（前置阶段）

**执行顺序不可颠倒**：
```
HERMES Capability Inventory → EE Logic Mapping → Delete / Reuse / Replace 裁定
                                   ↓
                       再决定哪些 AP 代码真正需要集成
```
**绝不能先按 AP 原架构实现，再发现 HERMES 已经有同等能力。**

⚠️ **必须用三分类，不是二分类**（漏掉中间类 = "以为能复用，实际还得写"）：

| 分类 | 含义 | AP 侧处置 |
|---|---|---|
| **HERMES-owned** | HERMES 完全承担 | Delete |
| **HERMES-governed, AP-local** | **策略/数据源在 HERMES，但 AP 内部仍需最小本地判定实现** | 最小实现 + 从 HERMES 取策略 |
| **AP-owned** | HERMES 无对应能力 | 保留 / 重实现 |

中间类的典型：`authz/authorize.ts:102` —— AP 每个 project 级请求都要授权判定，
**判定点不可能每次回调 HERMES**（延迟不可接受、无同步调用通道），故 AP 侧仍需本地最小实现。

**依赖顺序**：
```
Doc1 REQUIREMENTS → Doc2 ARCHITECTURE ANALYSIS → Doc3 DEPENDENCY MAP
                                                      ├── AG-02（Frontend Workspace）
                                                      └── Doc3.5（EE Removal Plan）
                                                              ↓
                                                      Doc4 SYSTEM DESIGN
```

---

### <a id="d4"></a>D4 — 合规评估（R-B）状态（2026-07-23）

**结论：尚未启动。**

DEPENDENCY_MAP §2.2.5 的 R-B（由法务/合规评估 EE LICENSE 在我方场景的适用性，
含其 "development and testing purposes" 豁免与 "production" 限制）**目前没有进行中的评估**。

**影响**：R-A（重实现 ~1000–1300 行）当前是唯一在既有裁决框架内自洽的路线，
且已按 D1 接受。合规评估若后续启动并给出不同结论，可能改变 R-A 的必要范围——
但**在得到正式合规意见前，一律按 R-A 推进，不得以"可能不需要"为由推迟 EE 剥离工作**。

---

### <a id="d5"></a>D5 — AP 代码树位置与 workspace 归属（2026-07-23，**修订 Q1 的位置部分**）

**触发**：[D2](#d2) 使前端不再经 `workspace:*` 引用 AP shared，
Q1 原定"全量 vendor 到 `frontend/activepieces/` 并入 frontend pnpm workspace"随之失去依据。
若保留原方案，会把 **fastify / typeorm / bullmq / isolated-vm（原生编译）** 等后端依赖
装进前端 workspace——**正是 D2 要避免的"把 AP 原有工程体系带进 HERMES"**，
且 4 个 Vue 应用的开发者每次 `pnpm install` 都要付这个代价。

**裁决：三分归属**

| 内容 | 位置 | workspace / 构建 |
|---|---|---|
| AP 服务端运行体（api/engine/worker/utils）+ web 源码 + **完整** shared + pieces + cli + ee（留树） | **仓库根 `activepieces/`** | **自有 pnpm workspace + 独立锁**；产物 = Docker 镜像 + builder 组件 |
| 裁剪后的类型契约层 | **`frontend/packages/ap-contracts/`** | **frontend workspace 成员**；由 **Codegen 从 canonical 生成**（[AG-02.7](OPEN_GATES.md)） |

**理由**：
1. 仓库已按工具链分区（`backend/`=Maven+Java，`frontend/`=pnpm+Vue，`deploy/`=部署），
   `activepieces/` 作为**第三个自包含子系统**与既有结构一致；
2. 放 `backend/` 不合适——那是 Maven 多模块，混入 Node workspace 会破坏其构建约定；
3. 前端 workspace 只含 Vue 生态 + 一个 HERMES 自有类型包，`install` 不再拉入任何 AP 后端依赖；
4. AP 子树保留自己的 workspace 是 **frozen vendor + controlled fork**（[D1](#d1)）的自然形态——
   它整体来自上游 tag，独立锁文件让 baseline ↔ fork 的 diff 更干净（NFR-C02）。
   > **[D12](#d12) 后**：理由 4 的"diff 更干净"已不再成立，但**结论不变**——独立 workspace 现在的
   > 理由是工具链隔离（AP 的锁文件受公司 Nexus / FOSS Guard 约束，不该与前端锁互相牵动，见 VT-11）。

> ⚠️ **这不是回到"AP 独立 workspace 供前端消费"**（那个方案已被 Q1 否决、并被 D2 进一步排除）。
> 关键区别：**前端与 AP 之间没有 workspace 依赖边**，只有 Codegen 产出的**单向派生关系**。

**连带修订**：
- Q1 的"位置 + workspace 归属"部分由本决策取代；Q1 其余部分（全量 vendor、frozen baseline +
  controlled fork 性质）不变；
- **NFR-C03** CI 路径过滤对象改为 `activepieces/**` 与 `frontend/packages/ap-contracts/**`；
- **CR-04 风险显著下降**：不再需要把 AP 后端依赖并入 frontend 锁；
  前端侧剩余对账面仅为 `ap-contracts` 的 typebox 版本对齐 + builder 组件的 React 19；
- **AG-02 验证对象相应调整**（见 OPEN_GATES）。

**⚠️ 本决策引入的新设计项（留 AG-02 / Document 4）**：
builder 组件源在 `activepieces/packages/web`，消费方 DW 在 `frontend/`——**二者跨 workspace 边界**。
须定义 builder 组件的**打包与消费路径**（构建产物如何交付给 DW：本地包 / 构建期拷贝 / 私有 registry），
这是 D5 之前不存在的问题。

---

### <a id="d6"></a>D6 — 沙箱基线降级至 `SANDBOX_CODE_ONLY` + `STRICT`（2026-07-23）

**触发**：AG-05 实测确认候选基线 `SANDBOX_CODE_AND_PROCESS` 需容器 `CAP_SYS_ADMIN`
（isolate(1) 要挂 mount namespace），与 K8s PSS `restricted` 冲突。
**用户裁决（安全策略）**：*"安全策略不允许提权，降级"* ⇒ 落到降级阶梯**第 3 级**。

#### 裁决

| 项 | 值 |
|---|---|
| `AP_EXECUTION_MODE` | **`SANDBOX_CODE_ONLY`**（isolated-vm，无 isolate(1) 进程沙箱） |
| `AP_NETWORK_MODE` | **`STRICT`**（保留） |
| 容器 capability | **仅 `NET_ADMIN`**，**不要 `SYS_ADMIN`** ⇒ PSS `restricted` 可行 |

dev 环境已按此配置实跑（`deploy/environments/dev/docker-compose.dev.yml`），
AI Generate 端到端复测通过（HTTP 200、契约完整、17s）。

#### 安全代价 — 实测确认，不是推断

`AP_EGRESS_LOCKDOWN` 的 iptables owner-uid 链**只在 isolate 进程沙箱模式下安装**。
降级后实测（2026-07-23，dev 容器）：

| 观测 | 结果 |
|---|---|
| `iptables -S` 含 `AP_EGRESS_LOCKDOWN` | **0 条**；`OUTPUT` policy `ACCEPT` |
| 启动日志 `Kernel-level SSRF lockdown applied` | **不再出现**（只剩 `Egress proxy listening on loopback`） |
| 进程 uid | **api / worker / sandbox 全部 uid=0**，**无 60000-60999 uid 隔离** |
| **原始 socket → `platform-admin-center-dev:8080`** | **⚠️ 连通** — 无内核层拦截 |
| 原始 socket → 公网 | 可达 |

⇒ **[P-1](#q4a) 的缓解失效**。AG-05 中"STRICT 堵住 `X-User-Id` 冒充路径"的结论
**仅在 isolate 模式下成立**，在 D6 基线下**不再成立**，须在 OPEN_GATES 与 Doc4 同步更正。

#### 仍然保留的控制（同批实测）

| 控制 | 状态 |
|---|---|
| 应用层 egress 代理（SSRF 守卫） | ✅ 仍生效 — http piece 打 `169.254.169.254` 与 `platform-admin-center-dev` 均 **403 `Egress blocked`** |
| CODE step 出网 | ✅ **完全不可能** — isolated-vm 无 `fetch`/`require`/`net`（见 T3），比 uid 隔离更强 |

#### 残余风险的精确边界

**不是** CODE step（它连网络原语都没有），而是 **piece 代码（npm）**：
piece 跑在 worker/engine 的 Node 进程内、uid=0，可以开原始 socket **绕过应用层代理**。
在 isolate 模式下这类代码会落进 60000-60999 uid 被 iptables 兜住；D6 基线下没有这层兜底。

#### 补偿控制（**强制**，须在 Document 4 / Document 8 落地并复评）

| # | 补偿 | 状态 | 依据 |
|---|---|---|---|
| C-1 | **集群 NetworkPolicy**：AP pod egress 默认拒绝，仅显式放行必需目标（DB / Redis / 已批准的公网 API） | 🟡 **manifest 已编写（2026-07-23，operator-gated）**，待集群应用+验证 | 替代丧失的内核层管控 |
| C-2 | **piece 白名单冻结 + 离线预装**（[X 约束](#0-项目性质与不可变约束context)已有）⇒ 进程内可执行的第三方代码是**已评审的固定集合**，无运行时安装面 | 🟢 已有约束 | 收敛残余风险的代码面 |
| C-3 | **桥加固**：admin-center **不得再把裸 `X-User-Id`/`X-Username` 铸成已认证主体**，须凭可信服务共享密钥（`X-Service-Token`） | ✅ **已实现并实测（2026-07-23）** | 直接堵 P-1 根因，不再依赖网络层 |
| C-4 | 若未来安全策略放开 `SYS_ADMIN`（或改用 gVisor/Kata 等无需提权的强隔离），**回到阶梯第 1 级并复评** | ⏳ 留待 | 留出升级路径 |

> C-3 是唯一从根因消除 P-1 的控制；C-1 只是纵深。**两者不可互相替代**——C-3 已落地，C-1 仍必须补。

#### C-3 实现细节（静态共享密钥，复用仓库既有 `X-Internal-Token` 模式）

**机制**：可信一方服务（DW / user-portal）调用 admin-center 时附带 `X-Service-Token: <SERVICE_INTERNAL_TOKEN>`；
admin-center 仅当该 token 常量时间校验通过时，才信任裸 `X-User-Id`/`X-Username` 铸身份或做审计归属。
**未配置 token ⇒ header 身份兜底整体禁用（fail-closed）**。**AP 容器不持有此密钥**（信任边界）。

**代码切点**（三处 header-trust 全部 gate）：
- `SecurityConfig.java` `ServiceCallAuthenticationFilter`（认证铸主体，越权根因）
- `SecurityConfig.java` `auditContextEnrichmentFilter`（审计归属，抵赖根因）
- `FunctionUnitController.deployFunctionUnit`（deployerId 审计归属，改为只信任已认证 username，裸 header 兜底 → `"system"`）
- 生产方补 token：DW `DeploymentComponentImpl`、portal `RoleAccessComponent`/`VirtualGroupAccessComponent`/`UserPermissionController`
- 常量：`PlatformConstants.HEADER_SERVICE_TOKEN`；配置：三服务 `application.yml` `service.internal-token`

**信任边界（K8s）**：admin-center/DW/portal 用 `envFrom` 整包消费共享 Secret ⇒ 自动获得新键；
**AP 仅逐键 `secretKeyRef`，拿不到**（且不得为其新增该键的 `secretKeyRef`）。

**实测（dev，AP 容器模拟 piece 代码在共享网络伪造身份打 `POST /api/v1/admin/configs`）**：

| 用例 | 结果 |
|---|---|
| 伪造 `X-User-Id`，**无 token** | **HTTP 500** = `RuntimeException: auth.unauthenticated_user`（身份未铸，攻击被挡） |
| 伪造 `X-User-Id` + **错误 token** | **HTTP 500**（同上，被挡） |
| 伪造 `X-User-Id` + **正确 token** | HTTP 201（身份铸成、config 入库）⇒ 证明门只对持密钥者开 |
| 供给核对 | admin-center/DW/portal 有 token、**activepieces 无** |

**残余（须 Doc4 承接）**：C-3 只堵"冒充身份"；piece 代码仍能开 raw socket **无身份地**触达内网端口
（如未认证也放行的端点、或 DB/Redis 直连），故 **C-1 NetworkPolicy 仍是必须的纵深**。

#### C-1 实现细节（AP egress NetworkPolicy）

**文件**：`deploy/k8s/networkpolicy/activepieces-egress-networkpolicy.yaml`（+ 同目录 README）。

**allowlist 依据实证**（AP 容器 env + 真实 established 连接）：Postgres、Redis、容器内 loopback；
AI Generate 另需 LLM(deepseek) 公网/内网。**默认拒绝其余**——尤其切断 Istio Sidecar `egress: ./*`
放行的 AP→admin-center/portal/DW/engine/kafka 横向面。

| # | 目标 | 端口 | selector |
|---|---|---|---|
| 1 | kube-dns (kube-system) | 53 U+T | namespace+pod |
| 2 | istiod (istio-system) | 15012/15010/15014 | namespace |
| 3 | Redis（同 ns） | 6379 | podSelector `app: redis` |
| 4 | 外部 Postgres | 5432 | `ipBlock`（按环境 CIDR） |
| 5 | LLM（AI Generate） | 443 | `ipBlock`（按环境 CIDR；**air-gap 生产=内部端点，禁 0.0.0.0/0**） |

**operator-gated**：规则 4/5 的 CIDR 按环境不同，故文件**不进** kustomization、**不在** istio 渲染扫描路径，
避免带未解析占位符污染 apply-all；operator 填 CIDR 后手动 `sed | kubectl apply`。

**边界考量**：`ap-bootstrap` job 标签 `app: ap-bootstrap`（≠ `app: activepieces`），不受此策略影响。

**⚠️ 未在本环境运行时验证**：dev 是 compose 无 k8s；已做 YAML 解析 + NetworkPolicy v1 schema 结构校验。
README 附**集群内验证流程**（AP pod 打 admin-center 须失败、打 redis/DNS/AI-Generate 须成功）——
**生产依赖前必须先在集群跑通，并确认 CNI 真正执行 NetworkPolicy**（否则 manifest 形同虚设）。

---

### <a id="d7"></a>D7 — HERMES 侧命名改为 ServiceTask（2026-07-24）

**裁决（用户）**：AP 已 vendor 进仓库自维护，**在本项目里就叫 ServiceTask**；用户可见处不出现厂商品牌。

**改名范围（B 档：文案 + HERMES 自有代码）**：

| 层 | 改动 |
|---|---|
| 后端 | 包 `com.admin.ap` → `com.admin.servicetask`；`ApConfig`/`ActivepiecesProperties`/`ApTokenController`/`ApBridgeNonceStore`/`ActivepiecesApiClient`/`ActivepiecesApiException` → `ServiceTask*`；workflow-engine 的 `ApTaskExecutor`/`ApExecutionController`/`ApActionRequest`/`ApExecutionResult`/`ApExecutionRecord`/`ApExecutionRecordRepository`/`ApVariableMappingUtil` → `ServiceTask*`；配置前缀 `activepieces.*` → `service-task.*` |
| 前端 | `api/ap.ts` → `api/serviceTask.ts`（两应用）；`launchActivepieces` → `launchServiceTask`；`ApTaskConfig` → `ServiceTaskConfig`；`apConfigSerializer.ts` → `serviceTaskConfigSerializer.ts`；`components/activepieces/ApBuilderCanvas.vue` → `components/serviceTask/ServiceTaskBuilderCanvas.vue`；`ApTaskPropertiesPanel.vue` → **`ServiceTaskFlowPanel.vue`**（不叫 `ServiceTaskPropertiesPanel`，避免与既有 BPMN `ServiceTaskProperties.vue` 混淆） |

**明确不改（改了会断）**：
- vendored `activepieces/` 树、`@activepieces/*` 包名（engine 的 piece loader 按 `pieces/@activepieces/piece-x` 解析）、AP 自读的 `AP_*` 环境变量；
- 服务主机名 `activepieces:80` / k8s `activepieces-service` 与 27 个 `ACTIVEPIECES_*`（属 C 档，未选）；
- **JPA 表名/索引名**（`wf_ap_execution_record`、`idx_ap_exec_*`）——`@Table`/`@Index` 显式硬编码，schema 完全未动，**init-scripts 无需更新**；
- **BPMN `serviceType` 枚举值 `"ap"`** 及其 `ap*` i18n 键——该值**持久化在已保存流程定义**里，改则废存量流程。

**命名冲突的处置**：BPMN 里 "Service Task" 本就是元素类型，其实现枚举含 `http/script/message/ap/dmn`。
若把 `ap` 这一项也叫 ServiceTask 会读成"服务任务的类型是服务任务"。⇒ **该项用户可见文案定为
"Automation Flow / 自动化流程"**（去品牌且不重复父概念），同批清掉 flow-id / webhook / 变量映射文案里的裸 "AP"。

**验证**：后端 `BUILD SUCCESS`（JDK17）；两前端 `vue-tsc` 与基线持平，改名文件 0 error。

---

### <a id="d8"></a>D8 — admin-center 接受 DW 的平台 JWT cookie（2026-07-24）

**背景**：DW 页面（FU → Service Task）直调 admin-center 的 `/internal/ap/*` 登录桥端点。浏览器持有的是
**DW 签发的 `dw_access_token`** cookie，而 admin-center 的 `platform.security.jwt.cookie-names` 只列
`ac_access_token`/`access_token` ⇒ `JwtAuthenticationFilter` 取不到 token、无认证主体 → `/launch` **401**
（表现为 Service Task 页签 "Failed to connect"）。

**裁决**：给 admin-center 的 `cookie-names` 增加**只读项** `dw_access_token`（首项 `ac_access_token` 不变，
仍是它自己写出的 cookie）——正是该配置注释既定的用法（"首项写出，其余用于读取"）。

**与 [C-3](#d6) 不冲突**：C-3 拒的是**无密码学凭据的裸 `X-User-Id` 头**；这里读取并**验签平台 JWT**，属真实认证。
`workflow-engine-core` 早已同时接受 `up_`/`dw_`/`ac_` 三种 cookie 名，此为同一模式。

**诊断要点（复现同类问题时用）**：DW 前端各 api 客户端会注入 `X-User-Id`（自称身份、无 JWT），C-3 后该路径不再铸主体；
真凭据是 **httpOnly cookie**，`document.cookie` 看不到，须用 `fetch(..., {credentials:'include'})` 打一个
**需要认证主体**的端点（如 DW 的 `/api/v1/auth/me`）判定是否真登录——多数 admin 端点从 URL 取 userId、不需主体，
**它们返回 200 并不能证明已认证**。

### <a id="d10"></a>D10 — sync webhook 终态释放：第一批 HERMES-PATCH 落在运行时（2026-07-27）

> 编号跳过 D9 —— 该号已由 `D9_PIECE_ONLINE_ADMIN_DRAFT.md` 预留。

**背景**：AP 只在 "Return Response" 一步发布 sync 响应。跑挂的 flow 什么都不发，阻塞在 `handleSync` 的
调用方要等满 `AP_WEBHOOK_TIMEOUT_SECONDS`（dev 300s）才拿到兜底 204。实测：flow **3 秒** FAILED，
BPMN 服务任务 **300 秒**后才知道，期间流程实例一直卡着。204 的语义本身没错（flow 确实没产出响应，
见 `ServiceTaskExecutor.ApFlowNoResponseException`），坏的只是延迟。

**候选与否决理由**：
- *调小 `AP_WEBHOOK_TIMEOUT_SECONDS`* —— 否决。它同时是慢 flow 的上限，AI 生成那条要 ~230s（见 [Q7](#q7) 上下文）。
- *引擎侧改异步回调 + 等待态* —— 否决。与 [D7](#d7) 确立的同步 service-task 模型冲突，为一个延迟问题重开架构。
- **采用：改 vendor 运行时，run 进终态即主动发布响应。** 语义不变（仍是 204），只把等待从固定超时改成跟随 run 生命周期。

**裁决**：这是 [Q8](#q8) controlled-fork 模式下的第一批运行时补丁，两处，缺一不可：

| 补丁点 | 覆盖 |
|---|---|
| `packages/server/engine/src/lib/operations/flow.operation.ts` | 步骤内失败。此类 run 的 sandbox 仍返回 `EngineResponseStatus.OK`，**不走** worker 的 `reportFlowStatus` |
| `packages/server/worker/src/lib/execute/jobs/execute-flow.ts` | 引擎启动之前就死的：piece 安装失败、flow version 缺失、sandbox 超时 / OOM |

"两处" 是**被测试打出来的**，不是推出来的：只打 engine 一侧时，biz-calendar flow 仍然 300s，查出它死在
worker 的 piece 安装阶段，引擎根本没跑起来。

**约束**：只在 `isFlowRunStateTerminal` 时释放 —— PAUSED（waitpoint / 审批）必须继续等，响应在 resume 之后
才来。重复发布无害：`oneTimeListener` 首次响应即自注销，无监听者的 publish 是空操作。两处都做成
**best-effort**——run 已经报完状态，不能为了省延迟把已完成的 run 判成 `INTERNAL_ERROR`；真发不出去时
`AP_WEBHOOK_TIMEOUT_SECONDS` 仍是兜底。

**dev 实测**（同一条 flow、同一份 payload，镜像重建后复测）：

| 场景 | 前 | 后 |
|---|---|---|
| worker 侧失败（piece 安装） | 300s | 3s / 204 |
| 步骤内失败（HTTP 拿不到文件） | 300s | 0-1s / 204 |
| happy path（有 Return Response） | 4s | 2s / 200 + 完整 body，未被覆盖 |

**PAUSED 分支（2026-07-27 补齐）**：dev 环境**测不到**——能暂停的 piece（`core/delay`、
`core/approval`、`core/subflows`）都不在已装白名单里，唯一装了的 `core/webhook` 是「先答后停」
（`createWaitpoint({ responseToSend })` 在同一步就发出响应），调用方在暂停前已被回答，那里即使
判断写反也观察不到症状。故改由单测逐 `FlowRunStatus` 穷举锁定（见 HERMES_PATCHES.md 的回归网一节）。
**一旦 `core/delay` 进白名单**，暂停变成"还没人回答过"，这条判断立刻承重——这是白名单准入时
必须连带复核的一项。

**代价**：vendor 的补丁面增至 8 处（本次两处即 HERMES-PATCH-007 / 008）。借此把 [Q8](#q8) 只画了
结构、一直没有实体的编号台账建了出来：**[HERMES_PATCHES.md](HERMES_PATCHES.md)**，8 处全部登记、
代码侧标记统一为 `HERMES-PATCH-0NN`。其中 001/002 是构建期改写脚本、不在 vendor 树里，
是此前最容易在盘点时漏掉的两个。

### <a id="d11"></a>D11 — signing-key 供给保持手工，不自动化（2026-07-29）

**背景**：AP 供给共四项——① platform + 默认 project、② project `externalId`、③ `piece_metadata`、
④ **signing-key**（L7 per-user 换 token 用）。空库/重建卷/手工 drop 掉 AP 表之后四项全空，而
**重打镜像一项都不恢复**；症状是 DW 的 Automation 页签报错、各处日志却全绿（桥拿着一个已不存在的
key 去签名，AP 只回 401）。①②③ 已在 dev（`build-and-deploy.ps1` 的 `Invoke-ApProvisioning`）与
k8s（`ap-bootstrap-job.yaml` 的两个 initContainer）自动化。

**裁决**：**第 ④ 项不自动化**。`ap-verify-provisioning.js` 只做只读检测，缺了就让 Job 失败并打印
逐条手工命令；不 mint、不写 Secret。

**理由**（三条独立成立，任一条都足够）：

1. **私钥只在创建时返回一次**，必须落进 `workflow-platform-secrets`。让 Job 自动写 Secret 需要一个
   能改 Secret 的 ServiceAccount + RBAC——气隙/合规集群未必批得下来。注意这与 ②③ 不同：**写库要的是
   DB 凭据（AP Deployment 本来就在用），不是 k8s 权限**，所以那两项自动化没有引入任何新权限。
2. **重跑会轮换正在使用的密钥**。即便加"只在 `signing_key` 为空时 mint"的闸门，也只能挡住最常见的
   情形；密钥是活凭据，自动轮换的爆炸半径远大于省下的一次手工操作。
3. **k8s 侧目前根本没接这条线**：`admin-center.yaml` 没有任何 `ACTIVEPIECES_MANAGED_*` env，
   preprod/uat 的 configmap 与 secret 里也没有这三个键。L7 只在 dev 启用。现在自动写 Secret，
   等于往没人读的地方写值。**要在集群上用 L7，得先补这套接线**——那是另一件事，且应先于任何自动化。

**含义**：新集群部署后，若启用了 L7，必须人工执行一次 mint + 回填 Secret/ConfigMap + 重启
admin-center。Job 会明确告诉你这件事没做（退 1 + 打印命令），不会静默放过。dev 不受影响——
那边私钥写的是 `.env`，`Invoke-ApProvisioning` 照常自动处理。

---

### <a id="d12"></a>D12 — 终止 controlled fork，转为**硬分叉 + 深度裁剪**（2026-07-30，**取代 Q8 / 修订 Q1 与 D1**）

**触发**：VT-11 在公司机器上取得的实测结论（见 [VENDOR_TRIM_CHECKLIST](VENDOR_TRIM_CHECKLIST.md)）——
公司 FOSS Guard 隔离的是**上游锁定的精确版本**：`expr-eval@2.0.2`、`vitest@3.0.8`、
`fast-xml-parser@5.2.5`，外加 Nexus 里 metadata 不完整的 `isolated-vm@6.0.2`。

#### 裁决

> **`activepieces/` 子树自 2026-07-30 起视为 HERMES 自有源码。**
> 不再以"对齐上游 0.84.0 / 保持 vendor diff 可重放"作为设计约束；
> 目标从"少改上游"改为"**只保留我们真正运行的那部分，并让它在公司内网可构建、可维护**"。

#### 理由 —— controlled fork 的前提已被证伪，不是"收益变小"而是"做不到"

controlled fork 的全部价值押在一件事上：**将来能对着上游 tag 逐条重放补丁**。这需要两个前提，
现在两个都不成立：

1. **"冻结基线"在依赖层面已不可能维持。** 内网决定我们能装哪些版本，上游说了不算。
   VT-11 之后每解决一个隔离项就是一次对上游 pin 的偏离，"frozen baseline" 只剩源码层面装作还冻着。
2. **rebase 到新上游 tag 这件事，在 X-2 / X-3 下不会发生**（只有一次集成机会、之后完全断网）。
   为一个不会发生的动作付纪律成本，是 D1 成本模型里唯一没有被重估过的一项。

**这不是新方向，是 [X-7](#0-项目性质与不可变约束context) 贯彻到底**——X-7 从一开始就写着
"目标不是持续跟随上游…在封闭环境长期自主维护"。Q8 当时只走了一半：放弃了跟随，却保留了跟随所需的全套纪律。

#### 作废项（即刻停止为其付成本）

| 停止 | 原先为什么这么做 |
|---|---|
| 把"vendor diff 干净"当设计约束 | PATCH-009 宁可写 vite 插件构建期改写，也不改那 16 文件 47 处 |
| 保留死配置只为 diff 干净 | PATCH-014 留着 `crowdin.yml`，只让 npm script 拒跑 |
| 裁剪必须写成可重放脚本 + `--check` | PATCH-013 |
| rebase 重放顺序（VT-04） | 012 必须先于 013 之类的约束 |
| HERMES-PATCH 编号作为**重放索引** | Q8 的 baseline→patch 树 |

#### 保留项（各有独立理由，与上游可追溯性无关，且几乎零成本）

1. **MIT 义务**：分发物保留 `activepieces/LICENSE` 与版权声明。这是文件级义务，与台账无关。
2. **公司 OSS 入库申报**：硬分叉不豁免，反而让我们成为 maintainer of record。
3. **"改了什么"的可追溯性 —— 已经免费了**：基线是干净的单提交
   `de4f6469 vendor(ap): pristine Activepieces 0.84.0 source baseline`，
   `git diff de4f6469..HEAD -- activepieces/` 随时给出完整答案。
   ⇒ **[HERMES_PATCHES.md](HERMES_PATCHES.md) 就此从"重放施工图"降级为"为什么改"的变更日志**：
   继续记录动机与踩坑（那是 git diff 给不出的部分），编号继续递增以便交叉引用，
   但**不再承担 replay 语义**，也不再要求新改动都去挂一个编号。

#### 深度裁剪 —— 它不是清洁工作，是 VT-12 的解法

盘点（2026-07-30 实测）发现内网装不上的依赖分成**两类，解法不同**——
这个区分是 2026-07-30 实施 VT-17 时用 `pnpm why` 逐条核出来的，**初稿把两类混为一谈，已更正**：

**① 无消费方的功能面拖进来的依赖 ⇒ 删功能**

```
@ai-sdk/{amazon-bedrock,anthropic,azure,google,google-vertex,openai,
         openai-compatible,provider,replicate,mcp}  共 10 个 + ai@^6
  ← server/api/src/app/ai/providers/        AP 的 AI provider 代理        ✅ 已删
  ← server/api/src/app/mcp/                 AP 自带 MCP server（448K）    ✅ 已删
  ← server/worker/src/lib/execute/jobs/ee/chat/   EE chat agent           ✅ 已删
  ← server/engine/src/lib/tools/            agent tools（+ framework 契约）✅ 已删
  ← server/utils/src/chat-ai-utils.ts       ee/chat 的唯一消费方          ✅ 已删
```

**结果（2026-07-30 收口）**：10 个厂商 provider 包在锁文件里归零，锁条目 5147 → 5030；
[VT-12](VENDOR_TRIM_CHECKLIST.md) 关闭。保留 `ai@6.0.170` 一个（web 的 chat 前端真在用，
且它不在隔离清单上）。**类 ① 的"删功能面"路径在这一轮被完整验证了一次。**

这些功能**我们一个都不跑**：`piece-ai` 已按"气隙下 AI 件无用"删除（PATCH-002 作废），
AI Generate 已改 HTTP piece 直连模型端点，气隙内也没有 MCP 客户端。

**② 在跑的功能拖进来的依赖 ⇒ 只能升级或放行，删不掉**

```
fast-xml-parser@5.2.5  (FOSS Guard 隔离)
  ← @aws-sdk/xml-builder@{3.894.0, 3.972.0}
  ← @aws-sdk/core@{3.894.0, 3.972.0}  ← @aws-sdk/middleware-sdk-s3
  ← @aws-sdk/s3-request-presigner@3.894.0 + @aws-sdk/client-s3@3.974.0   ← **S3 文件存储**
```

> ⚠️ **本裁决初稿把这条链接到了 `@ai-sdk/amazon-bedrock` 上，是错的。**
> VT-11 原文只说"来源是 `@aws-sdk/xml-builder@3.894.0 / 3.972.0` 的传递依赖"，没有指认 Bedrock；
> `pnpm why` 显示真正的持有者是 **S3 文件存储链**。所以删 AI 面**不会**消掉 `fast-xml-parser`。
>
> **已于同日按类 ② 的办法解决（VT-21 / HERMES-PATCH-016）**：把 `@aws-sdk/client-s3` 与
> `@aws-sdk/s3-request-presigner` 升到 3.997.0，使 `xml-builder` 解析到 3.972.36+
> （该版本改用 `fast-xml-builder`）⇒ `fast-xml-parser@5.2.5` 在锁中归零。
> **升级而非删除——因为 S3 是在跑的功能，这正是两类划分的意义。**

**于是 VT-12 的三条路径是按类分工，而不是互相替代：**

| 路径 | 适用 | 评价 |
|---|---|---|
| 删掉持有依赖的功能面 | 类 ① | **首选**：依赖随功能消失，无需放行、无需升级、install 面永久缩小 |
| 升级到 Nexus 放行的版本 | 类 ② | 对 S3 链可行且便宜（见上）；但 `isolated-vm@6.0.2` metadata 不全堵过一次 |
| 求 FOSS Guard 复核放行 | 类 ② 兜底 | 不由我们控制，且每次 install 面变化都要重来 |

**教训（写进裁决而不是只写进 checklist）**：依赖链**必须用 `pnpm why` 核到具体持有者**，
不能按包名的"AI 味"推断归属。类 ① 和类 ② 在锁文件里长得一样，但解法相反。

#### 裁剪边界

**判定标准**：不在成品镜像里跑、或跑但没有消费方 ⇒ 删。分批执行，每批以 `pnpm install` +
`turbo run build --filter=web --filter=engine --filter=api --filter=worker` + 镜像构建 + dev 冒烟为闸门。

**明确保留，别误删**：
- `packages/cli` —— 自研件（`biz-calendar` / `hash-helper`）的开发链路依赖它，
  见 [PIECE_DEVELOPMENT_HOWTO](PIECE_DEVELOPMENT_HOWTO.md)；VT-11 的 `workspace:*` 修复正落在这里。
- `packages/web` 的 AP 独立应用形态 —— `/ap-cdn` 资产只随它发布
  （见 [HERMES_PATCHES](HERMES_PATCHES.md) 009 的补充说明），砍掉会让内嵌 builder 的图标全裂。
- `app-event-routing.service.ts` 与 `app_event_routing` 表 —— PATCH-012 已论证删了编译不过。

**批次与逐条证据见 [VENDOR_TRIM_CHECKLIST.md](VENDOR_TRIM_CHECKLIST.md) 的 VT-16 及以后。**

#### 代价（明确承担，不粉饰）

- **我们成为 AP 这份代码的唯一维护者**。AP 自身代码出 CVE 由我们自行修补——
  但这在 Q8 冻结基线下**已经是事实**，D12 没有新增这项风险，只是让它更显眼。
- **裁剪的回归风险高于纯文档变更**。缓解手段是分批 + 每批过构建与冒烟闸门，
  而不是"删完再一起验"。
- **删掉的上游源码不再可读**（VT-09 已就此定过补救约定：需要时从 `de4f6469` 取回）。

---

### <a id="d13"></a>D13 — 执行 D12 说了但没做的部分，并延伸到代码层（2026-08-07，**修订 D12 保留项 #1**）

**触发**：用户指出「之前改 AP 都是保守型，要完全断开 upstream、纯自维护」。核查后的事实是
——**方向 D12 已经定了，作废项一条都没执行**：

| D12 的作废项 | 2026-08-07 现状 |
|---|---|
| 裁剪必须写成可重放脚本 + `--check` | `hermes/trim-vendor-pieces.mjs` 仍在，注释里 6 处讲 rebase 之后怎么办 |
| 保留死配置只为 diff 干净 | `activepieces/crowdin.yml` 仍在（PATCH-014 只让 npm script 拒跑） |
| HERMES-PATCH 编号作为重放索引 | 当天还在给两个新改动挂 017 / 018 |

所以本条不是新方向，是**执行令 + 两处修订**。

#### 裁决 1 —— D12 的作废项即刻落地

删 `trim-vendor-pieces.mjs`（裁剪结果就是我们树的状态，KEEP 的理由并入 `hermes/README.md`）；
删 `crowdin.yml`；新改动不再挂台账编号（已有 001–018 保留，仅作交叉引用）。

#### 裁决 2 —— 同一原则延伸到**代码层**（D12 没写这一段）

> **一个字段、默认值或分支，如果在 HERMES 侧没有产生者，就删掉；
> 不因"上游可能这么发"而保留。**

D12 只清了"为 diff 干净付的成本"，没清"为上游**契约**付的成本"。后者当天还在新增：

- `external-token-extractor.ts` 维持 v1/v2/v3 payload 联合体，并解析 `pieces` /
  `concurrencyPool*` 后原样丢弃，注释写明"只为兼容 ee token 契约"；
- 同日新加的 `platformRole ?? PlatformRole.MEMBER` 兜底，理由是"缺省保持上游语义"——
  可 HERMES 是**唯一**的 token 签发方，缺省分支永远走不到。

⇒ 外部 token 收成 HERMES 自己的契约：单一 schema、该必填的必填、没有消费方的字段删掉。
**留着这类兜底的真实代价不是几行代码，是它让"谁会发这个字段"永远说不清**，
下一个读代码的人必须假设存在第二个签发方。

#### 裁决 3 —— 修订 D12 保留项 #1（LICENSE）

D12 保留项 #1 写的是「分发物保留 `activepieces/LICENSE` 与版权声明」。
该文件已由 `fd96c997c` 删除；2026-08-07 用户明确裁决**不恢复**，并一并删掉 `Dockerfile` 里那行
`COPY LICENSE`（它已经让镜像构建直接失败）。**故保留项 #1 作废。**

> ⚠️ **这是法律问题，不是纪律问题，因此不随 D12 一起"因硬分叉而消失"**：
> 成品镜像仍然分发 AP 的源码与构建产物，MIT 的保留义务附着在**分发**上。
> 本条只记录"用户已作此裁决"，**不代表评估已完成** —— 合规结论仍挂在
> [D4](#d4)（合规评估，尚未启动）名下，且这是 D4 现在必须回答的具体问题之一。

#### 附带 —— 构建确定性（同源，但属工程而非纪律）

`Dockerfile` 中途 `rm -f pnpm-lock.yaml && pnpm install` 是"跟随上游锁"的残留形态：
自维护下锁文件是我们自己的资产，没有理由每次构建重新联网解析全树。
2026-08-07 两次镜像构建分别死在这条链上（`ECONNRESET` 与 `ERR_PNPM_ENOSPC`）
⇒ 改为单一提交的锁文件 + 两个 stage 都 `--frozen-lockfile`。

#### 不变项

X-7 与 D12 的裁剪判定标准不变；"改了什么"继续由
`git diff de4f6469..HEAD -- activepieces/` 回答；`HERMES_PATCHES.md` 继续记动机与踩坑。

---

## 3. Q 系列裁决（2026-07-22，正文见各条）

### <a id="q1"></a>Q1 — Vendor 边界与位置
全量 vendor（web + server + engine + worker + pieces + shared + cli）。
**⚠️ 位置与 workspace 归属经 [D5](#d5) 修订**：AP 子树 → **仓库根 `activepieces/`（自有 pnpm workspace）**；
前端仅保留 `frontend/packages/ap-contracts/` 裁剪层。原"`frontend/activepieces/` + 并入 frontend workspace"**已作废**。
**⚠️ 经 D1 升级**：性质为 *frozen vendor + controlled fork*，非"拷进来不动"。
**⚠️ 经 [D12](#d12) 再改（2026-07-30）**：性质为 **硬分叉 + 深度裁剪**——"全量 vendor"只描述 2026-07-22
的入库动作，**不再是维持中的状态**；vendor 树按"是否真的在跑"持续收敛（013 已把 686 个 community piece
收敛到 4，后续批次见 VENDOR_TRIM_CHECKLIST）。
**⚠️ 经 D2 细化**：**前端侧不再保留 AP 独立 workspace**，改为裁剪 AP shared 并入 HERMES frontend；
服务端 AP 运行体仍需完整 shared（类型漂移风险见 D2 备注）。
连带强制项：bun→pnpm 锁重建（CR-01）、4 应用回归（GW-12）、CI 路径过滤（NFR-C03）、
上游路径映射（NFR-C02）；依赖对账（CR-04）范围因 D5 缩小至 `ap-contracts`。

### <a id="q2"></a>Q2 — Bun 策略
**全面禁止**（公司禁令 X-4）。AP 子树整体迁 pnpm + Node；锁文件重建与 piece-installer 关停
是 vendor 后第一批动作。

### <a id="q3"></a>Q3 — 画布裁剪深度
v1 即抽 **"纯 builder 组件"**（否决胖挂载）：不携带 AP 登录页 / platform-admin / 全局路由 /
全局 localStorage 会话假设；React 及其依赖封装在组件内部。
**与 X-6 一致**：Builder 不得以"React SPA 直接并入 HERMES"的方式处理。
实现方案（lib mode + Shadow DOM）为**候选**，框架级 PoC 已过，真实 builder 待验（AG-04）。

### <a id="q4"></a>Q4 — 身份/租户
**废除共享账号承载人的会话**，画布会话对应当前登录 DW 的平台用户（per-user）。
v1 单 platform + 一个指定共享 project；`hermes-svc` 降级为纯系统服务账号。
供给机制**不得依赖 AP 的 SMTP 配置状态、不得由平台托管用户密码**（见 ARCHITECTURE_ANALYSIS §2.3）。

### <a id="q4a"></a>Q4a — 共享 project 放行方式
**CE core 层 RBAC 小补丁**。切点两处（均在 `core/`，MIT）：
① `core/security/v2/authz/authorize.ts:93-103`（HTTP）；
② `core/websockets.service.ts:96-115`（WebSocket，**独立路径，漏打则"能编辑不能调试"**）。
约束：保留 Permission 粒度、仅对配置的 shared projectId + USER + 同 platform 生效、
未配置时惰性同上游、HERMES-PATCH 标记。
**连带**：provisioning 不需写 `project_member` 行（避开 ee 依赖，且保最小权限）。

### <a id="q5"></a>Q5 — 数据库
**独立 schema `activepieces`**（同 PostgreSQL 实例）。改造点集中：DataSource `schema` 属性 +
新增 env + 启动前 `CREATE SCHEMA`；3 处硬编码 `table_schema='public'` 的早期迁移需参数化。

### <a id="q6"></a>Q6 — 网关与桥的终局
AP server-api 经 Kong 收进平台域；:8085 edge 桥与 nonce 握手**并行一个版本后退役**，
退役列入下一版本收尾清单。生产继续仅暴露 `/api/v1/webhooks`。

### <a id="q7"></a>Q7 — flowId 治理
建平台侧 flow 注册表，v1 采用**部署期解析**：`ProcessDeploymentManager` 在 BPMN 部署期
把逻辑 flow 引用改写为当前环境实 flowId。代价：映射变更后须重新部署流程定义。

**✅ 已实施（2026-07-26，dev E2E PASS）**。落地形态与裁决略有具体化：

- **"注册表" = flow 迁移键**：不建独立表。跨环境迁移经 admin-center 的
  `/automation/flows` 管理面（uat 导出 JSON → prod 导入），导入时把源环境 flowId 写进
  目标 flow 的 `metadata.hermesFlowKey`（选 metadata 而非 externalId：AP REST 创建接口
  只收 metadata，**零 fork 补丁**，合 Q8 Frozen Baseline）。
- **部署期解析**：引擎 `ServiceTaskFlowRefResolver` 凭 C-3 `X-Service-Token` 调
  admin-center `/automation/flows/resolve?ref=`（本环境同 id 直查 → 迁移键查找），
  `ProcessDeploymentManager` 把部署产物里的 `ap:flowId` 改写为实值；源 BPMN 不动。
  解析失败/不可达时保留原引用 WARN 继续（缺失最终在运行时由执行记录暴露）。
- **拓扑适配**：DW 不上 prod；prod 的 FU 经 admin-center Function Unit 管理导入并
  经 `WorkflowEngineClient.deployProcess` 部署——正好命中改写切点，全链无 DW 依赖。
  k8s 接线：`ACTIVEPIECES_FLOW_RESOLVE_URL` 入 uat/preprod configmap；
  `SERVICE_INTERNAL_TOKEN` 引擎经 envFrom 天然持有（AP 仍拿不到，C-3 边界不变）。
- **顺序约束**：flow 须先导入再部署 FU；若倒置，flow 导入后重新部署即可（即裁决中
  "映射变更后须重新部署"的代价）。connection 不随包走（设计使然，prod 用生产凭据重建）。
- **flow 随 FU 导出包走（2026-07-27 补齐）**：`/automation/flows` 管理面仍是独立发布通道，
  但 FU 导出不再只带一个解析不到的 `ap:flowId`——`FunctionUnitExporter` 按 BPMN 里的
  `ap:flowId` 经 admin-center `/automation/flows/internal/export`（同 C-3 门禁）取可携带
  JSON，打进 ZIP 的 `automation-flows/flow_*.json`（登记于 manifest `components.automationFlows`）；
  DW 导入与 admin-center 的 FU ZIP 导入在写任何 FU 内容<b>之前</b>还原它们
  （`/automation/flows/internal/restore` → `AutomationFlowService.restoreFlows`）。
  规则：**只补缺、不覆盖**——迁移键已能解析到本环境 flow 的一律跳过，同环境重导不会用包里的
  旧快照盖掉正在维护的草稿；源环境引用不到 flow（已删）时导出直接失败，不产出缺自动化的包。
  connection 依旧不随包走，故新建的 flow 可能发布失败 → 结果里回传 `PUBLISH_FAILED` +
  原因，补齐凭据后在管理面手工发布。flow 本体不进版本快照（AP 侧自带版本，同库回滚引用不变）。

### <a id="q8"></a>Q8 — AP 版本演进 → ~~Frozen Baseline + Controlled Fork~~ **已由 [D12](#d12) 取代**

> ⚠️ **本条已于 2026-07-30 被 [D12](#d12) 取代，仅存档。**
> 当前有效表述：**硬分叉 + 深度裁剪**——`activepieces/` 视为 HERMES 自有源码，
> 不再以"对齐上游 tag / diff 可重放"为设计约束。
> 下面保留原文，因为 D12 的推理建立在它之上（它的方向没错，错在保留了跟随所需的全套纪律）。

**旧表述（作废）**："完全放弃跟随上游，因为我们只维护一份 vendor 代码。"
**新表述（采用，D1；2026-07-30 经 D12 再度作废）**：
> **不承诺持续跟随 Activepieces 上游版本，采用 0.84.0 frozen baseline + HERMES controlled fork 模式。**

依据 X-2/X-3：只有一次集成机会、之后完全断网，"长期跟随上游"的价值大幅下降
（无法 `git pull upstream` / 在线升级 / 重装 piece / 在线取依赖）。

**但增加一个前提**：放弃跟随上游 **≠** 放弃上游可追溯性。必须保留：
```
AP 0.84.0 官方 Tag（frozen baseline）
   ├── HERMES-PATCH-001
   ├── HERMES-PATCH-002
   └── ...
```
即：不追求未来版本升级兼容，但**保留完整的 baseline、patch、变更原因与许可审计能力**。

**实体清单见 [HERMES_PATCHES.md](HERMES_PATCHES.md)**（2026-07-27 建，首批 8 条）。加补丁时取下一个
编号写进代码并登记入表；`grep -rn "HERMES-PATCH-0" activepieces/ deploy/` 应与该表逐条对上。
注意其中 001/002 是**构建期改写脚本**、不在 vendor 树里，只按源码注释盘点会漏。

### <a id="q9"></a>Q9 — approval / todos piece
从 v1 白名单**移除**（12→10），`patch-web-approvals` 一并移除；vendor 源码树不物理删除。
**能力边界（长期有效）**：Flowable 是 HERMES 业务审批与业务待办的唯一系统；
AP 不得用于实现业务审批、业务待办或审批状态的 Source of Truth。
重新开放须过架构评审（use case / 权限模型 / 审计设计 / 数据生命周期 / 与 Flowable 边界 五项）。
