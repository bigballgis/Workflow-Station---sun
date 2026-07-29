/**
 * 左侧栏「最近打开」的 Function Unit 列表。
 *
 * - **模块级单例状态**：侧栏渲染、进入设计器时记录访问、列表页删除后剔除，三处共用同一份 ref。
 * - **按账号一份**：服务端 `user` 作用域偏好为准（跟随账号跨设备），localStorage 按 userId
 *   分键只作本机快显缓存 —— 与 Launchpad 布局的 `shared` 作用域相反（那份是全平台共享一份）。
 * - **只存展示所需的最小快照**（名称 / 图标 / 状态）：FU 列表加载后按 id 回填最新值，
 *   避免改名、换图标、归档之后侧栏长期显示过期信息。
 */
import { ref, computed } from 'vue'
import { getUser } from '@/api/auth'
import { userPreferenceApi } from '@/api/userPreference'

/** 侧栏最多展示的条数：够覆盖一天里来回切换的几个 FU，再多就该回列表页找 */
export const MAX_RECENT = 8

const SCHEMA_VERSION = 1
/** 后端偏好键（dw_user_preferences.pref_key） */
const SERVER_PREF_KEY = 'recent-function-units'
/** 服务端保存防抖（连续切换 FU 时合并写） */
const SERVER_SAVE_DEBOUNCE_MS = 800

/** 侧栏渲染一条「最近打开」所需的最小快照 */
export interface RecentFunctionUnit {
  id: number
  name: string
  iconId?: number | null
  status: string
  /** 最后一次打开的时间戳，用于排序 */
  visitedAt: number
}

/** 回填元数据的来源（FU 列表项即可满足） */
export interface RecentMetadataSource {
  id: number
  name: string
  iconId?: number
  status: string
}

// ==================== 纯函数（供单测直接调用） ====================

/** 置顶一条访问记录：按 id 去重、最近的在前、超出上限截断 */
export function promote(
  list: readonly RecentFunctionUnit[],
  entry: RecentFunctionUnit,
  max = MAX_RECENT
): RecentFunctionUnit[] {
  return [entry, ...list.filter(item => item.id !== entry.id)].slice(0, max)
}

/** 解析持久化内容；版本不符或结构损坏时按「没有记录」处理，不抛错 */
export function parseStored(raw: string | null): RecentFunctionUnit[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (parsed?.version !== SCHEMA_VERSION || !Array.isArray(parsed.items)) return []
    return (parsed.items as RecentFunctionUnit[])
      .filter(item => typeof item?.id === 'number' && typeof item?.name === 'string')
      .slice(0, MAX_RECENT)
  } catch {
    return []
  }
}

export function serialize(list: readonly RecentFunctionUnit[]): string {
  return JSON.stringify({ version: SCHEMA_VERSION, items: list })
}

/**
 * 用最新的 FU 列表回填名称 / 图标 / 状态。
 * **只更新、不删除**：列表受搜索、状态过滤与团队可见范围影响，「这次没返回」不等于「已被删除」。
 */
export function mergeMetadata(
  list: readonly RecentFunctionUnit[],
  source: readonly RecentMetadataSource[]
): RecentFunctionUnit[] {
  if (source.length === 0) return [...list]
  const byId = new Map(source.map(item => [item.id, item]))
  return list.map(item => {
    const fresh = byId.get(item.id)
    if (!fresh) return item
    return { ...item, name: fresh.name, iconId: fresh.iconId ?? null, status: fresh.status }
  })
}

// ==================== 单例状态 ====================

const recent = ref<RecentFunctionUnit[]>([])

let initialized = false
let serverLoaded = false
let dirtyBeforeServerLoad = false
let serverSaveTimer: ReturnType<typeof setTimeout> | undefined

function storageKey(): string {
  // 「最近打开」是个人痕迹，同一台机器换账号必须换一份缓存
  return `dw-fu-recent:${getUser()?.userId ?? 'anonymous'}`
}

function loadLocal(): RecentFunctionUnit[] {
  try {
    return parseStored(localStorage.getItem(storageKey()))
  } catch {
    return []
  }
}

function scheduleServerSave(): void {
  clearTimeout(serverSaveTimer)
  serverSaveTimer = setTimeout(() => {
    userPreferenceApi.save(SERVER_PREF_KEY, serialize(recent.value), 'user').catch((e) => {
      // 同步失败不打断主流程，但必须可见（下次访问会重试）
      console.warn('[recent-fu] failed to save recent list to server:', e)
    })
  }, SERVER_SAVE_DEBOUNCE_MS)
}

async function loadFromServer(): Promise<void> {
  try {
    const raw = await userPreferenceApi.get(SERVER_PREF_KEY, 'user')
    if (raw && !dirtyBeforeServerLoad) {
      const parsed = parseStored(raw)
      if (parsed.length > 0) {
        recent.value = parsed
        serverLoaded = true
        persistLocal()
        return
      }
    }
    serverLoaded = true
    // 服务端无记录（或本地已抢先记下一次访问）：把本地内容上推，完成迁移
    if (recent.value.length > 0) scheduleServerSave()
  } catch (e) {
    // 拉取失败：退回仅本地模式，不往服务端写，避免用空列表覆盖账号数据
    console.warn('[recent-fu] failed to load recent list from server:', e)
  }
}

function persistLocal(): void {
  try {
    localStorage.setItem(storageKey(), serialize(recent.value))
  } catch {
    // 本地缓存失败（隐私模式 / 配额）可忽略：服务端仍会保存
  }
}

/**
 * @param userChange 用户真实动作（打开 / 删除 / 清空）为 true；元数据回填等系统对账为 false ——
 *                   服务端列表未拉回前不算 dirty，否则「FU 列表接口先返回」会让服务端记录被误跳过。
 */
function persist(userChange = true): void {
  persistLocal()
  if (serverLoaded) {
    scheduleServerSave()
  } else if (userChange) {
    dirtyBeforeServerLoad = true
  }
}

export function useRecentFunctionUnits() {
  if (!initialized) {
    initialized = true
    recent.value = loadLocal()
    void loadFromServer()
  }

  /** 记录一次打开（进入设计器且 FU 详情已取到时调用） */
  function recordVisit(fu: RecentMetadataSource): void {
    if (!fu?.id || !fu.name) return
    recent.value = promote(recent.value, {
      id: fu.id,
      name: fu.name,
      iconId: fu.iconId ?? null,
      status: fu.status,
      visitedAt: Date.now(),
    })
    persist()
  }

  /** 从列表中剔除一条（FU 被永久删除后调用） */
  function removeRecent(id: number): void {
    const next = recent.value.filter(item => item.id !== id)
    if (next.length === recent.value.length) return
    recent.value = next
    persist()
  }

  function clearRecent(): void {
    if (recent.value.length === 0) return
    recent.value = []
    persist()
  }

  /** 用最新 FU 列表回填名称 / 图标 / 状态 */
  function syncMetadata(source: readonly RecentMetadataSource[]): void {
    const next = mergeMetadata(recent.value, source)
    recent.value = next
    persist(false)
  }

  return {
    recent: computed(() => recent.value),
    recordVisit,
    removeRecent,
    clearRecent,
    syncMetadata,
  }
}

/** 仅供测试：重置单例，避免用例之间互相污染 */
export function __resetRecentFunctionUnits(): void {
  recent.value = []
  initialized = false
  serverLoaded = false
  dirtyBeforeServerLoad = false
  clearTimeout(serverSaveTimer)
}
