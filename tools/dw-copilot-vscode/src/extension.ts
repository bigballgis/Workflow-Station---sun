import * as vscode from 'vscode';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { DwAuth, listenLoopback, NoDwAccessError } from './auth';
import { DwClient } from './dwClient';
import { ApplyInput, asToolText, DwTools, errorText, PreparedApply, readOnlyHandlers, ToolContext } from './tools';
import { registerParticipant } from './participant';

const ACTIVE_ENV_KEY = 'dwCopilot.activeEnvironment';

interface Environment { name: string; url: string }

let auth: DwAuth;

export function activate(context: vscode.ExtensionContext): void {
  auth = new DwAuth({
    secrets: context.secrets,
    openExternal: url => Promise.resolve(vscode.env.openExternal(vscode.Uri.parse(url, true))),
    listenLoopback,
    fetch,
  });
  const backupRoot = join(context.globalStorageUri.fsPath, 'backups');
  const tools = new DwTools(new DwClient(auth, fetch), backupRoot);

  const environments = (): Environment[] =>
    vscode.workspace.getConfiguration('dwCopilot').get<Environment[]>('environments', []);
  const activeEnvironment = (): Environment => {
    const all = environments();
    if (all.length === 0) throw new Error('No DW environment configured. Add one in the "dwCopilot.environments" setting.');
    const name = context.globalState.get<string>(ACTIVE_ENV_KEY);
    const env = name ? all.find(e => e.name === name) : all.length === 1 ? all[0] : undefined;
    if (!env) throw new Error('No DW environment selected. Run "DW: Select environment".');
    return env;
  };
  const toolContext: ToolContext = {
    origin: () => new URL(activeEnvironment().url).origin,
    tools,
    exportFolder: () => {
      const folder = vscode.workspace.workspaceFolders?.[0];
      if (!folder) throw new Error('Open a folder first: exports are written to <folder>/dw-exports.');
      return folder.uri.fsPath;
    },
  };

  const status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left);
  status.command = 'dwCopilot.selectEnvironment';
  const tree = new FunctionUnitTree(toolContext, tools);
  const refreshUi = async () => {
    try {
      const env = activeEnvironment();
      const session = await auth.session(new URL(env.url).origin);
      status.text = `$(cloud) DW: ${env.name}` +
        (session ? ` (${session.username}${session.readOnly ? ', read-only' : ''})` : ' — signed out');
    } catch {
      status.text = '$(cloud) DW: no environment';
    }
    status.show();
    tree.refresh();
  };
  // Roles can change between sessions: re-check DW admission for the stored session, then redraw.
  const verifyActive = async () => {
    try {
      await auth.verify(toolContext.origin());
    } catch (e) {
      if (e instanceof NoDwAccessError) vscode.window.showErrorMessage(e.message);
    }
    await refreshUi();
  };

  context.subscriptions.push(
    status,
    vscode.window.registerTreeDataProvider('dwCopilot.functionUnits', tree),
    vscode.commands.registerCommand('dwCopilot.selectEnvironment', async () => {
      const all = environments();
      if (all.length === 0) {
        await vscode.commands.executeCommand('workbench.action.openSettings', 'dwCopilot.environments');
        return;
      }
      const pick = await vscode.window.showQuickPick(all.map(e => ({ label: e.name, description: e.url })));
      if (!pick) return;
      await context.globalState.update(ACTIVE_ENV_KEY, pick.label);
      await verifyActive();
    }),
    vscode.commands.registerCommand('dwCopilot.signIn', () => reportErrors(async () => {
      const origin = toolContext.origin();
      const session = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Notification, title: `DW: finish sign-in in the browser (${origin})` },
        () => auth.signIn(origin));
      vscode.window.showInformationMessage(`Signed in to DW as ${session.username}` +
        (session.readOnly ? ' (read-only: your team can view function units but you have no DW role to change them)' : ''));
      await refreshUi();
    })),
    vscode.commands.registerCommand('dwCopilot.signOut', () => reportErrors(async () => {
      await auth.signOut(toolContext.origin());
      await refreshUi();
    })),
    vscode.commands.registerCommand('dwCopilot.refreshTree', () => tree.refresh()),
    vscode.commands.registerCommand('dwCopilot.openInBrowser', (item: FunctionUnitItem) => reportErrors(async () => {
      await vscode.env.openExternal(vscode.Uri.parse(`${toolContext.origin()}/dev/function-units/${item.functionUnitId}`));
    })),
    vscode.commands.registerCommand('dwCopilot.openBackups', () => reportErrors(async () => {
      await mkdir(backupRoot, { recursive: true });
      await vscode.commands.executeCommand('revealFileInOS', vscode.Uri.file(backupRoot));
    })),
    vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('dwCopilot.environments')) void refreshUi();
    }),
    ...registerTools(toolContext),
    registerParticipant(context, toolContext),
  );
  void verifyActive();
}

export function deactivate(): void {}

async function reportErrors(run: () => Promise<void>): Promise<void> {
  try {
    await run();
  } catch (e) {
    vscode.window.showErrorMessage((e as Error).message);
  }
}

function text(value: string): vscode.LanguageModelToolResult {
  return new vscode.LanguageModelToolResult([new vscode.LanguageModelTextPart(value)]);
}

function registerTools(ctx: ToolContext): vscode.Disposable[] {
  const { tools } = ctx;
  const disposables = Object.entries(readOnlyHandlers(ctx)).map(([name, run]) => vscode.lm.registerTool(name, {
    invoke: async options => text(await asToolText(() => run(options.input))),
  }));

  disposables.push(
    vscode.lm.registerTool<{ name: string; description?: string; workspaceIds: string[] }>('dw_create_function_unit', {
      prepareInvocation: options => ({
        invocationMessage: `DW: creating function unit "${options.input.name}"`,
        confirmationMessages: {
          title: 'Create a DW function unit?',
          message: new vscode.MarkdownString(`Create **${options.input.name}** in workspace(s) ` +
            `\`${options.input.workspaceIds.join(', ')}\` at \`${safeOrigin(ctx)}\`.`),
        },
      }),
      invoke: async options => text(await asToolText(() => tools.createFunctionUnit(ctx.origin(), options.input))),
    }),
    vscode.lm.registerTool<{ undoToken: string }>('dw_undo', {
      prepareInvocation: () => ({
        invocationMessage: 'DW: undoing an apply',
        confirmationMessages: { title: 'Undo the DW apply?', message: `Undo at ${safeOrigin(ctx)}.` },
      }),
      invoke: async options => text(await asToolText(() => tools.undo(ctx.origin(), options.input.undoToken))),
    }),
    registerApplyTool(ctx),
  );
  return disposables;
}

/**
 * Dry run and backup happen in prepareInvocation so the confirmation shows what will change and where the backup is.
 * invoke only writes what prepareInvocation prepared for the same input; it never writes unconfirmed input.
 */
const PREPARED_TTL_MS = 10 * 60 * 1000;

function registerApplyTool(ctx: ToolContext): vscode.Disposable {
  // Entries the user skipped are never invoked; drop them once they are stale.
  const prepared = new Map<string, PreparedApply & { at: number }>();
  return vscode.lm.registerTool<ApplyInput>('dw_apply_design', {
    prepareInvocation: async options => {
      for (const [k, v] of prepared) {
        if (Date.now() - v.at > PREPARED_TTL_MS) prepared.delete(k);
      }
      const key = JSON.stringify(options.input);
      let result: PreparedApply;
      try {
        result = await ctx.tools.prepareApply(ctx.origin(), options.input);
      } catch (e) {
        result = { ok: false, text: errorText(e) };
      }
      prepared.set(key, { ...result, at: Date.now() });
      if (!result.ok) return { invocationMessage: 'DW: design not applied (see result)' };
      return {
        invocationMessage: `DW: applying ${options.input.scope} to function unit ${options.input.functionUnitId}`,
        confirmationMessages: {
          title: 'Apply design to DW?',
          message: new vscode.MarkdownString(
            `Write **${options.input.scope}** to function unit **${options.input.functionUnitId}** at \`${ctx.origin()}\`.\n\n` +
            '```\n' + result.summary + '\n```\n\nBackup: `' + result.backupPath + '`'),
        },
      };
    },
    invoke: async options => {
      const key = JSON.stringify(options.input);
      const result = prepared.get(key);
      prepared.delete(key);
      if (!result) return text('Error: this apply was not prepared and confirmed; call dw_apply_design again.');
      if (!result.ok) return text(result.text);
      return text(await asToolText(() => ctx.tools.apply(ctx.origin(), options.input, result.backupPath)));
    },
  });
}

function safeOrigin(ctx: ToolContext): string {
  try {
    return ctx.origin();
  } catch (e) {
    return (e as Error).message;
  }
}

class FunctionUnitItem extends vscode.TreeItem {
  constructor(readonly functionUnitId: number, name: string, status: string) {
    super(name);
    this.description = `${status} · #${functionUnitId}`;
    this.contextValue = 'functionUnit';
    this.command = { command: 'dwCopilot.openInBrowser', title: 'Open in browser', arguments: [this] };
  }
}

class FunctionUnitTree implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly changed = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this.changed.event;

  constructor(private readonly ctx: ToolContext, private readonly tools: DwTools) {}

  refresh(): void {
    this.changed.fire();
  }

  async getChildren(): Promise<vscode.TreeItem[]> {
    let origin: string;
    try {
      origin = this.ctx.origin();
    } catch (e) {
      return [message((e as Error).message, 'dwCopilot.selectEnvironment')];
    }
    if (!(await auth.session(origin))) return [message('Sign in to DW', 'dwCopilot.signIn')];
    try {
      const { rows } = await this.tools.functionUnits(origin);
      return rows.map(r => new FunctionUnitItem(r.id, r.name, r.status));
    } catch (e) {
      return [message(`Could not load function units: ${(e as Error).message}`, 'dwCopilot.refreshTree')];
    }
  }

  getTreeItem(item: vscode.TreeItem): vscode.TreeItem {
    return item;
  }
}

function message(label: string, command: string): vscode.TreeItem {
  const item = new vscode.TreeItem(label);
  item.command = { command, title: label };
  return item;
}
