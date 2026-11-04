# FU50125：独立 Assignee Email 与脚本配置

2026-11-04，按用户后续授权修改实际本地 FU50125（MCY dual binding 1101 1793499506034），不是仅改隔离示例。

## 已完成

- 交易表 50590 新增 nullable VARCHAR(255) 字段 `assignee_email`，显示名 Assignee Email。
- 保留 `merchant_name` 及全部既有字段的数据类型、主外键和引用契约；没有把商户字段当邮箱存储。
- Assignment 表单 50898：普通子表编辑和 Batch Form 都使用同一段优化脚本，邮箱控件绑定真正的 `assignee_email`，设置对应 binding 的 EDITABLE 权限。
- Sub Case 表单 50899：同一交易绑定增加只读邮箱展示，未开放下游编辑或添加会写只读字段的事件。
- 使用官方保存/发布接口发布到本地，部署 ID `4143cbcb-698f-4680-93c4-1575628c1b00`。原 BPMN 未改，邮件监听保持禁用。未发布 DEV。
- 设计更新前保存 `.codex-mcy-email-backup-*.json`，原始调查备份 `.codex-mcy-email-before.json` 保留；这些本地元数据不提交。

## 验证结果

- 原 Markdown 脚本运行相同用例：4 失败 / 1 通过；优化脚本：5 项全部通过。覆盖完整 Lookup 对象、字符串 ID、缺邮箱的部分对象、缺失邮箱、HTTP 失败、清空及旧错误清除，并核对商户值保留。
- 相关回归 DW119 / Portal276 通过；新增配置用例已加入 `regression:batch-events` 的单测列表。此次没有新增产品运行时代码。Portal 类型诊断仍为380项既有错误，无新增。
- 在实际 FU50125 新发布版本创建新请求，使用真实 MANAGER 工作区正常 Claim；未改角色成员或绕过办理权限。
- 浏览器真实 Lookup 选择 Developer，独立邮箱输入 DOM 正确；两行 Batch Apply 的真实提交 200，刷新后 GET 确认两行 email 及人员身份正确，第三行保持未分派，三行 merchant_name 原值保留。
- 普通 Edit：清除 Lookup 后邮箱清空，重新选人后邮箱回填；Cancel 后业务值与保存基线一致。页面自身自动保存会更新 `_wsRowVersion` / updated_at / updated_by，因此取消比较单独剔除这些元数据，并未据此声称页面没有任何后台保存。
- 数据库 `up_process_instance.variables.__subTables__['dw:hmdc_transaction_copy5']` 回读：EMAIL-QA-CARD-1/2 的独立邮箱为所选人员邮箱，EMAIL-QA-CARD-3 的人员及邮箱为空；各行 EMAIL-QA-MERCHANT-1/2/3 保留。
- 浏览器无未捕获异常。测试请求已正常 Unclaim 回到 Department Manager 池，未完成 Transaction Assignment，也没有推进到 MI 审批。

实际测试请求：

- process：`b8efc362-d82b-11f1-a1af-aeb2e07f8cc8`
- task：`b9001662-d82b-11f1-a1af-aeb2e07f8cc8`
- [打开新版本验证任务](http://localhost:3000/portal/tasks/b9001662-d82b-11f1-a1af-aeb2e07f8cc8)，Manager 用户正常 Claim 后可继续测试。

**旧任务 `1163b550-d826-11f1-a1af-aeb2e07f8cc8` 的冻结配置未替换，刷新旧任务不会自动变成新配置。** 本次没有直接写冻结快照或数据库基线；请用新版本请求验证。

## 截图和日志

- [实际 FU Batch 回填](../../frontend/user-portal/verification-screenshots/2026-11-04-mcy-actual-email-batch.png)
- [实际 FU 普通编辑回填](../../frontend/user-portal/verification-screenshots/2026-11-04-mcy-actual-email-ordinary.png)
- 本地日志：`.codex-mcy-email-configured.log`、`.codex-mcy-email-deploy.log`、`.codex-mcy-email-browser-final2.log`、`.codex-mcy-email-db.log`、`.codex-mcy-email-released.log`。中间失败记录保留，不将它们当通过证据。

## 脚本

优先使用 Lookup 行内的有效 email，减少重复查询；输入是 ID 或对象缺邮箱时才查询现有用户接口。清空/重新选择会清除旧邮箱和错误。业务失败走 setFieldError 阻止提交；异步返回 Promise 并核对当前人员身份，运行时继续承担取消与失效写入保护。

外部可直接复制的正文文件：`F:/hermes1.0/31.1299,script error/assignee-email.optimized.js`。FU 内保存的值使用既有 `$FNX:` 包装。脚本源为 `frontend/scripts/fixtures/assignee-email-script.mjs`。

```javascript
var api = $inject.api;
var selected = $inject.args[0];
var source = 'assignee_id';
var target = 'assignee_email';

function userId(value) {
  if (typeof value === 'string') return value.trim();
  if (value && typeof value === 'object' && !Array.isArray(value) && typeof value.id === 'string') return value.id.trim();
  return '';
}

function emailAddress(value) {
  if (typeof value !== 'string') return '';
  var email = value.trim();
  return /^[^\s@;,]+@[^\s@;,]+\.[^\s@;,]+$/.test(email) ? email : '';
}

api.clearFieldError(target);
api.setValue(target, '');
api.updateRule(target, { info: '' });
if (selected === null || selected === undefined || selected === '') return;

var selectedId = userId(selected);
if (!selectedId) {
  api.setFieldError(target, 'Please select a valid assignee.');
  return;
}

function isCurrent() { return userId(api.getValue(source)) === selectedId; }

var lookupEmail = selected && typeof selected === 'object' ? emailAddress(selected.email) : '';
if (lookupEmail) {
  if (isCurrent()) api.setValue(target, lookupEmail);
  return;
}

api.updateRule(target, { info: 'Loading email...' });
return fetch('/api/v1/admin/users/' + encodeURIComponent(selectedId), {
  credentials: 'same-origin',
  headers: { Accept: 'application/json' },
  signal: AbortSignal.timeout(15000)
})
  .then(function(response) {
    if (!response.ok) throw new Error('User lookup failed: HTTP ' + response.status);
    return response.json();
  })
  .then(function(user) {
    if (!isCurrent()) return;
    var email = user && user.id === selectedId ? emailAddress(user.email) : '';
    if (!email) throw new Error('Selected user has no valid email.');
    api.setValue(target, email);
    api.clearFieldError(target);
    api.updateRule(target, { info: '' });
  })
  .catch(function(error) {
    if (!isCurrent()) return;
    api.setValue(target, '');
    api.updateRule(target, { info: '' });
    api.setFieldError(target, error && error.message ? error.message : 'Unable to load the assignee email.');
  });
```

## 审查

本次配置及脚本未发现未处理的 Blocker / Major；普通与 Batch 的目标字段都在真实表元数据中存在且权限对应正确，商户字段及行身份未变。产品运行时沿用上一轮构建，未为 MCY 增加平台分支。仓库既有全量测试/类型失败仍保留，不宣称全仓库 PASS。
