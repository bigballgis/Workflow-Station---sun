# D9(草案)— Piece 在线管理面与「运行时禁止安装」边界的重划

> **状态:草案,待评审。未拍板前本文件不具约束力,[DECISIONS.md](DECISIONS.md) 现行裁决
> (D1「运行时安装依赖:禁止」、D6 补偿控制 C-2)继续有效——即 prod 环境的
> import/delete 能力在评审通过前不得开放。**
> 起草:2026-07-26。评审通过后:本文正文并入 DECISIONS.md 作为 D9,同步修订 C-2 表述,
> 本文件改为指向 DECISIONS.md 的存根。

---

## 1. 触发与背景

Admin Center「Automation Pieces」管理面 P1–P3 已实施并在 dev 端到端验证
(commits `bf133aaa` / `162e6862` / `ecaf032b` / `a602b51c`):

| 能力 | 机制 | 现状 |
|---|---|---|
| 目录 / 导出 | 只读查 `piece_metadata`;导出物与 `deploy/pieces/metadata/*.json` 同构 | dev 验证通过,**不触碰执行面** |
| 导入(在线安装) | 上传 build-piece tgz → 代理 AP `POST /v1/pieces`(ARCHIVE)→ pubsub 免重启 | dev 验证通过 |
| 删除 | flow 引用检查 → 代理 AP `DELETE /v1/pieces`(HERMES-PATCH 新增) | dev 验证通过 |
| 启停 | `platform.filteredPieceNames`(list-only 过滤,get 不滤) | dev 验证通过,**不触碰执行面** |
| 气隙离线安装 | 镜像烘 pnpm offline store + `AP_PIECES_OFFLINE_INSTALL=true`(`--offline`) | dev 气隙仿真(registry 不可达)E2E 通过 |

**冲突点(必须正面裁决,不得默认生效)**:

- **D1**:「运行时安装依赖:**禁止**」——在线导入正是运行时向 worker 执行面投放代码;
- **D6 / C-2**:「piece 白名单冻结 + 离线预装 ⇒ 进程内可执行的第三方代码是**已评审的固定集合**,
  **无运行时安装面**」——C-2 是沙箱降级(`SANDBOX_CODE_ONLY`)后的**安全补偿控制**,
  不是普通工程约定,松动它必须给出等效的替代控制并重新评审。

## 2. 威胁模型:C-2 到底在防什么

D6 已把残余风险定位得很精确:**piece 代码(npm 包)跑在 worker/engine 的 Node 进程内、
uid=0,可开 raw socket 绕过应用层 egress 代理**(CODE step 反而无网络原语,不是风险面)。
C-2 用「固定集合 + 无安装面」收敛的是这个面的**代码来源**。

因此在线安装面是否可接受,取决于四个问题,而不是"有没有安装动作"本身:

1. **谁能装**——安装入口的认证与授权;
2. **装什么**——包的类型与来源约束;
3. **装的东西从哪来**——供应链可追溯性;
4. **装的时候碰不碰网**——X-3 零联网不变量。

P1–P3 的实现对这四问已各有一个技术答案(§3 将其升格为裁决条款)。
**本 ADR 的实质:把 C-2 的保障从「静态冻结」重述为「受控供应链 + 单一经审计的安装面 +
安装过程零联网」——威胁收敛目标不变,达成机制升级。**

## 3. 裁决草案

| # | 条款 | 机制依据 |
|---|---|---|
| D9-1 | **单一安装面**:piece 在线导入/删除的唯一入口是 admin-center `/automation/pieces/*`(平台 JWT 认证 + SYS_ADMIN gate);AP 自身的 `POST /v1/pieces`、`DELETE /v1/pieces` **不经 Kong `/api/ap` 对浏览器放行**,且保持 `platformAdminOnly`(共享服务账号专用)双保险 | Kong 路由现状 + fastify securityAccess |
| D9-2 | **仅 ARCHIVE**:管理面只提供 ARCHIVE(上传 tarball)安装;**REGISTRY 安装面永不开放**(那才是"从 registry 拉任意 npm 包") | admin-center 后端硬编码 `packageType=ARCHIVE` |
| D9-3 | **供应链约束**:上传的 tarball 必须是仓库 CI `build-piece` 产物;admin-center 在导入时记录 SHA-256、上传人、时间(审计日志);`deploy/pieces/tarballs/` 的留档职责由审计记录接替(git 留档可并行保留)。**依赖闭包由离线 store 机械强制**:tarball 依赖若超出 framework 闭包(pieces-framework/-common/shared/tslib),`--offline` 安装直接失败(fail-loud),不存在静默拉取第三方包的路径 | P3 offline store 的固有性质 |
| D9-4 | **零联网不变量保持**:prod/uat `AP_PIECES_OFFLINE_INSTALL=true` + `NPM_CONFIG_REGISTRY` 维持 fail-closed;`--offline` 下 registry 值仅为缓存命名空间,无网络语义。**方案 C(集群运行时可达内网 Nexus)被否决**,不得作为替代实现 | pkg-runner HERMES-PATCH + Dockerfile 烘焙 |
| D9-5 | **C-2 重述**(评审通过后修订 DECISIONS.md 原文):由「piece 白名单冻结 + 离线预装 ⇒ 固定集合、无运行时安装面」改为「**受控集合:烘焙白名单(REGISTRY,镜像预装)+ 经 D9 安装面投放的自研 ARCHIVE 件;单一经审计的安装面;安装过程零联网**」。C-1(NetworkPolicy)与 C-3(X-Service-Token)**不受影响、仍为必须** | — |
| D9-6 | **分级开放**:dev / uat 全功能;**prod 第一期仅开放只读目录 + 导出 + 启停**(三者均不触碰执行面,C-2 现行表述下即合规);prod 的 import / delete 在本 ADR 拍板后随下一次发布开放 | — |
| D9-7 | **删除与启停语义**:删除=目录元数据下架(镜像内烘焙运行时包不受影响;白名单内的件重跑 seed 会重现,UI 已加重确认);启停=仅设计器目录可见性(list-only 过滤),存量 flow 的加载与运行(`get()` 路径)**永不受影响** | HERMES-PATCH filterPieces list-only |
| D9-8 | **能力边界继承**:Q9 对 approval/todos 的禁令不因在线安装面而松动——任何被裁决移出白名单的 piece,不得经 D9 安装面重新投放,除非先行推翻原裁决 | Q9 |

## 4. 明确不在本 ADR 范围内

- **CODE step 的 npm 依赖**:现状即 fail-closed(registry 不可达则装不上),本 ADR 不改变;
  若未来需要 CODE-step 依赖白名单,另立裁决。
- **AP 版本升级 / 上游跟随**:仍受 Q8(frozen baseline)约束。
- **piece 运行时的沙箱与网络管控**:仍由 D6 及其补偿控制 C-1/C-3 治理。

## 5. 评审清单(参照 Q9 重开评审的五项框架)

- [ ] **Use case**:自研件迭代频率与"烘焙链路 7 步"的运维成本是否证成一个在线安装面;
- [ ] **权限模型**:SYS_ADMIN 单角色是否足够,是否需要四眼原则(上传者 ≠ 批准者);
- [ ] **审计设计**:log.info 是否升格为审计表(建议 P4:`admin_ap_piece_audit`,含 SHA-256);
- [ ] **供应链**:D9-3 的"CI 产物"约束目前靠流程,是否需要技术校验(如 CI 签名、
      或后端比对 tarball 哈希与 git 留档);
- [ ] **数据生命周期**:file 表中 ARCHIVE 孤儿行(删除 piece 后遗留)是否需要清理任务。

## 6. 拍板后的落地清单

1. DECISIONS.md:收录 D9 正文、修订 D6 表格中 C-2 行、决策索引加行;
2. `deploy/k8s/activepieces.yaml` 的 `AP_PIECES_OFFLINE_INSTALL=true` 已就位(a602b51c),无需改动;
3. PIECE_DEVELOPMENT_HOWTO.md:新增「在线投放(推荐)」章,把 §3.2–§7 的离线链路标注为
   「气隙首装 / 灾备重建路径」;deploy/pieces/README.md 同步注记;
4. prod 发布 checklist:确认镜像含 offline store(`du /usr/src/app/pnpm-offline-store`)、
   导入冒烟(上传→目录可见→试运行→删除)。
