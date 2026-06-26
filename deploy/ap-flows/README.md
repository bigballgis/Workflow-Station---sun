# AP Flows — git 为源的 flow 发布通道(测试 → 生产)

Activepieces flow 的**定义**单一事实来源。非生产搭好 flow → 导出成 JSON 提交这里 →
脚本读 JSON 发布到生产 AP。对应 [ACTIVEPIECES_INTEGRATION.md](../ACTIVEPIECES_INTEGRATION.md) §7。

> AP CE 没有 Git Sync / project-releases(EE 功能,实测 404),所以走 **flow 导出/导入 API**。
> 各步已实测可用:`POST /flows`(201)→ `IMPORT_FLOW`(200)→ `LOCK_AND_PUBLISH`(200)→ `CHANGE_STATUS=ENABLED`(200)。

## 一个 flow 一个 JSON

`deploy/ap-flows/<displayName>.json` —— ap-export.js 产出,只含**跨环境可移植**的定义
(`displayName` + `trigger` 整棵树 + `schemaVersion` + 可选 `externalId`),**不含密钥**。

## 脚本

| 脚本 | 作用 |
|---|---|
| [../scripts/ap-export.js](../scripts/ap-export.js) | 非生产导出一条 flow → JSON(提交 git) |
| [../scripts/ap-import.js](../scripts/ap-import.js) | 读 JSON → 目标 AP **幂等**创建/覆盖 + 发布 + 启用 |

二者自包含(只用 node 内置 http,照 `ap-bootstrap-shared-account.js` 范式),靠环境变量配置:
`AP_INTERNAL_URL`、`ACTIVEPIECES_SHARED_EMAIL`、`ACTIVEPIECES_SHARED_PASSWORD`。

## 用法

### 导出(非生产 → git)
**首选走 Jenkins**:[../ci/Jenkinsfile.ap-flows-export](../ci/Jenkinsfile.ap-flows-export) —— 从指定非生产环境导出
(`FLOW=all` 全量 / 或单条)→ 写进 `deploy/ap-flows/` → 提交并推一个新分支 → 你去开 PR 评审合并。

本地一次性导出也行(dev,脚本经 stdin 喂进 AP 容器):
```bash
PW=$(docker exec platform-admin-center-dev sh -c 'printf %s "$ACTIVEPIECES_SHARED_PASSWORD"')
# 单条 → stdout 重定向到文件
docker exec -e AP_INTERNAL_URL=http://localhost:80 \
  -e ACTIVEPIECES_SHARED_EMAIL=hermes-svc@platform.local -e ACTIVEPIECES_SHARED_PASSWORD="$PW" \
  -e AP_FLOW=aptest -i platform-activepieces-dev node - \
  < deploy/scripts/ap-export.js > deploy/ap-flows/aptest.json
# 全量 → AP_FLOW=all + OUT_DIR(容器内路径,再 docker cp 出来)
# 审阅 diff → 提交 git
```

### 发布(到生产)—— 走 Jenkins(已定案)
生产发布由 **Jenkins 流水线**驱动(手动触发、带参数、prod 二次确认),不在本地手跑。
模板:[../ci/Jenkinsfile.ap-flows-publish](../ci/Jenkinsfile.ap-flows-publish)
(三处按环境填:agent / 各环境 AP_URL / Jenkins 凭据 id)。它内部就是对每个 flow JSON 跑:
```bash
AP_INTERNAL_URL=<目标AP> ACTIVEPIECES_SHARED_EMAIL=... ACTIVEPIECES_SHARED_PASSWORD=... \
node deploy/scripts/ap-import.js deploy/ap-flows/<flow>.json   # stdout 打印目标环境 flowId
```
> 本地想手动验证一条时也可直接跑上面这行(能直连目标 AP 的话)。

## 两个必须知道的坑

1. **connection 不跟着导**:flow 引用的 connection 是 per-环境凭据,导出 JSON **不含密钥**。
   **生产须预先建好同名 connection**,否则 flow 跑不起来(见 §11.5)。
2. **flowId 跨环境会变**:目标环境新建的 flow 有**新 id**。ap-import.js 末尾打印目标 flowId——
   BPMN service task 的 `ap:flowId` 要按目标环境填这个新 id(webhook base-url 由引擎按环境配、自动适配)。

## MVP 范围与三个待定项的取舍

当前是 §7 的 **MVP(方案 B:API 导出/导入脚本)**,已跑通。三个待定项的 MVP 决策:

- **① git 组织** → 一 flow 一 JSON,按 `displayName` 幂等对齐。
- **② 发布触发** → **Jenkins 流水线**(已定案,手动触发带参数;模板 `deploy/ci/Jenkinsfile.ap-flows-publish`)。
- **③ 生产 connection** → 手动预建同名 connection(暂不脚本化引导)。
