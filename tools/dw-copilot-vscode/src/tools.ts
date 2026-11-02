// Tool logic shared by agent-mode tools and the @dw participant. Returns text for the model; no `vscode` import.
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { DwApiError, DwClient } from './dwClient';

export interface ApplyInput {
  functionUnitId: number;
  scope: string;
  generatedData: Record<string, unknown>;
}

interface PreviewItem { slice: string; name: string; action: string }
interface PreviewIssue { severity: 'ERROR' | 'WARNING'; errorType?: string; fieldPath?: string; description?: string }
interface Preview {
  items: PreviewItem[];
  replacements: { slice: string; replacesExisting: number }[];
  issues: PreviewIssue[];
  checked: boolean;
  undoable: boolean;
}

export interface FunctionUnitRow { id: number; name: string; status: string }

export interface ToolContext {
  origin(): string;
  tools: DwTools;
  /** Folder for dw_export_function_unit: the first open workspace folder. */
  exportFolder(): string;
}

/** Tools that need no confirmation, shared by agent-mode tools and the @dw participant. */
export function readOnlyHandlers(ctx: ToolContext): Record<string, (input: any) => Promise<string>> {
  const { tools } = ctx;
  const handlers: Record<string, (input: any) => Promise<string>> = {
    dw_list_workspaces: () => tools.listWorkspaces(ctx.origin()),
    dw_list_function_units: i => tools.listFunctionUnits(ctx.origin(), i.keyword),
    dw_get_format_spec: () => tools.formatSpec(ctx.origin()),
    dw_get_design: i => tools.getDesign(ctx.origin(), i.functionUnitId, i.sections),
    dw_dry_run: i => tools.dryRun(ctx.origin(), i),
    dw_export_function_unit: i => tools.exportFunctionUnit(ctx.origin(), i.functionUnitId, ctx.exportFolder()),
  };
  // `page` is ours, not the backend's: strip it before the input reaches a request body (dw_dry_run posts it as is).
  return Object.fromEntries(Object.entries(handlers).map(([name, run]) =>
    [name, async ({ page, ...input }: any = {}) => paged(await run(input), page)]));
}

/**
 * Copilot shows the model only a short preview of a large tool result (about 500 characters; the rest goes to a
 * file it rarely reads), so a 28 KB format spec or a 13 KB design arrives cut off. Long output is served in pages
 * that each fit, and each page says how to fetch the next one.
 */
export const PAGE_CHARS = 4000;

export function paged(text: string, page = 1): string {
  const pages = splitPages(text);
  if (pages.length === 1) return text;
  if (!Number.isInteger(page) || page < 1 || page > pages.length) {
    return `Error: page must be between 1 and ${pages.length}.`;
  }
  const next = page < pages.length
    ? ` Call this tool again with the same arguments and "page": ${page + 1} for the next part.`
    : ' This is the last part.';
  return `[Part ${page} of ${pages.length}.${next} Read every part before relying on this output.]\n${pages[page - 1]}`;
}

function splitPages(text: string): string[] {
  const pages: string[] = [];
  let current = '';
  for (const line of text.split('\n')) {
    for (let i = 0; i < line.length || i === 0; i += PAGE_CHARS) {
      const piece = line.slice(i, i + PAGE_CHARS) + (i + PAGE_CHARS >= line.length ? '\n' : '');
      if (current.length + piece.length > PAGE_CHARS && current) {
        pages.push(current);
        current = '';
      }
      current += piece;
    }
  }
  pages.push(current.replace(/\n$/, ''));
  return pages;
}

export type PreparedApply =
  | { ok: false; text: string }
  | { ok: true; summary: string; backupPath: string };

const LIST_PAGE_SIZE = 200;

export class DwTools {
  constructor(
    private readonly client: DwClient,
    /** Root folder for automatic pre-apply backups. */
    private readonly backupRoot: string,
  ) {}

  async listWorkspaces(origin: string): Promise<string> {
    return JSON.stringify(await this.client.json(origin, 'GET', '/function-units/my-dev-groups'));
  }

  async functionUnits(origin: string, keyword?: string): Promise<{ rows: FunctionUnitRow[]; total: number }> {
    const q = new URLSearchParams({ page: '0', size: String(LIST_PAGE_SIZE) });
    if (keyword) q.set('name', keyword);
    const page = await this.client.json<{ content: FunctionUnitRow[]; totalElements: number }>(
      origin, 'GET', `/function-units?${q}`);
    return { rows: page.content.map(f => ({ id: f.id, name: f.name, status: f.status })), total: page.totalElements };
  }

  async listFunctionUnits(origin: string, keyword?: string): Promise<string> {
    const { rows, total } = await this.functionUnits(origin, keyword);
    const more = total > rows.length ? `\nShowing ${rows.length} of ${total}; pass a keyword to narrow down.` : '';
    return JSON.stringify(rows) + more;
  }

  async createFunctionUnit(origin: string, input: { name: string; description?: string; workspaceIds: string[] }): Promise<string> {
    const fu = await this.client.json<{ id: number; name: string }>(origin, 'POST', '/function-units', {
      name: input.name,
      description: input.description,
      virtualGroupIds: input.workspaceIds,
    });
    return JSON.stringify({ functionUnitId: fu.id, name: fu.name });
  }

  async formatSpec(origin: string): Promise<string> {
    const spec = await this.client.json<{ template: string; schemaMetadata: unknown }>(
      origin, 'GET', '/ai-generation/studio-chat/format-spec');
    // Plain template text keeps its line breaks, so pages split between lines rather than inside one huge JSON string.
    return `${spec.template}\n\nschemaMetadata (authoritative where it differs from the template):\n` +
      JSON.stringify(spec.schemaMetadata, null, 1);
  }

  /**
   * Without sections: a table of contents (counts and names per section), small enough to read whole.
   * With sections: just those sections in full.
   */
  async getDesign(origin: string, functionUnitId: number, sections?: string[]): Promise<string> {
    const design = await this.client.json<Record<string, unknown>>(origin, 'GET',
      `/ai-generation/studio-chat/design-context?functionUnitId=${functionUnitId}`);
    const { functionUnitId: id, name, description, ...rest } = design;
    if (!sections?.length) {
      const contents = Object.fromEntries(Object.entries(rest).map(([k, v]) => [k, summarize(v)]));
      return JSON.stringify({ functionUnitId: id, name, description, sections: contents }, null, 1) +
        '\nCall again with "sections" to read the sections you need in full.';
    }
    const unknown = sections.filter(s => !(s in rest));
    if (unknown.length) {
      return `Error: unknown section(s) ${unknown.join(', ')}. Valid: ${Object.keys(rest).join(', ')}.`;
    }
    return JSON.stringify({ functionUnitId: id, name, ...Object.fromEntries(sections.map(s => [s, rest[s]])) }, null, 1);
  }

  async dryRun(origin: string, input: ApplyInput): Promise<string> {
    return describePreview(input, await this.preview(origin, input));
  }

  /**
   * Dry run → stop on ERROR or an unfinished check → back up the current design.
   * Nothing is written here; the caller asks the user and then calls {@link apply}.
   */
  async prepareApply(origin: string, input: ApplyInput): Promise<PreparedApply> {
    const preview = await this.preview(origin, input);
    const summary = describePreview(input, preview);
    if (!preview.checked) {
      return { ok: false, text: `Not applied: the dry run could not finish, so the change was not verified.\n${summary}` };
    }
    if (preview.issues.some(i => i.severity === 'ERROR')) {
      return { ok: false, text: `Not applied: fix the errors and call again.\n${summary}` };
    }
    const backupPath = await this.backup(origin, input.functionUnitId);
    return { ok: true, summary, backupPath };
  }

  async apply(origin: string, input: ApplyInput, backupPath: string): Promise<string> {
    const result = await this.client.json<{ undoToken?: string; undoableUntil?: string }>(
      origin, 'POST', '/ai-generation/studio-chat/apply', input);
    return `Applied ${input.scope} to function unit ${input.functionUnitId}.\n` +
      `Backup of the previous design: ${backupPath} (restore: DW → Import, same name).\n` +
      (result.undoToken ? `undoToken: ${result.undoToken} (valid until ${result.undoableUntil})` : 'No undo token for this scope.');
  }

  async undo(origin: string, undoToken: string): Promise<string> {
    return JSON.stringify(await this.client.json(origin, 'POST', '/ai-generation/studio-chat/undo', { undoToken }));
  }

  async exportFunctionUnit(origin: string, functionUnitId: number, folder: string): Promise<string> {
    const file = join(folder, 'dw-exports', `function-unit-${functionUnitId}-${timestamp()}.zip`);
    await this.download(origin, functionUnitId, file);
    return `Exported function unit ${functionUnitId} to ${file}`;
  }

  private preview(origin: string, input: ApplyInput): Promise<Preview> {
    return this.client.json<Preview>(origin, 'POST', '/ai-generation/studio-chat/dry-run', input);
  }

  private async backup(origin: string, functionUnitId: number): Promise<string> {
    const file = join(this.backupRoot, new URL(origin).host.replace(/[^\w.-]/g, '_'), String(functionUnitId), `${timestamp()}.zip`);
    await this.download(origin, functionUnitId, file);
    return file;
  }

  private async download(origin: string, functionUnitId: number, file: string): Promise<void> {
    const zip = await this.client.bytes(origin, `/export-import/function-units/${functionUnitId}/export`);
    await mkdir(join(file, '..'), { recursive: true });
    await writeFile(file, zip);
  }
}

export function describePreview(input: ApplyInput, p: Preview): string {
  const lines = [`Dry run: function unit ${input.functionUnitId}, scope ${input.scope}`];
  for (const i of p.items) lines.push(`- ${i.action} ${i.slice}: ${i.name}`);
  for (const r of p.replacements) {
    if (r.replacesExisting > 0) lines.push(`- replaces ${r.replacesExisting} existing in ${r.slice}`);
  }
  for (const i of p.issues) {
    lines.push(`- ${i.severity}${i.fieldPath ? ` at ${i.fieldPath}` : ''}: ${i.description ?? i.errorType ?? ''}`);
  }
  if (!p.checked) lines.push('- the dry run did not complete (server could not finish the check)');
  lines.push(p.undoable ? 'Undo token will be issued.' : 'No undo for this scope; a backup zip is taken before writing.');
  return lines.join('\n');
}

/** Errors become tool text so the model can react (fix fieldPath, ask the user to sign in, ...). */
export async function asToolText(run: () => Promise<string>): Promise<string> {
  try {
    return await run();
  } catch (e) {
    return errorText(e);
  }
}

export function errorText(e: unknown): string {
  return e instanceof DwApiError ? e.toToolText() : `Error: ${(e as Error).message}`;
}

const NAME_KEYS = ['tableName', 'formName', 'actionName', 'decisionKey', 'viewName', 'templateName', 'flowKey', 'name', 'key'];

/** Section summary for the table of contents: arrays as count + item names, objects as their keys. */
function summarize(value: unknown): unknown {
  if (Array.isArray(value)) {
    const names = value.map(item => {
      const key = NAME_KEYS.find(k => typeof (item as Record<string, unknown>)?.[k] === 'string');
      return key ? (item as Record<string, string>)[key] : undefined;
    }).filter(Boolean);
    return names.length ? { count: value.length, names } : { count: value.length };
  }
  if (value && typeof value === 'object') return { keys: Object.keys(value) };
  return value ?? null;
}

function timestamp(): string {
  return new Date().toISOString().replace(/[:.]/g, '-');
}
