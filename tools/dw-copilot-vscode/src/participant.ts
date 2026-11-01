// @dw: runs its own tool loop with the user's Copilot model, so it works even where agent-mode tools are unavailable.
import * as vscode from 'vscode';
import { ApplyInput, asToolText, readOnlyHandlers, ToolContext } from './tools';

const MAX_ROUNDS = 12;
const SYSTEM_PROMPT = [
  'You help an IT developer design Function Units in Developer Workstation (DW).',
  'Read dw_get_format_spec before writing generatedData. Read dw_get_design before changing an existing function unit.',
  'Use dw_dry_run to check a design; dw_apply_design writes it after the user confirms.',
  'If a tool returns validation errors, fix the reported fieldPath and try again (at most 3 attempts).',
].join('\n');

export function registerParticipant(context: vscode.ExtensionContext, ctx: ToolContext): vscode.Disposable {
  const contributed = context.extension.packageJSON.contributes.languageModelTools as
    { name: string; modelDescription: string; inputSchema: object }[];
  const tools = contributed.map(t => ({ name: t.name, description: t.modelDescription, inputSchema: t.inputSchema }));

  return vscode.chat.createChatParticipant('dwCopilot.dw', async (request, _chat, stream, token) => {
    const messages = [
      vscode.LanguageModelChatMessage.User(SYSTEM_PROMPT),
      vscode.LanguageModelChatMessage.User(request.prompt),
    ];
    for (let round = 0; round < MAX_ROUNDS; round++) {
      const response = await request.model.sendRequest(messages, { tools }, token);
      const parts: (vscode.LanguageModelTextPart | vscode.LanguageModelToolCallPart)[] = [];
      const calls: vscode.LanguageModelToolCallPart[] = [];
      for await (const part of response.stream) {
        if (part instanceof vscode.LanguageModelTextPart) {
          stream.markdown(part.value);
          parts.push(part);
        } else if (part instanceof vscode.LanguageModelToolCallPart) {
          calls.push(part);
          parts.push(part);
        }
      }
      if (calls.length === 0) return;
      messages.push(vscode.LanguageModelChatMessage.Assistant(parts));
      for (const call of calls) {
        stream.progress(`DW: ${call.name}`);
        const result = await runTool(ctx, call.name, call.input as any);
        messages.push(vscode.LanguageModelChatMessage.User([
          new vscode.LanguageModelToolResultPart(call.callId, [new vscode.LanguageModelTextPart(result)]),
        ]));
      }
    }
    stream.markdown(`\n\n_Stopped after ${MAX_ROUNDS} tool rounds._`);
  });
}

async function confirm(message: string, detail: string): Promise<boolean> {
  return (await vscode.window.showWarningMessage(message, { modal: true, detail }, 'Continue')) === 'Continue';
}

function runTool(ctx: ToolContext, name: string, input: any): Promise<string> {
  const { tools } = ctx;
  const readOnly = readOnlyHandlers(ctx)[name];
  return asToolText(async () => {
    if (readOnly) return readOnly(input);
    switch (name) {
      case 'dw_create_function_unit':
        if (!(await confirm(`Create DW function unit "${input.name}"?`, `At ${ctx.origin()}`))) return 'The user declined.';
        return tools.createFunctionUnit(ctx.origin(), input);
      case 'dw_undo':
        if (!(await confirm('Undo the DW apply?', `At ${ctx.origin()}`))) return 'The user declined.';
        return tools.undo(ctx.origin(), input.undoToken);
      case 'dw_apply_design': {
        const apply = input as ApplyInput;
        const prepared = await tools.prepareApply(ctx.origin(), apply);
        if (!prepared.ok) return prepared.text;
        if (!(await confirm(`Apply ${apply.scope} to function unit ${apply.functionUnitId}?`,
          `${prepared.summary}\n\nBackup: ${prepared.backupPath}`))) return 'The user declined.';
        return tools.apply(ctx.origin(), apply, prepared.backupPath);
      }
      default:
        throw new Error(`Unknown tool ${name}`);
    }
  });
}
