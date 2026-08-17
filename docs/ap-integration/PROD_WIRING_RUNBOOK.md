# AP 接线运维手册（admin-center ↔ Activepieces）

> **适用范围：uat / preprod(sit) / prod 全部环境**，不只是生产。
> uat 与 preprod 的 ConfigMap 已有 `ACTIVEPIECES_BRIDGE_ENABLED: "true"`（AP UI 对用户开着），
> 它们同样需要走完本手册——**共享账号已从 admin-center 移除**，没有签名密钥就没有任何 AP 身份。
>
> **要解决的问题**：签名密钥（`ACTIVEPIECES_MANAGED_SIGNING_KEY_ID` + `ACTIVEPIECES_MANAGED_PRIVATE_KEY`）
> 尚未在任何环境生成投放。缺失时 admin-center **拿不到 AP 身份**，
> Automation Flow Migration、Piece 管理、登录桥 `/internal/ap/*` 点开即失败
> （`ServiceTaskApiClient#signInManaged` 直接抛 `ACTIVEPIECES_API_ERROR`）。
> 流程执行本身不受影响——引擎打 `/api/v1/webhooks/{flowId}/sync` 是公开端点，不需要认证。
>
> ⚠️ **不要照搬"admin-center.yaml 里没有 ACTIVEPIECES 关键字 ⇒ 连不上 AP"这个判断**（本文早期版本的错误）：
> `admin-center.yaml` 用 `envFrom: configMapRef + secretRef` 整体注入
> `workflow-platform-config` / `workflow-platform-secrets`，所以 Deployment 里看不到 AP 变量是正常的，
> `ACTIVEPIECES_INTERNAL_URL` 早已在两个环境的 ConfigMap 里配好。**新变量只加进 ConfigMap / Secret 即可**。
>
> **本手册的前提**：身份模型已改为 **managed-authn 按操作人**（无共享账号，见
> [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md)）。因此下面**不需要**共享账号，
> 也**不需要**在生产上跑 `ap-bootstrap-shared-account.js`。
>
> 遵循 [D11](DECISIONS.md#d11)：签名密钥的生成与投放**由人执行**，不做成 Job
> （私钥只产生一次；给 Job 写 Secret 的 RBAC 在气隙/合规集群未必批得下来，且重跑会轮换掉在用密钥）。

---

## 0. 前置检查

```bash
NS=<namespace>

# AP 已就绪
kubectl -n $NS get deploy activepieces -o jsonpath='{.status.readyReplicas}{"\n"}'
kubectl -n $NS exec deploy/activepieces -- curl -sf localhost/api/v1/flags -o /dev/null -w '%{http_code}\n'

# AP 库里已有 platform（首次部署后由 AP 自身迁移 + 首个身份创建产生）
kubectl -n $NS exec deploy/<postgres-or-jump-pod> -- \
  psql -U <user> -d <apdb> -tAc 'SELECT id, "ownerId" FROM platform ORDER BY created LIMIT 1;'
```

若 `platform` 表为空 ⇒ 这是全新库，先做 **附录 A：空库首次初始化**，再回到第 1 步。

---

## 1. 生成签名密钥（本地，不碰 AP）

AP 的 `signing_key` 表**只存公钥**，私钥从不回写、也从不被 AP 重新解析
（见 `automation/packages/server/api/src/app/signing-key/signing-key-generator.ts` 的 HERMES 注释）。
因此**不需要登录 AP、不需要调 `POST /v1/signing-keys`**，本地生成后把公钥写库即可。

格式必须与 AP 生成器一致：**RSA 4096**、**公钥 PKCS#1**、**私钥 PKCS#8**
（私钥用 PKCS#8 是 HERMES 的有意分歧：admin-center 是 Java，`PKCS8EncodedKeySpec` 原生可解，
不必为解 PKCS#1 引入 BouncyCastle——气隙 X-3）。

```bash
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out ap-managed-private.pkcs8.pem
openssl rsa -in ap-managed-private.pkcs8.pem -RSAPublicKey_out -out ap-managed-public.pkcs1.pem

# 自检：两者必须配对
openssl rsa -in ap-managed-private.pkcs8.pem -RSAPublicKey_out | diff - ap-managed-public.pkcs1.pem && echo "keypair OK"
head -1 ap-managed-private.pkcs8.pem   # 必须是 -----BEGIN PRIVATE KEY-----      (PKCS#8)
head -1 ap-managed-public.pkcs1.pem    # 必须是 -----BEGIN RSA PUBLIC KEY-----  (PKCS#1)
```

> **本节已实测（2026-08-14）**：上述命令产出的格式头正确、密钥对匹配；并模拟了完整链路——
> **以 PKCS#8 私钥签一个带 `kid` 的 RS256 JWT，再用 PKCS#1 公钥验签，结果 PASS**。
> 这正是运行时形态：admin-center（Java）持私钥签发外部令牌，AP 按 `kid` 从 `signing_key`
> 取公钥验签。两种编码互通，无需转换。

⚠️ 私钥文件用完即删（第 3 步落进 Secret 之后）。不要提交进仓库、不要贴进工单。

---

## 2. 把公钥写进 AP 的 `signing_key` 表

`id` 是 21 位 nanoid 形状（`ApIdSchema` length=21）。生成一个：

```bash
KEY_ID=$(head -c 32 /dev/urandom | base64 | tr -d '+/=' | head -c 21)
echo "KEY_ID=$KEY_ID"     # 记下来，第 4 步 configmap 要用
```

```bash
PLATFORM_ID=$(kubectl -n $NS exec deploy/<pg-pod> -- \
  psql -U <user> -d <apdb> -tAc 'SELECT id FROM platform ORDER BY created LIMIT 1;' | tr -d ' ')

kubectl -n $NS exec -i deploy/<pg-pod> -- psql -U <user> -d <apdb> <<SQL
INSERT INTO signing_key (id, created, updated, "displayName", "platformId", "publicKey", algorithm)
VALUES ('$KEY_ID', now(), now(), 'hermes-managed-authn', '$PLATFORM_ID',
        \$\$$(cat ap-managed-public.pkcs1.pem)\$\$, 'RSA');
SQL

# 复核
kubectl -n $NS exec deploy/<pg-pod> -- psql -U <user> -d <apdb> \
  -tAc "SELECT id, \"displayName\", algorithm FROM signing_key;"
```

> 用 `$$...$$` 包裹 PEM，避免换行与单引号转义问题。

---

## 3. 私钥进 Secret

```bash
kubectl -n $NS patch secret workflow-platform-secrets --type merge -p \
  "{\"stringData\":{\"ACTIVEPIECES_MANAGED_PRIVATE_KEY\":\"$(awk '{printf "%s\\n", $0}' ap-managed-private.pkcs8.pem)\"}}"

# 复核（只看长度，不打印内容）
kubectl -n $NS get secret workflow-platform-secrets \
  -o jsonpath='{.data.ACTIVEPIECES_MANAGED_PRIVATE_KEY}' | base64 -d | wc -c

shred -u ap-managed-private.pkcs8.pem 2>/dev/null || rm -P ap-managed-private.pkcs8.pem
```

---

## 4. 填 ConfigMap / Secret（**不要动 `admin-center.yaml`**）

admin-center 用 `envFrom` 整体注入 `workflow-platform-config` 与 `workflow-platform-secrets`，
所以变量只需落在这两处，Deployment 一行都不用改（见本文开头的 ⚠️）。
**本仓库规则**：改环境变量必须同一会话内同步 ConfigMap/Secret（见 `deploy/CONFIG_SYNC.md`）。

**ConfigMap**（`deploy/k8s/config_map/<env>/configmap-workflow-platform-config.yml`）——
uat / preprod 的键位已就位（2026-08-14 补），只需把占位符换成真值：

```yaml
  # 第 2 步记下的 KEY_ID。uat/preprod 现为 CHANGE_ME_{UAT,SIT}_AP_SIGNING_KEY_ID
  ACTIVEPIECES_MANAGED_SIGNING_KEY_ID: "<第 2 步记下的 KEY_ID>"
  # 必须与第 5 步 stamp 的值一致（应用侧默认也是 hermes-main）
  ACTIVEPIECES_MANAGED_PROJECT_EXTERNAL_ID: "hermes-main"
```

**Secret**（`deploy/k8s/secret/<env>/secret-workflow-pal*t*form.yml`，`stringData`）：

```yaml
  # 第 1 步的 PKCS#8 私钥，换行写成字面量 \n
  ACTIVEPIECES_MANAGED_PRIVATE_KEY: "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

其余相关键的现状（无需改动）：`ACTIVEPIECES_INTERNAL_URL` 两个环境都已配
`http://activepieces-service:80`；`ACTIVEPIECES_BRIDGE_ENABLED` 在 uat/preprod 为 `"true"`
（非生产开放 AP UI），生产环境应为 `"false"`；`MANAGED_PLATFORM_ROLE` / `MANAGED_PROJECT_ROLE`
应用侧默认已是 `ADMIN` / `Admin`，不配即可。

> `ACTIVEPIECES_SHARED_EMAIL` / `_PASSWORD` **保留但 admin-center 不再读取**——
> 空库引导 Job（走 sign-up 建 AP 第一个身份）与 `deploy/scripts/ap-{export,import}.js` 仍依赖它们。
> 不要因为"共享账号已移除"就把它们删掉，那会打断引导与迁移脚本。

---

## 5. 给共享 project 打 externalId

managed-authn 按 `externalId` 找共享 TEAM project；**stamp 必须早于第一次 managed 换取**，
否则每个用户会各自得到一个新 project，侧栏出现重名的 "Personal Project"。

```bash
kubectl -n $NS exec -i deploy/<pg-pod> -- psql -U <user> -d <apdb> <<'SQL'
UPDATE project SET "externalId" = 'hermes-main'
WHERE id = (SELECT id FROM project WHERE type = 'TEAM' ORDER BY created LIMIT 1)
  AND ("externalId" IS NULL OR "externalId" <> 'hermes-main');
SELECT id, "displayName", type, "externalId" FROM project ORDER BY created;
SQL
```

> ⚠️ 被 stamp 的 project **必须 `type='TEAM'`**。stamp 到 PERSONAL 上会让侧栏出现两个同名
> "Personal Project"（历史踩坑）。

---

## 6. 灌 piece 元数据 seed

`AP_PIECES_SYNC_MODE=NONE`（代码默认已是 fail-closed），没有任何人会自动填 `piece_metadata`。

```bash
kubectl -n $NS exec -i deploy/<pg-pod> -- psql -U <user> -d <apdb> \
  < deploy/pieces/metadata/pieces-seed.sql

kubectl -n $NS exec deploy/<pg-pod> -- psql -U <user> -d <apdb> \
  -tAc 'SELECT count(*), count(DISTINCT name) FROM piece_metadata;'   # 期望 13 / 13
```

seed 走 API 之外的直连写库，**不会触发 AP 的目录缓存失效**——灌完重启 AP：

```bash
kubectl -n $NS rollout restart deploy/activepieces
kubectl -n $NS rollout status  deploy/activepieces
```

> 经 AC 的 Piece 管理页 import 则**不需要**重启：那条路走 AP 的 `POST /v1/pieces`，
> `publishCacheRefresh` 默认 true，会往 Redis 频道 `piece-registry-invalidation` 发失效。

---

## 7. 应用与验证

```bash
kubectl -n $NS apply -k deploy/k8s        # 或既有发布流程
kubectl -n $NS rollout restart deploy/admin-center
kubectl -n $NS rollout status  deploy/admin-center
```

逐项验收：

| 检查 | 命令 / 动作 | 期望 |
|---|---|---|
| AC 起得来 | `kubectl -n $NS logs deploy/admin-center \| grep -i activepieces` | 无认证错误 |
| 签名密钥可用 | 以 SYS_ADMIN 打开 AC → **Automation Flows** | 列表正常返回（不是 401/500） |
| 归属到人 | 在 AC 停用再启用某条 flow，然后查 AP 审计 | 事件的 user 是**你本人**，不是任何共享账号 |
| Piece 目录 | AC → **Automation Pieces** | 13 个白名单件在列 |
| 执行链路未受影响 | 触发一条带 AP Service Task 的流程 | 变量正常回写 |

> 也可用只读脚本探缺口：`deploy/scripts/ap-verify-provisioning.js`。
> ⚠️ **该脚本目前仍用共享账号登录取 token**，在去共享账号之后需要改造成 managed 签名
> （或临时以 SYS_ADMIN 的浏览器会话手工核对上表）。**改造前不要依赖它的结论。**

---

## 附录 A：空库首次初始化

全新库（`platform` 表为空）时，AP 里还没有任何身份，而 managed-authn 需要 platform 才能工作——
存在鸡生蛋。按序执行：

1. **建第一个身份与平台**：`POST /v1/authentication/sign-up` 在**平台尚不存在**时仍被放行
   （HERMES-PATCH-022 只拒绝已解析到平台的请求，正是为保住这条路），随后
   `POST /v1/platforms` 建平台。这两步已封装在 `deploy/scripts/ap-bootstrap-shared-account.js`。
2. 该脚本创建的账号**仅用于完成初始化**。初始化后：本手册第 1–5 步做完，
   managed-authn 即成为唯一身份路径，该账号不再被任何代码使用，可在 AP 侧禁用。
3. 若合规要求连这个初始账号都不能存在，替代方案是直接向 `user_identity` / `user` / `platform` /
   `project` 四张表插入初始行——**成本高、易与 AP 迁移冲突，不推荐**，仅在合规明确要求时采用。

---

## 附录 B：轮换签名密钥

1. 按第 1 步生成新密钥对，第 2 步插入**新的一行**（不要 UPDATE 旧行）。
2. 更新 Secret 的私钥与 ConfigMap 的 `ACTIVEPIECES_MANAGED_SIGNING_KEY_ID`，重启 admin-center。
3. 观察一个换取周期（managed token TTL，默认见 `service-task.managed.tokenTtlSeconds`）无 401 后，
   再删除旧的 `signing_key` 行。

> 顺序不可颠倒：先删旧行会让**尚未刷新的 token 立刻 401**——AP 按 JWT header 的 `kid`
> 查 `signing_key`，查不到直接拒绝。
