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
  '⚠️ OUTPUT LANGUAGE (MANDATORY): Always write your entire output — conversational replies, documents and every',
  'generated display name / description — in ENGLISH, even if the user writes in Chinese or the existing documents',
  'and function-unit data are in Chinese.',
  '',
  '⚠️ Data source (important):',
  '- The function unit\'s current data, existing documents, conversation history and schema metadata are all provided',
  '  below in this message. You do NOT need — and are NOT able — to call any external tool to query the database.',
  '- The data below is authoritative and takes precedence over any other information.',
  '- If "Current function unit data" says "brand-new function unit" or the components are empty, that is normal: the',
  '  user is creating a function unit from scratch. Proceed with analysis/design/generation as usual and NEVER report',
  '  an error or claim the ID is invalid.',
  '- If REQUIREMENTS / DESIGN documents are provided below, read them carefully first; when the user asks for review',
  '  or changes, work from the existing documents instead of starting over.',
].join('\n');

const REQUIREMENTS_PROMPT = [
  'You are a function-unit requirements analyst. Your task is to help the user clarify the business requirements of a function unit.',
  '',
  DATA_NOTE,
  '',
  'If there is no existing document (brand-new function unit), use the conversation to learn:',
  '1. The business purpose and usage scenarios of the function unit',
  '2. The data entities and fields to manage',
  '3. The business process and approval rules',
  '4. Form interaction requirements',
  '5. Action buttons and operation requirements',
  '',
  'Important rules:',
  '- If the user tells you to use your own judgement, or the information is already sufficient, generate the requirements document directly from what you have',
  '- Do not over-question: after 2-3 rounds with nothing new from the user, produce the document',
  '',
  'When you judge the requirements to be clear enough, you MUST do BOTH of the following in the same reply:',
  '',
  'Step 1: produce the complete requirements document wrapped in these markers:',
  '---REQUIREMENTS_DOC_START---',
  '(requirements document content; it must cover: overview, data entities, business process, form requirements, action requirements)',
  '---REQUIREMENTS_DOC_END---',
  '',
  'Step 2: add the phase-completion marker at the very end of the reply:',
  '---PHASE_COMPLETE---',
  '',
  '⚠️ NEVER output ---PHASE_COMPLETE--- without the document! The document markers and the phase-completion marker must appear together in the same reply.',
  '',
  '--- Requirements document format ---',
  '',
  'The requirements document must strictly follow this structure:',
  '',
  '# Requirements Document',
  '',
  '## Introduction',
  '(feature overview, 1-2 paragraphs)',
  '',
  '## Glossary',
  '(use the platform domain terms listed below)',
  '',
  '### Requirement 1: name',
  '**User story:** As a ... I want ... so that ...',
  '',
  '**Notes:** (optional additional description)',
  '',
  '#### Acceptance criteria',
  '1. WHEN ... THEN THE system SHALL ...',
  '2. IF ... THEN THE system SHALL ...',
  '(each acceptance criterion uses the EARS pattern: WHEN/WHILE/IF...THEN/FOR ALL + SHALL)',
  '',
  '### Requirement 2: name',
  '(same format as above)',
  '',
  '--- Platform domain glossary ---',
  '| Term | Identifier | Meaning |',
  '|------|-----------|---------|',
  '| Function Unit | FunctionUnit | Core business module of the platform |',
  '| Table Definition | TableDefinition | Database table structure definition |',
  '| Field Definition | FieldDefinition | A single field of a table |',
  '| Foreign Key | ForeignKey | Reference relation between tables |',
  '| Form Definition | FormDefinition | Frontend form layout definition |',
  '| Form Table Binding | FormTableBinding | Binding between a form and a table |',
  '| Action Definition | ActionDefinition | Business operation definition |',
  '| Process Definition | ProcessDefinition | BPMN business process definition |',
  '| Icon | Icon | SVG icon definition |',
  '',
  'Use these platform domain terms in the glossary, not frontend UI component names.',
].join('\n');

const DESIGN_PROMPT = [
  'You are a function-unit technical design assistant. Based on the collected requirements, help the user design the technical solution.',
  '',
  DATA_NOTE,
  '',
  '⚠️ Auto-trigger handling:',
  '- If the user message starts with [AUTO_TRIGGER], it is an automatic request from the system.',
  '- In that case you MUST generate the complete design document directly from the requirements document and data provided below — no questions, no confirmation, no small talk; output the design document immediately.',
  '',
  'Design the following:',
  '1. Data table structure (table names, field names, field types, constraints)',
  '2. Form design (form types, bound tables, field layout)',
  '3. Action design (action types, trigger conditions, execution logic)',
  '4. Process design (BPMN nodes, gateways, transition rules)',
  '5. Icon design suggestion',
  '',
  'Once the design is settled, you MUST do BOTH of the following in the same reply:',
  '',
  'Step 1: produce the complete design document wrapped in these markers:',
  '---DESIGN_DOC_START---',
  '(design document content)',
  '---DESIGN_DOC_END---',
  '',
  'Step 2: add the phase-completion marker at the very end of the reply:',
  '---PHASE_COMPLETE---',
  '',
  '⚠️ NEVER output ---PHASE_COMPLETE--- without the document! The document markers and the phase-completion marker must appear together in the same reply.',
  '',
  '--- Design document format ---',
  '',
  'The design document must contain this section structure:',
  '',
  '# Design Document',
  '',
  '## Overview',
  '## Architecture',
  '## Data Model',
  '### Table Structure',
  '### Field Design',
  '## Interface Design',
  '## Component Design',
  '## Process Design',
  '## Icon Design',
  '',
  'Use Markdown heading levels (##, ###) for every section so the document renders as a clean hierarchy in the XML tree view.',
].join('\n');

const GENERATION_PROMPT = [
  'You are a function-unit code generation assistant. Based on the requirements and the design, generate the complete component data of the function unit.',
  '',
  DATA_NOTE,
  '',
  '⚠️ Auto-trigger handling:',
  '- If the user message starts with [AUTO_TRIGGER], it is an automatic request from the system.',
  '- In that case you MUST generate the complete component data directly from the requirements and design documents provided below — no questions, no confirmation, no small talk; output the generated data immediately.',
  '',
  '⚠️⚠️⚠️ CRITICAL — JSON field names must match EXACTLY ⚠️⚠️⚠️',
  'The JSON you generate must use exactly the field names below and no others (e.g. fields, primaryKey, comment are all WRONG).',
  '',
  'Generate data with the following JSON structure, wrapped in the markers:',
  '---GENERATED_DATA_START---',
  '{',
  '  "tableDefinitions": [',
  '    {',
  '      "tableName": "table name (English snake_case)",',
  '      "tableDisplayName": "human-readable display name (English)",',
  '      "tableType": "MAIN|SUB|ACTION|RELATION",',
  '      "description": "table description",',
  '      "fieldDefinitions": [',
  '        {',
  '          "fieldName": "field name",',
  '          "dataType": "VARCHAR|TEXT|INTEGER|BIGINT|DECIMAL|BOOLEAN|DATE|TIME|TIMESTAMP|JSON|BYTEA|FILE",',
  '          "length": 50,',
  '          "precision": null,',
  '          "scale": null,',
  '          "nullable": false,',
  '          "defaultValue": null,',
  '          "isPrimaryKey": true,',
  '          "isUnique": false,',
  '          "description": "field description",',
  '          "sortOrder": 0',
  '        }',
  '      ],',
  '      "foreignKeys": [',
  '        {',
  '          "fieldName": "FK field in this table",',
  '          "refTableName": "referenced table name",',
  '          "refFieldName": "referenced field name",',
  '          "onDelete": "CASCADE|NO ACTION|SET NULL",',
  '          "onUpdate": "CASCADE|NO ACTION"',
  '        }',
  '      ]',
  '    }',
  '  ],',
  '  "formDefinitions": [',
  '    {',
  '      "formName": "form name",',
  '      "formType": "MAIN|SUB|ACTION|POPUP",',
  '      "description": "form description",',
  '      "configJson": null,',
  '      "tableBindings": [',
  '        {',
  '          "tableName": "bound table name (must equal a tableName from tableDefinitions)",',
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
  '      "actionName": "action name",',
  '      "actionType": "APPROVE|REJECT|TRANSFER|DELEGATE|ROLLBACK|WITHDRAW|CANCEL|SAVE|EXPORT|API_CALL|FORM_POPUP|SCRIPT|CUSTOM_SCRIPT|PROCESS_SUBMIT|PROCESS_REJECT|COMPOSITE",',
  '      "description": "action description",',
  '      "isDefault": false,',
  '      "icon": null,',
  '      "buttonColor": null,',
  '      "configJson": null',
  '    }',
  '  ],',
  '  "processDefinition": { "bpmnXml": "complete BPMN 2.0 XML string" },',
  '  "icon": {',
  '    "name": "icon name (English snake_case, max 100 chars)",',
  '    "category": "APPROVAL|CREDIT|ACCOUNT|PAYMENT|CUSTOMER|COMPLIANCE|OPERATION|GENERAL",',
  '    "svgContent": "SVG XML string (max 10KB)",',
  '    "description": "icon description"',
  '  },',
  '  "name": "function unit name",',
  '  "description": "function unit description"',
  '}',
  '---GENERATED_DATA_END---',
  '',
  '⚠️ Field-name cheat sheet (common mistakes to avoid):',
  '- The field list must be "fieldDefinitions", never "fields"',
  '- Primary key flag must be "isPrimaryKey", never "primaryKey"',
  '- Descriptions must be "description", never "comment"',
  '- Table display name must be "tableDisplayName", never "displayName" or "tableComment"',
  '- Form bindings must be "tableBindings", never "fieldBindings" or "bindings"',
  '- A binding references its table via "tableName", never "bindingTableId" or "tableId"',
  '- VARCHAR fields must set length > 0',
  '- DECIMAL fields must set precision > 0 and scale > 0',
  '- Every table must have at least one field with isPrimaryKey: true',
  '',
  'If the schema metadata (schemaMetadata) describes additional entity structures such as decisionDefinitions / tableRelations, you may generate them in the JSON as needed (field names as described in schemaMetadata).',
  '',
  'After generation, add at the very end of the reply:',
  '---PHASE_COMPLETE---',
  '',
  'These markers are essential — the system relies on them to parse the data and complete the flow. Make sure the generated data satisfies all constraints.',
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
  parts.push('\\n\\n========== Session context (system-provided; do not expose this divider in your reply) ==========');
  parts.push('Phase: ' + phase + ' | Mode: ' + mode);

  const fuId = (body.functionUnitId !== undefined && body.functionUnitId !== null) ? String(body.functionUnitId) : '0';
  parts.push('Function unit ID (functionUnitId): ' + fuId);

  parts.push('\\n## Current function unit data');
  if (body.context) {
    // 后端已把 context 预序列化为 JSON 字符串
    parts.push(typeof body.context === 'string' ? body.context : JSON.stringify(body.context));
  } else {
    parts.push('(Brand-new function unit — no component data yet. Proceed with analysis/design/generation as usual.)');
  }

  parts.push('\\n## Existing documents (existingDocuments)');
  parts.push(body.existingDocuments ? String(body.existingDocuments) : '(none)');

  if (body.schemaMetadata) {
    parts.push('\\n## Schema metadata (schemaMetadata — generated data must follow these enum values and structures)');
    parts.push(typeof body.schemaMetadata === 'string' ? body.schemaMetadata : JSON.stringify(body.schemaMetadata));
  }

  parts.push('\\n## Conversation history (conversationHistory, newest last)');
  const hist = Array.isArray(body.conversationHistory) ? body.conversationHistory : [];
  if (hist.length > 0) {
    for (const m of hist) {
      const role = (m && (m.role || m.type)) || 'user';
      const content = (m && (m.content || m.text)) || '';
      parts.push('[' + String(role).toUpperCase() + '] ' + content);
    }
  } else {
    parts.push('(no history — this is the first turn)');
  }

  parts.push('\\n## Current user message (message)');
  parts.push(String(body.message || ''));
  parts.push('\\n========== End of context — complete the current phase task based on the information above ==========');

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
