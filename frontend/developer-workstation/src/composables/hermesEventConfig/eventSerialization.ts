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
import { normalizeEventEditorBody } from '@/utils/formCreateDefaultEvents';

/** FNX 函数体前缀标记（与原 SFC 顶层 $T 逐字一致）。 */
export const FNX_PREFIX = '$FNX:';

/** 判断值是否为 FNX 字符串（与原 SFC 顶层 isFNX 逐字一致）。 */
export const isFNX = (v: any): boolean => {
    return is.String(v) && v.indexOf(FNX_PREFIX) === 0;
};

/**
 * 把 rule 的事件 / hook 配置反序列化为编辑器侧的 { [name]: string[] } 结构。
 * 对应原 loadFN()，modelValue / activeRule 由调用方传入。
 */
export function loadEventData(modelValue: any, activeRule: any): Record<string, string[]> {
    const e = deepExtend({}, modelValue || {});
    const hooks = activeRule ? { ...activeRule._hook || {} } : {};
    Object.keys(hooks).forEach(k => {
        e['hook_' + k] = hooks[k];
    });
    const val: Record<string, string[]> = {};
    Object.keys(e).forEach(k => {
        if (Array.isArray(e[k])) {
            const data: string[] = [];
            e[k].forEach((v: any) => {
                if (isFNX(v)) {
                    data.push(v.replace(FNX_PREFIX, ''));
                } else if (is.Function(v) && isFNX(v.__json)) {
                    data.push(v.__json.replace(FNX_PREFIX, ''));
                } else if (v && v.indexOf('$GLOBAL:') === 0) {
                    data.push(v);
                } else if (typeof v === 'string' && v.trim()) {
                    data.push(normalizeEventEditorBody(v));
                }
            });
            val[k] = data;
        } else if (isFNX(e[k])) {
            val[k] = [e[k].replace(FNX_PREFIX, '')];
        } else if (is.Function(e[k])) {
            const json = e[k].__json || '';
            if (!json) {
                val[k] = ['' + e[k]];
            } else if (isFNX(json)) {
                val[k] = [json.replace(FNX_PREFIX, '')];
            } else {
                val[k] = [json];
            }
        } else if (e[k] && e[k].indexOf('$GLOBAL:') === 0) {
            val[k] = [e[k]];
        } else if (typeof e[k] === 'string' && e[k].trim()) {
            val[k] = [normalizeEventEditorBody(e[k])];
        }
    });
    return val;
}

/**
 * 把编辑器侧的 { [name]: string[] } 结构序列化为 rule 的 on / hooks。
 * 对应原 parseFN()，逻辑逐字一致。
 */
export function parseEventData(e: Record<string, string[]>): { hooks: Record<string, any>; on: Record<string, any> } {
    const on: Record<string, any> = {};
    const hooks: Record<string, any> = {};
    Object.keys(e).forEach(k => {
        const lst: string[] = [];
        e[k].forEach((v, i) => {
            lst[i] = v.indexOf('$GLOBAL:') !== 0 ? (FNX_PREFIX + v) : v;
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
