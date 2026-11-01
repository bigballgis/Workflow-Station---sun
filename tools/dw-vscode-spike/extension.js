// P0 spike: does Copilot (agent mode / @dw) work against DW when the org disables MCP?
// Throwaway code — answers the P0 questions in docs/plans/dw-vscode-copilot-extension.md, not the P1 design.
const vscode = require('vscode');

const TOKEN_KEY = 'dwCopilot.accessToken';
const TOOL_NAMES = ['dw_list_function_units', 'dw_get_format_spec', 'dw_get_tables', 'dw_apply_design'];

let secrets;
let output;

function baseUrl() {
  return vscode.workspace.getConfiguration('dwCopilot').get('apiBaseUrl').replace(/\/+$/, '');
}

async function dwFetch(path, init = {}) {
  const token = await secrets.get(TOKEN_KEY);
  if (!token) {
    throw new Error('Not signed in to DW. Run "DW: Sign in" or "DW: Paste access token" first.');
  }
  const res = await fetch(baseUrl() + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...(init.headers || {}) },
  });
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  return { status: res.status, body };
}

// Tool implementations shared by agent-mode tools and the @dw participant.
const handlers = {
  async dw_list_function_units({ keyword } = {}) {
    const q = new URLSearchParams({ page: '0', size: '50' });
    if (keyword) q.set('name', keyword);
    const { status, body } = await dwFetch(`/function-units?${q}`);
    if (status !== 200 || !body.success) return `HTTP ${status}: ${JSON.stringify(body)}`;
    return JSON.stringify(body.data.content.map(f => ({ id: f.id, name: f.name, status: f.status })));
  },
  async dw_get_format_spec() {
    const { status, body } = await dwFetch('/ai-generation/prompt-templates/GENERATION');
    if (status !== 200) return `HTTP ${status}: ${JSON.stringify(body)}`;
    return (body.data || body).content;
  },
  async dw_get_tables({ functionUnitId }) {
    const { status, body } = await dwFetch(`/function-units/${functionUnitId}/tables`);
    return status === 200 ? JSON.stringify(body.data ?? body) : `HTTP ${status}: ${JSON.stringify(body)}`;
  },
  async dw_apply_design({ functionUnitId, scope, generatedData }) {
    const { status, body } = await dwFetch('/ai-generation/studio-chat/apply', {
      method: 'POST',
      body: JSON.stringify({ functionUnitId, scope, generatedData }),
    });
    // Validation errors go back to the model verbatim so it can fix the fieldPath and retry.
    return `HTTP ${status}: ${JSON.stringify(body)}`;
  },
};

function log(line) {
  output.appendLine(`[${new Date().toISOString()}] ${line}`);
}

async function runTool(name, input) {
  log(`tool ${name} input=${JSON.stringify(input).slice(0, 300)}`);
  try {
    const result = await handlers[name](input || {});
    log(`tool ${name} -> ${String(result).slice(0, 300)}`);
    return result;
  } catch (e) {
    log(`tool ${name} failed: ${e.message}`);
    return `Error: ${e.message}`;
  }
}

function registerTools(context) {
  for (const name of TOOL_NAMES) {
    context.subscriptions.push(
      vscode.lm.registerTool(name, {
        async prepareInvocation(options) {
          if (name !== 'dw_apply_design') return { invocationMessage: `DW: ${name}` };
          const { functionUnitId, scope } = options.input;
          return {
            invocationMessage: `DW: applying ${scope} to function unit ${functionUnitId}`,
            confirmationMessages: {
              title: 'Apply design to DW?',
              message: new vscode.MarkdownString(
                `Write **${scope}** to function unit **${functionUnitId}** at \`${baseUrl()}\`.`),
            },
          };
        },
        async invoke(options) {
          const text = await runTool(name, options.input);
          return new vscode.LanguageModelToolResult([new vscode.LanguageModelTextPart(text)]);
        },
      }));
  }
}

// @dw fallback: drives its own tool loop with the user's Copilot model, independent of tool registration policy.
const SYSTEM_PROMPT = [
  'You help an IT developer design Function Units in Developer Workstation (DW).',
  'Call dw_get_format_spec before writing generatedData. Use dw_list_function_units to find ids.',
  'If dw_apply_design returns validation errors, fix the reported fieldPath and retry (max 3 attempts).',
  'After a successful apply, verify with dw_get_tables.',
].join('\n');

function participantTools() {
  const contributed = vscode.extensions.getExtension('workflow-station.dw-copilot-spike')
    .packageJSON.contributes.languageModelTools;
  return contributed.map(t => ({ name: t.name, description: t.modelDescription, inputSchema: t.inputSchema }));
}

async function chatHandler(request, _context, stream, token) {
  const messages = [
    vscode.LanguageModelChatMessage.User(SYSTEM_PROMPT),
    vscode.LanguageModelChatMessage.User(request.prompt),
  ];
  const tools = participantTools();
  for (let round = 0; round < 10; round++) {
    const response = await request.model.sendRequest(messages, { tools }, token);
    const assistantParts = [];
    const calls = [];
    for await (const part of response.stream) {
      if (part instanceof vscode.LanguageModelTextPart) {
        stream.markdown(part.value);
        assistantParts.push(part);
      } else if (part instanceof vscode.LanguageModelToolCallPart) {
        calls.push(part);
        assistantParts.push(part);
      }
    }
    if (calls.length === 0) return;
    messages.push(vscode.LanguageModelChatMessage.Assistant(assistantParts));
    for (const call of calls) {
      if (call.name === 'dw_apply_design') {
        const ok = await vscode.window.showWarningMessage(
          `Apply ${call.input.scope} to function unit ${call.input.functionUnitId}?`, { modal: true }, 'Apply');
        if (ok !== 'Apply') {
          messages.push(vscode.LanguageModelChatMessage.User([
            new vscode.LanguageModelToolResultPart(call.callId, [new vscode.LanguageModelTextPart('User declined the apply.')]),
          ]));
          continue;
        }
      }
      stream.progress(`DW: ${call.name}`);
      const text = await runTool(call.name, call.input);
      messages.push(vscode.LanguageModelChatMessage.User([
        new vscode.LanguageModelToolResultPart(call.callId, [new vscode.LanguageModelTextPart(text)]),
      ]));
    }
  }
  stream.markdown('\n\n_Stopped after 10 tool rounds._');
}

async function signIn() {
  const username = await vscode.window.showInputBox({ prompt: 'DW username (dev)' });
  if (!username) return;
  const password = await vscode.window.showInputBox({ prompt: 'DW password', password: true });
  if (!password) return;
  const res = await fetch(baseUrl() + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok || !body.accessToken) {
    vscode.window.showErrorMessage(`DW sign-in failed: HTTP ${res.status}`);
    return;
  }
  await secrets.store(TOKEN_KEY, body.accessToken);
  vscode.window.showInformationMessage(`Signed in to DW as ${username}`);
}

async function setToken() {
  const token = await vscode.window.showInputBox({ prompt: 'DW access token (JWT)', password: true });
  if (token) await secrets.store(TOKEN_KEY, token.trim());
}

async function selfCheck() {
  output.show(true);
  log('==== P0 self-check ====');
  log(`VS Code ${vscode.version}`);
  const visible = vscode.lm.tools.map(t => t.name);
  const ours = TOOL_NAMES.filter(n => visible.includes(n));
  log(`Q1 tools registered and visible to Copilot: ${ours.length}/${TOOL_NAMES.length} ${JSON.stringify(ours)}`);
  try {
    const models = await vscode.lm.selectChatModels({ vendor: 'copilot' });
    log(`Copilot models available to @dw fallback: ${models.length} ${JSON.stringify(models.map(m => m.id))}`);
  } catch (e) {
    log(`Copilot models: error ${e.message}`);
  }
  try {
    const { status, body } = await dwFetch('/auth/me');
    log(`DW API ${baseUrl()} /auth/me -> HTTP ${status} user=${body.username ?? '?'}`);
  } catch (e) {
    log(`DW API: ${e.message}`);
  }
  log('Next: in Copilot Chat (Agent mode) ask it to create a table via #dwApplyDesign, then try "@dw" in Ask mode.');
}

function activate(context) {
  secrets = context.secrets;
  output = vscode.window.createOutputChannel('DW Copilot');
  registerTools(context);
  context.subscriptions.push(
    output,
    vscode.chat.createChatParticipant('dwCopilot.dw', chatHandler),
    vscode.commands.registerCommand('dwCopilot.signIn', signIn),
    vscode.commands.registerCommand('dwCopilot.setToken', setToken),
    vscode.commands.registerCommand('dwCopilot.selfCheck', selfCheck),
  );
}

module.exports = { activate, deactivate() {} };
