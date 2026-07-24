# Activepieces 0.84.0 集成 — 当前状态

> **从这里开始读。** 更新日期：2026-07-23

---

## 一句话现状

AP 0.84.0 的源码逆向已完成并证实：这**不是一次简单的 vendor，而是一个受控 fork**
（约 1000–1300 行重实现 + Bun 迁移 + React→Vue 宿主适配 + EE 剥离 + 离线/安全改造）。
需求与依赖分析已成文，**设计（Document 4）被 3 个红灯 Gate 阻塞**（AG-02 / AG-EE / AG-05）。

**Phase 0 + AG-02 均已完成（2026-07-23）**：
Phase 0 → 18 项能力盘点 + 13 条 EE-4 裁定 + 20 组处置，**必须重写的仅 5 项，AP 认证域可整体删除**；
AG-02 → **AP shared 独立编译零错误、零 AP 后端依赖、零 bun、DW(Vite/Vue) 消费构建通过**。
**AG-05 也已 8/8 实测通过**；因安全策略不允许提权，**[D6](DECISIONS.md#d6) 将基线降级为
`SANDBOX_CODE_ONLY`+`STRICT`**（dev 已实跑，AI Generate 复测 17s 完好）——
代价是**丧失内核级出网管控**，必须补 NetworkPolicy 与桥加固。
**目前无红灯 Gate 阻塞 Doc4**，但 D6 补偿控制是生产上线的红线。

---

## 文档地图

| 文件 | 角色 | 状态 |
|---|---|---|
| **STATUS.md** | 本文 · 一屏总览 | — |
| **[DECISIONS.md](DECISIONS.md)** | **ADR — 全局约束唯一事实来源**（X 约束 / Q1–Q9 / Q4a / D1–D5） | 现行 |
| **[OPEN_GATES.md](OPEN_GATES.md)** | **阻塞项唯一总账**（AG-01~06 + AG-EE，含全部子项） | 现行 |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Document 1 — 需求规格 | 待冻结 |
| [ARCHITECTURE_ANALYSIS.md](ARCHITECTURE_ANALYSIS.md) | Document 2 — 源码逆向（6 线全完成） | 待冻结（依赖 Gate） |
| [DEPENDENCY_MAP.md](DEPENDENCY_MAP.md) | Document 3 — 依赖图与模块处置 | 初稿 |
| [EE_REMOVAL_PLAN.md](EE_REMOVAL_PLAN.md) | **Document 3.5 — EE 剥离实施方案**（D3 新增） | ✅ §2/§3/§4/§5 完成；余 §1 两向清单 + §4.7 补测 + §6 CI Guard |
| **[INTEGRATION_DESIGN.md](INTEGRATION_DESIGN.md)** | **Document 4 — 集成设计（九层 + §0.5 实施快照）** | 🟢 **九层全部已实施并实测：L1 浏览器 E2E PASS**（真实 builder 挂载/交互）+ L2/L3/L7 dev 实测 + 地基（L4/5/6/8/9）；余收尾（接 DW 视图/交付/C-1/air-gap） |

---

## Gate 状态（详见 [OPEN_GATES.md](OPEN_GATES.md)）

| ID | Gate | 状态 |
|---|---|---|
| **AG-02** | Frontend workspace 边界（ap-contracts 落地） | 🟢 **核心已验证**，余 CI/Codegen 实施 |
| **AG-EE** | EE 剥离闭合（EE-4 清零） | 🟡 **13 条已裁定处置**，实施与验证待做 |
| AG-05 | Sandbox + offline + prebuilt piece 联合验证 | 🟢 **8/8 PASS**；基线经 **[D6](DECISIONS.md#d6) 降级**为 `SANDBOX_CODE_ONLY`+`STRICT`；余补偿控制 C-1/C-3 |
| AG-01 | React version consistency | 🟡 版本已定，真实 dedupe 待验 |
| AG-03 | 共享 project 完整 RBAC + 资源所有权 | 🟢 **HTTP RBAC 回归 PASS**（per-user Editor/Viewer 同端点 allow/deny，2026-07-24；Q4a bypass 不需要） |
| AG-04 | Builder 组件化 | 🟢 **浏览器 E2E PASS**（2026-07-24）：真实 builder Shadow-DOM 挂载、flow 加载、socket 连接、piece 选择器（Radix Portal in shadow）全绿 |
| AG-06 | Per-user provisioning | 🟢 **实现完成并 dev 端到端 PASS**（Option A：CE 重写 managed-authn+signing-key；重建镜像+引导+复测：幂等/隔离/审计到人/DB 全绿，2026-07-24） |

---

## 已确立的事实（不再重复讨论）

- **基线**：0.84.0（与官方 tag 逐字节一致的快照）；0.86 结论**禁止**用于 0.84 落地。
- **前端**：0.84 web 是 **React 19**；HERMES 无微前端框架（qiankun 是误述）。
- **不可拆分运行体**：`server/{api,engine,worker,utils}` 紧耦合，只能整体部署；**不做 TS→Java 翻译**。
- **builder API 面**：**39 个同源端点 + 16 个 WS 事件**（远超早期估计的 5 个）；
  测试与观测 **100% 依赖 socket.io，无 HTTP 兜底**。
- **共享 project 可行**：所有资源 project-scoped，`ownerId` 不参与访问控制。
- **沙箱默认不安全**：0.84 默认 `UNSANDBOXED` + `UNRESTRICTED`。候选基线
  （`SANDBOX_CODE_AND_PROCESS`+`STRICT`，8/8 子项 PASS）**需容器 `CAP_SYS_ADMIN`**，与 K8s PSS
  `restricted` 冲突 ⇒ **[D6](DECISIONS.md#d6) 裁决降级**：**现行基线 = `SANDBOX_CODE_ONLY`+`STRICT`
  （仅 NET_ADMIN）**，dev 已实跑、AI Generate 复测通过。
- **⚠️ CODE step 能力面很窄**：isolated-vm **无 `fetch`/`require`/Node 内置**（T3，实测），
  叠加 FR-F03B（不能装外部 npm 依赖）⇒ **CODE step 只能写纯 JS**；HTTP 调用须改用 http piece。
  dev 全量扫描：含 CODE step 的 flow 共 2 个，受影响 1 个（`csv`）——**已改用 http piece 并实跑 SUCCEEDED**；
  **AI Generate 干净**。
- **⚠️ D6 的安全代价（实测）**：`SANDBOX_CODE_ONLY` 下 **iptables `AP_EGRESS_LOCKDOWN` 不再安装**
  （`OUTPUT` policy ACCEPT、进程全 uid=0）⇒ **原始 socket 直连 admin-center 连通，[P-1](DECISIONS.md#q4a) 缓解失效**。
  仍有效的是**应用层** egress 代理（piece 打内网/metadata 仍 403）。**残余风险面 = piece 代码**
  （进程内可绕代理），**不是** CODE step。补偿：**C-3 桥加固已实现并实测**（admin-center 凭 `X-Service-Token`
  才信任裸 `X-User-Id`，AP 无密钥，冒充被挡）；**C-1 NetworkPolicy manifest 已编写**（operator-gated，
  切断 AP→HERMES 横向，待填环境 CIDR + 集群验证）。
- **Redis**：生产必须**独立实例**（eviction/persistence 是实例级配置，逻辑 DB 号不够）。
- **EE 现实**：CE 有 105 处 import `app/ee/**`，"ee 目录不进构建"**不可行**（编译期即死）；
  但**表 DDL 在 MIT 区、实体映射才在 EE 区** ⇒ 可依 MIT DDL 独立重写。
- **EE 剥离已裁定**（Doc3.5）：13 条 EE-4 = Delete 3 / Stub 4 / Reimplement 5 / 保留 1，
  **"全部重实现"是错误预设**——必须重写的仅 5 项。**AP 认证域可整体删除**（HERMES 已完整覆盖）。
- **HERMES 无统一授权内核**（`PermissionService` 是死代码）；其自身模式 =
  "远程取策略 + 本地缓存 5min + 本地判定"，**AP 照搬即可**；且 **JWT 已带 roles/permissions ⇒ 主路径零回调**。
- **成本口径修正**：原"1000–1300 行"**只含 EE 剥离**；另有 **HERMES 侧新建 ≈580 行**（原完全遗漏）
  + Bun 迁移 / Vue 集成 / offline / 安全基线（按工作包评估，**Vue 集成是最大不确定项**）。
- **代码树布局（D5）**：AP 子树 → **仓库根 `activepieces/`**（自有锁，产物=镜像）；
  前端只留 **`frontend/packages/ap-contracts/`**（Codegen 派生，经 `file:` 依赖被 DW 消费）。
- **AG-02 实测推翻三处前提**：① **AP shared 用 zod 4.3.6 不是 TypeBox** ⇒ 与 HERMES 的 typebox pin
  **无冲突**（那个 pin 与 AP 无关）；② **frontend 根本不是 workspace**——无 pnpm-lock，4 个独立 npm 项目，
  `pnpm-workspace.yaml`/`packages/core` 均为零引用遗留物；③ **爆炸半径只有 DW 一个应用**。
- **AG-02 两个技术点**：**CJS 产物无法被 Vite 具名导入**（`__exportStar` 静态分析不了）⇒ ap-contracts
  **必须产出 ESM**（dual exports 已验证）；**`lib/ee` 可干净剥离**（去 19 行 barrel，0 编译错误，
  440 个符号中仅 5 个失败且全属已裁定删除的 EE 域）。

---

## 下一步（按优先级）

| # | 动作 | 归属 | 阻塞什么 |
|---|---|---|---|
| ~~0~~ | ~~Integration Phase 0 + Doc3.5 §2/§4/§5~~ **✅ 已完成（2026-07-23）** | 架构 | — |
| 0b | ✅ **D6 补偿控制 C-3 已实现并实测**（admin-center 凭 `X-Service-Token` 才信任裸 `X-User-Id`；AP 无密钥；三态 500/500/201） | 平台安全/后端 | — |
| 0c | 🟡 **D6 补偿控制 C-1 manifest 已编写**（`deploy/k8s/networkpolicy/`，operator-gated）—— 待填环境 CIDR + 集群应用/验证（无 k8s 环境未运行时验证） | 平台安全 | 生产上线 |
| 1 | Doc3.5 剩余：§1 两向清单、§4.7 八条补测、§6 CI Guard；AG-02 剩余：CI 路径过滤 / Codegen 新鲜度 / builder 交付 | 工程/架构 | Doc4 |
| 2 | **启动合规评估（R-B）** — [D4](DECISIONS.md#d4) 确认**尚未启动** | 法务/合规 | 可能缩减 EE 剥离范围 |
| 3 | **HERMES 侧三项新建**（Phase 0 反推，≈580 行）：身份供给端点 / 用户状态事件通道 / 审计写入接口 | 平台后端 | AG-06 |
| 4 | AG-05 联合验证（需真实容器环境） | 工程/安全 | Doc4 Layer 3–5 |
| ~~5~~ | ~~AG-03 权限面回归 / AG-06 端点实现~~ **✅ 已完成（2026-07-24）**：L7 per-user 供给端到端 PASS、L3 HTTP RBAC 回归 PASS | 工程 | — |
| ~~6~~ | ~~**L2 网关**（Kong builder API + per-user 透传）~~ **✅ 已完成（2026-07-24）**：AP `/api/ap/*` 收编进 Kong，REST/WS/per-user token dev 实测全绿 | 工程 | — |
| ~~7~~ | ~~**L1 builder 挂载**~~ ✅ **浏览器 E2E PASS（2026-07-24）**：真实 builder Shadow-DOM 挂载/flow 加载/socket/piece 选择器全绿；余接 DW 视图 + 交付落地（AG-02.8） | 前端 | — |

> **#2 合规评估仍是性价比最高且不在工程侧的待办**：若结论允许更宽用法，
> EE 剥离的重实现范围可能显著缩小。但在得到正式意见前，**一律按 R-A 推进**。

---

## 待决策清单

目前**无阻塞性待决策**——Q1–Q9、Q4a、D1–D5 均已裁决（见 [DECISIONS.md](DECISIONS.md)）。
以下为实施期需要在对应 Gate 内确定的技术选择，非架构级：

- AG-02.7：同步机制的具体实现（**Codegen 首选**；Canonical 已定 = vendored AP shared；
  **CI 新鲜度校验无条件强制**）；
- ~~Document 3.5 处置归类~~ **✅ 已完成**（20 组 G1–G20，见 [Doc3.5 §2](EE_REMOVAL_PLAN.md)）；
- ~~AG-05 降级阶梯落点~~ **✅ 已裁决**（[D6](DECISIONS.md#d6) → 第 3 级，补偿控制 C-1~C-4 已列，**实施未做**）；
- ~~AG-02.8：builder 组件跨 workspace 的交付方式~~ **✅ 已定案（2026-07-24）= 构建期拷贝**：`prebuild` 把 web-embed 产物拷进 DW `public/service-task-builder/`，DW 自身 nginx 提供，离线可用，产物不入库。
