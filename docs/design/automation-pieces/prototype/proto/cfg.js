// 步骤配置面板。现有组件的配置项按源码里的 props；平台数据按方案页的设计。
window.CFG = (() => {
  const { CATS, PD_NOTES, OPS, MAX_PAGE_SIZE, TABLES, DISCOUNT_RULES, WS, CONNECTIONS } = window.PROTO;
  const { findByKey } = window.SIM;

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = (h) => String(h).padStart(2, '0');
  const icon = (p, size) => `<span class="ic ic-${p.cat}${size ? ` ${size}` : ''}" aria-hidden="true">${esc(shortName(p))}</span>`;
  function shortName(p) {
    const map = { webhook: 'HOOK', schedule: '定时', http: 'HTTP', csv: 'CSV', json: 'JSON', xml: 'XML', pdf: 'PDF', 'file-helper': '文件', 'text-helper': '文本', 'data-mapper': '映射', 'biz-calendar': '日历', 'hash-helper': '哈希', postgres: 'PG', 'content-organizer': 'CO', 'platform-data': '数据', 'call-data-flow': '调用', 'fu-purchase': '采购', 'fu-expense': '报销', 'ext-sap': 'SAP' };
    return map[p.id] || p.name.slice(0, 2);
  }
  const catTag = (cat) => `<span class="cat cat-${cat}">${CATS[cat].label}</span>`;
  const fld = (label, ctrl, hint, id) => `<div class="field">${id ? `<label class="lbl" for="${id}">${label}</label>` : `<span class="lbl">${label}</span>`}${ctrl}${hint ? `<span class="hint">${hint}</span>` : ''}</div>`;
  const txt = (id, bind, val, opt = {}) => `<input class="inp${opt.expr ? ' expr' : ''}" id="${id}" data-bind="${bind}" value="${esc(val)}"${opt.num ? ' data-type="num" inputmode="decimal"' : ''}${opt.ph ? ` placeholder="${esc(opt.ph)}"` : ''} autocomplete="off">`;
  const sel = (id, bind, opts, val, after) => `<select class="inp" id="${id}" data-bind="${bind}"${after ? ` data-after="${after}"` : ''}>${opts.map((o) => `<option value="${esc(o.v)}"${String(o.v) === String(val) ? ' selected' : ''}>${esc(o.l)}</option>`).join('')}</select>`;
  const chk = (id, bind, on, label) => `<label class="chk" for="${id}"><input type="checkbox" id="${id}" data-bind="${bind}" data-type="bool"${on ? ' checked' : ''}>${label}</label>`;
  const ro = (v) => `<div class="ro">${v}</div>`;
  const auto = (v) => `<div class="auto">${v}</div>`;
  const opts = (arr) => arr.map((v) => (Array.isArray(v) ? { v: v[0], l: v[1] } : { v, l: v }));
  // 只列出授权给当前工作区的连接（Admin Center → 环境配置 → 连接）
  function connField(st, ws, type, emptyLabel) {
    const list = CONNECTIONS.filter((c) => c.type === type && c.workspaces.includes(ws.id));
    const options = [{ v: '', l: list.length ? emptyLabel : `（这个工作区没有可用的${type === 'db' ? '数据库' : 'HTTP'}连接）` }, ...list.map((c) => ({ v: c.id, l: `${c.id} · ${type === 'db' ? `${c.host}/${c.database}` : c.baseUrl}` }))];
    return fld(`连接 <span class="new-mark">设计新增</span>`, sel('c-conn', 'step.cfg.conn', options, st.cfg.conn || ''), '来自 Admin Center → 环境配置 → 连接；只列出给这个工作区用的。', 'c-conn');
  }

  function defaultCfg(pid, action) {
    if (pid === 'platform-data') return { conn: '', table: 'supplier', where: [], sortField: '', sortDir: 'ASC', page: 1, pageSize: 100, columns: [], keyValue: '', rows: '', mode: 'key', maxAffected: '', sets: [{ f: 'name', v: '' }], keyField: '' };
    if (pid === 'call-data-flow') return { flow: 'supplier.query', params: { code: '' } };
    if (pid === 'fu-purchase') return action === '计算折扣' ? { amount: '', level: '' } : { amount: '', balance: '' };
    if (pid === 'content-organizer') return { file: '', staffId: '', fields: [{ key: '', label: '', type: 'text' }] };
    if (pid === 'http') return { method: 'GET', conn: '', path: '', url: '' };
    if (pid === 'csv') return { csv: '', hasHeaders: true, delimiter: ',' };
    if (pid === 'webhook') return { responseType: 'json', status: 200, flowExecution: 'stop', vars: [{ k: '', v: '' }] };
    if (pid === 'postgres') return { conn: '', table: '', condition: '', args: '', columns: '', limit: 100 };
    return {};
  }

  const countConds = (where) => (where || []).reduce((n, g) => n + g.length, 0);
  function summary(st, flow) {
    const c = st.cfg || {};
    switch (st.piece) {
      case 'webhook':
        if (st.action === 'Catch Webhook') return `收到调用 · 业务键 ${flow.key}`;
        if (st.action === 'Return Response') return `返回 variables：${(c.vars || []).filter((x) => x.k).map((x) => x.k).join('、') || '（未填）'}`;
        return '';
      case 'schedule': return `每天 ${pad(c.hour)}:00${c.weekends ? '' : '，周末不跑'}`;
      case 'http': return c.conn ? `${c.method || ''} 连接 ${c.conn} · ${c.path || '/'}` : `${c.method || ''} ${c.url || '（未填地址）'}`;
      case 'csv': return 'CSV 文本 → 一行行数据';
      case 'platform-data':
        if (st.action === 'List Tables') return '列出已部署的表';
        return `表 ${c.table}${['Find Rows', 'Count'].includes(st.action) ? `，${countConds(c.where)} 个条件` : ''}${st.action === 'Update Row' ? (c.mode === 'where' ? '，按条件批量' : '，按主键') : ''}`;
      case 'postgres': return '外部系统的库';
      case 'call-data-flow': { const hit = findByKey(c.flow); return hit ? `${hit.flow.name} · ${c.flow}` : '（未选数据 flow）'; }
      case 'content-organizer': return `抽取 ${(c.fields || []).filter((x) => x.key).length} 个字段`;
      case 'fu-purchase': return st.action === '计算折扣' ? '按供应商等级和金额算折扣' : '申请金额和预算余额比较';
      default: return '';
    }
  }

  /* ---------- webhook ---------- */
  function cfgCatch(st, flow, ws) {
    const sample = flow.sample || {};
    const inputs = Object.keys(sample).map((k) => {
      const v = sample[k];
      if (v !== null && typeof v === 'object') return fld(k, `<pre class="out">${esc(JSON.stringify(v))}</pre>`);
      return fld(k, txt(`s-${k}`, `flow.sample.${k}`, v, { num: typeof v === 'number' }), '', `s-${k}`);
    }).join('');
    return fld('业务键', ro(`<span class="mono">${esc(flow.key)}</span>`), 'Service Task 和「调用数据 flow」都按这个键找到这条 flow；创建后不能改。')
      + fld('同步调用地址', ro('<span class="mono">…/v1/webhooks/&lt;flowId&gt;/sync</span>'), 'flowId 每个环境不一样，所以调用方按业务键找，不写死地址。')
      + fld('Authentication', sel('c-auth', 'step.cfg.authType', opts([['none', 'None'], ['basic', 'Basic Auth'], ['header', 'Header Auth'], ['hmac', 'HMAC Signature']]), st.cfg.authType), ws.isData ? '数据 flow 必须开鉴权，否则谁都能调。' : '', 'c-auth')
      + (st.cfg.authType === 'header' ? fld('Header Name / Header Value', auto('由平台统一管理；Service Task 和「调用数据 flow」调用时自动带上')) : '')
      + `<div class="field"><span class="lbl">请求体是固定信封：{ "variables": {…}, "context": {…} }。试运行用下面这组 variables（可以改）</span>${inputs ? `<div class="two">${inputs}</div>` : '<span class="hint">这个 flow 没有示例输入。</span>'}</div>`;
  }
  function cfgReturn(st) {
    const rows = (st.cfg.vars || []).map((x, i) => `<div class="kvrow">${txt(`c-rk-${i}`, `step.cfg.vars.${i}.k`, x.k, { ph: '变量名' })}${txt(`c-rv-${i}`, `step.cfg.vars.${i}.v`, x.v, { expr: true, ph: '{{step3.xxx}}' })}<button type="button" class="icon-btn" data-act="del-row" data-list="vars" data-i="${i}" aria-label="删除第 ${i + 1} 个变量">✕</button></div>`).join('');
    return '<div class="design-note"><b>契约：</b>返回体固定是 { "variables": { … } }。Service Task 把 variables 写回流程变量；缺了 variables 就报错，不会静默放过（FR-C08）。</div>'
      + `<div class="two">${fld('Response Type', sel('c-rt', 'step.cfg.responseType', opts([['json', 'JSON'], ['raw', 'Raw'], ['redirect', 'Redirect']]), st.cfg.responseType), '', 'c-rt')}${fld('Status', txt('c-st', 'step.cfg.status', st.cfg.status, { num: true }), '', 'c-st')}</div>`
      + `<div class="field"><span class="lbl">返回的 variables</span>${rows}<button type="button" class="link" data-act="add-row" data-list="vars">+ 添加变量</button></div>`
      + fld('Flow Execution', sel('c-fe', 'step.cfg.flowExecution', opts([['stop', 'Stop'], ['respond', 'Respond and Continue']]), st.cfg.flowExecution), '', 'c-fe');
  }

  /* ---------- 平台数据（按设计） ---------- */
  function condRow(cd, gi, ci, fOpts) {
    const base = `step.cfg.where.${gi}.${ci}`;
    const id = `c-w${gi}-${ci}`;
    let vals;
    if (cd.op === 'isEmpty') vals = '<div class="vals one"><span class="hint">不需要值</span></div>';
    else if (cd.op === 'between') vals = `<div class="vals">${txt(`${id}-v`, `${base}.v`, cd.v, { expr: true, ph: '从' })}${txt(`${id}-v2`, `${base}.v2`, cd.v2, { expr: true, ph: '到' })}</div>`;
    else vals = `<div class="vals one">${txt(`${id}-v`, `${base}.v`, cd.v, { expr: true, ph: cd.op === 'in' || cd.op === 'not in' ? '多个值用逗号分隔' : '值或 {{…}}' })}</div>`;
    return `<div class="cond">${sel(`${id}-f`, `${base}.f`, fOpts, cd.f)}${sel(`${id}-o`, `${base}.op`, opts(OPS), cd.op)}${vals}<button type="button" class="icon-btn" data-act="del-cond" data-g="${gi}" data-i="${ci}" aria-label="删除条件">✕</button></div>`;
  }
  function whereEditor(c, fOpts) {
    const groups = c.where || [];
    const body = groups.length
      ? groups.map((g, gi) => `${gi ? '<div class="or-sep">或者</div>' : ''}<div class="grp-box"><div class="grp-h"><span>条件组 ${gi + 1}：组内条件全部满足</span><button type="button" class="icon-btn" data-act="del-group" data-g="${gi}">删除这一组</button></div>${g.map((cd, ci) => condRow(cd, gi, ci, fOpts)).join('') || '<span class="hint">这一组还没有条件。</span>'}<button type="button" class="link" data-act="add-cond" data-g="${gi}">+ 添加条件（并且）</button></div>`).join('')
        + '<button type="button" class="link" data-act="add-group">+ 添加「或者」条件组</button>'
      : '<span class="hint">没有条件：不过滤。</span><button type="button" class="link" data-act="add-group">+ 添加条件</button>';
    return `<div class="field"><span class="lbl">条件（支持 AND，最多一层 OR；运算符按字段类型比较）</span>${body}</div>`;
  }
  function setsEditor(c, dOpts) {
    const rows = (c.sets || []).map((s, i) => `<div class="kvrow">${sel(`c-sf-${i}`, `step.cfg.sets.${i}.f`, dOpts, s.f)}${txt(`c-sv-${i}`, `step.cfg.sets.${i}.v`, s.v, { expr: true })}<button type="button" class="icon-btn" data-act="del-row" data-list="sets" data-i="${i}" aria-label="删除第 ${i + 1} 个字段">✕</button></div>`).join('');
    return `<div class="field"><span class="lbl">要改的字段</span>${rows}<button type="button" class="link" data-act="add-row" data-list="sets">+ 添加字段</button></div>`;
  }
  function cfgPlatformData(st, ws) {
    const c = st.cfg;
    const a = st.action;
    const note = `<div class="design-note"><b>设计要点：</b>${esc(PD_NOTES[a])}</div>${connField(st, ws, 'db', '请选择数据库连接')}`;
    if (a === 'List Tables') return `${note}<p class="hint">没有参数。输出所有已部署的 Relation Table（表名 + 显示名）。</p>`;
    const T = TABLES[c.table];
    const tableSel = fld('表名', sel('c-table', 'step.cfg.table', Object.keys(TABLES).map((k) => ({ v: k, l: `${k}（${TABLES[k].label}）` })), c.table, 'resetTable'), '下拉里是已部署的 Relation Table。', 'c-table');
    const cols = [...T.fields.map((f) => f.n), '_status'];
    const peek = `<details class="peek"><summary>看这张表的结构和示例数据（${T.rows.length} 行）</summary><div class="mini-wrap"><table class="mini"><thead><tr>${cols.map((f) => `<th>${f === '_status' ? 'status（行状态）' : `${esc(f)}${f === T.pk ? '（主键）' : ''} · ${T.fields.find((x) => x.n === f).t === 'number' ? '数字' : '文本'}`}</th>`).join('')}</tr></thead><tbody>${T.rows.map((row) => `<tr>${cols.map((f) => `<td>${esc(row[f] ?? 'null')}</td>`).join('')}</tr>`).join('')}</tbody></table></div></details>`;
    const fOpts = [...T.fields.map((f) => ({ v: f.n, l: `${f.n} · ${f.t === 'number' ? '数字' : '文本'}` })), { v: '_status', l: 'status · 行状态' }];
    const dOpts = T.fields.map((f) => ({ v: f.n, l: f.n }));
    const keyField = (hint) => fld(`主键 ${T.pk} 的值`, txt('c-kv', 'step.cfg.keyValue', c.keyValue, { expr: true }), hint, 'c-kv');
    switch (a) {
      case 'Get Schema': return note + tableSel + peek;
      case 'Count': return note + tableSel + peek + whereEditor(c, fOpts);
      case 'Find Rows':
        return note + tableSel + peek + whereEditor(c, fOpts)
          + `<div class="two">${fld('排序字段', sel('c-sort', 'step.cfg.sortField', [{ v: '', l: '不排序' }, ...dOpts], c.sortField), '', 'c-sort')}${fld('排序方向', sel('c-dir', 'step.cfg.sortDir', opts([['ASC', '升序'], ['DESC', '降序']]), c.sortDir), '', 'c-dir')}</div>`
          + `<div class="two">${fld('页码', txt('c-page', 'step.cfg.page', c.page, { num: true }), '', 'c-page')}${fld('每页行数', txt('c-size', 'step.cfg.pageSize', c.pageSize, { num: true }), `行数上限 ${MAX_PAGE_SIZE}，超过会报错。`, 'c-size')}</div>`
          + `<div class="field"><span class="lbl">选列（都不勾 = 返回全部列）</span><div class="chk-list">${cols.map((f) => `<label class="chk"><input type="checkbox" data-col="${esc(f)}"${(c.columns || []).includes(f) ? ' checked' : ''}>${f === '_status' ? 'status' : esc(f)}</label>`).join('')}</div></div>`;
      case 'Get Row by Key': return note + tableSel + peek + keyField('找不到时报错（技术方案里再确认要不要改成返回空）。');
      case 'Deactivate Row': return note + tableSel + peek + keyField('把这一行的状态改成 INACTIVE，数据还在。');
      case 'Delete Row': return note + tableSel + peek + keyField('硬删除，数据没了。能停用就优先用 Deactivate Row。');
      case 'Insert Row(s)':
        return note + tableSel + peek
          + fld('数据（一行或多行）', txt('c-rows', 'step.cfg.rows', c.rows, { expr: true, ph: '{{trigger.body.variables.rows}}' }), '', 'c-rows')
          + `<ul class="hint" style="margin:0;padding-left:1.2em"><li>主键：${T.pkAuto ? `${T.pk} 不填时由平台分配。` : `${T.pk} 要自己填，这张表不是平台分配主键。`}</li><li>字段校验：按表结构检查字段名和类型。</li><li>审计字段（创建人、创建时间）由平台填写；flow 写入时记成谁，技术方案里定。</li></ul>`;
      case 'Update Row':
        return note + tableSel + peek
          + fld('更新方式', sel('c-mode', 'step.cfg.mode', opts([['key', '按主键（默认）'], ['where', '按条件批量']]), c.mode), '', 'c-mode')
          + (c.mode === 'where'
            ? whereEditor(c, fOpts) + fld('最大影响行数（必填）', txt('c-max', 'step.cfg.maxAffected', c.maxAffected, { num: true }), '匹配到的行数超过它，就一行都不改并报错。', 'c-max')
            : keyField(''))
          + setsEditor(c, dOpts);
      case 'Upsert':
        return note + tableSel + peek
          + fld('按哪个字段判断已存在', sel('c-kf', 'step.cfg.keyField', dOpts, c.keyField || T.pk), '这个字段的值已存在就修改，否则新增。', 'c-kf')
          + fld('数据（一行或多行）', txt('c-rows', 'step.cfg.rows', c.rows, { expr: true, ph: '{{trigger.body.variables.rows}}' }), '', 'c-rows');
      default: return note;
    }
  }

  /* ---------- 其他组件 ---------- */
  function cfgCall(st) {
    const c = st.cfg;
    const hit = findByKey(c.flow);
    const params = (hit ? hit.flow.params : []).map((k) => fld(`参数 ${k}`, txt(`c-p-${k}`, `step.cfg.params.${k}`, (c.params || {})[k] ?? '', { expr: true }), '', `c-p-${k}`)).join('');
    return '<div class="design-note"><b>设计要点：</b>按业务键选数据 flow，填参数，鉴权自动带上。换环境不用改。</div>'
      + fld('数据 flow', sel('c-flow', 'step.cfg.flow', WS.public.flows.map((x) => ({ v: x.key, l: `${x.name} · ${x.key}` })), c.flow, 'resetParams'), '列出数据 workspace 里的所有数据 flow。', 'c-flow')
      + (params || '<p class="hint">这个数据 flow 不需要参数。</p>')
      + fld('鉴权', auto('自动带上，不用配置'))
      + (hit ? `<button type="button" class="link" data-act="goto-key" data-key="${esc(hit.flow.key)}">打开这个数据 flow →</button>` : '');
  }
  function cfgCo(st) {
    const rows = (st.cfg.fields || []).map((x, i) => `<div class="frow">${txt(`c-fk-${i}`, `step.cfg.fields.${i}.key`, x.key, { ph: 'Key' })}${txt(`c-fl-${i}`, `step.cfg.fields.${i}.label`, x.label, { ph: 'Label on the document' })}${sel(`c-ft-${i}`, `step.cfg.fields.${i}.type`, opts([['text', 'Text'], ['date', 'Date'], ['number', 'Number']]), x.type)}<button type="button" class="icon-btn" data-act="del-row" data-list="fields" data-i="${i}" aria-label="删除第 ${i + 1} 个字段">✕</button></div>`).join('');
    return `<div class="two">${fld('File URL', txt('c-file', 'step.cfg.file', st.cfg.file, { expr: true }), '', 'c-file')}${fld('Staff ID', txt('c-staff', 'step.cfg.staffId', st.cfg.staffId, { expr: true }), '', 'c-staff')}</div>`
      + `<div class="field"><span class="lbl">Fields（Key / Label on the document / Type）</span>${rows}<button type="button" class="link" data-act="add-row" data-list="fields">+ 添加字段</button></div>`
      + fld('账号', auto('iB2B 服务账号由部署环境注入，flow 里不用填'))
      + '<div class="catline">这一步在组件里做了：换 iB2B token → 上传文件 → 调用抽取接口 → 遇到 401 重新换 token 再试一次。flow 里只看到一步。</div>';
  }
  function cfgPostgres(st, ws) {
    if (st.action !== 'Find Rows') return '<p class="hint">配置项和现有 PostgreSQL 组件一样，原型里省略。</p>';
    const c = st.cfg;
    return connField(st, ws, 'db', '请选择数据库连接')
      + `<div class="two">${fld('Table', txt('c-pt', 'step.cfg.table', c.table, { ph: 'schema.table' }), '', 'c-pt')}${fld('Limit', txt('c-pl', 'step.cfg.limit', c.limit, { num: true }), '', 'c-pl')}</div>`
      + fld('Condition', txt('c-pc', 'step.cfg.condition', c.condition, { ph: 'status = $1 AND age > $2' }), '不写 WHERE。外部库是物理表，条件写 SQL。', 'c-pc')
      + `<div class="two">${fld('Arguments', txt('c-pa', 'step.cfg.args', c.args, { ph: '$1, $2 … 的值' }), '', 'c-pa')}${fld('Columns', txt('c-pcol', 'step.cfg.columns', c.columns, { ph: '不填 = 全部列' }), '', 'c-pcol')}</div>`
      + '<p class="hint">原型没有外部库数据，试运行返回空。</p>';
  }
  function discountTryHtml(S) {
    const a = Number(S.tryAmount);
    const hit = DISCOUNT_RULES.find((x) => x.test(a, S.tryLevel));
    if (!Number.isFinite(a) || a <= 0) return '<span class="warn">金额要是大于 0 的数字。</span>';
    return hit ? `命中「${esc(hit.cond)}」，输出 discount = <b>${hit.rate}</b>（${Math.round(hit.rate * 100)}%）` : '<span class="warn">没有命中的规则。</span>';
  }
  const ruleRows = (S) => DISCOUNT_RULES.map((x) => `<tr class="${x.test(Number(S.tryAmount), S.tryLevel) ? 'hit' : ''}"><td>${esc(x.cond)}</td><td>${Math.round(x.rate * 100)}%</td></tr>`).join('');
  function budgetTryHtml(S) {
    const a = Number(S.tryBAmount);
    const b = Number(S.tryBalance);
    if (!Number.isFinite(a) || !Number.isFinite(b)) return '<span class="warn">两个值都要是数字。</span>';
    return a <= b ? '输出 pass = <b>true</b>，over = 0' : `输出 pass = <b>false</b>，over = ${a - b}`;
  }
  function cfgFu(st, S) {
    if (st.action === '计算折扣') {
      return `<div class="two">${fld('金额（amount）', txt('c-amount', 'step.cfg.amount', st.cfg.amount, { expr: true }), '', 'c-amount')}${fld('供应商等级（level）', txt('c-level', 'step.cfg.level', st.cfg.level, { expr: true }), '', 'c-level')}</div>
        <div class="field"><span class="lbl">组件代码里写好的规则（改规则 = 改代码、发新版本）</span><div class="rules"><table><thead><tr><th>条件</th><th>折扣</th></tr></thead><tbody id="rule-rows">${ruleRows(S)}</tbody></table></div></div>
        <div class="try"><div class="try-h">试算一下</div><div class="two">
          <div class="field"><label class="lbl" for="try-amount">金额</label><input class="inp" id="try-amount" data-try="amount" inputmode="decimal" value="${esc(S.tryAmount)}" autocomplete="off"></div>
          <div class="field"><label class="lbl" for="try-level">供应商等级</label><select class="inp" id="try-level" data-try="level">${['A', 'B', 'C'].map((v) => `<option${v === S.tryLevel ? ' selected' : ''}>${v}</option>`).join('')}</select></div>
        </div><div class="try-out" id="try-out" aria-live="polite">${discountTryHtml(S)}</div></div>
        <p class="hint">只算，不查库：等级来自上一步数据 flow 的结果，算出的折扣交给下一步数据 flow 写回。</p>`;
    }
    return `<div class="two">${fld('申请金额（amount）', txt('c-bamt', 'step.cfg.amount', st.cfg.amount, { expr: true }), '', 'c-bamt')}${fld('预算余额（balance）', txt('c-bbal', 'step.cfg.balance', st.cfg.balance, { expr: true }), '', 'c-bbal')}</div>
      <div class="field"><span class="lbl">组件代码里写好的规则</span><div class="rules"><table><thead><tr><th>条件</th><th>输出</th></tr></thead><tbody><tr><td>申请金额 ≤ 预算余额</td><td>pass = true</td></tr><tr><td>申请金额 &gt; 预算余额</td><td>pass = false，over = 超出多少</td></tr></tbody></table></div></div>
      <div class="try"><div class="try-h">试算一下</div><div class="two">
        <div class="field"><label class="lbl" for="try-bamount">申请金额</label><input class="inp" id="try-bamount" data-try="bamount" inputmode="decimal" value="${esc(S.tryBAmount)}" autocomplete="off"></div>
        <div class="field"><label class="lbl" for="try-balance">预算余额</label><input class="inp" id="try-balance" data-try="balance" inputmode="decimal" value="${esc(S.tryBalance)}" autocomplete="off"></div>
      </div><div class="try-out" id="try-out" aria-live="polite">${budgetTryHtml(S)}</div></div>`;
  }

  function body(st, flow, ws, S) {
    const c = st.cfg;
    switch (`${st.piece}:${st.action}`) {
      case 'webhook:Catch Webhook': return cfgCatch(st, flow, ws);
      case 'webhook:Return Response': return cfgReturn(st);
      case 'schedule:Every Day':
        return `<div class="two">${fld('Hour of the day', sel('c-hour', 'step.cfg.hour', Array.from({ length: 24 }, (_, h) => ({ v: h, l: `${pad(h)}:00` })), c.hour), '', 'c-hour')}<div class="field"><span class="lbl">Run on weekends (Sat,Sun)</span>${chk('c-wk', 'step.cfg.weekends', c.weekends, '周末也跑')}</div></div>`;
      case 'http:Send HTTP request': {
        const conn = CONNECTIONS.find((x) => x.id === c.conn);
        const method = fld('Method', sel('c-method', 'step.cfg.method', opts(['GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'HEAD']), c.method), '', 'c-method');
        const target = conn
          ? `<div class="two">${method}${fld('路径', txt('c-path', 'step.cfg.path', c.path, { ph: '/orders' }), '', 'c-path')}</div>${fld('实际地址', ro(`<span class="mono">${esc(conn.baseUrl)}${esc(c.path || '')}</span>`), `认证：${conn.auth === 'none' ? 'None' : conn.auth === 'basic' ? `Basic Auth（用户名 ${esc(conn.user)}，密码取 VAULT 变量 ${esc(conn.pwdVar)}）` : `Bearer Token（取 VAULT 变量 ${esc(conn.pwdVar)}）`}。地址和密码都来自连接，不进 flow。`)}`
          : `<div class="two">${method}${fld('URL', txt('c-url', 'step.cfg.url', c.url), '不选连接时，地址直接写在 flow 里，迁到 prod 不会自动换。', 'c-url')}</div>`;
        return connField(st, ws, 'http', '不使用连接（直接填完整地址）') + target
          + '<div class="design-note"><b>设计（已定）：</b>认证改成选 Admin Center 里的「连接」。现在的 HTTP 组件还不支持选连接，要改它或另做一个。</div><p class="hint">Headers、Query params、Body Type、Body 等配置项和现有 HTTP 组件一样，原型里省略。</p>';
      }
      case 'csv:Convert CSV to JSON':
        return fld('CSV Text', txt('c-csv', 'step.cfg.csv', c.csv, { expr: true }), '一般引用上一步的 body，例如 {{step2.body}}。', 'c-csv')
          + `<div class="two"><div class="field"><span class="lbl">Does the CSV have headers?</span>${chk('c-hh', 'step.cfg.hasHeaders', c.hasHeaders, '第一行是表头')}</div>${fld('Delimiter Type', sel('c-dl', 'step.cfg.delimiter', opts([[',', 'Comma'], ['tab', 'Tab']]), c.delimiter), '', 'c-dl')}</div>`;
      case 'call-data-flow:Call Data Flow': return cfgCall(st);
      case 'content-organizer:Extract Fields': return cfgCo(st);
      default:
        if (st.piece === 'platform-data') return cfgPlatformData(st, ws);
        if (st.piece === 'postgres') return cfgPostgres(st, ws);
        if (st.piece === 'fu-purchase') return cfgFu(st, S);
        return '<p class="hint">原型里没有画这个 action 的配置项，配置和现有组件一样。</p>';
    }
  }

  return { esc, icon, catTag, fld, txt, sel, ro, auto, opts, defaultCfg, summary, body, discountTryHtml, ruleRows, budgetTryHtml };
})();
