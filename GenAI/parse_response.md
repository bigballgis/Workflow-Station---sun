export const code = async (inputs) => {
  const a = inputs && inputs.agent;
  let reply = '';

  // The HTTP piece returns { status, headers, body }. Extract the OpenAI-compatible
  // assistant text instead of serializing the entire HTTP response envelope.
  if (a && typeof a === 'object' && a.body && typeof a.body === 'object') {
    const choices = Array.isArray(a.body.choices) ? a.body.choices : [];
    const content = choices[0] && choices[0].message && choices[0].message.content;
    if (typeof content === 'string' && content.trim()) {
      reply = content;
    } else if (a.status >= 400) {
      const detail = a.body.message || (a.body.error && (a.body.error.message || a.body.error.code));
      throw new Error('AI gateway request failed with HTTP ' + a.status + (detail ? ': ' + detail : ''));
    } else if (choices.length > 0) {
      throw new Error('AI gateway returned an empty assistant response');
    }
  }

  // Backward compatibility for the previous run_agent response shape.
  if (!reply && typeof a === 'string') {
    reply = a;
  } else if (!reply && a && typeof a === 'object') {
    if (Array.isArray(a.steps)) {
      reply = a.steps
        .filter((s) => s && s.type === 'MARKDOWN' && typeof s.markdown === 'string')
        .map((s) => s.markdown)
        .join('\n');
    }
    if (!reply || !reply.trim()) {
      reply = a.output || a.text || a.message || a.result || a.answer || JSON.stringify(a);
    }
  } else if (!reply) {
    reply = a == null ? '' : String(a);
  }
  if (typeof reply !== 'string') reply = JSON.stringify(reply);

  let document = null, documentType = null, phaseComplete = false, generatedData = null;

  const reqDoc = reply.match(/---REQUIREMENTS_DOC_START---([\s\S]*?)---REQUIREMENTS_DOC_END---/);
  const designDoc = reply.match(/---DESIGN_DOC_START---([\s\S]*?)---DESIGN_DOC_END---/);
  const genData = reply.match(/---GENERATED_DATA_START---([\s\S]*?)(?:---GENERATED_DATA_END---|$)/);

  if (reqDoc) { document = reqDoc[1].trim(); documentType = 'REQUIREMENTS'; }
  if (designDoc) { document = designDoc[1].trim(); documentType = 'DESIGN'; }
  if (reply.indexOf('---PHASE_COMPLETE---') !== -1) phaseComplete = true;

  if (genData) {
    const raw = genData[1].trim();
    try {
      generatedData = JSON.parse(raw);
    } catch (e) {
      const block = raw.match(/\`\`\`json?\s*([\s\S]*?)\`\`\`/);
      if (block) { try { generatedData = JSON.parse(block[1].trim()); } catch (e2) {} }
      if (generatedData === null) {
        const s = raw.indexOf('{'); const e3 = raw.lastIndexOf('}');
        if (s !== -1 && e3 > s) { try { generatedData = JSON.parse(raw.slice(s, e3 + 1)); } catch (e4) {} }
      }
    }
  }

  // AiWriteService parses field size metadata and defaults from String values.
  // Normalize model-produced JSON numbers before preview/apply reaches the backend.
  if (generatedData && Array.isArray(generatedData.tableDefinitions)) {
    for (const table of generatedData.tableDefinitions) {
      if (!table || !Array.isArray(table.fieldDefinitions)) continue;
      for (const field of table.fieldDefinitions) {
        if (!field || typeof field !== 'object') continue;
        for (const key of ['length', 'precision', 'scale']) {
          if (field[key] !== null && field[key] !== undefined && typeof field[key] !== 'string') {
            field[key] = String(field[key]);
          }
        }
        if (field.defaultValue !== null && field.defaultValue !== undefined && typeof field.defaultValue !== 'string') {
          field.defaultValue = typeof field.defaultValue === 'object' ? JSON.stringify(field.defaultValue) : String(field.defaultValue);
        }
      }
    }
  }

  // Normalize JSON object fields emitted as JSON strings by the model.
  // Invalid optional config is discarded instead of causing a ClassCastException.
  if (generatedData && typeof generatedData === 'object') {
    const normalizeConfigJson = (value) => {
      if (value === null || value === undefined) return null;
      if (typeof value === 'string') {
        try { value = JSON.parse(value); } catch (e) { return null; }
      }
      return value && typeof value === 'object' && !Array.isArray(value) ? value : null;
    };
    if (Array.isArray(generatedData.formDefinitions)) {
      for (const form of generatedData.formDefinitions) {
        if (!form || typeof form !== 'object') continue;
        form.configJson = normalizeConfigJson(form.configJson);
        if (form.formType === 'TASK') form.formType = 'MAIN';
      }
    }
    if (Array.isArray(generatedData.actionDefinitions)) {
      for (const action of generatedData.actionDefinitions) {
        if (!action || typeof action !== 'object') continue;
        action.configJson = normalizeConfigJson(action.configJson);
      }
    }
  }

  // The platform validator expects raw BPMN XML. Models sometimes return Base64 or
  // malformed pseudo-BPMN; decode valid Base64 and replace invalid structures with
  // a deterministic executable Start-to-End BPMN process.
  if (generatedData && generatedData.processDefinition && typeof generatedData.processDefinition === 'object') {
    let xml = generatedData.processDefinition.bpmnXml;
    if (typeof xml === 'string') {
      xml = xml.replace(/^\uFEFF/, '').trim();
      if (xml && xml.charAt(0) !== '<' && /^[A-Za-z0-9+/=\s]+$/.test(xml)) {
        try {
          const decoded = Buffer.from(xml.replace(/\s/g, ''), 'base64').toString('utf8').replace(/^\uFEFF/, '').trim();
          if (decoded.charAt(0) === '<') xml = decoded;
        } catch (e) {}
      }
    }

    const validNamespace = typeof xml === 'string' && xml.indexOf('http://www.omg.org/spec/BPMN/20100524/MODEL') !== -1;
    const hasDefinitions = typeof xml === 'string' && /<(?:bpmn:)?definitions(?:\s|>)/.test(xml);
    const hasProcess = typeof xml === 'string' && /<(?:bpmn:)?process(?:\s|>)/.test(xml);
    const hasStart = typeof xml === 'string' && /<(?:bpmn:)?startEvent(?:\s|>)/.test(xml);
    const hasEnd = typeof xml === 'string' && /<(?:bpmn:)?endEvent(?:\s|>)/.test(xml);

    if (!(validNamespace && hasDefinitions && hasProcess && hasStart && hasEnd)) {
      const rawName = typeof generatedData.name === 'string' && generatedData.name.trim() ? generatedData.name.trim() : 'Generated Process';
      const processId = (rawName.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || 'generated_process') + '_process';
      const processName = rawName.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
      xml = '<?xml version="1.0" encoding="UTF-8"?>\n' +
        '<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1" targetNamespace="http://platform.local/bpmn">\n' +
        '  <bpmn:process id="' + processId + '" name="' + processName + '" isExecutable="true">\n' +
        '    <bpmn:startEvent id="StartEvent_1" name="Start"><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:startEvent>\n' +
        '    <bpmn:endEvent id="EndEvent_1" name="End"><bpmn:incoming>Flow_1</bpmn:incoming></bpmn:endEvent>\n' +
        '    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>\n' +
        '  </bpmn:process>\n' +
        '  <bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="' + processId + '">\n' +
        '    <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="152" y="102" width="36" height="36"/></bpmndi:BPMNShape>\n' +
        '    <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="302" y="102" width="36" height="36"/></bpmndi:BPMNShape>\n' +
        '    <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="188" y="120"/><di:waypoint x="302" y="120"/></bpmndi:BPMNEdge>\n' +
        '  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>\n' +
        '</bpmn:definitions>';
    }
    // Reject silent Start-to-End fallback BPMN with no executable work nodes.
    const workNodeCount = typeof xml === 'string' ? (xml.match(/<(?:bpmn:)?(?:userTask|serviceTask|task)(?:\s|>)/g) || []).length : 0;
    if (workNodeCount === 0) {
      throw new Error('AI generated invalid BPMN with no task nodes. Regenerate the preview; empty Start-to-End fallback is rejected.');
    }
    generatedData.processDefinition.bpmnXml = xml;
  }

  const cleanReply = reply
    .replace(/---REQUIREMENTS_DOC_START---[\s\S]*?---REQUIREMENTS_DOC_END---/g, '')
    .replace(/---DESIGN_DOC_START---[\s\S]*?---DESIGN_DOC_END---/g, '')
    .replace(/---GENERATED_DATA_START---[\s\S]*?(?:---GENERATED_DATA_END---|$)/g, '')
    .replace(/---PHASE_COMPLETE---/g, '')
    .trim();

  return { reply: cleanReply, document, documentType, phaseComplete, generatedData };
};