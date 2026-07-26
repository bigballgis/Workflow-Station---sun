# D9(草案)— Piece 在线管理面的环境边界与自研件 prod 投放路径

> **状态:草案 v2,待评审。未拍板前本文件不具约束力,[DECISIONS.md](DECISIONS.md) 现行裁决
> (D1「运行时安装依赖:禁止」、D6 补偿控制 C-2)继续有效。**
> 起草 2026-07-26;v2 修订依据:**部署拓扑澄清 —— DW 与 admin-center 不部署到 prod**。
> 评审通过后:正文并入 DECISIONS.md 作为 D9,本文件改为存根。

---

## 0. 部署拓扑前提(v2 关键输入)

**DW(Developer Workstation)与 admin-center 只部署到 dev / uat 等设计态环境,不上 prod。**
prod 只运行 user-portal、workflow-engine 与 AP 运行时(执行已投放的 flow)。

由此:**prod 上不存在 piece 在线管理面的任何调用方**——不是"被禁用",而是物理不存在。
D1/C-2 所防护的 prod 执行面,其代码投放通道与本裁决之前完全相同(烘焙镜像 + seed SQL)。
**C-2 对 prod 的原文表述因此无需修订**,本 ADR 的裁决对象缩小为两件事:

1. dev / uat 在线管理面(P1–P3 已实施)的治理条款;
2. 自研 ARCHIVE 件从 uat 定稿到 prod 的投放路径。

## 1. 背景

Admin Center「Automation Pieces」P1–P3 已实施并在 dev 端到端验证
(commits `bf133aaa` / `162e6862` / `ecaf032b` / `a602b51c`):
目录/导出(P1)、import/delete/启停(P2,经 AP API 代理 + 两个 HERMES-PATCH)、
气隙离线安装(P3,镜像烘 pnpm offline store + `AP_PIECES_OFFLINE_INSTALL`,
dev 仿真 registry 不可达 E2E 通过)。

P3 的实际受益环境是 **uat**:行内集群同样无公网(X-3),没有离线 store 的话,
uat 的在线导入在元数据提取那一步就会失败——P3 使「uat 在线迭代自研件」成立。

## 2. 威胁模型定位

D6 定位的残余风险:piece 代码(npm 包)跑在 worker/engine 进程内(uid=0),可开 raw
socket 绕过应用层 egress 代理。C-2 用「固定集合 + 无安装面」收敛该风险的**代码来源**。

拓扑澄清后的推论:

- **prod**:无安装面、无变化,C-2 原样成立;
- **dev / uat**:在线导入把"新代码进入执行面"从"烘镜像 + psql"缩短为"上传 tgz",
  但 uat 执行面与 prod 执行面**互不连通**(uat 装的件不会自动出现在 prod);
  uat 的风险敞口 = 已认证 SYS_ADMIN 上传的、依赖闭包被离线 store 机械限死的自研包。

## 3. 裁决草案

| # | 条款 | 机制依据 |
|---|---|---|
| D9-1 | **环境边界**:piece 在线管理面(import/delete/启停)仅存在于部署了 admin-center 的环境(dev/uat)。**prod 不部署 admin-center/DW,自然不存在在线安装面**;不得为 prod 引入任何等效调用方(含脚本化调用 AP install API 的"临时通道") | 部署拓扑(§0) |
| D9-2 | **单一安装面**(dev/uat 内):唯一入口 admin-center `/automation/pieces/*`(平台 JWT + SYS_ADMIN);AP 自身 `POST /v1/pieces`、`DELETE /v1/pieces` 不经 Kong `/api/ap` 对浏览器放行,保持 `platformAdminOnly`(共享服务账号专用)双保险 | Kong 路由 + fastify securityAccess |
| D9-3 | **仅 ARCHIVE**:管理面只提供 ARCHIVE(上传 tarball)安装;REGISTRY 安装面永不开放 | admin-center 硬编码 `packageType=ARCHIVE` |
| D9-4 | **供应链约束**:tarball 必须是仓库 CI `build-piece` 产物;导入时记录 SHA-256、上传人、时间。**依赖闭包由离线 store 机械强制**(uat):依赖超出 framework 闭包(pieces-framework/-common/shared/tslib)则 `--offline` 安装 fail-loud,无静默拉包路径 | P3 offline store 固有性质 |
| D9-5 | **零联网不变量**:uat/prod `AP_PIECES_OFFLINE_INSTALL=true` + `NPM_CONFIG_REGISTRY` fail-closed;prod 侧该开关为纵深防御(无调用方,但若表中意外出现 ARCHIVE 行,运行时也绝不触网)。**方案 C(运行时可达内网 Nexus)否决** | pkg-runner HERMES-PATCH + k8s env(a602b51c 已就位) |
| D9-6 | **prod 投放路径(唯一)**:自研件在 dev/uat 在线迭代;**定稿后回归离线烘焙管道投放 prod**——登记 `deploy/pieces/pieces.json` 白名单 → prewarm 烘镜像 → seed SQL(P1 导出的元数据 JSON 与 `deploy/pieces/metadata/piece-*.json` 逐字段同构,直接落盘对接 `generate-metadata-seed.js`,无需重跑本地序列化)。prod 的 `piece_metadata` 不接受 ARCHIVE 行 | P1 导出同构性(实测 diff 等价) |
| D9-7 | **删除与启停语义**:删除=目录元数据下架(烘焙件的镜像内运行时包不受影响;白名单内的件重跑 seed 会重现,UI 已加重确认);启停=仅设计器目录可见性(list-only 过滤),存量 flow 的 `get()` 路径永不受影响 | HERMES-PATCH filterPieces list-only |
| D9-8 | **能力边界继承**:Q9 对 approval/todos 的禁令不因在线安装面松动——被裁决移出白名单的 piece 不得经本安装面重新投放,除非先行推翻原裁决 | Q9 |

## 4. 明确不在本 ADR 范围内

- **CODE step 的 npm 依赖**:现状 fail-closed,不改变;
- **AP 版本升级**:仍受 Q8(frozen baseline)约束;
- **piece 运行时沙箱与网络管控**:仍由 D6 + C-1/C-3 治理;
- **FU / flow 的跨环境晋级机制**:另属既有部署管线,本 ADR 只约束 piece 本体的投放。

## 5. 评审清单

- [ ] **拓扑确认**:uat 是否与 prod 同为无公网集群(决定 P3 offline store 是否 uat 刚需——按 X-3 推定是,请确认);
- [ ] **D9-6 唯一路径**:是否需要为 prod 保留"紧急 ARCHIVE 投放"旁路(DBA 直灌 file 表 + metadata 行 + P3 离线装)?草案立场:**不保留**,紧急修复也走烘焙链路(可加急构建),避免 prod 出现两条投放通道;
- [ ] **权限模型**:dev/uat 的 SYS_ADMIN 单角色是否足够(uat 是否需要上传者≠批准者的四眼原则);
- [ ] **审计设计**:log.info 是否升格为审计表(候选 P4:`admin_ap_piece_audit`,含 SHA-256);
- [ ] **供应链校验**:"CI 产物"约束目前靠流程,是否需要技术校验(CI 签名 / 后端比对哈希与 git 留档);
- [ ] **数据生命周期**:uat file 表中 ARCHIVE 孤儿行(删 piece 后遗留)是否需要清理任务;
- [ ] **prod 纵深(可选)**:是否在 prod 的 AP 上以 env 开关直接不注册 community-piece 模块(彻底移除 install/delete 端点)——现状靠"无调用方 + platformAdminOnly + Kong 不放行"三层,已可接受,此项为加固备选。

## 6. 拍板后的落地清单

1. DECISIONS.md:收录 D9;D6 表格 C-2 行**加注**(不改原文):「在线管理面的环境边界与
   uat 治理见 D9;prod 投放管道不变」;决策索引加行;
2. PIECE_DEVELOPMENT_HOWTO.md:新增「dev/uat 在线投放(推荐迭代路径)」章,
   §3.2–§7 离线链路标注为「prod 投放 / 气隙首装路径」;deploy/pieces/README.md 同步注记;
3. 若评审勾选 prod 纵深加固项:community-piece 模块加环境开关(HERMES-PATCH);
4. uat 发布 checklist:镜像含 offline store(`du /usr/src/app/pnpm-offline-store` ≈ 44MB)、
   在线导入冒烟(上传 → 目录可见 → 试运行 → 删除)。
