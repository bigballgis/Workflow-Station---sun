# Approved Activepieces pieces（离线投放，IKP 合规）

集群禁止外网下载，AP 已配 `AP_PIECES_SOURCE=DB` + `AP_PIECES_SYNC_MODE=NONE` +
fail-closed 的 `NPM_CONFIG_REGISTRY`（见 `deploy/ACTIVEPIECES_INTEGRATION.md` §9）。
本目录是**经审批的 piece 白名单**的唯一来源。pieces 均为 MIT 开源，镜像合法。

一个 piece 由两半组成，两半都要投放、版本必须一致：

| 半边 | 给谁用 | 投放方式 |
|---|---|---|
| **元数据**（actions/triggers 定义） | 设计器 UI | `metadata/pieces-seed.sql` 导入 `piece_metadata` 表 |
| **npm 包**（可执行代码） | worker 运行时 | 预装进 AP 镜像（本目录 `Dockerfile`） |

## 文件

- `pieces.json` — 白名单清单（name + version），**唯一需要手改的文件**
- `fetch-pieces.sh` — 在有外网的机器上下载 tarball + 元数据 JSON
- `tarballs/` — npm 包原件（审计留档；将来接 Nexus npm repo 时的发布源）
- `metadata/*.json` — AP 云 API 的完整元数据（版本已 pin）
- `generate-metadata-seed.js` — 由 metadata JSON 生成幂等的 `metadata/pieces-seed.sql`
- `prewarm-pieces.sh` — 在镜像构建时把 pieces 预装进 worker 的 bun workspace
- `Dockerfile` — `FROM activepieces:0.84.0` + 预装（运行时零联网）

## 首次执行（Quick Start）

> 仓库里已含全部产物（tarballs / metadata / seed SQL），**首次执行不需要外网**，
> 只有改 `pieces.json` 换白名单时才需要重新 fetch（见「新增 / 升级」一节）。

**dev（本机 docker compose）——两步：**

```sh
# 1. 构建自定义 AP 镜像（≈1-2 分钟，bun 装包在 build 时发生，机器需能访问 npm）
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml build activepieces
docker compose -f docker-compose.dev.yml up -d activepieces

# 2. 导入 piece 元数据 + 重启（重启不能省，见下方"注意"：registry 缓存在进程内存）
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < ../../pieces/metadata/pieces-seed.sql
docker restart platform-activepieces-dev
```

验证：`curl` 或浏览器打开 AP → 设计器里能看到 12 个 piece；或
`docker exec platform-activepieces-dev node -e "require('http').get('http://127.0.0.1:80/api/v1/pieces',r=>{let d='';r.on('data',c=>d+=c);r.on('end',()=>console.log(JSON.parse(d).length))})"`
应输出 `12`。浏览器要**硬刷新**（Cmd+Shift+R），否则可能吃到旧 JS 缓存。

**k8s / IKP 集群（uat、prod）——四步：**

```sh
# 1. 在有外网的机器上构建（基础镜像用 nexus3 的 mirror）并推 nexus3
docker build --build-arg BASE_IMAGE=nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/activepieces:0.84.0 \
  -t nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/activepieces:0.84.0-pieces \
  deploy/pieces
docker push nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/activepieces:0.84.0-pieces

# 2. deploy/k8s/activepieces.yaml 的 image: 改成 :0.84.0-pieces，kubectl apply
# 3. 对该环境的共享库执行 metadata/pieces-seed.sql（DBA / 发布流程）
# 4. kubectl rollout restart deployment/activepieces   # 同样因为内存缓存
```

集群前置检查：configmap 里 `ACTIVEPIECES_NPM_REGISTRY` 保持 fail-closed 的 `.invalid` 值即可
（pieces 已预装进镜像，运行时不会用到 registry）；`AP_PIECES_SOURCE=DB`、`AP_PIECES_SYNC_MODE=NONE`
已在 activepieces.yaml 里，无需另配。

## 原理（为什么运行时不联网）

worker 的 `piece-installer.ts` 装 piece 前先查
`cache/v11/common/pieces/<name>-<version>/` 下的 `ready` 标记 + `node_modules`；
两者都在就**直接跳过、bun 不启动、任何 registry 都不会被访问**。
`prewarm-pieces.sh` 在 docker build 时按 installer 的原样布局装好并写 `ready`，
所以运行时安装永远是 no-op。`NPM_CONFIG_REGISTRY` 的 fail-closed 值只是兜底防线。

## 新增 / 升级一个 piece

在**有外网的机器**（dev 笔记本）上：

```sh
cd deploy/pieces
# 1. 编辑 pieces.json 加 { "name": "@activepieces/piece-xxx", "version": "a.b.c" }
#    版本查 https://cloud.activepieces.com/api/v1/pieces （latest 均兼容 0.84）
sh fetch-pieces.sh                  # 2. 下载 tarball + 元数据
node generate-metadata-seed.js      # 3. 重新生成 seed SQL
git add -A                          # 4. 全部入库（含 tarball，都很小）
```

## 构建带 pieces 的镜像

```sh
cd deploy/pieces
docker build -t activepieces:0.84.0-pieces .
# 集群版（基础镜像换成 nexus3 的 mirror，然后照常推 nexus3）：
docker build --build-arg BASE_IMAGE=nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/activepieces:0.84.0 \
  -t nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/activepieces:0.84.0-pieces .
```

然后把 `deploy/k8s/activepieces.yaml` 的 `image:` 改成新 tag。
dev compose 想用同款：把 `docker-compose.dev.yml` 的 AP `image:` 改成 `activepieces:0.84.0-pieces`。

## 导入元数据（每个环境的库各做一次）

```sh
# dev
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < deploy/pieces/metadata/pieces-seed.sql
# uat/prod：由 DBA / 发布流程对共享库执行同一份 SQL
```

幂等（按 name+version 先删后插），重复执行安全。`piece_metadata` 里有什么，
设计器就只显示什么——这张表就是白名单本身。

**导入后必须重启 AP**（dev：`docker restart platform-activepieces-dev`；k8s：
`kubectl rollout restart deployment/activepieces`）。AP 把 piece registry 缓存在**进程内存**
（`piece-cache.ts` 的 `cachedRegistry`），只在走 AP 自身 API 装 piece 时经 Redis pubsub 失效；
直接 psql 写表不会触发。症状：列表 `/api/v1/pieces` 有、单查
`/api/v1/pieces/<name>` 404 `piece_metadata_not_found`（列表直查 DB、单查走缓存）。

## 注意

- **两半版本必须一致**：flow 引用 `name@version`，运行时按这个精确版本找预装目录。
- 升级 AP 镜像版本时重新确认 `cache/v11` 路径（`LATEST_CACHE_VERSION`，见
  `packages/server/worker/src/lib/cache/cache-paths.ts`）与 installer 布局未变。
- `logoUrl` 指向 cdn.activepieces.com，离线时设计器里图标裂开——纯外观问题。
- 当前白名单（10 个，全部**本地执行**、不调外部 SaaS）：
  - `piece-webhook`（catch_webhook 触发 + return_response，BPMN service-task 同步回包必需）
  - `piece-http`（发 HTTP 请求）、`piece-schedule`（定时触发）
  - 数据提取/处理：`piece-csv`（CSV↔JSON，**convert_excel_to_csv 即离线 Excel 提取入口**）、
    `piece-json`、`piece-xml`、`piece-pdf`（extractText/合并/转图片等）、
    `piece-file-helper`（读文件/改编码/zip/unzip）、`piece-text-helper`、`piece-data-mapper`
  - 没有独立的离线 Excel piece——Microsoft Excel 365 / Google Sheets 都是 SaaS 连接器（要调外网 API），
    按政策不引入；Excel 场景 = file-helper 读文件 → csv.convert_excel_to_csv → csv.convert_csv_to_json。
  - `piece-approval` + `piece-todos`（本地审批链接/待办，AP 内生成审批链接，不依赖外部 SaaS）。
- **「Approvals」标签页已用 `patch-web-approvals.js` 从镜像里补丁掉**（Dockerfile 最后一步）：
  它硬编码 6 个 SaaS piece（slack/discord/ms-teams/ms-outlook/gmail/telegram-bot，见源码
  `approvals-tab-content.tsx` 的 `APPROVAL_PIECES_CONFIG`），要求**全部**加载成功才渲染，
  白名单环境下 6 个全 404 → 无限骨架屏 + console 刷屏。该功能=「经 Slack/Teams/Gmail 发消息审批」，
  离线集群里本质不可用，故直接隐藏标签页并清空其查询。补丁 fail-loud：升级 AP 镜像后若 bundle
  变了，docker build 会在这一步报错，需对照新版源码重新校验两个正则。
- 剩余的 console 404 噪音（Explore 页热门推荐位查 `piece-ai` 等）不影响功能，不要为了消音引入 SaaS piece。
- dev compose 的 AP 服务已切到本镜像（带 `build: ../../pieces`，新同事首次
  `docker compose build activepieces` 即可本地构建，无需外部镜像仓库）。
