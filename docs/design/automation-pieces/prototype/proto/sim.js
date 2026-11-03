// 试运行模拟：按 flow 的步骤逐步执行，规则来自方案页的设计。
window.SIM = (() => {
  const { PIECES, TABLES, DISCOUNT_RULES, WS, MAX_PAGE_SIZE, CONNECTIONS, TEST_RESULTS } = window.PROTO;
  // 连接要存在、类型对、授权给当前工作区；「测试连接」示意结果里连不通的，运行时也连不通
  function useConn(id, type, ws) {
    const label = type === 'db' ? '数据库' : 'HTTP';
    if (!id) throw new Error(`没有选${label}连接。`);
    const conn = CONNECTIONS.find((c) => c.id === id);
    if (!conn || conn.type !== type) throw new Error(`连接 ${id} 不存在，或者不是${label}连接。`);
    if (!conn.workspaces.includes(ws.id)) throw new Error(`连接 ${id} 没有授权给「${ws.name}」工作区。`);
    const t = TEST_RESULTS[id];
    if (t && !t.ok) throw new Error(`连接 ${id}：${t.kind}。${t.msg}`);
    return conn;
  }
  const clone = (o) => (o === undefined ? undefined : JSON.parse(JSON.stringify(o)));
  const pieceById = (id) => PIECES.find((p) => p.id === id);

  function findByKey(key) {
    if (!key) return null;
    for (const ws of Object.values(WS)) {
      const flow = ws.flows.find((f) => f.key === key);
      if (flow) return { ws, flow };
    }
    return null;
  }

  function getPath(obj, path) {
    const parts = path.replace(/\[(\d+)\]/g, '.$1').split('.').filter(Boolean);
    let cur = obj;
    for (const k of parts) { if (cur == null) return undefined; cur = cur[k]; }
    return cur;
  }
  function resolve(expr, ctx) {
    if (typeof expr !== 'string') return expr;
    const whole = expr.match(/^\s*\{\{\s*([\w.[\]]+)\s*\}\}\s*$/);
    if (whole) return getPath(ctx, whole[1]);
    return expr.replace(/\{\{\s*([\w.[\]]+)\s*\}\}/g, (_, p) => {
      const v = getPath(ctx, p);
      if (v == null) return '';
      return typeof v === 'object' ? JSON.stringify(v) : String(v);
    });
  }

  const cloneDb = () => Object.fromEntries(Object.entries(TABLES).map(([k, t]) => [k, { rows: clone(t.rows) }]));
  const fieldType = (T, f) => (f === '_status' ? 'text' : ((T.fields.find((x) => x.n === f) || {}).t || 'text'));

  function cmp(a, b, type) {
    if (type === 'number') {
      const x = Number(a);
      const y = Number(b);
      return x === y ? 0 : x < y ? -1 : 1;
    }
    const x = String(a ?? '');
    const y = String(b ?? '');
    return x === y ? 0 : x < y ? -1 : 1;
  }

  // 运算符按字段类型比较（方案页：= ≠ > ≥ < ≤ in / not in / contains / startsWith / isEmpty / between）
  function match(row, cd, r, type) {
    const a = row[cd.f];
    if (cd.op === 'isEmpty') return a == null || a === '';
    if (type === 'number' && (a == null || a === '')) return cd.op === '≠' || cd.op === 'not in';
    const v = r(cd.v);
    switch (cd.op) {
      case '=': return cmp(a, v, type) === 0;
      case '≠': return cmp(a, v, type) !== 0;
      case '>': return cmp(a, v, type) > 0;
      case '≥': return cmp(a, v, type) >= 0;
      case '<': return cmp(a, v, type) < 0;
      case '≤': return cmp(a, v, type) <= 0;
      case 'in':
      case 'not in': {
        const hit = String(v ?? '').split(',').map((s) => s.trim()).filter(Boolean).some((x) => cmp(a, x, type) === 0);
        return cd.op === 'in' ? hit : !hit;
      }
      case 'contains':
      case 'startsWith':
        if (type !== 'text') throw new Error(`${cd.op} 只能用在文本字段，${cd.f} 是数字字段。`);
        return cd.op === 'contains' ? String(a ?? '').includes(String(v ?? '')) : String(a ?? '').startsWith(String(v ?? ''));
      case 'between': return cmp(a, v, type) >= 0 && cmp(a, r(cd.v2), type) <= 0;
      default: return true;
    }
  }
  // 组内 AND，组与组之间 OR（最多一层 OR）
  function whereMatch(row, where, r, T) {
    const groups = (where || []).filter((g) => g.length);
    if (!groups.length) return true;
    return groups.some((g) => g.every((cd) => match(row, cd, r, fieldType(T, cd.f))));
  }

  function coerce(T, tableName, field, value) {
    const def = T.fields.find((x) => x.n === field);
    if (!def) throw new Error(`字段校验失败：表 ${tableName} 没有字段 ${field}。`);
    if (value == null || value === '') return null;
    if (def.t === 'number') {
      const n = Number(value);
      if (Number.isNaN(n)) throw new Error(`字段校验失败：${field} 是数字字段，收到「${value}」。`);
      return n;
    }
    return String(value);
  }
  function toRows(val) {
    if (Array.isArray(val)) return val;
    if (val && typeof val === 'object') return [val];
    throw new Error('「数据」要是一行（对象）或多行（数组），例如 {{trigger.body.variables.rows}}。');
  }
  function project(row, cols) {
    const out = {};
    (cols && cols.length ? cols : Object.keys(row)).forEach((k) => { out[k] = row[k]; });
    return out;
  }

  function execPlatformData(st, r, db, ws) {
    const c = st.cfg;
    const a = st.action;
    useConn(c.conn, 'db', ws);
    if (a === 'List Tables') return { tables: Object.keys(TABLES).map((name) => ({ name, label: TABLES[name].label })) };
    const T = TABLES[c.table];
    if (!T) throw new Error(`没有已部署的表 ${c.table || '（未选）'}。`);
    const t = db[c.table];
    const byPk = (v) => t.rows.find((x) => String(x[T.pk]) === String(v));
    const needRow = (v) => {
      const row = byPk(v);
      if (!row) throw new Error(`表 ${c.table} 里找不到 ${T.pk} = ${v} 的行。`);
      return row;
    };
    const allocPk = () => {
      const n = t.rows.reduce((m, x) => Math.max(m, Number(String(x[T.pk]).replace(T.pkPrefix, '')) || 0), 0) + 1;
      return `${T.pkPrefix}${String(n).padStart(4, '0')}`;
    };
    const prepare = (item) => {
      const row = {};
      Object.entries(item).forEach(([k, v]) => { row[k] = coerce(T, c.table, k, v); });
      return row;
    };
    switch (a) {
      case 'Get Schema':
        return { table: c.table, primaryKey: T.pk, fields: T.fields.map((f) => ({ name: f.n, type: f.t })) };
      case 'Find Rows': {
        const size = Number(c.pageSize);
        if (!Number.isInteger(size) || size <= 0) throw new Error('每页行数要是正整数。');
        if (size > MAX_PAGE_SIZE) throw new Error(`每页最多 ${MAX_PAGE_SIZE} 行（行数上限）。`);
        const page = Math.max(1, Number(c.page) || 1);
        let rows = t.rows.filter((row) => whereMatch(row, c.where, r, T));
        if (c.sortField) rows = [...rows].sort((x, y) => cmp(x[c.sortField], y[c.sortField], fieldType(T, c.sortField)) * (c.sortDir === 'DESC' ? -1 : 1));
        const total = rows.length;
        return { rows: rows.slice((page - 1) * size, page * size).map((row) => project(row, c.columns)), total, page, pageSize: size };
      }
      case 'Count':
        return { count: t.rows.filter((row) => whereMatch(row, c.where, r, T)).length };
      case 'Get Row by Key':
        return { ...needRow(r(c.keyValue)) };
      case 'Insert Row(s)': {
        const list = toRows(r(c.rows)).map(prepare);
        const seen = new Set();
        list.forEach((row) => {
          if (row[T.pk] == null) {
            if (!T.pkAuto) throw new Error(`缺少主键 ${T.pk}。这张表的主键要自己填，不是平台分配的。`);
            row[T.pk] = allocPk();
          }
          if (byPk(row[T.pk]) || seen.has(String(row[T.pk]))) throw new Error(`主键 ${T.pk} = ${row[T.pk]} 已存在。要改已有的行请用 Update Row 或 Upsert。`);
          seen.add(String(row[T.pk]));
          row._status = 'ACTIVE';
          t.rows.push(row);
        });
        return { inserted: list.length, keys: list.map((row) => row[T.pk]) };
      }
      case 'Update Row': {
        const sets = {};
        (c.sets || []).forEach((s) => { sets[s.f] = coerce(T, c.table, s.f, r(s.v)); });
        if (!Object.keys(sets).length) throw new Error('没有要改的字段。');
        let targets;
        if (c.mode === 'where') {
          const max = Number(c.maxAffected);
          if (!Number.isInteger(max) || max <= 0) throw new Error('按条件批量更新必须填「最大影响行数」。');
          targets = t.rows.filter((row) => whereMatch(row, c.where, r, T));
          if (targets.length > max) throw new Error(`匹配到 ${targets.length} 行，超过最大影响行数 ${max}，没有做任何修改。`);
        } else {
          targets = [needRow(r(c.keyValue))];
        }
        targets.forEach((row) => Object.assign(row, sets));
        return { updated: targets.length };
      }
      case 'Upsert': {
        const kf = c.keyField || T.pk;
        const list = toRows(r(c.rows)).map(prepare);
        let inserted = 0;
        let updated = 0;
        list.forEach((row) => {
          if (row[kf] == null) throw new Error(`每一行都要有 ${kf}，用它判断是新增还是修改。`);
          const hit = t.rows.find((x) => String(x[kf]) === String(row[kf]));
          if (hit) { Object.assign(hit, row); updated += 1; } else { t.rows.push({ ...row, _status: 'ACTIVE' }); inserted += 1; }
        });
        return { inserted, updated };
      }
      case 'Deactivate Row': {
        const row = needRow(r(c.keyValue));
        row._status = 'INACTIVE';
        return { deactivated: 1, key: row[T.pk] };
      }
      case 'Delete Row': {
        const row = needRow(r(c.keyValue));
        t.rows.splice(t.rows.indexOf(row), 1);
        return { deleted: 1, key: row[T.pk] };
      }
      default:
        throw new Error(`未知 action ${a}`);
    }
  }

  function calcDiscount(amount, level) {
    if (!Number.isFinite(amount) || amount <= 0) throw new Error('金额不对：需要一个大于 0 的数字。');
    if (!level) throw new Error('没有拿到供应商等级，可能上一步没查到这个供应商。');
    const hit = DISCOUNT_RULES.find((x) => x.test(amount, level));
    if (!hit) throw new Error(`供应商等级 ${level} 不在规则里`);
    return { discount: hit.rate };
  }
  function checkBudget(amount, balance) {
    if (!Number.isFinite(amount)) throw new Error('申请金额不对：需要一个数字。');
    if (!Number.isFinite(balance)) throw new Error('没有拿到预算余额，可能上一步没查到这个部门的预算。');
    return { pass: amount <= balance, over: Math.max(0, amount - balance) };
  }

  function execStep(st, ctx, db, depth, meta, ws) {
    const c = st.cfg || {};
    const r = (v) => resolve(v, ctx);
    if (st.piece === 'platform-data') return execPlatformData(st, r, db, ws);
    switch (`${st.piece}:${st.action}`) {
      case 'webhook:Catch Webhook': return ctx.trigger;
      case 'schedule:Every Day': return { 说明: `定时触发（每天 ${String(c.hour).padStart(2, '0')}:00），没有输入数据` };
      case 'webhook:Return Response': {
        const variables = {};
        (c.vars || []).filter((x) => x.k).forEach((x) => { variables[x.k] = r(x.v); });
        return { variables };
      }
      case 'http:Send HTTP request': {
        const url = c.conn ? useConn(c.conn, 'http', ws).baseUrl + (c.path || '') : (c.url || '');
        if (!url) throw new Error('没有填地址：选一个连接并填路径，或者直接填完整地址。');
        return /rates\.csv$/.test(url)
          ? { status: 200, headers: { 'content-type': 'text/csv' }, body: 'currency,rate\nUSD,7.12\nEUR,7.71\nJPY,0.047' }
          : { status: 200, headers: {}, body: {} };
      }
      case 'csv:Convert CSV to JSON': {
        const text = r(c.csv);
        if (typeof text !== 'string' || !text.trim()) throw new Error('没有拿到 CSV 文本，检查 CSV Text 是否引用了上一步的 body。');
        const sep = c.delimiter === 'tab' ? '\t' : ',';
        const lines = text.trim().split('\n').map((l) => l.split(sep));
        if (!c.hasHeaders) return lines;
        const [head, ...rest] = lines;
        return rest.map((v) => Object.fromEntries(head.map((k, j) => [k, v[j]])));
      }
      case 'call-data-flow:Call Data Flow': {
        const hit = findByKey(c.flow);
        if (!hit || !hit.ws.isData) throw new Error(`找不到业务键 ${c.flow || '（未选）'} 对应的数据 flow。`);
        if (depth > 2) throw new Error('调用层数太深。');
        const variables = {};
        Object.entries(c.params || {}).forEach(([k, v]) => { variables[k] = r(v); });
        const sub = runFlow(hit.flow, hit.ws, { body: { variables, context: {} } }, db, depth + 1);
        meta.sub = sub.log;
        meta.subName = hit.flow.name;
        if (!sub.ok) throw new Error(`数据 flow「${hit.flow.name}」运行失败：${sub.log.find((x) => x.status === 'fail').error}`);
        return sub.result;
      }
      case 'content-organizer:Extract Fields': {
        const values = {};
        (c.fields || []).filter((x) => x.key).forEach((x) => { values[x.key] = `（从文档识别出的${x.label || x.key}）`; });
        return { values, sessionId: '（Content Organizer 返回）', fileId: '（Content Organizer 返回）' };
      }
      case 'postgres:Find Rows': useConn(c.conn, 'db', ws); return { rows: [], rowCount: 0 };
      case 'fu-purchase:计算折扣': {
        const lv = r(c.level);
        return calcDiscount(Number(r(c.amount)), lv == null ? '' : String(lv));
      }
      case 'fu-purchase:检查预算': {
        const bal = r(c.balance);
        return checkBudget(Number(r(c.amount)), bal == null || bal === '' ? NaN : Number(bal));
      }
      default: return { 说明: '原型没有模拟这个 action 的输出' };
    }
  }

  function runFlow(flow, ws, trigger, db, depth) {
    const ctx = { trigger };
    const log = [];
    let ok = true;
    let result;
    flow.steps.forEach((st, i) => {
      const p = pieceById(st.piece);
      const title = `${p ? p.name : st.piece} · ${st.action}`;
      if (!ok) { log.push({ title, status: 'skip' }); return; }
      const meta = {};
      try {
        if (!p) throw new Error(`组件 ${st.piece} 已被删除，这一步跑不了。`);
        if (p.restricted && !ws.isData) throw new Error(`「${p.name}」组件只能在数据 workspace 的 flow 里使用。请改用「调用数据 flow」组件调用数据 flow。`);
        const out = execStep(st, ctx, db, depth, meta, ws);
        ctx[`step${i + 1}`] = out;
        if (st.piece === 'webhook' && st.action === 'Return Response') result = out;
        log.push({ title, status: 'ok', output: out, sub: meta.sub, subName: meta.subName });
      } catch (err) {
        ok = false;
        log.push({ title, status: 'fail', error: err.message, sub: meta.sub, subName: meta.subName });
      }
    });
    return { log, ok, result };
  }

  return { clone, pieceById, findByKey, resolve, cloneDb, runFlow, calcDiscount, checkBudget };
})();
