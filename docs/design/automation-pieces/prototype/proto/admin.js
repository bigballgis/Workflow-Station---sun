// Admin Center「环境配置」：环境变量（现有页面，移到新菜单下）+ 连接（新）。
window.ADMIN = (() => {
  const { ENV_VARS, CONNECTIONS, TEST_RESULTS, WS } = window.PROTO;
  const { esc, fld, opts } = window.CFG;
  const NEW = '<span class="new-mark">设计新增</span>';
  const S = () => window.APP.S;
  const $ = (sel) => document.querySelector(sel);
  const closedKeys = { envEdit: null, envDel: null, connEdit: null, connTest: null, connDel: null };
  const AUTH = { none: 'None', basic: 'Basic Auth', bearer: 'Bearer Token' };
  const TYPE = { db: '数据库', http: 'HTTP' };

  function connUsage(id) {
    const out = [];
    Object.values(WS).forEach((ws) => ws.flows.forEach((flow) => { if (flow.steps.some((x) => x.cfg && x.cfg.conn === id)) out.push({ ws, flow }); }));
    return out;
  }
  function envUsage(key) {
    const v = ENV_VARS.find((x) => x.key === key);
    return [...CONNECTIONS.filter((c) => c.pwdVar === key).map((c) => `连接「${c.id}」`), ...((v && v.usedBy) || [])];
  }
  const wsName = (id) => (WS[id] ? WS[id].name : id);

  /* ---------- side ---------- */
  function side() {
    const s = S();
    const item = (label, view, sub) => `<button type="button" class="${s.view === view ? 'on' : ''}${sub ? ' sub' : ''}" data-act="nav" data-v="${view}">${label}</button>`;
    const off = (label) => `<button type="button" disabled>${label}</button>`;
    return `<div class="grp">ADMIN CENTER</div>
      ${off('仪表盘')}${off('用户管理')}${off('权限管理 ▾')}${off('功能单元')}${off('BI 管理 ▾')}${off('审计日志 ▾')}${off('关联表 ▾')}
      <div class="parent">环境配置 ▾ ${NEW}</div>
      ${item('环境变量', 'envvars', true)}${item('连接', 'connections', true)}
      ${off('SLA 策略')}
      ${item('自动化组件', 'pieces')}${off('自动化流程迁移')}${off('自动化运行记录')}`;
  }

  /* ---------- 环境变量 ---------- */
  function envVars() {
    const rows = ENV_VARS.map((v) => {
      const used = envUsage(v.key);
      const value = v.kind === 'VAULT'
        ? `<span class="mono">ame-hase-hermes/env-var</span><span class="sub">字段 ${esc(v.key)}</span>`
        : `${v.current ? esc(v.current) : '<span class="hint">当前值为空，用默认值</span>'}<span class="sub">默认值：${esc(v.def)}</span>`;
      return `<tr><td class="num">${esc(v.key)}</td><td>${esc(v.name)}</td><td><span class="tag${v.kind === 'VAULT' ? ' tag-data' : ''}">${v.kind}</span></td><td>${value}</td>
        <td>${used.length ? used.map(esc).join('<br>') : '<span class="hint">没有</span>'}</td>
        <td class="nw"><button type="button" class="link" data-act="env-edit" data-key="${esc(v.key)}">编辑</button> <button type="button" class="link danger" data-act="env-del" data-key="${esc(v.key)}">删除</button></td></tr>`;
    }).join('');
    return `<div class="page-head"><div><div class="crumb">Admin Center / 环境配置</div><h1>环境变量</h1><div class="meta"><span class="new-mark">从一级菜单移到「环境配置」下，页面本身不变</span></div></div>
        <button type="button" class="btn primary" data-act="env-new">新增变量</button></div>
      <div class="banner info">TEXT 保存可选当前值和必填默认值。VAULT 只保存目录键：密码写在 Vault 的固定路径 <span class="mono">ame-hase-hermes/env-var</span>，键是这个 secret 的字段。邮件连接和新的「连接」，密码都必须选 VAULT 变量。</div>
      <div class="card tbl-wrap"><table class="tbl"><thead><tr><th>变量键</th><th>显示名称</th><th>类型</th><th>取值 / 密钥路径</th><th>被谁引用${NEW}</th><th>操作</th></tr></thead><tbody>${rows}</tbody></table></div>
      <p class="hint" style="margin-top:10px">变量都是示例。每个环境的 Admin 各配一份。</p>`;
  }
  function envModal() {
    const e = S().envEdit;
    const kindHint = e.kind === 'VAULT'
      ? 'VAULT 只保存目录键。写入固定路径 ame-hase-hermes/env-var，把键作为该 secret 的字段（namespace 与 mount 来自环境配置）。新建时若该字段不存在才写入密码。邮件连接密码必须选择 VAULT 变量。'
      : 'TEXT 保存可选当前值和必填默认值。邮件密码不能用 TEXT。';
    const kindFields = e.kind === 'VAULT'
      ? fld('Vault 密码', `<input class="inp" type="password" id="ee-pwd" data-bind="ee.pwd" value="${esc(e.pwd)}" autocomplete="new-password"${e.isNew ? '' : ' disabled'}>`, e.isNew ? '仅当 Vault 里还没有该字段时才会写入。已有字段会复用，不会覆盖。' : '编辑不会改 Vault 密码，请留空。', 'ee-pwd')
      : `<div class="two">${fld('当前值', `<input class="inp" id="ee-cur" data-bind="ee.current" value="${esc(e.current)}" autocomplete="off">`, '选填。空白或只有空格时使用默认值。', 'ee-cur')}${fld('默认值', `<input class="inp" id="ee-def" data-bind="ee.def" value="${esc(e.def)}" autocomplete="off">`, '', 'ee-def')}</div>`;
    return window.VIEWS.modal(e.isNew ? '新增变量' : '编辑变量', `<div class="m-body">
      <div class="two">${fld('变量键', `<input class="inp mono" id="ee-key" data-bind="ee.key" value="${esc(e.key)}" autocomplete="off"${e.isNew ? '' : ' disabled'}>`, '', 'ee-key')}${fld('显示名称', `<input class="inp" id="ee-name" data-bind="ee.name" value="${esc(e.name)}" autocomplete="off">`, '', 'ee-name')}</div>
      ${fld('类型', `<select class="inp" id="ee-kind" data-bind="ee.kind"${e.isNew ? '' : ' disabled'}>${['TEXT', 'VAULT'].map((k) => `<option${k === e.kind ? ' selected' : ''}>${k}</option>`).join('')}</select>`, kindHint, 'ee-kind')}
      ${kindFields}
      <div id="ee-err" class="warn"></div>
      <div class="m-foot"><button type="button" class="btn" data-act="close-modal">取消</button><button type="button" class="btn primary" data-act="env-save">保存</button></div></div>`, true);
  }

  /* ---------- 连接 ---------- */
  function testTag(c) {
    if (!c.lastTest) return '<span class="tag">未测试</span>';
    return c.lastTest.ok ? '<span class="tag tag-data">成功</span>' : `<span class="tag tag-fail">${esc(c.lastTest.kind)}</span>`;
  }
  function connections() {
    const s = S();
    const list = CONNECTIONS.filter((c) => s.connType === 'all' || c.type === s.connType);
    const count = (t) => CONNECTIONS.filter((c) => t === 'all' || c.type === t).length;
    const rows = list.map((c) => {
      const used = connUsage(c.id);
      const addr = c.type === 'db' ? `<span class="mono">${esc(c.host)}:${esc(c.port)}</span><span class="sub">库 ${esc(c.database)}</span>` : `<span class="mono">${esc(c.baseUrl)}</span>`;
      const auth = c.type === 'db' ? `用户名 ${esc(c.user)}` : `${AUTH[c.auth]}${c.auth === 'basic' ? `<span class="sub">用户名 ${esc(c.user)}</span>` : ''}`;
      return `<tr><td class="nw"><b class="mono">${esc(c.id)}</b><span class="sub">${esc(c.note || '')}</span></td>
        <td><span class="tag${c.type === 'db' ? ' tag-data' : ' tag-call'}">${TYPE[c.type]}</span></td>
        <td>${addr}</td><td>${auth}</td>
        <td class="num">${c.pwdVar ? esc(c.pwdVar) : '—'}</td>
        <td>${c.workspaces.map((w) => `<span class="tag${WS[w] && WS[w].isData ? ' tag-data' : ''}">${esc(wsName(w))}</span>`).join(' ')}</td>
        <td>${used.length ? used.map((u) => `<button type="button" class="link" data-act="goto-flow" data-ws="${u.ws.id}" data-id="${u.flow.id}">${esc(u.flow.name)}</button>`).join('<br>') : '<span class="hint">没有</span>'}</td>
        <td>${testTag(c)}</td>
        <td class="nw"><button type="button" class="link" data-act="conn-test" data-id="${esc(c.id)}">测试</button> <button type="button" class="link" data-act="conn-edit" data-id="${esc(c.id)}">编辑</button> <button type="button" class="link danger" data-act="conn-del" data-id="${esc(c.id)}">删除</button></td></tr>`;
    }).join('');
    return `<div class="page-head"><div><div class="crumb">Admin Center / 环境配置</div><h1>连接 ${NEW}</h1></div>
        <button type="button" class="btn primary" data-act="conn-new">新增连接</button></div>
      <div class="banner info">每个环境的 Admin 各配一份，<b>名称保持一致</b>。flow 里只记连接名称，迁到 prod 后按名称找到 prod 的连接；迁移页的连接预检会提示缺哪个。密码不存这里，选「环境变量」里的 VAULT 变量。只有 SYS_ADMIN 能改。</div>
      <div class="chips">${[['all', '全部'], ['db', '数据库'], ['http', 'HTTP']].map(([v, l]) => `<button type="button" class="chip${s.connType === v ? ' on' : ''}" data-act="conn-type" data-t="${v}">${l}<span>${count(v)}</span></button>`).join('')}</div>
      <div class="card tbl-wrap"><table class="tbl"><thead><tr><th>名称</th><th>类型</th><th>地址</th><th>认证</th><th>密码（VAULT 变量）</th><th>可用工作区</th><th>被谁使用</th><th>测试</th><th>操作</th></tr></thead><tbody>${rows || '<tr><td colspan="9"><div class="empty">没有连接。</div></td></tr>'}</tbody></table></div>
      <p class="hint" style="margin-top:10px">连接都是示例。数据库连接只能给数据 workspace（Public），这样只有数据 flow 能连库。</p>`;
  }
  function connModal() {
    const c = S().connEdit;
    const vaultOpts = [{ v: '', l: '请选择 VAULT 变量' }, ...ENV_VARS.filter((v) => v.kind === 'VAULT').map((v) => ({ v: v.key, l: `${v.key}（${v.name}）` }))];
    const vaultSel = (label) => fld(label, `<select class="inp" id="ce-pwd" data-bind="ce.pwdVar">${vaultOpts.map((o) => `<option value="${esc(o.v)}"${o.v === c.pwdVar ? ' selected' : ''}>${esc(o.l)}</option>`).join('')}</select>`, '密码在 Vault，这里只引用变量键。没有合适的，先到「环境变量」新增一个 VAULT 变量。', 'ce-pwd');
    let typeFields;
    if (c.type === 'db') {
      typeFields = `<div class="three">${fld('主机', `<input class="inp mono" id="ce-host" data-bind="ce.host" value="${esc(c.host)}" autocomplete="off">`, '', 'ce-host')}${fld('端口', `<input class="inp mono" id="ce-port" data-bind="ce.port" data-type="num" value="${esc(c.port)}" inputmode="numeric" autocomplete="off">`, '', 'ce-port')}${fld('库名', `<input class="inp mono" id="ce-db" data-bind="ce.database" value="${esc(c.database)}" autocomplete="off">`, '', 'ce-db')}</div>
        ${fld('用户名', `<input class="inp mono" id="ce-user" data-bind="ce.user" value="${esc(c.user)}" autocomplete="off">`, '用 DBA 开的专用账号，只给需要的表授权。', 'ce-user')}${vaultSel('密码')}`;
    } else {
      typeFields = fld('地址（Base URL）', `<input class="inp mono" id="ce-url" data-bind="ce.baseUrl" value="${esc(c.baseUrl)}" placeholder="https://…" autocomplete="off">`, 'flow 里的 HTTP 步骤只填路径，拼在这个地址后面。', 'ce-url')
        + fld('认证方式', `<select class="inp" id="ce-auth" data-bind="ce.auth">${Object.entries(AUTH).map(([v, l]) => `<option value="${v}"${v === c.auth ? ' selected' : ''}>${l}</option>`).join('')}</select>`, '', 'ce-auth')
        + (c.auth === 'basic' ? fld('用户名', `<input class="inp mono" id="ce-user" data-bind="ce.user" value="${esc(c.user)}" autocomplete="off">`, '', 'ce-user') + vaultSel('密码') : '')
        + (c.auth === 'bearer' ? vaultSel('Token') : '');
    }
    const wsChecks = Object.values(WS).map((w) => `<label class="chk"><input type="checkbox" data-wsck="${w.id}"${c.workspaces.includes(w.id) ? ' checked' : ''}>${esc(w.name)}${w.isData ? '（数据 workspace）' : ''}</label>`).join('');
    return window.VIEWS.modal(c.isNew ? '新增连接' : `编辑连接 ${c.id}`, `<div class="m-body">
      <div class="two">${fld('名称', `<input class="inp mono" id="ce-id" data-bind="ce.id" value="${esc(c.id)}" placeholder="例如 sap-api" autocomplete="off"${c.isNew ? '' : ' disabled'}>`, '各环境保持一致；flow 里只记这个名称。创建后不能改。', 'ce-id')}
        ${fld('类型', `<select class="inp" id="ce-type" data-bind="ce.type"${c.isNew ? '' : ' disabled'}><option value="http"${c.type === 'http' ? ' selected' : ''}>HTTP</option><option value="db"${c.type === 'db' ? ' selected' : ''}>数据库</option></select>`, '', 'ce-type')}</div>
      ${typeFields}
      <div class="field"><span class="lbl">可用工作区</span><div class="chk-list">${wsChecks}</div><span class="hint">${c.type === 'db' ? '数据库连接只能给数据 workspace，这样只有数据 flow 能连库。' : '保存时同步成这些工作区里的 AP 连接。'}</span></div>
      ${fld('说明', `<input class="inp" id="ce-note" data-bind="ce.note" value="${esc(c.note)}" autocomplete="off">`, '', 'ce-note')}
      <div id="ce-err" class="warn"></div>
      <div class="m-foot"><button type="button" class="btn" data-act="close-modal">取消</button><button type="button" class="btn primary" data-act="conn-save">保存</button></div></div>`, true);
  }
  function testModal() {
    const c = CONNECTIONS.find((x) => x.id === S().connTest);
    const r = c.lastTest;
    return window.VIEWS.modal(`测试连接 ${c.id}`, `<div class="m-body">
      <div class="banner ${r.ok ? 'data' : 'fail'}" style="margin:0"><b>${esc(r.kind)}。</b>${esc(r.msg)}</div>
      <p class="hint" style="margin:0">测试是从 AP 那边实际连一次，所以能发现网络放行、证书、认证这些只有在 AP 里才会暴露的问题。失败会分类提示：网络未放行 / 证书不受信任 / 认证失败 / 地址不通。（原型里是示意结果）</p>
      <div class="m-foot"><button type="button" class="btn" data-act="close-modal">关闭</button></div></div>`, true);
  }
  function delModal(title, used, msgOk, act) {
    return window.VIEWS.modal(title, `<div class="m-body">${used.length
      ? `<p style="margin:0">${esc(used.join('、'))} 正在使用，不能删除。先把它们改掉。</p><div class="m-foot"><button type="button" class="btn" data-act="close-modal">关闭</button></div>`
      : `<p style="margin:0">${esc(msgOk)}</p><div class="m-foot"><button type="button" class="btn" data-act="close-modal">取消</button><button type="button" class="btn danger" data-act="${act}">删除</button></div>`}</div>`, true);
  }

  function page(view) {
    if (view === 'envvars') return envVars();
    if (view === 'connections') return connections();
    return null;
  }
  function overlay() {
    const s = S();
    if (s.envEdit) return envModal();
    if (s.connEdit) return connModal();
    if (s.connTest) return testModal();
    if (s.envDel) return delModal('删除环境变量', envUsage(s.envDel), `删除环境变量“${s.envDel}”？`, 'env-del-do');
    if (s.connDel) return delModal('删除连接', connUsage(s.connDel).map((u) => `flow「${u.flow.name}」`), `删除连接「${s.connDel}」？各工作区里对应的 AP 连接会一起删除。`, 'conn-del-do');
    return '';
  }

  const err = (id, msg) => { $(id).textContent = msg; };
  const render = () => window.APP.render();
  const toast = (m) => window.APP.toast(m);
  const acts = {
    'env-new': () => { S().envEdit = { isNew: true, key: '', name: '', kind: 'VAULT', current: '', def: '', pwd: '' }; render(); },
    'env-edit': (d) => { const v = ENV_VARS.find((x) => x.key === d.key); S().envEdit = { isNew: false, current: '', def: '', pwd: '', ...v }; render(); },
    'env-save': () => {
      const e = S().envEdit;
      e.key = e.key.trim();
      if (!e.key || !e.name.trim()) return err('#ee-err', '请填写变量键和显示名称');
      if (e.isNew && ENV_VARS.some((x) => x.key === e.key)) return err('#ee-err', `变量键 ${e.key} 已存在`);
      if (e.kind === 'TEXT' && !String(e.def || '').trim()) return err('#ee-err', 'TEXT 变量必须填写默认值');
      if (e.kind === 'VAULT' && e.isNew && !e.pwd) return err('#ee-err', 'VAULT 变量必须填写密码');
      const row = { key: e.key, name: e.name.trim(), kind: e.kind, current: e.current, def: e.def, usedBy: e.usedBy };
      const i = ENV_VARS.findIndex((x) => x.key === e.key);
      if (i >= 0) ENV_VARS[i] = row; else ENV_VARS.push(row);
      S().envEdit = null;
      render();
      return toast('环境变量已保存');
    },
    'env-del': (d) => { S().envDel = d.key; render(); },
    'env-del-do': () => { ENV_VARS.splice(ENV_VARS.findIndex((x) => x.key === S().envDel), 1); S().envDel = null; render(); toast('环境变量已删除'); },
    'conn-type': (d) => { S().connType = d.t; render(); },
    'conn-new': () => { S().connEdit = { isNew: true, id: '', type: 'http', host: '', port: 5432, database: '', user: '', pwdVar: '', baseUrl: '', auth: 'basic', workspaces: [], note: '' }; render(); },
    'conn-edit': (d) => { S().connEdit = { isNew: false, ...JSON.parse(JSON.stringify(CONNECTIONS.find((x) => x.id === d.id))) }; render(); },
    'conn-save': () => {
      const c = S().connEdit;
      c.id = c.id.trim();
      if (!/^[a-z0-9][a-z0-9-]*$/.test(c.id)) return err('#ce-err', '名称只能用小写字母、数字和横线，并以字母或数字开头。');
      if (c.isNew && CONNECTIONS.some((x) => x.id === c.id)) return err('#ce-err', `名称 ${c.id} 已被占用。`);
      if (c.type === 'db') {
        if (!c.host || !Number.isInteger(Number(c.port)) || !c.database || !c.user || !c.pwdVar) return err('#ce-err', '数据库连接要填主机、端口、库名、用户名，并选密码变量。');
        if (c.workspaces.some((w) => !WS[w].isData)) return err('#ce-err', '数据库连接只能给数据 workspace（Public）。给了普通工作区，那里的 flow 就能直接连库。');
      } else {
        if (!/^https?:\/\//.test(c.baseUrl || '')) return err('#ce-err', '地址要以 http:// 或 https:// 开头。');
        if (c.auth === 'basic' && (!c.user || !c.pwdVar)) return err('#ce-err', 'Basic Auth 要填用户名，并选密码变量。');
        if (c.auth === 'bearer' && !c.pwdVar) return err('#ce-err', 'Bearer Token 要选 Token 变量。');
      }
      if (!c.workspaces.length) return err('#ce-err', '至少选一个可用工作区。');
      const { isNew, ...row } = c;
      const i = CONNECTIONS.findIndex((x) => x.id === row.id);
      if (i >= 0) CONNECTIONS[i] = { ...CONNECTIONS[i], ...row }; else CONNECTIONS.push(row);
      S().connEdit = null;
      render();
      return toast(`连接 ${row.id} 已保存，并同步到 ${row.workspaces.map(wsName).join('、')} 的 AP 连接`);
    },
    'conn-test': (d) => {
      const c = CONNECTIONS.find((x) => x.id === d.id);
      c.lastTest = TEST_RESULTS[c.id] || { ok: true, kind: '成功', msg: '连接成功（示意）。' };
      S().connTest = c.id;
      render();
    },
    'conn-del': (d) => { S().connDel = d.id; render(); },
    'conn-del-do': () => { CONNECTIONS.splice(CONNECTIONS.findIndex((x) => x.id === S().connDel), 1); S().connDel = null; render(); toast('连接已删除'); },
  };
  function onChange(el) {
    if (!el.dataset.wsck) return false;
    const ws = S().connEdit.workspaces;
    const i = ws.indexOf(el.dataset.wsck);
    if (el.checked && i < 0) ws.push(el.dataset.wsck);
    if (!el.checked && i >= 0) ws.splice(i, 1);
    return true;
  }

  return { side, page, overlay, acts, closedKeys, onChange, connUsage };
})();
