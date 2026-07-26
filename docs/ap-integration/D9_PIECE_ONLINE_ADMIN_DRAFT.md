# D9(草案)— Piece 在线管理面治理与 C-2 重述

> **状态:草案 v3,待评审。未拍板前本文件不具约束力,[DECISIONS.md](DECISIONS.md) 现行裁决
> (D1「运行时安装依赖:禁止」、D6 补偿控制 C-2)继续有效——**即 prod 的 import/delete
> 在拍板前不得使用**(D9-7 分级开放条款亦如此约定)。**
> 起草 2026-07-26;v2 依据拓扑澄清重写;**v3 更正:admin-center 会部署到 prod,DW 不上 prod**。
> 评审通过后:正文并入 DECISIONS.md 作为 D9,同步修订 C-2 表述,本文件改为存根。

---

## 0. 部署拓扑前提(v3 更正)

| 组件 | dev / uat | prod |
|---|---|---|
| DW(设计器,含 Automation builder) | ✅ | ❌ |
| **admin-center(含 piece 管理面)** | ✅ | **✅** |
| user-portal / engine / AP 运行时 | ✅ | ✅ |

推论:

1. **prod 存在安装面**(admin-center 的 import/delete 端点可达 prod 的 AP)——与 D1/C-2
   的冲突真实存在,必须正面裁决,这是本 ADR 的核心;
2. **prod 无设计器** ⇒ piece 目录在 prod 的消费方只有「已部署 flow 的运行时加载」与
   「admin 管理页展示」;**启停(filteredPieceNames)在 prod 语义弱化**(没有设计器目录可藏),
   其主战场在 uat;
3. **prod 投放自研件可以免烘焙**:uat 定稿的同一 tarball 在 prod 经 admin-center 导入
   (ARCHIVE),P3 离线 store 保证装载过程零联网——这是本管理面对 prod 的核心价值,
   也是 C-2 需要重述的原因。

## 1. 背景

Admin Center「Automation Pieces」P1–P3 已实施并在 dev 端到端验证
(commits `bf133aaa` / `162e6862` / `ecaf032b` / `a602b51c`):
目录/导出(P1)、import/delete/启停(P2,经 AP API 代理 + 两个 HERMES-PATCH)、
气隙离线安装(P3:镜像烘 pnpm offline store,`AP_PIECES_OFFLINE_INSTALL=true` 时
`--offline` 安装,dev 仿真 registry 不可达全链 E2E 通过)。

## 2. 威胁模型:C-2 到底在防什么

D6 定位的残余风险:piece 代码(npm 包)跑在 worker/engine 进程内(uid=0),可开 raw
socket 绕过应用层 egress 代理。C-2 用「固定集合 + 无安装面」收敛该风险的**代码来源**。

在线安装面是否可接受,取决于四个问题,而非"有没有安装动作"本身:

1. **谁能装**——认证与授权;
2. **装什么**——包类型与依赖闭包;
3. **从哪来**——供应链可追溯性;
4. **装时碰不碰网**——X-3 零联网不变量。

**本 ADR 的实质:把 C-2 的保障从「静态冻结」重述为「受控供应链 + 单一经审计的安装面 +
安装过程零联网」——威胁收敛目标不变,达成机制升级。**

## 3. 裁决草案

| # | 条款 | 机制依据 |
|---|---|---|
| D9-1 | **单一安装面**:piece 在线导入/删除的唯一入口是 admin-center `/automation/pieces/*`(平台 JWT + SYS_ADMIN gate);AP 自身 `POST /v1/pieces`、`DELETE /v1/pieces` 不经 Kong `/api/ap` 对浏览器放行,保持 `platformAdminOnly`(共享服务账号专用)双保险;不得引入其它调用方(含脚本化"临时通道") | Kong 路由 + fastify securityAccess |
| D9-2 | **仅 ARCHIVE**:管理面只提供 ARCHIVE(上传 tarball)安装;REGISTRY 安装面永不开放(那才是"从 registry 拉任意包") | admin-center 硬编码 `packageType=ARCHIVE` |
| D9-3 | **供应链约束**:tarball 必须是仓库 CI `build-piece` 产物;导入时记录 SHA-256、上传人、时间(审计)。**prod 加严:仅接受与 uat 已导入记录(或 git `deploy/pieces/tarballs/` 留档)哈希一致的包**——prod 不是新代码的首发环境,只接受 uat 已验证的同一物料。**依赖闭包由离线 store 机械强制**:依赖超出 framework 闭包则 `--offline` 安装 fail-loud,无静默拉包路径 | P3 offline store 固有性质 + 审计记录 |
| D9-4 | **零联网不变量**:uat/prod `AP_PIECES_OFFLINE_INSTALL=true` + `NPM_CONFIG_REGISTRY` fail-closed;`--offline` 下 registry 值仅为缓存命名空间,无网络语义。**方案 C(运行时可达内网 Nexus)否决** | pkg-runner HERMES-PATCH + k8s env(a602b51c 已就位) |
| D9-5 | **C-2 重述**(拍板后修订 DECISIONS.md):由「piece 白名单冻结 + 离线预装 ⇒ 固定集合、无运行时安装面」改为「**受控集合:烘焙白名单(REGISTRY,镜像预装)+ 经 D9 安装面投放的自研 ARCHIVE 件;单一经审计的安装面;安装过程零联网;prod 仅接受 uat 已验证物料**」。C-1(NetworkPolicy)与 C-3(X-Service-Token)不受影响、仍为必须 | — |
| D9-6 | **prod 投放路径**:自研件在 dev/uat 在线迭代定稿后,**推荐经 prod admin-center 导入同一 tarball**(免烘焙免发版);烘焙管道(pieces.json 白名单 + prewarm + seed)保留为**官方件投放 / 气隙首装 / 灾备重建**路径,两者按 piece 类型分工(OFFICIAL=烘焙,CUSTOM=在线),不混用 | P1 导出物与 seed 工具链同构(实测) |
| D9-7 | **分级开放**:dev/uat 全功能即刻生效;**prod 在本 ADR 拍板前仅使用只读目录 + 导出**(不触碰执行面),拍板后随下一次发布开放 import/delete | — |
| D9-8 | **删除与启停语义**:删除=目录元数据下架(烘焙件的镜像内运行时包不受影响;白名单内的件重跑 seed 会重现,UI 已加重确认);启停=仅设计器目录可见性(list-only 过滤),存量 flow 的 `get()` 路径永不受影响。**prod 无 DW,启停仅影响 admin 展示,不作为 prod 的管控手段**(prod 下架手段=删除或回滚 flow) | HERMES-PATCH filterPieces list-only |
| D9-9 | **能力边界继承**:Q9 对 approval/todos 的禁令不因在线安装面松动——被裁决移出白名单的 piece 不得经本安装面重新投放,除非先行推翻原裁决 | Q9 |

## 4. 明确不在本 ADR 范围内

- **CODE step 的 npm 依赖**:现状 fail-closed,不改变;
- **AP 版本升级**:仍受 Q8(frozen baseline)约束;
- **piece 运行时沙箱与网络管控**:仍由 D6 + C-1/C-3 治理;
- **FU / flow 的跨环境晋级机制**:另属既有部署管线,本 ADR 只约束 piece 本体投放。

## 5. 评审清单(参照 Q9 重开评审的五项框架)

- [ ] **Use case**:prod 免烘焙投放(免发版窗口、免镜像推送)的收益 vs 在 prod 保留安装面的
      治理成本——若评审认为 prod 收益不足,退化方案=prod 管理页只读+导出、投放仍走烘焙
      (即 v2 立场,机制上随时可退);
- [ ] **权限模型**:SYS_ADMIN 单角色是否足够;**prod 导入是否要求四眼原则**(上传者≠批准者);
- [ ] **审计设计**:log.info 是否升格为审计表(候选 P4:`admin_ap_piece_audit`,含 SHA-256、
      环境、操作类型)——D9-3 的"prod 哈希比对 uat 记录"依赖它,**建议列为拍板前置**;
- [ ] **供应链校验落地形态**:哈希比对的权威源选 git 留档(`tarballs/`)还是 uat 审计表;
      是否需要 CI 签名;
- [ ] **数据生命周期**:file 表 ARCHIVE 孤儿行(删 piece 后遗留)是否需要清理任务;
- [ ] **prod 纵深(可选)**:AP community-piece 模块是否加环境开关(评审若对 prod 安装面
      仍有保留,可用此开关实现"代码在、面不开")。

## 6. 拍板后的落地清单

1. DECISIONS.md:收录 D9、修订 D6 表格 C-2 行、决策索引加行;
2. **P4(若审计表列为前置):`admin_ap_piece_audit` 表 + prod 导入哈希校验**(按
   init-scripts 只增不改规则新建编号 SQL);
3. PIECE_DEVELOPMENT_HOWTO.md:新增「在线投放(推荐)」章,§3.2–§7 离线链路标注为
   「官方件 / 气隙首装 / 灾备重建路径」;deploy/pieces/README.md 同步注记;
4. 发布 checklist(uat/prod):镜像含 offline store(`du /usr/src/app/pnpm-offline-store`
   ≈ 44MB)、导入冒烟(上传 → 目录可见 → 试运行 → 删除)。
