// 状态、交互和事件。
window.APP = (() => {
  const { PIECES, IMPORTABLE, TABLES, WS, FU, CATS } = window.PROTO;
  const { clone, pieceById, findByKey, cloneDb, runFlow } = window.SIM;
  const { defaultCfg, discountTryHtml, ruleRows, budgetTryHtml } = window.CFG;
  const $ = (sel) => document.querySelector(sel);

  const S = {
    product: 'dw', view: 'automation', role: 'admin', ws: 'purchase', flowId: null, sel: 0, tab: 'cfg', run: null, flowQ: '',
    picker: null, pickQ: '', acCat: 'all', acQ: '', acOpen: {}, imp: null, del: null, newFlow: null,
    connType: 'all', envEdit: null, envDel: null, connEdit: null, connTest: null, connDel: null,
    st: 'st1', stRun: null, tour: window.innerWidth >= 1200, tourStep: 0,
    tryAmount: '120000', tryLevel: 'A', tryBAmount: '120000', tryBalance: '300000',
  };
  const curFlow = () => (WS[S.ws] ? WS[S.ws].flows.find((f) => f.id === S.flowId) : undefined);
  const curStep = () => { const f = curFlow(); return f ? f.steps[S.sel] : undefined; };
  const curTask = () => FU.tasks.find((t) => t.id === S.st);
  const closed = { picker: null, imp: null, del: null, newFlow: null, ...window.ADMIN.closedKeys };
  const anyOverlay = () => Object.keys(closed).some((k) => S[k]);

  function render() {
    const V = window.VIEWS;
    ['dw', 'ac'].forEach((p) => {
      const b = $(`#tab-${p}`);
      b.classList.toggle('on', S.product === p);
      b.setAttribute('aria-selected', String(S.product === p));
    });
    $('#persona').value = S.role;
    $('#side').innerHTML = V.side();
    $('#main').innerHTML = V.main();
    const ov = $('#overlay');
    const html = V.overlay();
    ov.innerHTML = html;
    ov.hidden = !html;
    renderTour();
  }
  function renderTour() {
    const el = $('#tour');
    el.hidden = !S.tour;
    $('#tour-btn').setAttribute('aria-expanded', String(S.tour));
    if (S.tour) el.innerHTML = window.VIEWS.tour();
  }
  let toastTimer;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 3000);
  }
  function go(patch) {
    Object.assign(S, patch);
    render();
    $('#main').scrollTop = 0;
  }
  const openFlow = (wsId, id) => go({ ...closed, product: 'dw', view: 'flow', ws: wsId, flowId: id, sel: 0, tab: 'cfg', run: null });
  const envelope = (variables) => ({ body: { variables, context: {} } });
  function runCurrent() {
    const f = curFlow();
    const trigger = f.steps[0].piece === 'webhook' ? envelope(clone(f.sample || {})) : {};
    S.run = runFlow(f, WS[S.ws], trigger, cloneDb(), 0);
    S.tab = 'run';
  }
  function insertStep(pid, action) {
    const f = curFlow();
    const at = S.picker.at;
    f.steps.splice(at, 0, { piece: pid, action, cfg: defaultCfg(pid, action) });
    const p = pieceById(pid);
    Object.assign(S, { picker: null, sel: at, run: null, tab: 'cfg' });
    render();
    toast(p.restricted && !WS[S.ws].isData ? `已添加。注意：「${p.name}」在这个工作区运行时会报错。` : `已添加「${p.name} · ${action}」。`);
  }
  const firstField = () => TABLES[curStep().cfg.table].fields[0].n;
  const ROW_DEFAULT = {
    vars: () => ({ k: '', v: '' }),
    sets: () => ({ f: firstField(), v: '' }),
    fields: () => ({ key: '', label: '', type: 'text' }),
  };
  const AFTER = {
    resetTable() {
      const c = curStep().cfg;
      const T = TABLES[c.table];
      Object.assign(c, { where: [], sortField: '', columns: [], keyField: T.pk, sets: [{ f: (T.fields[1] || T.fields[0]).n, v: '' }] });
    },
    resetParams() {
      const c = curStep().cfg;
      const hit = findByKey(c.flow);
      c.params = {};
      (hit ? hit.flow.params : []).forEach((k) => { c.params[k] = ''; });
    },
  };

  const ACTS = {
    product: (d) => go({ ...closed, product: d.p, view: d.p === 'dw' ? 'automation' : 'pieces' }),
    nav: (d) => go({ ...closed, view: d.v }),
    toast: (d) => toast(d.msg),
    'open-flow': (d) => openFlow(d.ws, d.id),
    'goto-flow': (d) => openFlow(d.ws, d.id),
    'goto-key': (d) => { const hit = findByKey(d.key); if (hit) openFlow(hit.ws.id, hit.flow.id); },
    'new-flow': () => {
      const ws = WS[S.ws];
      S.newFlow = { name: '', key: `${ws.isData ? 'data' : 'purchase'}.flow-${ws.flows.length + 1}` };
      render();
      $('#nf-name').focus();
    },
    'new-flow-do': () => {
      const ws = WS[S.ws];
      const name = S.newFlow.name.trim();
      const key = S.newFlow.key.trim();
      const err = !name ? '请填写名称。' : !/^[a-z0-9][a-z0-9.-]*$/.test(key) ? '业务键只能用小写字母、数字、点和横线。' : findByKey(key) ? `业务键 ${key} 已被占用（全平台唯一）。` : '';
      if (err) { $('#nf-err').textContent = err; return; }
      const id = `new${Date.now()}`;
      ws.flows.push({ id, name, key, params: [], sample: {}, steps: [
        { piece: 'webhook', action: 'Catch Webhook', cfg: { authType: 'header' } },
        { piece: 'webhook', action: 'Return Response', cfg: defaultCfg('webhook', 'Return Response') },
      ] });
      openFlow(ws.id, id);
      toast(`流程 ${name} 已创建`);
    },
    'sel-step': (d) => { S.sel = Number(d.i); S.tab = 'cfg'; render(); },
    'add-step': (d) => {
      S.picker = { at: Number(d.i), cat: 'all', piece: null };
      S.pickQ = '';
      render();
      const q = $('#pick-q');
      if (q) q.focus();
    },
    'pick-cat': (d) => { S.picker.cat = d.c; S.picker.piece = null; render(); },
    'pick-piece': (d) => {
      const p = pieceById(d.id);
      if (p.actions.length === 1) insertStep(p.id, p.actions[0]);
      else { S.picker.piece = p.id; render(); }
    },
    'pick-action': (d) => insertStep(S.picker.piece, d.a),
    'picker-back': () => { S.picker.piece = null; render(); },
    'close-modal': () => { Object.assign(S, closed); render(); },
    'del-step': () => {
      const f = curFlow();
      if (S.sel === 0) return;
      f.steps.splice(S.sel, 1);
      S.sel = Math.min(S.sel, f.steps.length - 1);
      S.run = null;
      render();
    },
    run: () => { runCurrent(); render(); },
    publish: () => toast(`已发布 ${curFlow().name}（原型不会真的保存）`),
    'panel-tab': (d) => { S.tab = d.t; render(); },
    'add-group': () => { const c = curStep().cfg; c.where = c.where || []; c.where.push([{ f: firstField(), op: '=', v: '', v2: '' }]); S.run = null; render(); },
    'add-cond': (d) => { curStep().cfg.where[Number(d.g)].push({ f: firstField(), op: '=', v: '', v2: '' }); S.run = null; render(); },
    'del-cond': (d) => {
      const w = curStep().cfg.where;
      const g = Number(d.g);
      w[g].splice(Number(d.i), 1);
      if (!w[g].length) w.splice(g, 1);
      S.run = null;
      render();
    },
    'del-group': (d) => { curStep().cfg.where.splice(Number(d.g), 1); S.run = null; render(); },
    'add-row': (d) => { const c = curStep().cfg; c[d.list] = c[d.list] || []; c[d.list].push(ROW_DEFAULT[d.list]()); S.run = null; render(); },
    'del-row': (d) => { curStep().cfg[d.list].splice(Number(d.i), 1); S.run = null; render(); },
    'ac-cat': (d) => { S.acCat = d.c; render(); },
    'ac-expand': (d) => { S.acOpen[d.id] = !S.acOpen[d.id]; $('#ac-list').innerHTML = window.VIEWS.acList(); },
    'ac-toggle': (d) => {
      const p = pieceById(d.id);
      p.enabled = !p.enabled;
      $('#ac-list').innerHTML = window.VIEWS.acList();
      toast(p.enabled ? `已启用 ${p.name}` : `已停用 ${p.name}：设计器组件面板里不再显示，已在用它的 flow 照常运行`);
    },
    'ac-export': (d) => { const p = pieceById(d.id); toast(`已导出 ${p.pkg.replace('@', '').replace('/', '-')}-${p.ver}.tgz（原型不会真的下载）`); },
    'ac-del': (d) => { S.del = d.id; render(); },
    'ac-del-do': () => {
      const p = pieceById(S.del);
      PIECES.splice(PIECES.indexOf(p), 1);
      Object.assign(S, closed);
      render();
      toast('组件已删除');
    },
    'import-open': () => { S.imp = { stage: 0, i: null }; render(); },
    'import-pick': (d) => { S.imp = { stage: 1, i: Number(d.i) }; render(); },
    'import-back': () => { S.imp = { stage: 0, i: null }; render(); },
    'import-do': () => {
      const p = clone(IMPORTABLE[S.imp.i].piece);
      PIECES.push(p);
      Object.assign(S, closed, { acCat: p.cat });
      render();
      toast(`已导入 ${p.name} v${p.ver},设计器立即可见；归到「${CATS[p.cat].label}」`);
    },
    'st-sel': (d) => { S.st = d.id; S.stRun = null; render(); },
    'st-run': () => {
      const t = curTask();
      const request = { variables: clone(FU.vars), context: { processInstanceId: '（引擎填）', executionId: '（引擎填）', flowId: '（部署时按业务键解析）' } };
      const hit = findByKey(t.key);
      if (!hit) S.stRun = { ok: false, request, error: `业务键 ${t.key} 无人持有，部署时就会失败。` };
      else {
        const r = runFlow(hit.flow, hit.ws, { body: request }, cloneDb(), 0);
        if (!r.ok) S.stRun = { ok: false, request, error: r.log.find((x) => x.status === 'fail').error };
        else if (!r.result || typeof r.result.variables !== 'object') S.stRun = { ok: false, request, error: '返回体没有 variables，Service Task 按契约报错（FR-C08）。' };
        else S.stRun = { ok: true, request, result: r.result };
      }
      $('#st-result').innerHTML = window.VIEWS.stResult();
    },
    'st-open-flow': () => { const hit = findByKey(curTask().key); if (hit) openFlow(hit.ws.id, hit.flow.id); else toast('这个业务键没有对应的流程。'); },
    'tour-toggle': () => { S.tour = !S.tour; renderTour(); },
    'tour-go': (d) => tourGo(Number(d.n)),
  };

  Object.assign(ACTS, window.ADMIN.acts);

  function tourGo(n) {
    S.tourStep = n;
    const base = { ...closed, run: null, stRun: null };
    const flow = (ws, id, sel) => ({ ...base, product: 'dw', view: 'flow', ws, flowId: id, sel, tab: 'cfg' });
    const runFlowAt = (ws, id, sel) => { Object.assign(S, flow(ws, id, sel)); runCurrent(); go({}); };
    switch (window.PROTO.TOUR[n - 1].id) {
      case 'pieces': go({ ...base, product: 'ac', view: 'pieces', acCat: 'all', acQ: '', acOpen: { 'platform-data': true } }); break;
      case 'import': go({ ...base, product: 'ac', view: 'pieces', imp: { stage: 0, i: null } }); break;
      case 'conns': go({ ...base, product: 'ac', view: 'connections', connType: 'all' }); break;
      case 'envs': go({ ...base, product: 'ac', view: 'envvars' }); break;
      case 'fsq': go(flow('public', 'f_sq', 1)); break;
      case 'fud': go(flow('public', 'f_ud', 1)); break;
      case 'pcd': runFlowAt('purchase', 'p_cd', 0); break;
      case 'fu': go(flow('purchase', 'p_cd', 2)); break;
      case 'bad': runFlowAt('purchase', 'p_bad', 1); break;
      case 'ct': runFlowAt('purchase', 'p_ct', 1); break;
      case 'st': go({ ...base, product: 'dw', view: 'process', st: 'st1' }); break;
      default: break;
    }
    if (window.innerWidth < 1200) { S.tour = false; renderTour(); }
  }

  function setBind(path, raw, type) {
    const parts = path.split('.');
    const roots = { step: curStep(), flow: curFlow(), st: curTask(), fuvars: FU.vars, nf: S.newFlow, ee: S.envEdit, ce: S.connEdit };
    let obj = roots[parts[0]];
    if (!obj) return;
    for (let i = 1; i < parts.length - 1; i += 1) {
      if (obj[parts[i]] == null) obj[parts[i]] = {};
      obj = obj[parts[i]];
    }
    let val = raw;
    if (type === 'num') { const num = Number(raw); val = raw === '' || Number.isNaN(num) ? raw : num; }
    obj[parts[parts.length - 1]] = val;
  }
  function onTry(el) {
    const k = el.dataset.try;
    if (k === 'amount') S.tryAmount = el.value;
    if (k === 'level') S.tryLevel = el.value;
    if (k === 'bamount') S.tryBAmount = el.value;
    if (k === 'balance') S.tryBalance = el.value;
    const out = $('#try-out');
    if (!out) return;
    if (k === 'amount' || k === 'level') {
      out.innerHTML = discountTryHtml(S);
      $('#rule-rows').innerHTML = ruleRows(S);
    } else out.innerHTML = budgetTryHtml(S);
  }

  document.addEventListener('click', (e) => {
    const el = e.target.closest('[data-act]');
    if (!el || el.disabled || el.closest('fieldset[disabled]')) return;
    const fn = ACTS[el.dataset.act];
    if (fn) { e.preventDefault(); fn(el.dataset, el); }
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && anyOverlay()) { ACTS['close-modal'](); return; }
    if (e.key === 'Enter' && e.target.matches('tr[data-act]')) { e.preventDefault(); e.target.click(); }
  });
  document.addEventListener('input', (e) => {
    const el = e.target;
    if (el.id === 'pick-q') { S.pickQ = el.value; $('#pick-list').innerHTML = window.VIEWS.pickList(); return; }
    if (el.id === 'ac-q') { S.acQ = el.value; $('#ac-list').innerHTML = window.VIEWS.acList(); return; }
    if (el.id === 'flow-q') { S.flowQ = el.value; $('#flow-list').innerHTML = window.VIEWS.flowRows(); return; }
    if (el.dataset.try) { onTry(el); return; }
    if (!el.dataset.bind || el.tagName === 'SELECT' || el.type === 'checkbox') return;
    setBind(el.dataset.bind, el.value, el.dataset.type);
    S.run = null;
    if (el.id === 'st-key') {
      S.stRun = null;
      $('#st-kind').innerHTML = window.VIEWS.ownership(el.value.trim());
      $('#st-result').innerHTML = '';
    }
  });
  document.addEventListener('change', (e) => {
    const el = e.target;
    if (el.id === 'persona') { S.role = el.value; render(); return; }
    if (el.id === 'ws-sel') { go({ ws: el.value, flowQ: '' }); return; }
    if (el.dataset.try) { onTry(el); return; }
    if (window.ADMIN.onChange(el)) return;
    if (el.dataset.col) {
      const c = curStep().cfg;
      const set = new Set(c.columns || []);
      if (el.checked) set.add(el.dataset.col); else set.delete(el.dataset.col);
      c.columns = [...set];
      S.run = null;
      return;
    }
    if (!el.dataset.bind || (el.tagName !== 'SELECT' && el.type !== 'checkbox')) return;
    setBind(el.dataset.bind, el.type === 'checkbox' ? el.checked : el.value, el.dataset.type);
    if (el.dataset.after) AFTER[el.dataset.after]();
    S.run = null;
    render();
  });

  return { S, curFlow, render, toast };
})();
window.APP.render();
