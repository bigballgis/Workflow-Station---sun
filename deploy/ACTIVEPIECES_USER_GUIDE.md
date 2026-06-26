# Activepieces 自动化 — 新用户使用手册

面向**第一次用本平台 Activepieces(AP)功能**的人。读完你能:进 AP 搭一条自动化、在审批流程里调用它、
从测试环境发布到生产。技术原理见 [ACTIVEPIECES_INTEGRATION.md](ACTIVEPIECES_INTEGRATION.md),本文只讲**怎么用**。

---

## 0. 它是什么 / 能帮你做什么

AP 是个**可视化自动化工具**:你拖几个积木(发邮件、调 API、查数据库、OCR 识别…)拼成一条 **flow**,
平台的 BPMN 流程走到某一步时**自动触发**它、拿回结果继续往下走。

```
你的审批流程 ──走到"自动化节点"──▶ 触发 AP flow ──▶ 结果写回流程变量 ──▶ 流程继续
                                  (全程无人值守)
```

典型场景:报销流程走到"识别发票"节点 → AP 自动 OCR → 金额/日期回填表单 → 继续审批。

---

## 1. 访问地址一览(dev 环境)

| 你要做的事 | 地址 | 说明 |
|---|---|---|
| 平台总入口 | http://localhost:3000 | 边缘网关,登录后按角色进各端 |
| 管理中心(admin) | http://localhost:3100 | 系统管理员用;**AP 界面从这里进** |
| 开发工作台(设计器) | http://localhost:3000/dev (或 :3102) | 搭功能单元 / 画 BPMN 流程 |
| 用户门户(办事) | http://localhost:3000/portal (或 :3101) | 发起申请、办任务 |
| **AP 自动化界面** | http://localhost:8085/__ap/bridge | **不要直接开**,从 admin 菜单进(见 §2) |

> 生产环境:AP **不开界面**,只当后台 runtime 跑 flow。搭 flow 都在测试环境做,再发布到生产(见 §5)。

---

## 2. 进入 AP 界面(搭自动化的地方)

AP 界面不是直接打开的,要**从管理中心带着平台登录态跳进去**(单点登录桥):

1. 浏览器打开 **http://localhost:3100**,用**系统管理员**账号登录。
2. 左侧菜单点 **「Activepieces 自动化」**。
3. 新标签页打开 AP 界面,已自动登录(共享服务账号,管理员权限)。

> 进不去/白屏?见 §6 常见问题。**直接开 :8085 而不先登录平台**会看到 AP 原生登录页(无害,但进不去)。

---

## 3. 在 AP 里搭一条自动化 flow

以最简单的"回声"flow 为例(平台要求:**必须能同步返回结果**)。

1. AP 界面点 **新建(Create flow)**,起名,比如 `aptest`。
2. **触发器(Trigger)选 Webhook**(Catch Webhook)。这一步会生成一条 webhook 地址 —— 这就是平台调用它的入口。
3. 往后加积木(你的实际自动化逻辑:调 API、查库、识别…)。
4. **最后一步必须加 `Return Response`**(Webhook 积木里的"返回响应"),设置返回内容,例如:
   ```json
   { "echo": "{{trigger.body.text}}" }
   ```
   > ⚠️ **没有 Return Response,平台就拿不到结果**,流程会等到超时。这是同步调用的硬要求。
5. 右上角点 **Publish(发布)**,把 flow 状态变成 **ENABLED**。没发布的 flow 不会被触发。
6. 记下这条 flow 的 **Flow ID**(在 webhook 地址里,形如 `.../api/v1/webhooks/WIkVuyWuQuBcTblpChUeS`)。
   后面在流程里要填它。

---

## 4. 在审批流程里调用这条自动化(核心用法)

在**开发工作台的设计器**里,给 BPMN 流程加一个"自动化节点"。

1. 打开 **http://localhost:3000/dev**,进入(或新建)一个功能单元,编辑它的流程图。
2. 拖一个 **服务任务(Service Task)** 到画布,接进流程线里。
3. 选中它,右侧属性面板 → **服务类型** 选 **「Activepieces 自动化」**。
4. 填写:
   | 字段 | 填什么 |
   |---|---|
   | **AP Flow ID** | §3 记下的那个 id(如 `WIkVuyWuQuBcTblpChUeS`) |
   | **输入映射** | 流程变量 → AP 参数,例:`inputText` → `text` |
   | **输出映射** | AP 返回字段 → 流程变量,例:`echo` → `apResult` |
   | 超时 / 重试 | 默认 120 秒 / 3 次,按需调 |
5. **保存 + 部署/发布**这个功能单元。

部署时平台会自动把这个节点接到 AP 执行器上;流程一旦走到它,就会调
`http://<AP地址>/api/v1/webhooks/<你的flowId>/sync`,把输入映射的数据发过去、拿回结果按输出映射写回流程变量。

> **不用填完整 webhook 地址**:只填 Flow ID,完整地址由引擎按环境自动拼(测试/生产指向各自的 AP)。

---

## 5. 测试一下(端到端)

1. 去**用户门户** http://localhost:3000/portal,对刚部署的功能单元**发起一个新申请**,填上输入(如 `inputText`)。
2. 因为这是纯自动化节点,流程会**瞬间跑完**:触发 AP → 拿结果 → 写回 `apResult` → 结束。
3. 验证成功的标志:流程实例**已完成**,变量 `apResult` 里是 AP 返回的值。

> 如果流程里只有自动化节点、没有人工任务,它会几百毫秒内跑完,门户里看不到"下一步"——这是正常的(纯自动化)。
> 想要有交互/能看到结果,在自动化节点前后加**用户任务**(填表单 / 看结果),见 §6。

---

## 6. 测试 → 生产 发布通道

测试环境搭好、测好的 flow,用脚本发布到生产(AP 社区版没有自带的 Git 同步,所以走导出/导入)。
详见 [deploy/ap-flows/README.md](ap-flows/README.md)。

### 第 1 步:从测试环境导出 flow(提交 git)
```bash
PW=$(docker exec platform-admin-center-dev sh -c 'printf %s "$ACTIVEPIECES_SHARED_PASSWORD"')
docker exec -e AP_INTERNAL_URL=http://localhost:80 \
  -e ACTIVEPIECES_SHARED_EMAIL=hermes-svc@platform.local -e ACTIVEPIECES_SHARED_PASSWORD="$PW" \
  -e AP_FLOW=aptest -i platform-activepieces-dev node - \
  < deploy/scripts/ap-export.js > deploy/ap-flows/aptest.json
```
导出的 JSON **只含 flow 定义、不含密钥**。审阅 `git diff` 后提交。

### 第 2 步:发布到生产 —— 走 Jenkins
生产发布**由 Jenkins 流水线触发**(手动、带参数、prod 二次确认),不在本地手跑。
流水线模板:[ci/Jenkinsfile.ap-flows-publish](ci/Jenkinsfile.ap-flows-publish)。
它内部对每个 flow JSON 跑 `ap-import.js`,**幂等**地:目标没有就建、有就覆盖新版本 → 发布 → 启用,
并**打印目标环境的 flowId**。

### 第 3 步:两件必须做的事
1. **生产先建好同名 connection**:flow 里用到的凭据(API key 等)不会跟着导出,生产要**手动建同名连接**,否则 flow 跑不起来。
2. **更新生产流程的 Flow ID**:flow 在生产是**新的 id**(导入脚本会打印)。把生产环境 BPMN 节点的 **AP Flow ID** 改成这个新 id。

---

## 7. 常见问题 / 坑

| 现象 | 原因 / 怎么办 |
|---|---|
| **AP 界面进不去 / 白屏** | 必须先登录平台(admin),再从「Activepieces 自动化」菜单进;别直接开 :8085 |
| **流程卡在自动化节点不动** | AP flow 没加 **Return Response**,或没 **Publish(ENABLED)** |
| **门户里"没有下文"** | 纯自动化流程瞬间就跑完了(没人工任务);想有交互就加用户任务 |
| **申请编号(request id)是 "-"** | 功能单元没配"请求编号"生成规则(需要主表单 + 编号规则);自动化本身不受影响 |
| **生产 flow 跑不起来** | 生产没建同名 connection;或 BPMN 的 Flow ID 还是测试环境的旧 id |
| **AP 里删不掉某条 flow** | AP 社区版删除接口受限;在 AP 界面里删 |

---

## 速查

- **AP 界面**:admin(:3100)→ 菜单「Activepieces 自动化」
- **搭 flow 三要素**:Webhook 触发 + 你的逻辑 + Return Response → **Publish**
- **接进流程**:设计器 → 服务任务 → 类型「Activepieces 自动化」→ 填 Flow ID + 输入输出映射 → 部署
- **发布到生产**:`ap-export.js` 导出 → 提交 git → `ap-import.js` 导入 → 生产建 connection + 改 Flow ID
