// 页面渲染。DW「自动化」页和 Admin「自动化组件」页的结构对照现有代码；虚线框「设计新增」是方案里新加的部分。
window.VIEWS = (() => {
  const { CATS, PIECES, IMPORTABLE, WS, FU, TOUR } = window.PROTO;
  const { pieceById, findByKey } = window.SIM;
  const { esc, icon, catTag, fld, ro, summary, body } = window.CFG;
  const NEW = '<span class="new-mark">设计新增</span>';
  const S = () => window.APP.S;
  const canWrite = (ws) => S().role === 'admin' || !ws.isData;
  const curFlow = () => window.APP.curFlow();
  const rdot = (s) => `<span class="rdot ${s}" aria-hidden="true">${s === 'ok' ? '✓' : s === 'fail' ? '✕' : '–'}</span>`;
  function fmt(v) {
    if (v === undefined) return '（没有输出）';
    const s = JSON.stringify(v, null, 2);
    return s.length > 1200 ? `${s.slice(0, 1200)}\n…` : s;
  }
  function usage(pid) {
    const out = [];
    Object.values(WS).forEach((ws) => ws.flows.forEach((flow) => { if (flow.steps.some((x) => x.piece === pid)) out.push({ ws, flow }); }));
    return out;
  }
  function callers(key) {
    const out = [];
    Object.values(WS).forEach((ws) => ws.flows.forEach((flow) => {
      if (flow.steps.some((x) => x.piece === 'call-data-flow' && x.cfg.flow === key)) out.push(`flow「${flow.name}」`);
    }));
    FU.tasks.forEach((t) => { if (t.key === key) out.push(`Service Task「${t.name}」`); });
    return out;
  }

  /* ---------------- side ---------------- */
  function side() {
    const s = S();
    if (s.product === 'dw') {
      return `<div class="grp">DEVELOPER WORKSTATION</div>
        <button type="button" class="${['fu', 'process'].includes(s.view) ? 'on' : ''}" data-act="nav" data-v="fu">Function Units</button>
        <button type="button" class="${['automation', 'flow'].includes(s.view) ? 'on' : ''}" data-act="nav" data-v="automation">自动化</button>`;
    }
    return window.ADMIN.side();
  }

  /* ---------------- DW 自动化 ---------------- */
  function flowRows() {
    const s = S();
    const ws = WS[s.ws];
    const q = s.flowQ.trim().toLowerCase();
    const list = ws.flows.filter((f) => !q || f.name.toLowerCase().includes(q) || f.key.toLowerCase().includes(q));
    if (!list.length) return '<div class="empty">没有找到流程。</div>';
    return `<table class="tbl"><thead><tr><th>名称</th><th>业务键</th><th>类型${NEW}</th><th>就绪状态</th><th>操作</th></tr></thead><tbody>${list.map((f) => `
      <tr class="click" data-act="open-flow" data-ws="${ws.id}" data-id="${f.id}" tabindex="0">
        <td><b>${esc(f.name)}</b></td><td class="num">${esc(f.key)}</td>
        <td>${ws.isData ? '<span class="tag tag-data">数据 flow</span>' : '<span class="tag tag-call">普通 flow</span>'}</td>
        <td><span class="state">运行中</span></td>
        <td class="nw"><button type="button" class="link" data-act="open-flow" data-ws="${ws.id}" data-id="${f.id}">设计</button></td></tr>`).join('')}</tbody></table>`;
  }
  function automation() {
    const s = S();
    const ws = WS[s.ws];
    const cw = canWrite(ws);
    return `<div class="page-head"><div><div class="crumb">Developer Workstation</div><h1>自动化</h1></div>
        <button type="button" class="btn primary" data-act="new-flow"${cw ? '' : ' disabled'}>新建流程</button></div>
      <div class="ws-row"><label class="lbl" for="ws-sel">工作区:</label>
        <select class="inp" id="ws-sel">${Object.values(WS).map((w) => `<option value="${w.id}"${w.id === s.ws ? ' selected' : ''}>${esc(w.name)}</option>`).join('')}</select>
        ${cw ? '' : '<span class="tag">只读</span>'}
        ${ws.isData ? `<span class="tag tag-data">数据 workspace</span>${NEW}` : ''}</div>
      ${cw ? '' : '<div class="banner info">Public 工作区承载共享与历史流程，仅系统管理员可修改；切换到你所属的团队即可创建自己的流程。</div>'}
      <div class="banner info">执行记录已移至 Admin Center → 自动化运行记录。</div>
      ${ws.isData
        ? `<div class="banner data">${NEW} 数据 workspace：这里的 flow 可以用「平台数据」「PostgreSQL」组件连数据库，别的 flow 和 Service Task 调用它们来操作数据。</div>`
        : `<div class="banner call">${NEW} 普通 flow：不能直接连数据库，要查、改数据请用「调用数据 flow」组件。</div>`}
      <div class="toolbar"><input class="inp" id="flow-q" placeholder="搜索名称 / 业务键" value="${esc(s.flowQ)}" autocomplete="off" aria-label="搜索流程"><span class="hint">共 ${ws.flows.length} 个流程</span></div>
      <div class="card tbl-wrap" id="flow-list">${flowRows()}</div>`;
  }

  function flowEditor() {
    const s = S();
    const ws = WS[s.ws];
    const f = curFlow();
    const cw = canWrite(ws);
    const by = callers(f.key);
    const steps = f.steps.map((st, i) => {
      const p = pieceById(st.piece) || { id: st.piece, cat: 'tool', name: st.piece, triggers: [] };
      const blocked = p.restricted && !ws.isData;
      const isTrigger = i === 0;
      const entry = s.run ? s.run.log[i] : null;
      const card = `<button type="button" class="step${i === s.sel ? ' sel' : ''}${blocked ? ' bad' : ''}" data-act="sel-step" data-i="${i}">
        ${icon(p)}<span class="txt"><span class="kind">${isTrigger ? '触发器' : `第 ${i + 1} 步`}</span><span class="nm">${esc(p.name)} · ${esc(st.action)}</span><span class="ds">${esc(summary(st, f))}</span>${blocked ? '<span class="warn">这个 workspace 用不了，运行会报错</span>' : ''}</span>${entry ? rdot(entry.status) : ''}</button>`;
      const last = i === f.steps.length - 1;
      const endsWithReturn = st.piece === 'webhook' && st.action === 'Return Response';
      const joint = !cw || (last && endsWithReturn) ? (last ? '' : '<div class="joint"><span class="v"></span><span class="v"></span></div>')
        : `<div class="joint"><span class="v"></span><button type="button" class="plus" data-act="add-step" data-i="${i + 1}" aria-label="在第 ${i + 1} 步后添加步骤">+</button>${last ? '' : '<span class="v"></span>'}</div>`;
      return card + joint;
    }).join('');
    return `<div class="page-head"><div>
        <div class="crumb"><button type="button" class="link" data-act="nav" data-v="automation">自动化</button> / ${esc(ws.name)}</div>
        <h1>${esc(f.name)}</h1>
        <div class="meta"><span class="tag mono">${esc(f.key)}</span>${ws.isData ? '<span class="tag tag-data">数据 flow</span>' : '<span class="tag tag-call">普通 flow</span>'}${cw ? '' : '<span class="tag">只读</span>'}${by.length ? `<span class="hint">被 ${by.map(esc).join('、')} 调用</span>` : ''}</div></div>
        <div class="actions">${cw ? '<button type="button" class="btn" data-act="run">试运行</button><button type="button" class="btn primary" data-act="publish">发布</button>' : ''}</div></div>
      <div class="editor">
        <div class="canvas" aria-label="flow 步骤">${steps}</div>
        <div class="card panel">
          <div class="ptabs" role="tablist">
            <button type="button" role="tab" class="${s.tab === 'cfg' ? 'on' : ''}" data-act="panel-tab" data-t="cfg">配置</button>
            <button type="button" role="tab" class="${s.tab === 'run' ? 'on' : ''}" data-act="panel-tab" data-t="run">试运行结果${s.run ? (s.run.ok ? ' ✓' : ' ✕') : ''}</button>
          </div>
          <div class="pbody">${s.tab === 'cfg' ? cfgPanel(f, ws, cw) : runPanel()}</div>
        </div>
      </div>`;
  }
  function cfgPanel(f, ws, cw) {
    const s = S();
    const st = f.steps[s.sel];
    if (!st) return '<div class="empty">点左边的步骤查看配置。</div>';
    const p = pieceById(st.piece);
    if (!p) return `<div class="banner fail" style="margin:0">组件 ${esc(st.piece)} 已在 Admin Center 删除，这一步跑不了。</div>${cw && s.sel > 0 ? '<button type="button" class="btn text-danger" data-act="del-step">删除这一步</button>' : ''}`;
    const blocked = p.restricted && !ws.isData;
    return `<div class="p-title">${icon(p)}<b>${esc(p.name)} · ${esc(st.action)}</b>${catTag(p.cat)}${p.planned ? '<span class="tag tag-plan">设计中</span>' : ''}</div>
      <div class="catline">${CATS[p.cat].desc}</div>
      ${blocked ? '<div class="banner fail" style="margin:0"><b>这个组件在这里用不了。</b>它只能在数据 workspace 的 flow 里使用，试运行会报错。要查数据，请删掉这一步，改用「调用数据 flow」。</div>' : ''}
      ${cw ? '' : '<div class="banner info" style="margin:0">只读：Public 工作区仅系统管理员可修改。</div>'}
      <fieldset class="fs"${cw ? '' : ' disabled'}>${body(st, f, ws, s)}</fieldset>
      <div class="p-foot"><span class="hint">表达式：{{trigger.body.variables.xxx}} 取调用方传进来的变量，{{step2.xxx}} 取第 2 步的输出。</span>${cw && s.sel > 0 ? '<button type="button" class="btn text-danger" data-act="del-step">删除这一步</button>' : ''}</div>`;
  }
  function runPanel() {
    const r = S().run;
    if (!r) return '<div class="empty">还没有试运行。点右上角「试运行」，用触发器里的示例 variables 跑一遍。</div>';
    const head = r.ok
      ? `<div class="banner data" style="margin:0"><b>运行成功。</b>${r.result !== undefined ? '返回体在最后一步。' : '这条 flow 没有 Return Response 步骤。'}</div>`
      : `<div class="banner fail" style="margin:0"><b>运行失败。</b>${esc(r.log.find((x) => x.status === 'fail').error)}</div>`;
    return head + r.log.map((e, i) => `<div class="rl ${e.status}">
      <div class="rl-h">${rdot(e.status)}<b>${i + 1}. ${esc(e.title)}</b><span class="rl-s">${e.status === 'ok' ? '成功' : e.status === 'fail' ? '失败' : '未执行'}</span></div>
      ${e.sub ? `<div class="rl-sub"><span class="hint">在数据 workspace 里运行了数据 flow「${esc(e.subName)}」：</span>${e.sub.map((x, j) => `<div>${rdot(x.status)}<span>${j + 1}. ${esc(x.title)}</span></div>`).join('')}</div>` : ''}
      ${e.error ? `<div class="rl-err">${esc(e.error)}</div>` : ''}
      ${e.status === 'ok' ? `<pre class="out">${esc(fmt(e.output))}</pre>` : ''}</div>`).join('');
  }

  /* ---------------- Function Unit / Service Task ---------------- */
  function ownership(key) {
    const hit = findByKey(key);
    if (!hit) return '<span class="tag tag-fail">无人持有：部署会失败</span>';
    if (hit.ws.id === FU.ws) return '<span class="tag tag-call">本工作区的流程（普通 flow）</span>';
    return `<span class="tag tag-data">其他工作区的流程：${esc(hit.ws.name)}${hit.ws.isData ? ' · 数据 flow' : ''}</span><span class="hint">部署时仍能解析，但这里看不到、不能改。</span>`;
  }
  function fuList() {
    return `<div class="page-head"><div><div class="crumb">Developer Workstation</div><h1>Function Units</h1></div></div>
      <div class="card tbl-wrap"><table class="tbl"><thead><tr><th>名称</th><th>团队</th><th>Service Task</th><th>状态</th></tr></thead>
      <tbody><tr class="click" data-act="nav" data-v="process" tabindex="0"><td><b>${esc(FU.name)}</b></td><td>${esc(FU.team)}</td><td class="num">${FU.tasks.filter((t) => t.type === 'service').length} 个</td><td><span class="state">已发布 ${FU.version}</span></td></tr></tbody></table></div>
      <p class="hint" style="margin-top:10px">原型里只放了这一个 FU。点它进入流程设计。</p>`;
  }
  function stResult() {
    const r = S().stRun;
    if (!r) return '';
    const req = `<div class="field"><span class="lbl">发出的请求体（全量流程变量 + context）</span><pre class="out">${esc(fmt(r.request))}</pre></div>`;
    if (!r.ok) return `${req}<div class="banner fail" style="margin:0"><b>Service Task 失败，流程停在这个节点。</b>${esc(r.error)}</div>`;
    return `${req}<div class="field"><span class="lbl">返回体</span><pre class="out">${esc(fmt(r.result))}</pre></div>
      <div class="banner data" style="margin:0"><b>写回流程变量：</b>${Object.keys(r.result.variables).map((k) => `<span class="mono">${esc(k)}</span>`).join('、') || '（没有）'}，然后继续往下走。</div>`;
  }
  function process() {
    const s = S();
    const t = FU.tasks.find((x) => x.id === s.st);
    const keys = Object.values(WS).flatMap((w) => w.flows.map((f) => `<option value="${esc(f.key)}">${esc(f.name)}</option>`)).join('');
    const bpmn = `<div class="bpmn"><span class="bp-ev" aria-label="开始"></span><span class="bp-ar"></span>${FU.tasks.map((x) => (x.type === 'service'
      ? `<button type="button" class="bp-task${x.id === s.st ? ' sel' : ''}" data-act="st-sel" data-id="${x.id}">${esc(x.name)}<small>Service Task</small></button>`
      : `<span class="bp-task">${esc(x.name)}<small>User Task</small></span>`) + '<span class="bp-ar"></span>').join('')}<span class="bp-ev end" aria-label="结束"></span></div>`;
    const vars = Object.entries(FU.vars).map(([k, v]) => fld(k, `<input class="inp" id="fv-${k}" data-bind="fuvars.${k}" value="${esc(v)}"${typeof v === 'number' ? ' data-type="num" inputmode="decimal"' : ''} autocomplete="off">`, '', `fv-${k}`)).join('');
    return `<div class="page-head"><div><div class="crumb"><button type="button" class="link" data-act="nav" data-v="fu">Function Units</button> / ${esc(FU.name)}</div><h1>流程设计</h1>
        <div class="meta"><span class="tag">${esc(FU.team)}</span><span class="hint">点蓝框的 Service Task 看配置</span></div></div></div>
      <div class="proc">
        <div class="card bpmn-wrap">${bpmn}</div>
        <div class="proc-grid">
          <div class="card st-panel">
            <div class="p-title"><b>${esc(t.name)} · Service Task</b></div>
            ${fld('Automation 流程业务键', `<input class="inp mono" id="st-key" list="st-keys" data-bind="st.key" value="${esc(t.key)}" autocomplete="off"><datalist id="st-keys">${keys}</datalist>`, '只填一个业务键，可以从下拉里选，也可以手填。部署时按业务键换成本环境的 flowId。', 'st-key')}
            <div class="meta" id="st-kind">${ownership(t.key)}</div>
            <div class="actions"><button type="button" class="btn primary" data-act="st-run">模拟流程走到这里</button><button type="button" class="btn" data-act="st-open-flow">在自动化中打开</button></div>
            <div id="st-result" style="display:grid;gap:12px">${stResult()}</div>
          </div>
          <div class="card st-panel"><div class="p-title"><b>流程变量（示例，可以改）</b></div><div class="two">${vars}</div>
            <p class="hint">Service Task 调用时把全量流程变量放进 variables 发出去（FR-C09）。直接调数据 flow 时，数据 flow 读到的就是这些变量，名字要对得上。</p></div>
        </div>
      </div>`;
  }

  /* ---------------- Admin 自动化组件 ---------------- */
  function acList() {
    const s = S();
    const q = s.acQ.trim().toLowerCase();
    const list = PIECES.filter((p) => (s.acCat === 'all' || p.cat === s.acCat)
      && (!q || [p.name, p.pkg, ...p.actions, ...p.triggers].some((x) => x.toLowerCase().includes(q))));
    if (!list.length) return '<div class="empty">没有找到组件。</div>';
    return `<table class="tbl"><thead><tr><th aria-label="展开"></th><th>显示名</th><th>包名</th><th>版本</th><th>类型</th><th>类别${NEW}</th><th>启用</th><th>动作</th><th>触发器</th><th>操作</th></tr></thead><tbody>${list.map((p) => {
      const open = !!s.acOpen[p.id];
      const used = usage(p.id);
      const row = `<tr>
        <td><button type="button" class="icon-btn" data-act="ac-expand" data-id="${p.id}" aria-expanded="${open}" aria-label="展开 ${esc(p.name)}">${open ? '▾' : '▸'}</button></td>
        <td class="nw"><b>${esc(p.name)}</b>${p.planned ? ' <span class="tag tag-plan">设计中</span>' : ''}</td>
        <td class="num">${esc(p.pkg)}</td><td class="num">${p.ver}</td>
        <td><span class="tag${p.pieceType === 'CUSTOM' ? ' tag-data' : ''}">${p.pieceType}</span></td>
        <td>${catTag(p.cat)}</td>
        <td><button type="button" class="switch" role="switch" aria-checked="${p.enabled}" data-act="ac-toggle" data-id="${p.id}" aria-label="启用 ${esc(p.name)}"></button></td>
        <td class="num">${p.actions.length}</td><td class="num">${p.triggers.length}</td>
        <td class="nw"><button type="button" class="link" data-act="ac-export" data-id="${p.id}">导出</button> <button type="button" class="link danger" data-act="ac-del" data-id="${p.id}">删除</button></td></tr>`;
      if (!open) return row;
      return `${row}<tr class="xrow"><td></td><td colspan="9"><div class="xdetail">
        ${p.desc ? `<div>${esc(p.desc)}</div>` : ''}
        ${p.actions.length ? `<div class="xline"><span class="xlabel">动作:</span>${p.actions.map((a) => `<span class="atag">${esc(a)}</span>`).join('')}</div>` : ''}
        ${p.triggers.length ? `<div class="xline"><span class="xlabel">触发器:</span>${p.triggers.map((a) => `<span class="ttag">${esc(a)}</span>`).join('')}</div>` : ''}
        <div class="xline"><span class="xlabel">包名:</span><code>${esc(p.pkg)}</code></div>
        <div class="xline"><span class="xlabel">运行时包:</span><span class="tag">${p.archive ? '库内包' : '镜像烘焙'}</span><span class="hint">${p.archive ? '运行时包以 archive 存于数据库,按需安装' : '运行时半已预装进 AP 镜像(REGISTRY 白名单件)'}</span></div>
        <div class="xnew"><div class="xline">${NEW}${catTag(p.cat)}<span>${CATS[p.cat].desc}</span></div>
          <div class="xline"><span class="xlabel">谁能用:</span>${p.restricted ? '只限数据 workspace（Public）的 flow' : '所有 flow'}</div>
          <div class="xline"><span class="xlabel">被这些 flow 用:</span>${used.length ? used.map((u) => `<button type="button" class="link" data-act="goto-flow" data-ws="${u.ws.id}" data-id="${u.flow.id}">${esc(u.flow.name)}</button>`).join('、') : '<span class="hint">还没有</span>'}</div>
          ${p.planned ? '<div class="hint">设计中：这个组件还没开发，图里是做好并导入以后的样子。</div>' : ''}</div>
      </div></td></tr>`;
    }).join('')}</tbody></table>`;
  }
  function pieces() {
    const s = S();
    const count = (c) => PIECES.filter((p) => c === 'all' || p.cat === c).length;
    return `<div class="page-head"><div><div class="crumb">Admin Center</div><h1>自动化组件</h1></div>
        <div class="actions"><button type="button" class="btn" data-act="toast" data-msg="已导出 automation-pieces.csv（原型不会真的下载）">导出</button><button type="button" class="btn" data-act="toast" data-msg="已刷新">刷新</button><button type="button" class="btn primary" data-act="import-open">导入组件(.tgz)</button></div></div>
      <div class="chips" role="tablist" aria-label="按类别筛选"><span class="lbl">类别${NEW}</span>${['all', 'tool', 'data', 'fu'].map((c) => `<button type="button" class="chip${s.acCat === c ? ' on' : ''}" data-act="ac-cat" data-c="${c}">${c === 'all' ? '全部' : CATS[c].label}<span>${count(c)}</span></button>`).join('')}</div>
      ${s.acCat === 'all' ? '' : `<div class="catdesc">${catTag(s.acCat)}<span>${CATS[s.acCat].desc}</span></div>`}
      <div class="toolbar"><input class="inp" id="ac-q" placeholder="搜索包名 / 动作 / 触发器" value="${esc(s.acQ)}" autocomplete="off" aria-label="搜索组件"><span class="hint">共 ${PIECES.length} 个组件</span></div>
      <div class="card tbl-wrap" id="ac-list">${acList()}</div>`;
  }

  /* ---------------- overlays ---------------- */
  const modal = (title, inner, narrow) => `<div class="backdrop" data-act="close-modal"></div>
    <div class="modal${narrow ? ' narrow' : ''}" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="m-head"><h2>${esc(title)}</h2><button type="button" class="icon-btn" data-act="close-modal" aria-label="关闭">✕</button></div>${inner}</div>`;
  function pickList() {
    const s = S();
    const ws = WS[s.ws];
    const q = s.pickQ.trim().toLowerCase();
    const list = PIECES.filter((p) => p.enabled && p.actions.length && (s.picker.cat === 'all' || p.cat === s.picker.cat)
      && (!q || [p.name, p.note, ...p.actions].some((x) => x.toLowerCase().includes(q))));
    if (!list.length) return '<div class="empty">没有找到组件。</div>';
    return list.map((p) => `<button type="button" class="pk-item" data-act="pick-piece" data-id="${p.id}">${icon(p)}
      <span class="txt"><b>${esc(p.name)}${p.planned ? ' <span class="tag tag-plan">设计中</span>' : ''}</b><span class="ds">${esc(p.note)}</span>${p.restricted ? (ws.isData ? '<span class="ok-tag">只限数据 workspace：这里可以用</span>' : '<span class="warn">只限数据 workspace：放在这里运行会报错</span>') : ''}</span>${catTag(p.cat)}</button>`).join('');
  }
  function picker() {
    const s = S();
    const ws = WS[s.ws];
    const usable = PIECES.filter((p) => p.enabled && p.actions.length);
    const nav = ['all', 'tool', 'data', 'fu'].map((c) => `<button type="button" class="pk-cat${s.picker.cat === c ? ' on' : ''}" data-act="pick-cat" data-c="${c}">${c === 'all' ? '全部' : CATS[c].label}<span>${usable.filter((p) => c === 'all' || p.cat === c).length}</span></button>`).join('');
    let main;
    if (s.picker.piece) {
      const p = pieceById(s.picker.piece);
      main = `<button type="button" class="link" data-act="picker-back">← 返回组件列表</button>
        <div class="p-title">${icon(p)}<b>${esc(p.name)}</b>${catTag(p.cat)}</div><p class="hint" style="margin:0">选一个动作：</p>
        <div class="pk-list">${p.actions.map((a) => `<button type="button" class="pk-item" data-act="pick-action" data-a="${esc(a)}"><span class="txt"><b>${esc(a)}</b>${p.id === 'platform-data' ? `<span class="ds">${esc(window.PROTO.PD_NOTES[a])}</span>` : ''}</span></button>`).join('')}</div>`;
    } else {
      main = `${s.picker.cat !== 'all' ? `<div class="catline">${CATS[s.picker.cat].desc}</div>` : ''}
        <input class="inp" id="pick-q" placeholder="搜索组件或动作" value="${esc(s.pickQ)}" autocomplete="off" aria-label="搜索组件">
        <div class="pk-list" id="pick-list">${pickList()}</div>
        <p class="hint" style="margin:0">只列有动作的组件（Schedule 只有触发器，放在第一步）。在 Admin 停用的组件不会出现在这里。</p>`;
    }
    return modal(`添加步骤 · ${ws.name}`, `<div class="picker"><div class="pk-nav">${nav}</div><div class="pk-main">${main}</div></div>`);
  }
  function importModal() {
    const s = S();
    const left = IMPORTABLE.map((it, i) => ({ it, i })).filter(({ it }) => !pieceById(it.piece.id));
    let inner;
    if (s.imp.stage === 0) {
      inner = left.length
        ? `<p class="hint" style="margin:0">原型里用一个示例包代替选文件。真实的包来自仓库 CI 打出的 .tgz。</p><div class="filepick">${left.map(({ it, i }) => `<button type="button" data-act="import-pick" data-i="${i}"><span class="mono">${esc(it.file)}</span><span class="hint">${it.size}</span></button>`).join('')}</div>`
        : '<div class="empty">示例包已经导入了。</div>';
    } else {
      const p = IMPORTABLE[s.imp.i].piece;
      inner = `<dl class="kvl"><dt>文件</dt><dd class="mono">${esc(IMPORTABLE[s.imp.i].file)}</dd><dt>显示名</dt><dd>${esc(p.name)}</dd><dt>包名</dt><dd class="mono">${esc(p.pkg)}</dd><dt>版本</dt><dd class="mono">${p.ver}</dd><dt>类型</dt><dd>CUSTOM · 库内包</dd>
        <dt>类别${NEW}</dt><dd>${catTag(p.cat)} <span class="hint">包名以 piece-fu- 开头，自动识别</span></dd><dt>动作</dt><dd>${p.actions.map(esc).join('、')}</dd></dl>
        <div class="m-foot"><button type="button" class="btn" data-act="import-back">返回</button><button type="button" class="btn primary" data-act="import-do">导入</button></div>`;
    }
    return modal('导入组件(.tgz)', `<div class="m-body">${inner}</div>`, true);
  }
  function deleteModal() {
    const p = pieceById(S().del);
    const used = usage(p.id);
    let msg;
    if (used.length) msg = `该组件被以下 flow 使用:${used.map((u) => u.flow.name).join('、')}。强制删除后这些 flow 运行会失败。仍要删除?`;
    else if (p.pieceType === 'OFFICIAL') msg = `确认把烘焙件 ${p.name} v${p.ver} 从目录中删除?镜像内预装的运行时包不受影响;若它仍在白名单(automation/hermes/pieces.json)且重跑 seed SQL,该组件会再次出现。仍要删除?`;
    else msg = `确认删除 ${p.name} v${p.ver}?其元数据与运行时包将被移除。`;
    return modal('删除组件', `<div class="m-body"><p style="margin:0">${esc(msg)}</p><div class="m-foot"><button type="button" class="btn" data-act="close-modal">取消</button><button type="button" class="btn danger" data-act="ac-del-do">${used.length ? '强制删除' : '删除'}</button></div></div>`, true);
  }
  function newFlowModal() {
    const s = S();
    const ws = WS[s.ws];
    return modal('新建自动化流程', `<div class="m-body">
      ${fld('名称', `<input class="inp" id="nf-name" data-bind="nf.name" value="${esc(s.newFlow.name)}" placeholder="流程显示名称" autocomplete="off">`, '', 'nf-name')}
      ${fld('业务键', `<input class="inp mono" id="nf-key" data-bind="nf.key" value="${esc(s.newFlow.key)}" placeholder="默认由名称生成，创建前可修改" autocomplete="off">`, '服务任务按此键引用流程，它也是跨环境迁移的身份标识；创建后不可再修改。业务键全平台唯一。', 'nf-key')}
      ${ws.isData ? `<div class="banner data" style="margin:0">${NEW} 建在数据 workspace，是数据 flow。</div>` : ''}
      <div id="nf-err" class="warn"></div>
      <div class="m-foot"><button type="button" class="btn" data-act="close-modal">取消</button><button type="button" class="btn primary" data-act="new-flow-do">创建</button></div></div>`, true);
  }
  function overlay() {
    const s = S();
    if (s.picker) return picker();
    if (s.imp) return importModal();
    if (s.del) return deleteModal();
    if (s.newFlow) return newFlowModal();
    return window.ADMIN.overlay();
  }
  function tour() {
    const s = S();
    return `<h2>演示路线<button type="button" class="icon-btn" data-act="tour-toggle" aria-label="收起演示路线">✕</button></h2>
      <p>按顺序点，每一步带你到对应页面。顶栏可以切换身份：用「采购团队 Developer」看 Public 工作区是只读的。</p>
      <ol>${TOUR.map((x, i) => `<li><button type="button" class="${s.tourStep === i + 1 ? 'on' : ''}" data-act="tour-go" data-n="${i + 1}"><span class="n">${i + 1}</span><span class="tt">${esc(x.t)}</span><span class="td">${esc(x.d)}</span></button></li>`).join('')}</ol>
      <p>表里的行是示例数据，刷新页面会还原。</p>`;
  }

  function main() {
    const s = S();
    if (s.view === 'flow' && !curFlow()) s.view = 'automation';
    switch (s.view) {
      case 'automation': return automation();
      case 'flow': return flowEditor();
      case 'fu': return fuList();
      case 'process': return process();
      case 'pieces': return pieces();
      case 'envvars':
      case 'connections': return window.ADMIN.page(s.view);
      default: return '';
    }
  }

  return { side, main, overlay, tour, flowRows, acList, pickList, ownership, stResult, usage, modal };
})();
