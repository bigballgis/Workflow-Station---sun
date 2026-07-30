/**
 * HermesEventConfig 事件序列化 / 反序列化纯函数。
 *
 * 从 HermesEventConfig.vue 抽出的无状态数据转换逻辑：把 rule 上的
 * _on / _hook（FNX 字符串、函数、$GLOBAL: 引用）与编辑器侧的字符串数组
 * 互相转换。行为零变化——逻辑与原 SFC 的 loadFN / parseFN 逐字一致，
 * 仅把对 this.modelValue / this.activeRule 的访问改为显式入参。
 */
import is from '@form-create/utils/lib/type';
import deepExtend from '@form-create/utils/lib/deepextend';
import { isEmptyFormCreateHandler, normalizeEventEditorBody } from '@/utils/formCreateDefaultEvents';

/** FNX 函数体前缀标记（与原 SFC 顶层 $T 逐字一致）。 */
export const FNX_PREFIX = '$FNX:';

/** 判断值是否为 FNX 字符串（与原 SFC 顶层 isFNX 逐字一致）。 */
export const isFNX = (v: any): boolean => {
    return is.String(v) && v.indexOf(FNX_PREFIX) === 0;
};

function unwrapHandlerSource(raw: unknown): unknown {
    if (typeof raw === 'function') {
        const tagged = raw as { __hermesFormEventSource?: unknown; __json?: unknown }
        if (tagged.__hermesFormEventSource != null) return tagged.__hermesFormEventSource
        if (typeof tagged.__json === 'string') return tagged.__json
    }
    return raw
}

/** Merge designer shadow buckets with persist buckets so Event panel still sees scripts. */
function mergeEventBuckets(
    primary: Record<string, unknown> | undefined | null,
    secondary: Record<string, unknown> | undefined | null,
): Record<string, unknown> {
    const out: Record<string, unknown> = { ...(primary || {}) }
    for (const [key, value] of Object.entries(secondary || {})) {
        const existing = unwrapHandlerSource(out[key])
        if (existing != null && !isEmptyFormCreateHandler(existing)) continue
        const next = unwrapHandlerSource(value)
        if (next != null && !isEmptyFormCreateHandler(next)) {
            out[key] = next
        } else if (out[key] == null && next != null) {
            out[key] = next
        }
    }
    return out
}

function pushEditorBody(data: string[], raw: unknown): void {
    const source = unwrapHandlerSource(raw)
    if (isFNX(source)) {
        const body = normalizeEventEditorBody(String(source))
        if (body.trim()) data.push(body)
        return
    }
    if (is.Function(source)) {
        const json = (source as { __json?: string }).__json || ''
        if (!json) {
            const printed = String(source)
            if (printed.trim()) data.push(printed)
            return
        }
        const body = normalizeEventEditorBody(String(json))
        if (body.trim()) data.push(body)
        return
    }
    if (typeof source === 'string' && source.indexOf('$GLOBAL:') === 0) {
        data.push(source)
        return
    }
    if (typeof source === 'string' && source.trim()) {
        const body = normalizeEventEditorBody(source)
        if (body.trim()) data.push(body)
    }
}

/**
 * 把 rule 的事件 / hook 配置反序列化为编辑器侧的 { [name]: string[] } 结构。
 * 对应原 loadFN()，modelValue / activeRule 由调用方传入。
 *
 * Empty `$FNX:` stubs (seeded defaults) are omitted so reopening Event only lists
 * handlers that actually have code — matching what users expect after Save.
 */
export function loadEventData(modelValue: any, activeRule: any): Record<string, string[]> {
    const fromModel = (modelValue && typeof modelValue === 'object')
        ? modelValue as Record<string, unknown>
        : {}
    // Prefer designer shadow `_on` (like `_hook`), then persist `on`.
    const fromOn = mergeEventBuckets(
        activeRule?._on && typeof activeRule._on === 'object'
            ? activeRule._on as Record<string, unknown>
            : {},
        activeRule?.on && typeof activeRule.on === 'object'
            ? activeRule.on as Record<string, unknown>
            : {},
    )
    const e = deepExtend({}, mergeEventBuckets(fromModel, fromOn));
    const hooks = mergeEventBuckets(
        activeRule?._hook && typeof activeRule._hook === 'object'
            ? activeRule._hook as Record<string, unknown>
            : {},
        activeRule?.hook && typeof activeRule.hook === 'object'
            ? activeRule.hook as Record<string, unknown>
            : {},
    );
    Object.keys(hooks).forEach(k => {
        e['hook_' + k] = hooks[k];
    });
    const val: Record<string, string[]> = {};
    Object.keys(e).forEach(k => {
        if (Array.isArray(e[k])) {
            const data: string[] = [];
            e[k].forEach((v: any) => {
                pushEditorBody(data, v);
            });
            if (data.length > 0) val[k] = data;
        } else {
            const data: string[] = [];
            pushEditorBody(data, e[k]);
            if (data.length > 0) val[k] = data;
        }
    });
    return val;
}

/**
 * 把编辑器侧的 { [name]: string[] } 结构序列化为 rule 的 on / hooks。
 * 对应原 parseFN()，逻辑逐字一致。
 * Empty bodies are skipped so Save does not re-persist blank `$FNX:` stubs.
 */
export function parseEventData(e: Record<string, string[]>): { hooks: Record<string, any>; on: Record<string, any> } {
    const on: Record<string, any> = {};
    const hooks: Record<string, any> = {};
    Object.keys(e).forEach(k => {
        const lst: string[] = [];
        e[k].forEach((v) => {
            if (v == null) return
            const text = String(v)
            if (text.indexOf('$GLOBAL:') === 0) {
                lst.push(text)
                return
            }
            const body = normalizeEventEditorBody(text)
            if (!body.trim()) return
            lst.push(FNX_PREFIX + body);
        });
        if (lst.length > 0) {
            if (k.indexOf('hook_') > -1) {
                hooks[k.replace('hook_', '')] = lst.length === 1 ? lst[0] : lst;
            } else {
                on[k] = lst.length === 1 ? lst[0] : lst;
            }
        }
    });
    return { hooks, on };
}
