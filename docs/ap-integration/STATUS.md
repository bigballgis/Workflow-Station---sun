# Activepieces 0.84.0 集成 — 当前状态

> **从这里开始读。** 更新日期：2026-07-28

---

## 一句话现状

AP 0.84.0 的源码逆向已完成并证实：这**不是一次简单的 vendor，而是一个 fork**
（约 1000–1300 行重实现 + 去 bun + React→Vue 宿主适配 + EE 剥离 + 离线/安全改造）。
**2026-07-30（[D12](DECISIONS.md#d12)）起该 fork 不再是"受控 fork"，而是硬分叉 + 深度裁剪**：
`activepieces/` 视为 HERMES 自有源码，按"是否真的在跑"持续收敛，不再以对齐上游 tag 为设计约束。
**现已全部落地**：九层集成逐层实施并实测（L1 浏览器 E2E、L2/L3/L7 dev 实测、地基 L4–L9），原先卡住 Doc4 的三个红灯 Gate（AG-02 / AG-EE / AG-05）均已转绿。

**07-25 → 07-28 又落了一批（本文档此前停在 07-24）**：
- **运行时去 bun 彻底完成 + 气隙闭环**：piece 安装器改 pnpm（`node-linker=isolated`，布局须与引擎加载器一致）；
  新增硬开关 **`AP_PIECES_OFFLINE_INSTALL`**（只认镜像内烘焙的离线 store，闭包外依赖 fail-closed）；
  白名单与预烘焙脚本迁至 **`activepieces/hermes/`**，预烘焙并入 `activepieces/Dockerfile` 末层。
- **气隙外观合规**：白标 + **CDN 资源本地化**（含存量数据的 DB 迁移），离线环境不再外链 cdn.activepieces.com。
- **flow 跨环境迁移通道 + Q7**：admin-center 迁移页（启停/删除/**导入前 connection 清单比对**）+ 引擎**部署期 flowId 解析**
  （迁移键 = `metadata.hermesFlowKey`，免打 AP 补丁）；**FU 导出包随带 Automation flow**，导入只补缺不覆盖。
- **piece / flow 管理面与治理**：审计日志覆盖 Automation flow 与 piece、flow 删除守卫补扫 DW 侧 FU 引用、
  悬空绑定可恢复；**在线管理的 prod 开放仍受 [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) 约束（未拍板前 prod 不得用）**。
- **[D10](DECISIONS.md#d10)**：sync webhook 终态即释放（run 结束不再空等满 300s），第一批 HERMES-PATCH 落运行时。
- **自研 piece 链路打通**：开发→构建→离线物料→烘焙→投放→DW 可用，全程有文档且**已实建两个件**
  （`biz-calendar`、`hash-helper`）验证过流程可靠。

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
| **[DECISIONS.md](DECISIONS.md)** | **ADR — 全局约束唯一事实来源**（X 约束 / Q1–Q9 / Q4a / **D1–D8**） | 现行 |
| **[OPEN_GATES.md](OPEN_GATES.md)** | **阻塞项唯一总账**（AG-01~06 + AG-EE，含全部子项） | 现行 |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Document 1 — 需求规格 | 待冻结 |
| [ARCHITECTURE_ANALYSIS.md](ARCHITECTURE_ANALYSIS.md) | Document 2 — 源码逆向（6 线全完成） | 待冻结（依赖 Gate） |
| [DEPENDENCY_MAP.md](DEPENDENCY_MAP.md) | Document 3 — 依赖图与模块处置 | 初稿 |
| [EE_REMOVAL_PLAN.md](EE_REMOVAL_PLAN.md) | **Document 3.5 — EE 剥离实施方案**（D3 新增） | ✅ §2/§3/§4/§5 完成；余 §1 两向清单 + §4.7 补测 + §6 CI Guard |
| **[INTEGRATION_DESIGN.md](INTEGRATION_DESIGN.md)** | **Document 4 — 集成设计（九层 + §0.5 实施快照）** | 🟢 **九层全部已实施并实测：L1 浏览器 E2E PASS**（真实 builder 挂载/交互）+ L2/L3/L7 dev 实测 + 地基（L4/5/6/8/9）；余收尾（接 DW 视图/交付/C-1/air-gap） |
| [HERMES_PATCHES.md](HERMES_PATCHES.md) | **HERMES-PATCH 变更日志**——记录改 AP 代码的**动机与踩坑**。[D12](DECISIONS.md#d12) 后不再承担重放施工图职责（"改了什么"由 `git diff de4f6469..HEAD -- activepieces/` 免费给出） | 现行 |
| [PIECE_DEVELOPMENT_HOWTO.md](PIECE_DEVELOPMENT_HOWTO.md) | 自研 piece 开发→部署到 DW 全链路（流程） | 现行 |
| [PIECE_DEVELOPMENT_EXAMPLE.md](PIECE_DEVELOPMENT_EXAMPLE.md) | 配套完整示例（业务日历件，可直接抄） | 现行 |
| [D9_PIECE_ONLINE_ADMIN_DRAFT.md](D9_PIECE_ONLINE_ADMIN_DRAFT.md) | D9 — piece 在线管理面治理 + C-2 重述 | ⚠️ **草案 v3，待评审；未拍板前不具约束力**（prod 的 import/delete 不得使用） |

---

## Gate 状态（详见 [OPEN_GATES.md](OPEN_GATES.md)）

| ID | Gate | 状态 |
|---|---|---|
| **AG-02** | Frontend workspace 边界（ap-contracts 落地） | 🟢 **核心已验证**，余 CI/Codegen 实施 |
| **AG-EE** | EE 剥离闭合（EE-4 清零） | 🟢 **出口标准已达成**：删 `app/ee/` + 12 CE 重写，CE 编译 0 error、启动、dev 端到端处理请求；余文档/CI 子项 |
| AG-05 | Sandbox + offline + prebuilt piece 联合验证 | 🟢 **8/8 PASS**；基线经 **[D6](DECISIONS.md#d6) 降级**为 `SANDBOX_CODE_ONLY`+`STRICT`；**C-3 已实测**，余 C-1 待集群 |
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
- **运行时装包 = pnpm，不是 bun**（X-4 全环境禁 bun，已彻底落地）：`node-linker=isolated` **不可改**——
  引擎的 piece 加载器按 `pieces/<name>-<ver>/node_modules/<name>` 解析，hoist 就找不到（原 bun isolated 布局须逐字复刻）。
  旧文档里的 `bunfig.toml` / `minimumReleaseAge` 排障线索已失效（文件随去 bun 删除）。
- **气隙 piece 投放 = 两半**：**运行时半**（可执行包）预烘焙进镜像末层；**设计器半**（`piece_metadata` 行）
  **不在镜像里**，需对目标库跑 seed SQL 后**重启 AP**（registry 缓存在进程内存，直接写表不失效 ⇒ 症状是列表有、单查 404）。
  白名单唯一来源 = `activepieces/hermes/pieces.json`；`AP_PIECES_OFFLINE_INSTALL=true` 时闭包外依赖 fail-closed。
- **k8s 镜像是自建 vendored 镜像**（`activepieces:0.84.0-ee-removed`，EE 剥离+去 bun+预烘焙），
  **不是**上游二进制——**不要**再用 `mirror-thirdparty-images-k8s.ps1` 同步 AP（那条拉的镜像气隙下跑不通）。
- **flow 跨环境迁移键 = `metadata.hermesFlowKey`**（不是 flowId——**flowId 跨环境必变**）；
  引擎在**部署期**解析真实 flowId，故 BPMN 里不必按环境改绑。connection 仍不跟着迁，须目标环境预建同名。
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
| ~~8~~ | ~~去 bun 收尾 / 气隙 piece 闭环 / flow 跨环境迁移 + Q7~~ **✅ 已完成（2026-07-25~28）**：见「一句话现状」 | 工程 | — |
| 9 | 🔴 **气隙实证**：预烘焙镜像**尚未在真正断网的集群上验证过**（prod 红线，`activepieces.yaml` 里已标注） | 工程/运维 | 生产上线 |
| 10 | 🟡 **[D9](D9_PIECE_ONLINE_ADMIN_DRAFT.md) 评审**：piece 在线管理面（import/delete）在 prod 的开放条件 —— **未拍板前 prod 不得使用** | 架构/安全 | piece 管理面上 prod |

> **#2 合规评估仍是性价比最高且不在工程侧的待办**：若结论允许更宽用法，
> EE 剥离的重实现范围可能显著缩小。但在得到正式意见前，**一律按 R-A 推进**。

---

## 待决策清单

目前**无阻塞性待决策**——Q1–Q9、Q4a、D1–D8 均已裁决（见 [DECISIONS.md](DECISIONS.md)）。
以下为实施期需要在对应 Gate 内确定的技术选择，非架构级：

- AG-02.7：同步机制的具体实现（**Codegen 首选**；Canonical 已定 = vendored AP shared；
  **CI 新鲜度校验无条件强制**）；
- ~~Document 3.5 处置归类~~ **✅ 已完成**（20 组 G1–G20，见 [Doc3.5 §2](EE_REMOVAL_PLAN.md)）；
- ~~AG-05 降级阶梯落点~~ **✅ 已裁决**（[D6](DECISIONS.md#d6) → 第 3 级，补偿控制 C-1~C-4 已列，**实施未做**）；
- ~~AG-02.8：builder 组件跨 workspace 的交付方式~~ **✅ 已定案（2026-07-24）= 构建期拷贝**：`prebuild` 把 web-embed 产物拷进 DW `public/service-task-builder/`，DW 自身 nginx 提供，离线可用，产物不入库。
