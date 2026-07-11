#!/usr/bin/env node
/*
 * 生成 "AI Function Unit Generation" 的 Activepieces flow 定义 JSON。
 *
 * 该 flow 取代旧的 n8n `ai-function-unit-gen` workflow(已弃用),供 developer-workstation
 * 的「AI Generate」对话使用。结构:
 *   Catch Webhook  →  Code: Build Prompt  →  Run Agent(deepseek)  →  Code: Parse Response  →  Return Response
 *
 * 关键点:
 *  - 后端每次请求已把 功能单元上下文/已有文档/对话历史/架构元数据 一并 POST 过来,
 *    因此 agent 不需要 n8n 那套「DB 查询工具 + Postgres 对话记忆」,AP flow 完全无状态。
 *  - Run Agent 用平台级 custom provider = deepseek(用户已在 AP 里配好,`aiProviderModel` 内嵌于 flow,可移植)。
 *  - 响应契约与旧 n8n 完全一致:{reply, document, documentType, phaseComplete, generatedData},
 *    后端 AiGenerationComponentImpl 无需改解析。
 *
 * 用法: node deploy/scripts/build-ai-fu-flow.js [outFile]
 *   缺省写到 deploy/ap-flows/ai-function-unit-gen.json
 */
'use strict';
const fs = require('fs');
const path = require('path');

// ---- deepseek 绑定(与用户在 AP UI 中配置的一致;custom provider = 平台级 AI provider) ----
const AI_PROVIDER_MODEL = { model: 'deepseek-v4-pro', provider: 'custom' };

// ============================================================================
// 三个阶段的 system prompt(从 deploy/n8n-workflows/ai-function-unit-gen-workflow.json
// 移植;删去「必须调用 DB 查询工具」段落,改为「数据已在下方提供」——因为后端已把上下文喂进来)。
// ============================================================================

const DATA_NOTE = [
  '⚠️ 当前数据来源(重要):',
  '- 该功能单元的现有数据、已有文档、对话历史、架构元数据已在本条消息下方一并提供,你**无需也无法**调用任何外部工具查询数据库。',
  '- 下方数据是最权威的,优先于任何其他信息。',
  '- 如果「当前功能单元数据」显示为「全新功能单元」或组件为空,这是正常情况,说明用户正在从零创建功能单元,请正常进行分析/设计/生成,绝对不要报错或提示 ID 无效。',
  '- 如果下方提供了 REQUIREMENTS / DESIGN 文档,你必须先仔细阅读理解;用户要求检阅/修改时基于已有文档进行,而不是从头开始。',
].join('\n');

const REQUIREMENTS_PROMPT = [
  '你是一个功能单元需求分析助手。你的任务是帮助用户明确功能单元的业务需求。',
  '',
  DATA_NOTE,
  '',
  '如果没有已有文档(全新功能单元),请通过对话了解以下信息:',
  '1. 功能单元的业务目的和使用场景',
  '2. 需要管理的数据实体和字段',
  '3. 业务流程和审批规则',
  '4. 表单交互需求',
  '5. 操作按钮和动作需求',
  '',
  '重要规则:',
  '- 如果用户表示让你自由发挥或信息已足够,你应该基于已有信息直接生成需求文档',
  '- 不要过度追问,2-3 轮对话后如果用户不再补充,就应该生成文档',
  '',
  '当你认为需求已经足够清晰时,你必须同时完成以下两步(缺一不可):',
  '',
  '第一步:生成完整的需求文档,用以下标记包裹:',
  '---REQUIREMENTS_DOC_START---',
  '(需求文档内容,必须包含:功能概述、数据实体、业务流程、表单需求、操作需求)',
  '---REQUIREMENTS_DOC_END---',
  '',
  '第二步:在回复的最末尾添加阶段完成标记:',
  '---PHASE_COMPLETE---',
  '',
  '⚠️ 绝对不能只输出 ---PHASE_COMPLETE--- 而不输出文档!文档标记和阶段完成标记必须在同一条回复中同时出现。',
  '',
  '--- 需求文档输出格式要求 ---',
  '',
  '你生成的需求文档必须严格遵循以下结构化格式:',
  '',
  '# 需求文档',
  '',
  '## 简介',
  '(功能概述,1-2 段)',
  '',
  '## 术语表',
  '(使用平台领域术语,见下方术语表)',
  '',
  '### 需求 1:名称',
  '**用户故事:** 作为...我希望...以便...',
  '',
  '**说明:**(可选的补充描述)',
  '',
  '#### 验收标准',
  '1. WHEN ... THEN THE system SHALL ...',
  '2. IF ... THEN THE system SHALL ...',
  '(每条验收标准使用 EARS 模式:WHEN/WHILE/IF...THEN/FOR ALL + SHALL)',
  '',
  '### 需求 2:名称',
  '(同上格式)',
  '',
  '--- 平台领域术语表 ---',
  '| 中文名称 | 英文标识 | 说明 |',
  '|---------|---------|------|',
  '| 功能单元 | FunctionUnit | 平台中的核心业务模块 |',
  '| 表定义 | TableDefinition | 数据库表结构定义 |',
  '| 字段定义 | FieldDefinition | 表中的单个字段 |',
  '| 外键 | ForeignKey | 表与表之间的引用关系 |',
  '| 表单定义 | FormDefinition | 前端表单布局定义 |',
  '| 表单表绑定 | FormTableBinding | 表单与表之间的绑定关系 |',
  '| 动作定义 | ActionDefinition | 业务操作定义 |',
  '| 流程定义 | ProcessDefinition | BPMN 业务流程定义 |',
  '| 图标 | Icon | SVG 图标定义 |',
  '',
  '请在术语表中使用上述平台领域术语,而非前端 UI 组件名称。',
].join('\n');

const DESIGN_PROMPT = [
  '你是一个功能单元设计方案助手。基于已收集的需求,帮助用户设计技术方案。',
  '',
  DATA_NOTE,
  '',
  '⚠️ 自动触发处理:',
  '- 如果用户消息以 [AUTO_TRIGGER] 开头,说明这是系统自动触发的请求。',
  '- 此时你必须直接基于下方提供的需求文档和数据生成完整的设计方案文档,不要提问,不要确认,不要寒暄,直接输出设计文档。',
  '',
  '请设计以下内容:',
  '1. 数据表结构设计(表名、字段名、字段类型、约束)',
  '2. 表单设计(表单类型、绑定表、字段布局)',
  '3. 动作设计(动作类型、触发条件、执行逻辑)',
  '4. 流程设计(BPMN 流程节点、网关、流转规则)',
  '5. 图标设计建议',
  '',
  '当设计方案确定后,你必须同时完成以下两步(缺一不可):',
  '',
  '第一步:生成完整的设计文档,用以下标记包裹:',
  '---DESIGN_DOC_START---',
  '(设计文档内容)',
  '---DESIGN_DOC_END---',
  '',
  '第二步:在回复的最末尾添加阶段完成标记:',
  '---PHASE_COMPLETE---',
  '',
  '⚠️ 绝对不能只输出 ---PHASE_COMPLETE--- 而不输出文档!文档标记和阶段完成标记必须在同一条回复中同时出现。',
  '',
  '--- 设计文档输出格式要求 ---',
  '',
  '你生成的设计文档必须包含以下章节结构:',
  '',
  '# 设计文档',
  '',
  '## 概述',
  '## 架构设计',
  '## 数据模型',
  '### 表结构设计',
  '### 字段设计',
  '## 接口设计',
  '## 组件设计',
  '## 流程设计',
  '## 图标设计',
  '',
  '每个章节使用 Markdown 标题层级(##、###),确保文档在 XML 树形视图中呈现清晰的层级结构。',
].join('\n');

const GENERATION_PROMPT = [
  '你是一个功能单元代码生成助手。基于需求和设计方案,生成完整的功能单元组件数据。',
  '',
  DATA_NOTE,
  '',
  '⚠️ 自动触发处理:',
  '- 如果用户消息以 [AUTO_TRIGGER] 开头,说明这是系统自动触发的请求。',
  '- 此时你必须直接基于下方提供的需求文档和设计文档生成完整的功能单元组件数据,不要提问,不要确认,不要寒暄,直接输出生成数据。',
  '',
  '⚠️⚠️⚠️ 极其重要 — JSON 字段名必须严格匹配 ⚠️⚠️⚠️',
  '你生成的 JSON 数据必须严格使用以下字段名,不能使用任何其他名称(如 fields、primaryKey、comment 等都是错误的)。',
  '',
  '你必须生成以下 JSON 结构的数据,并用标记包裹:',
  '---GENERATED_DATA_START---',
  '{',
  '  "tableDefinitions": [',
  '    {',
  '      "tableName": "表名(英文下划线命名)",',
  '      "tableDisplayName": "表中文显示名",',
  '      "tableType": "MAIN|SUB|ACTION|RELATION",',
  '      "description": "表描述",',
  '      "fieldDefinitions": [',
  '        {',
  '          "fieldName": "字段名",',
  '          "dataType": "VARCHAR|TEXT|INTEGER|BIGINT|DECIMAL|BOOLEAN|DATE|TIME|TIMESTAMP|JSON|BYTEA|FILE",',
  '          "length": 50,',
  '          "precision": null,',
  '          "scale": null,',
  '          "nullable": false,',
  '          "defaultValue": null,',
  '          "isPrimaryKey": true,',
  '          "isUnique": false,',
  '          "description": "字段描述",',
  '          "sortOrder": 0',
  '        }',
  '      ],',
  '      "foreignKeys": [',
  '        {',
  '          "fieldName": "本表外键字段名",',
  '          "refTableName": "引用表名",',
  '          "refFieldName": "引用字段名",',
  '          "onDelete": "CASCADE|NO ACTION|SET NULL",',
  '          "onUpdate": "CASCADE|NO ACTION"',
  '        }',
  '      ]',
  '    }',
  '  ],',
  '  "formDefinitions": [',
  '    {',
  '      "formName": "表单名称",',
  '      "formType": "MAIN|SUB|ACTION|POPUP",',
  '      "description": "表单描述",',
  '      "configJson": null,',
  '      "tableBindings": [',
  '        {',
  '          "tableName": "绑定的表名(必须与 tableDefinitions 中的 tableName 一致)",',
  '          "bindingType": "PRIMARY|SUB|RELATED",',
  '          "bindingMode": "EDITABLE|READONLY",',
  '          "foreignKeyField": null,',
  '          "sortOrder": 0',
  '        }',
  '      ]',
  '    }',
  '  ],',
  '  "actionDefinitions": [',
  '    {',
  '      "actionName": "动作名称",',
  '      "actionType": "APPROVE|REJECT|TRANSFER|DELEGATE|ROLLBACK|WITHDRAW|CANCEL|SAVE|EXPORT|API_CALL|FORM_POPUP|SCRIPT|CUSTOM_SCRIPT|PROCESS_SUBMIT|PROCESS_REJECT|COMPOSITE",',
  '      "description": "动作描述",',
  '      "isDefault": false,',
  '      "icon": null,',
  '      "buttonColor": null,',
  '      "configJson": null',
  '    }',
  '  ],',
  '  "processDefinition": { "bpmnXml": "完整的 BPMN 2.0 XML 字符串" },',
  '  "icon": {',
  '    "name": "图标英文名(下划线命名,不超过100字符)",',
  '    "category": "APPROVAL|CREDIT|ACCOUNT|PAYMENT|CUSTOMER|COMPLIANCE|OPERATION|GENERAL",',
  '    "svgContent": "SVG XML 字符串(不超过10KB)",',
  '    "description": "图标描述"',
  '  },',
  '  "name": "功能单元名称",',
  '  "description": "功能单元描述"',
  '}',
  '---GENERATED_DATA_END---',
  '',
  '⚠️ 字段名对照(常见错误纠正):',
  '- 字段列表必须用 "fieldDefinitions",不能用 "fields"',
  '- 主键标记必须用 "isPrimaryKey",不能用 "primaryKey"',
  '- 描述必须用 "description",不能用 "comment"',
  '- 表显示名必须用 "tableDisplayName",不能用 "displayName" 或 "tableComment"',
  '- 表单绑定必须用 "tableBindings",不能用 "fieldBindings" 或 "bindings"',
  '- 绑定中引用表必须用 "tableName",不能用 "bindingTableId" 或 "tableId"',
  '- VARCHAR 类型必须指定 length > 0',
  '- DECIMAL 类型必须指定 precision > 0 和 scale > 0',
  '- 每张表必须有至少一个 isPrimaryKey: true 的字段',
  '',
  '如果架构元数据(schemaMetadata)中提供了 decisionDefinitions / tableRelations 等新实体结构说明,可按需在 JSON 中一并生成(字段名以 schemaMetadata 描述为准)。',
  '',
  '生成完成后,在回复末尾添加:',
  '---PHASE_COMPLETE---',
  '',
  '这些标记非常重要,系统依赖它们来解析数据和完成流程。请确保生成的数据符合所有约束条件。',
].join('\n');

// ============================================================================
// Build Prompt 代码步:根据 phase 选 system prompt,拼上后端传来的上下文,产出单个 prompt 字符串。
// ============================================================================
const BUILD_PROMPT_CODE = `
const PROMPTS = ${JSON.stringify({ REQUIREMENTS: REQUIREMENTS_PROMPT, DESIGN: DESIGN_PROMPT, GENERATION: GENERATION_PROMPT })};

export const code = async (inputs) => {
  const body = (inputs && inputs.body) || {};
  let phase = String(body.phase || 'REQUIREMENTS').trim().toUpperCase();
  if (!PROMPTS[phase]) phase = 'REQUIREMENTS';
  const mode = String(body.mode || 'NEW').trim().toUpperCase();

  const sys = PROMPTS[phase];
  const parts = [];
  parts.push(sys);
  parts.push('\\n\\n========== 会话上下文(系统提供,请勿在回复中暴露本分隔线) ==========');
  parts.push('阶段(phase): ' + phase + ' | 模式(mode): ' + mode);

  const fuId = (body.functionUnitId !== undefined && body.functionUnitId !== null) ? String(body.functionUnitId) : '0';
  parts.push('功能单元ID(functionUnitId): ' + fuId);

  parts.push('\\n## 当前功能单元数据');
  if (body.context) {
    // 后端已把 context 预序列化为 JSON 字符串
    parts.push(typeof body.context === 'string' ? body.context : JSON.stringify(body.context));
  } else {
    parts.push('(全新功能单元,尚未创建任何组件数据。请正常进行需求分析/设计/生成。)');
  }

  parts.push('\\n## 已有文档(existingDocuments)');
  parts.push(body.existingDocuments ? String(body.existingDocuments) : '(无)');

  if (body.schemaMetadata) {
    parts.push('\\n## 架构元数据(schemaMetadata,生成数据时须遵循这些枚举值与结构说明)');
    parts.push(typeof body.schemaMetadata === 'string' ? body.schemaMetadata : JSON.stringify(body.schemaMetadata));
  }

  parts.push('\\n## 对话历史(conversationHistory,越靠后越新)');
  const hist = Array.isArray(body.conversationHistory) ? body.conversationHistory : [];
  if (hist.length > 0) {
    for (const m of hist) {
      const role = (m && (m.role || m.type)) || 'user';
      const content = (m && (m.content || m.text)) || '';
      parts.push('[' + String(role).toUpperCase() + '] ' + content);
    }
  } else {
    parts.push('(无历史,或本次为首轮对话)');
  }

  parts.push('\\n## 用户本轮消息(message)');
  parts.push(String(body.message || ''));
  parts.push('\\n========== 上下文结束,请根据以上信息完成本阶段任务 ==========');

  return { prompt: parts.join('\\n'), phase: phase, mode: mode };
};
`.trim();

// ============================================================================
// Parse Response 代码步:复刻 n8n Format Response —— 从 agent 文本里提取 markers。
// ============================================================================
const PARSE_CODE = `
export const code = async (inputs) => {
  const a = inputs && inputs.agent;
  // run_agent 输出形如 { status, steps:[{type:'MARKDOWN', markdown}, {type:'TOOL_CALL',...}], structuredOutput }。
  // LLM 文本分散在 MARKDOWN 步骤的 markdown 字段里,需拼接。
  let reply = '';
  if (typeof a === 'string') {
    reply = a;
  } else if (a && typeof a === 'object') {
    if (Array.isArray(a.steps)) {
      reply = a.steps
        .filter((s) => s && s.type === 'MARKDOWN' && typeof s.markdown === 'string')
        .map((s) => s.markdown)
        .join('\\n');
    }
    if (!reply || !reply.trim()) {
      reply = a.output || a.text || a.message || a.result || a.answer || JSON.stringify(a);
    }
  } else {
    reply = a == null ? '' : String(a);
  }
  if (typeof reply !== 'string') reply = JSON.stringify(reply);

  let document = null, documentType = null, phaseComplete = false, generatedData = null;

  const reqDoc = reply.match(/---REQUIREMENTS_DOC_START---([\\s\\S]*?)---REQUIREMENTS_DOC_END---/);
  const designDoc = reply.match(/---DESIGN_DOC_START---([\\s\\S]*?)---DESIGN_DOC_END---/);
  const genData = reply.match(/---GENERATED_DATA_START---([\\s\\S]*?)---GENERATED_DATA_END---/);

  if (reqDoc) { document = reqDoc[1].trim(); documentType = 'REQUIREMENTS'; }
  if (designDoc) { document = designDoc[1].trim(); documentType = 'DESIGN'; }
  if (reply.indexOf('---PHASE_COMPLETE---') !== -1) phaseComplete = true;

  if (genData) {
    const raw = genData[1].trim();
    try {
      generatedData = JSON.parse(raw);
    } catch (e) {
      const block = raw.match(/\\\`\\\`\\\`json?\\s*([\\s\\S]*?)\\\`\\\`\\\`/);
      if (block) { try { generatedData = JSON.parse(block[1].trim()); } catch (e2) {} }
      if (generatedData === null) {
        // 兜底:截取首个 { 到末个 }
        const s = raw.indexOf('{'); const e3 = raw.lastIndexOf('}');
        if (s !== -1 && e3 > s) { try { generatedData = JSON.parse(raw.slice(s, e3 + 1)); } catch (e4) {} }
      }
    }
  }

  const cleanReply = reply
    .replace(/---REQUIREMENTS_DOC_START---[\\s\\S]*?---REQUIREMENTS_DOC_END---/g, '')
    .replace(/---DESIGN_DOC_START---[\\s\\S]*?---DESIGN_DOC_END---/g, '')
    .replace(/---GENERATED_DATA_START---[\\s\\S]*?---GENERATED_DATA_END---/g, '')
    .replace(/---PHASE_COMPLETE---/g, '')
    .trim();

  return { reply: cleanReply, document: document, documentType: documentType, phaseComplete: phaseComplete, generatedData: generatedData };
};
`.trim();

// ============================================================================
// 组装 flow 树
// ============================================================================
const EHO = { retryOnFailure: { value: false }, continueOnFailure: { value: false } };
const EMPTY_PKG = '{\n  "dependencies": {}\n}';
// AP schema 要求每个节点(trigger + 每个 action)都带 lastUpdatedDate(string,必填)。用固定时间戳保证可复现。
const LUD = '2026-07-06T00:00:00.000Z';

const returnResponse = {
  name: 'step_4',
  type: 'PIECE',
  valid: true,
  displayName: 'Return Response',
  skip: false,
  lastUpdatedDate: LUD,
  settings: {
    input: {
      fields: { body: '{{step_3}}', status: 200, headers: {} },
      respond: 'stop',
      responseType: 'json',
    },
    pieceName: '@activepieces/piece-webhook',
    actionName: 'return_response',
    pieceVersion: '0.1.36',
    propertySettings: {
      fields: {
        type: 'MANUAL',
        schema: {
          body: { type: 'JSON', required: true, displayName: 'JSON Body' },
          status: { type: 'NUMBER', required: false, displayName: 'Status', defaultValue: 200 },
          headers: { type: 'OBJECT', required: false, displayName: 'Headers' },
        },
      },
      respond: { type: 'MANUAL' },
      responseType: { type: 'MANUAL' },
    },
    errorHandlingOptions: EHO,
  },
};

const parseStep = {
  name: 'step_3',
  type: 'CODE',
  valid: true,
  displayName: 'Parse Response',
  skip: false,
  lastUpdatedDate: LUD,
  settings: {
    input: { agent: '{{step_2}}' },
    sourceCode: { code: PARSE_CODE, packageJson: EMPTY_PKG },
    errorHandlingOptions: EHO,
  },
  nextAction: returnResponse,
};

const agentStep = {
  name: 'step_2',
  type: 'PIECE',
  valid: true,
  displayName: 'Run Agent',
  skip: false,
  lastUpdatedDate: LUD,
  settings: {
    input: {
      prompt: '{{step_1.prompt}}',
      maxSteps: 6,
      webSearch: false,
      agentTools: [],
      aiProviderModel: AI_PROVIDER_MODEL,
      structuredOutput: [],
      webSearchOptions: {},
    },
    pieceName: '@activepieces/piece-ai',
    actionName: 'run_agent',
    pieceVersion: '0.4.4',
    propertySettings: {
      prompt: { type: 'MANUAL' },
      maxSteps: { type: 'MANUAL' },
      webSearch: { type: 'MANUAL' },
      agentTools: { type: 'MANUAL' },
      aiProviderModel: { type: 'MANUAL' },
      structuredOutput: { type: 'MANUAL' },
      webSearchOptions: { type: 'MANUAL', schema: {} },
    },
    errorHandlingOptions: EHO,
  },
  nextAction: parseStep,
};

const buildPromptStep = {
  name: 'step_1',
  type: 'CODE',
  valid: true,
  displayName: 'Build Prompt',
  skip: false,
  lastUpdatedDate: LUD,
  settings: {
    input: { body: '{{trigger.body}}' },
    sourceCode: { code: BUILD_PROMPT_CODE, packageJson: EMPTY_PKG },
    errorHandlingOptions: EHO,
  },
  nextAction: agentStep,
};

const flow = {
  displayName: 'AI Function Unit Generation',
  schemaVersion: '20',
  externalId: '1FAyAJM7acmztiYXcwPec',
  trigger: {
    name: 'trigger',
    valid: true,
    displayName: 'Catch Webhook',
    lastUpdatedDate: LUD,
    type: 'PIECE_TRIGGER',
    settings: {
      propertySettings: {
        authType: { type: 'MANUAL' },
        authFields: { type: 'MANUAL', schema: {} },
        liveMarkdown: { type: 'MANUAL' },
        syncMarkdown: { type: 'MANUAL' },
        testMarkdown: { type: 'MANUAL' },
      },
      pieceName: '@activepieces/piece-webhook',
      pieceVersion: '0.1.36',
      triggerName: 'catch_webhook',
      input: { authType: 'none', authFields: {}, liveMarkdown: '', syncMarkdown: '', testMarkdown: '' },
    },
    nextAction: buildPromptStep,
  },
};

const outFile = process.argv[2] || path.join('deploy', 'ap-flows', 'ai-function-unit-gen.json');
fs.writeFileSync(outFile, JSON.stringify(flow, null, 2) + '\n');
console.error('[build-ai-fu-flow] wrote ' + outFile);
