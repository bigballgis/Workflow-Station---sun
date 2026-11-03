// 原型数据。现有组件的名称、版本、action/trigger 取自 automation/packages/pieces 源码与 hermes/pieces.json；
// 「设计中」的组件（平台数据、调用数据 flow、采购 FU 组件）按方案页的设计；表里的行是示例数据。
window.PROTO = (() => {
  const CATS = {
    tool: { label: '基础工具', desc: '和业务无关的通用工具：格式转换、发请求、定时、文本处理。配好参数就能用，所有 flow 都能用。' },
    data: { label: '数据', desc: '唯一能碰数据库的一类。「平台数据」「PostgreSQL」只能在数据 workspace 用；「调用数据 flow」谁都能用，它是普通 flow 拿数据的入口。' },
    fu: { label: 'FU 专属', desc: '某一个 Function Unit 的业务规则，比如折扣、预算校验。只算，不连数据库、不调外部系统。' },
  };

  const P = (id, name, ver, cat, desc, note, actions, triggers, extra) => ({
    id, name, ver, cat, desc, note, actions, triggers,
    pkg: `@activepieces/piece-${id}`, pieceType: 'OFFICIAL', archive: false, enabled: true, planned: false, restricted: false,
    ...(extra || {}),
  });
  const PLANNED = { planned: true, pieceType: 'CUSTOM', archive: true };

  const PIECES = [
    P('webhook', 'Webhook', '0.1.40', 'tool', 'Receive HTTP requests and trigger flows using unique URLs.', 'flow 的入口（Catch Webhook）和出口（Return Response）',
      ['Return Response', 'Respond and Wait for Next Webhook'], ['Catch Webhook']),
    P('schedule', 'Schedule', '0.1.21', 'tool', 'Trigger flow with fixed schedule', '按时间启动 flow（只有触发器）',
      [], ['Every X Minutes', 'Every Hour', 'Every Day', 'Every Week', 'Every Month', 'Cron Expression']),
    P('http', 'HTTP', '0.11.18', 'tool', 'Sends HTTP requests and return responses', '调用任意一个接口',
      ['Send HTTP request', 'Parse URL'], []),
    P('csv', 'CSV', '0.4.20', 'tool', 'Manipulate CSV text', 'CSV、Excel 和 JSON 互转',
      ['Convert CSV to JSON', 'Convert JSON to CSV', 'Convert Excel to CSV'], []),
    P('json', 'JSON', '0.1.11', 'tool', '', 'JSON 和文本互转、合并、JSONata 查询',
      ['Convert Text to Json', 'Convert Json to Text', 'Merge JSON Objects', 'Run JSONata Query'], []),
    P('xml', 'XML', '0.1.19', 'tool', 'Extensible Markup Language for storing and transporting data', 'XML 和 JSON 互转',
      ['Convert JSON to XML', 'Convert XML to JSON'], []),
    P('pdf', 'PDF', '0.5.9', 'tool', '', '提取文字、合并、拆页、转图片',
      ['Extract Text', 'Text to PDF', 'Image to PDF', 'Merge PDFs', 'Extract PDF Pages', 'PDF Page Count', 'Add Text to PDF', 'Add Image to PDF', 'Convert to Image'], []),
    P('file-helper', 'Files Helper', '0.1.28', 'tool', 'Read file content and return it in different formats.', '读写文件、压缩解压、改编码',
      ['Read File', 'Create file', 'Get File Name', 'Check file type', 'Change File Encoding', 'Zip Files', 'Unzip File'], []),
    P('text-helper', 'Text Helper', '0.6.4', 'tool', 'Tools for text processing', '拼接、查找替换、拆分、HTML / Markdown 转换',
      ['Concatenate', 'Replace', 'Split', 'Find', 'Find All', 'Slugify', 'Use Default Value if Input is Empty', 'Extract from HTML', 'HTML to Markdown', 'Markdown to HTML', 'Remove HTML Tags', 'List to Text Table'], []),
    P('data-mapper', 'Data Mapper', '0.3.20', 'tool', 'tools to manipulate data structure', '把一种字段结构对应成另一种',
      ['Advanced Mapping'], []),
    P('biz-calendar', 'Business Calendar', '1.0.0', 'tool', '工作日 / SLA 到期日计算（纯本地，无外网）。', '工作日计算（自研）',
      ['Add Business Days', 'Business Days Between', 'Is Business Day'], ['SLA Due Soon']),
    P('hash-helper', 'Hash Helper', '1.0.0', 'tool', '文本摘要与 Base64 编解码(纯本地,无外网)。', '哈希、Base64 编解码（自研）',
      ['Hash Text', 'Base64 Encode', 'Base64 Decode'], []),
    P('postgres', 'PostgreSQL', '0.3.0', 'data', 'Connect to the postgres database over SSL', '连外部系统的数据库，条件写 SQL',
      ['Find Rows', 'Insert Row', 'Update Row', 'Delete Row', 'Run Query', 'Get Tables'], ['New Row'], { restricted: true }),
    P('content-organizer', 'Content Organizer', '1.0.2', 'tool', '文档 OCR / 字段抽取（Content Organizer；iB2B 服务账号凭证取自部署环境）。', '文档识别、字段抽取（自动取 iB2B token）',
      ['Extract Fields'], []),
    P('platform-data', '平台数据', '1.0.0', 'data', '按表名查、改平台的 Relation Table（JSON 行存储）。', '按表名查、改平台的 Relation Table',
      ['List Tables', 'Get Schema', 'Find Rows', 'Get Row by Key', 'Count', 'Insert Row(s)', 'Update Row', 'Upsert', 'Deactivate Row', 'Delete Row'], [],
      { ...PLANNED, restricted: true }),
    P('call-data-flow', '调用数据 flow', '1.0.0', 'data', '按业务键调用一个数据 flow，拿回结果。', '按业务键调用一个数据 flow，拿回结果',
      ['Call Data Flow'], [], PLANNED),
    P('fu-purchase', '采购（fu-purchase）', '1.0.0', 'fu', '采购申请 FU 的业务规则。', '采购申请 FU 的业务规则',
      ['计算折扣', '检查预算'], [], PLANNED),
  ];

  // 方案页「第 1 层：基础数据」表格里的「要点」原文
  const PD_NOTES = {
    'List Tables': '给下拉框用。按表名引用，不用 id：id 跨环境会被重新映射，flow 从 uat 导到 prod 就会断。',
    'Get Schema': '给下拉框用。按表名引用，不用 id：id 跨环境会被重新映射，flow 从 uat 导到 prod 就会断。',
    'Find Rows': '表名 + 条件（字段、运算符、值；支持 AND，最多一层 OR）+ 排序 + 分页 + 选列；设行数上限（1000）。',
    'Get Row by Key': '按主键取一行。',
    Count: '按条件计数。',
    'Insert Row(s)': '走平台的主键分配、字段校验和审计字段填充。',
    'Update Row': '默认按主键更新。开放「按条件批量更新」时，必须带最大影响行数的守卫。',
    Upsert: '有就改，没有就新增。数据同步类的 flow 最常用。',
    'Deactivate Row': '优先停用（Relation Table 本身有 ACTIVE / INACTIVE 状态）。',
    'Delete Row': '硬删除只允许按主键。',
  };
  const OPS = ['=', '≠', '>', '≥', '<', '≤', 'in', 'not in', 'contains', 'startsWith', 'isEmpty', 'between'];
  const MAX_PAGE_SIZE = 1000;

  const IMPORTABLE = [
    { file: 'activepieces-piece-fu-expense-1.0.0.tgz', size: '38 KB',
      piece: P('fu-expense', '报销（fu-expense）', '1.0.0', 'fu', '报销申请 FU 的业务规则。', '报销申请 FU 的业务规则',
        ['按职级算报销上限', '检查发票是否重复'], [], { pieceType: 'CUSTOM', archive: true }) },
  ];

  const F = (n, t) => ({ n, t });
  const TABLES = {
    supplier: {
      label: '供应商', pk: 'code', pkAuto: false,
      fields: [F('code', 'text'), F('name', 'text'), F('level', 'text')],
      rows: [
        { code: 'S001', name: '华东物流', level: 'A', _status: 'ACTIVE' },
        { code: 'S002', name: '南方包装', level: 'B', _status: 'ACTIVE' },
        { code: 'S003', name: '远洋贸易', level: 'C', _status: 'ACTIVE' },
        { code: 'S004', name: '旧供应商', level: 'A', _status: 'INACTIVE' },
      ],
    },
    purchase_order: {
      label: '采购订单', pk: 'orderNo', pkAuto: true, pkPrefix: 'PO-2026-',
      fields: [F('orderNo', 'text'), F('supplierCode', 'text'), F('amount', 'number'), F('discount', 'number')],
      rows: [
        { orderNo: 'PO-2026-0153', supplierCode: 'S001', amount: 120000, discount: null, _status: 'ACTIVE' },
        { orderNo: 'PO-2026-0154', supplierCode: 'S002', amount: 56000, discount: null, _status: 'ACTIVE' },
      ],
    },
    budget: {
      label: '部门预算', pk: 'dept', pkAuto: false,
      fields: [F('dept', 'text'), F('year', 'number'), F('balance', 'number')],
      rows: [
        { dept: 'IT', year: 2026, balance: 300000, _status: 'ACTIVE' },
        { dept: 'HR', year: 2026, balance: 50000, _status: 'ACTIVE' },
      ],
    },
    fx_rate: {
      label: '汇率', pk: 'currency', pkAuto: false,
      fields: [F('currency', 'text'), F('rate', 'number')],
      rows: [
        { currency: 'USD', rate: 7.10, _status: 'ACTIVE' },
        { currency: 'EUR', rate: 7.69, _status: 'ACTIVE' },
      ],
    },
  };

  // 环境配置（设计新增）：环境变量 + 连接。值是示例；每个环境的 Admin 各配一份，名称保持一致。
  // 环境变量的字段和规则照现有 Admin「环境变量」页（TEXT 存当前值/默认值；VAULT 只存键，密码在 Vault）。
  const ENV_VARS = [
    { key: 'platform_db_password', name: '平台数据库密码（自动化）', kind: 'VAULT' },
    { key: 'sap_api_password', name: 'SAP 接口密码', kind: 'VAULT' },
    { key: 'mail_smtp_password', name: '发信邮箱密码', kind: 'VAULT', usedBy: ['DW 邮件连接「发信邮箱」'] },
    { key: 'purchase_notify_cc', name: '采购通知抄送地址', kind: 'TEXT', current: '', def: 'purchase-team@example.internal', usedBy: ['DW 发送邮件任务 ${env:purchase_notify_cc}'] },
  ];
  const CONNECTIONS = [
    { id: 'platform-db', type: 'db', host: 'platform-db.internal', port: 5432, database: 'workflow_platform', user: 'automation_rw', auth: 'basic', pwdVar: 'platform_db_password', baseUrl: '', workspaces: ['public'], note: '平台数据组件用，只给数据 workspace' },
    { id: 'fx-source', type: 'http', baseUrl: 'https://fx.example.internal', auth: 'none', user: '', pwdVar: '', host: '', port: '', database: '', workspaces: ['purchase'], note: '每日汇率文件' },
    { id: 'sap-api', type: 'http', baseUrl: 'https://sap.example.internal/api', auth: 'basic', user: 'hermes_svc', pwdVar: 'sap_api_password', host: '', port: '', database: '', workspaces: ['purchase'], note: '采购下单' },
  ];
  // 「测试连接」的示意结果：按失败原因分类提示
  const TEST_RESULTS = {
    'platform-db': { ok: true, kind: '成功', msg: '连接成功，耗时 12 ms。' },
    'fx-source': { ok: true, kind: '成功', msg: '连接成功，耗时 38 ms。' },
    'sap-api': { ok: false, kind: '网络未放行', msg: 'sap.example.internal 不在 AP_SSRF_ALLOW_LIST，AP 连不出去。把地址加进 ConfigMap 的 ACTIVEPIECES_SSRF_ALLOW_LIST 后再测。' },
  };

  const DISCOUNT_RULES = [
    { cond: 'A 级，金额 ≥ 100,000', rate: 0.08, test: (a, l) => l === 'A' && a >= 100000 },
    { cond: 'A 级，金额 < 100,000', rate: 0.05, test: (a, l) => l === 'A' && a < 100000 },
    { cond: 'B 级', rate: 0.03, test: (a, l) => l === 'B' },
    { cond: 'C 级', rate: 0, test: (a, l) => l === 'C' },
  ];

  const S0 = (piece, action, cfg) => ({ piece, action, cfg: cfg || {} });
  const V = (k) => `{{trigger.body.variables.${k}}}`;
  const where1 = (...conds) => [conds];
  const C = (f, op, v, v2) => ({ f, op, v, v2: v2 || '' });
  const RET = (pairs) => S0('webhook', 'Return Response', { responseType: 'json', status: 200, flowExecution: 'stop', vars: pairs.map(([k, v]) => ({ k, v })) });
  const HOOK = () => S0('webhook', 'Catch Webhook', { authType: 'header' });
  const PD = (action, cfg) => S0('platform-data', action, {
    conn: 'platform-db', table: 'supplier', where: [], sortField: '', sortDir: 'ASC', page: 1, pageSize: 100, columns: [],
    keyValue: '', rows: '', mode: 'key', maxAffected: '', sets: [], keyField: '', ...cfg,
  });

  const WS = {
    public: {
      id: 'public', name: 'Public（共享）', isData: true,
      flows: [
        { id: 'f_sq', name: '查询供应商', key: 'supplier.query', params: ['code'], sample: { code: 'S001' }, steps: [
          HOOK(),
          PD('Find Rows', { table: 'supplier', where: where1(C('code', '=', V('code')), C('_status', '=', 'ACTIVE')), sortField: 'name', columns: ['code', 'name', 'level'] }),
          RET([['rows', '{{step2.rows}}'], ['total', '{{step2.total}}']]),
        ] },
        { id: 'f_ud', name: '更新订单折扣', key: 'order.update-discount', params: ['orderNo', 'discount'], sample: { orderNo: 'PO-2026-0153', discount: 0.08 }, steps: [
          HOOK(),
          PD('Update Row', { table: 'purchase_order', mode: 'key', keyValue: V('orderNo'), sets: [{ f: 'discount', v: V('discount') }], where: where1(C('supplierCode', '=', 'S001')), maxAffected: 10 }),
          RET([['updated', '{{step2.updated}}']]),
        ] },
        { id: 'f_bq', name: '查询预算余额', key: 'budget.query', params: ['dept', 'year'], sample: { dept: 'IT', year: 2026 }, steps: [
          HOOK(),
          PD('Find Rows', { table: 'budget', where: where1(C('dept', '=', V('dept')), C('year', '=', V('year'))), pageSize: 1 }),
          RET([['rows', '{{step2.rows}}']]),
        ] },
        { id: 'f_fx', name: '汇率新增或修改', key: 'fx.upsert', params: ['rows'], sample: { rows: [{ currency: 'USD', rate: '7.12' }] }, steps: [
          HOOK(),
          PD('Upsert', { table: 'fx_rate', keyField: 'currency', rows: V('rows') }),
          RET([['inserted', '{{step2.inserted}}'], ['updated', '{{step2.updated}}']]),
        ] },
      ],
    },
    purchase: {
      id: 'purchase', name: '采购团队', isData: false,
      flows: [
        { id: 'p_cd', name: '采购-计算折扣', key: 'purchase.calc-discount', params: [], sample: { orderNo: 'PO-2026-0153', supplierCode: 'S001', amount: 120000 }, steps: [
          HOOK(),
          S0('call-data-flow', 'Call Data Flow', { flow: 'supplier.query', params: { code: V('supplierCode') } }),
          S0('fu-purchase', '计算折扣', { amount: V('amount'), level: '{{step2.variables.rows[0].level}}' }),
          S0('call-data-flow', 'Call Data Flow', { flow: 'order.update-discount', params: { orderNo: V('orderNo'), discount: '{{step3.discount}}' } }),
          RET([['discount', '{{step3.discount}}']]),
        ] },
        { id: 'p_cb', name: '采购-检查预算', key: 'purchase.check-budget', params: [], sample: { dept: 'IT', amount: 120000 }, steps: [
          HOOK(),
          S0('call-data-flow', 'Call Data Flow', { flow: 'budget.query', params: { dept: V('dept'), year: '2026' } }),
          S0('fu-purchase', '检查预算', { amount: V('amount'), balance: '{{step2.variables.rows[0].balance}}' }),
          RET([['budgetPass', '{{step3.pass}}'], ['budgetOver', '{{step3.over}}']]),
        ] },
        { id: 'p_ex', name: '采购-合同识别', key: 'purchase.extract-contract', params: [], sample: { fileUrl: 'https://files.example.internal/contract.pdf', staffId: 'E00001' }, steps: [
          HOOK(),
          S0('content-organizer', 'Extract Fields', { file: V('fileUrl'), staffId: V('staffId'), fields: [
            { key: 'contractNo', label: '合同号', type: 'text' }, { key: 'amount', label: '金额', type: 'number' }, { key: 'signDate', label: '签约日期', type: 'date' },
          ] }),
          RET([['contract', '{{step2.values}}']]),
        ] },
        { id: 'p_fx', name: '采购-每日汇率同步', key: 'purchase.fx-sync', params: [], sample: {}, steps: [
          S0('schedule', 'Every Day', { hour: 8, weekends: false }),
          S0('http', 'Send HTTP request', { method: 'GET', conn: 'fx-source', path: '/rates.csv', url: '' }),
          S0('csv', 'Convert CSV to JSON', { csv: '{{step2.body}}', hasHeaders: true, delimiter: ',' }),
          S0('call-data-flow', 'Call Data Flow', { flow: 'fx.upsert', params: { rows: '{{step3}}' } }),
        ] },
        { id: 'p_ct', name: '连通性测试（SAP）', key: 'purchase.connect-test-sap', params: [], sample: {}, steps: [
          HOOK(),
          S0('http', 'Send HTTP request', { method: 'GET', conn: 'sap-api', path: '/health', url: '' }),
          RET([['status', '{{step2.status}}']]),
        ] },
        { id: 'p_bad', name: '反例：直接查供应商表', key: 'purchase.bad-direct-query', params: [], sample: { code: 'S001' }, steps: [
          HOOK(),
          PD('Find Rows', { conn: '', table: 'supplier', where: where1(C('code', '=', V('code'))) }),
          RET([['rows', '{{step2.rows}}']]),
        ] },
      ],
    },
  };

  const FU = {
    name: '采购申请', team: '采购团队', ws: 'purchase', version: '1.0.4',
    vars: { orderNo: 'PO-2026-0153', supplierCode: 'S001', amount: 120000, dept: 'IT' },
    tasks: [
      { id: 't1', type: 'user', name: '部门审批' },
      { id: 'st2', type: 'service', name: '检查预算', key: 'purchase.check-budget' },
      { id: 'st1', type: 'service', name: '计算折扣', key: 'purchase.calc-discount' },
      { id: 't4', type: 'user', name: '财务审批' },
    ],
  };

  const TOUR = [
    { id: 'pieces', t: 'Admin：自动化组件按类别看', d: '页面和现在的 Admin 一样，虚线框标着「设计新增」的是类别列和筛选。点 ▸ 展开详情。' },
    { id: 'import', t: 'Admin：导入一个组件包', d: '选报销的 FU 专属组件包，按包名自动归到 FU 专属。' },
    { id: 'conns', t: 'Admin：环境配置 → 连接', d: '数据库连接只给 Public；点「测试」看连不通时怎么提示。' },
    { id: 'envs', t: 'Admin：环境配置 → 环境变量', d: '连接的密码选这里的 VAULT 变量，密码在 Vault，不进数据库。' },
    { id: 'fsq', t: '数据 flow：Find Rows', d: '选连接和表名，条件组里是 AND，组和组之间是 OR；排序、分页、选列，每页最多 1000 行。' },
    { id: 'fud', t: '数据 flow：Update Row 的守卫', d: '默认按主键；切到「按条件批量」必须填最大影响行数。' },
    { id: 'pcd', t: '采购团队：试运行普通 flow', d: '「采购-计算折扣」调用两次数据 flow，中间用 FU 组件算折扣。' },
    { id: 'fu', t: 'FU 专属组件：试算折扣', d: '改金额和等级，看命中哪条规则。' },
    { id: 'bad', t: '反例：普通 flow 直接用平台数据', d: '试运行会报错，提示改用「调用数据 flow」。' },
    { id: 'ct', t: '连通性测试 flow', d: 'HTTP 选连接、只填路径；试运行看到「网络未放行」的提示。' },
    { id: 'st', t: 'Function Unit：Service Task', d: '填业务键，看归属提示；模拟时请求体是 variables + context，返回体必须带 variables。' },
  ];

  return { CATS, PIECES, PD_NOTES, OPS, MAX_PAGE_SIZE, IMPORTABLE, TABLES, DISCOUNT_RULES, WS, FU, TOUR, S0, ENV_VARS, CONNECTIONS, TEST_RESULTS };
})();
