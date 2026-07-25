/**
 * FU 列表「Launchpad」布局：iOS 图标式排列 + 拖拽重排 + 拖拽合并成组。
 *
 * - 布局为**全平台共享一份**（后端 shared 作用域，last-write-wins）：任何账号的调整
 *   对所有账号生效；localStorage 只作本机快显缓存。
 * - 因不同账号可见的 FU 集合不同，对账（reconcile）**只补不删**：把「我这边新出现的
 *   FU」追加到末尾、去重、解散孤儿分组，但绝不因为「我看不见某 id」就把它从共享布局
 *   里剔除（那可能是别人团队的 FU）。已删除 FU 的幽灵 id 仅存在于数据里，渲染层按
 *   可见性求交集，永远不会显示。
 * - 过滤（搜索/状态/标签）只影响渲染（visibleEntries），不改布局本身。
 */
import { ref, computed, watch, onBeforeUnmount, type Ref } from 'vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'
import { userPreferenceApi } from '@/api/userPreference'

export interface LaunchpadItemEntry {
  type: 'item'
  id: number
}

export interface LaunchpadFolderEntry {
  type: 'folder'
  id: string
  name: string
  itemIds: number[]
}

export type LaunchpadEntry = LaunchpadItemEntry | LaunchpadFolderEntry

export type DropMode = 'before' | 'after' | 'merge'

const LAYOUT_VERSION = 1
/** 后端偏好键（dw_user_preferences.pref_key） */
const SERVER_PREF_KEY = 'launchpad-layout'
/** 服务端保存防抖（拖拽连续操作时合并写） */
const SERVER_SAVE_DEBOUNCE_MS = 800

function storageKey(): string {
  // 布局全平台共享，本机缓存也用共享键（换账号立即看到同一布局）
  return 'dw-fu-launchpad:shared'
}

export function keyOf(entry: LaunchpadEntry): string {
  return entry.type === 'item' ? `i:${entry.id}` : `f:${entry.id}`
}

export function useLaunchpadLayout(options: {
  /** 全量列表（当前页），布局的事实来源 */
  list: Ref<FunctionUnitResponse[]>
  /** 过滤后的可见列表 */
  visibleList: Ref<FunctionUnitResponse[]>
  /** 新分组默认名（i18n，延迟求值） */
  defaultGroupName: () => string
}) {
  const { list, visibleList, defaultGroupName } = options

  const entries = ref<LaunchpadEntry[]>(loadLayout())

  function loadLayout(): LaunchpadEntry[] {
    try {
      const raw = localStorage.getItem(storageKey())
      if (!raw) return []
      const parsed = JSON.parse(raw)
      if (parsed?.version !== LAYOUT_VERSION || !Array.isArray(parsed.entries)) return []
      return parsed.entries as LaunchpadEntry[]
    } catch {
      return []
    }
  }

  // ==================== 服务端同步（跨设备/跨浏览器跟随账号） ====================
  // localStorage 只作本机快显缓存；服务端值为准。启动时拉取服务端布局；
  // 若用户在拉取完成前已做了改动（dirty），以本地为准并回推服务端。
  let serverLoaded = false
  let dirtyBeforeServerLoad = false
  let serverSaveTimer: ReturnType<typeof setTimeout> | undefined

  function scheduleServerSave() {
    clearTimeout(serverSaveTimer)
    serverSaveTimer = setTimeout(() => {
      const payload = JSON.stringify({ version: LAYOUT_VERSION, entries: entries.value })
      userPreferenceApi.save(SERVER_PREF_KEY, payload, 'shared').catch((e) => {
        // 布局同步失败不打断主流程，但必须可见（下次改动会重试）
        console.warn('[launchpad] failed to save layout to server:', e)
      })
    }, SERVER_SAVE_DEBOUNCE_MS)
  }

  async function loadFromServer() {
    try {
      const raw = await userPreferenceApi.get(SERVER_PREF_KEY, 'shared')
      if (raw && !dirtyBeforeServerLoad) {
        const parsed = JSON.parse(raw)
        if (parsed?.version === LAYOUT_VERSION && Array.isArray(parsed.entries)) {
          entries.value = parsed.entries as LaunchpadEntry[]
          serverLoaded = true
          if (list.value.length > 0) reconcile()
          return
        }
      }
      serverLoaded = true
      // 服务端无记录（或本地已抢先改动）：把本地布局上推，完成迁移
      if (entries.value.length > 0) scheduleServerSave()
    } catch (e) {
      // 拉取失败：保持仅本地模式，不往服务端写，避免用空布局覆盖账号数据
      console.warn('[launchpad] failed to load layout from server:', e)
    }
  }

  void loadFromServer()

  onBeforeUnmount(() => {
    clearTimeout(serverSaveTimer)
  })

  /**
   * @param userChange 用户主动改动（拖拽/改名/移出等）为 true；
   *                   reconcile 等系统对账为 false —— 服务端布局未拉回前不算 dirty，
   *                   否则「列表接口先返回」会让服务端布局被误跳过。
   */
  function persist(userChange = true) {
    try {
      localStorage.setItem(storageKey(), JSON.stringify({ version: LAYOUT_VERSION, entries: entries.value }))
    } catch {
      // 本地缓存失败（隐私模式/配额）可忽略：服务端仍会保存
    }
    if (serverLoaded) {
      scheduleServerSave()
    } else if (userChange) {
      dirtyBeforeServerLoad = true
    }
  }

  /**
   * 与后端列表对账：去重、追加我可见的新 FU、解散孤儿分组。
   * 共享布局**只补不删**——「我看不见的 id」可能是别的团队的 FU，不能剔除；
   * 真正被删除的 FU 只会留下永不渲染的幽灵 id，无碍。
   */
  function reconcile() {
    // 列表尚未加载时跳过，避免把已存布局误当成全部失效
    if (list.value.length === 0) return
    const seen = new Set<number>()
    const next: LaunchpadEntry[] = []
    for (const entry of entries.value) {
      if (entry.type === 'item') {
        if (!seen.has(entry.id)) {
          next.push(entry)
          seen.add(entry.id)
        }
      } else {
        const kept = entry.itemIds.filter((id) => !seen.has(id))
        kept.forEach((id) => seen.add(id))
        if (kept.length >= 2) {
          next.push({ ...entry, itemIds: kept })
        } else if (kept.length === 1) {
          next.push({ type: 'item', id: kept[0] })
        }
      }
    }
    for (const item of list.value) {
      if (!seen.has(item.id)) next.push({ type: 'item', id: item.id })
    }
    entries.value = next
    persist(false) // 系统对账，非用户改动
  }

  watch(list, reconcile, { immediate: true })

  const itemById = computed(() => new Map(list.value.map((i) => [i.id, i])))
  const visibleIds = computed(() => new Set(visibleList.value.map((i) => i.id)))

  /** 渲染用：按可见性收缩；分组只显示可见成员，全部被过滤时整组隐藏 */
  const visibleEntries = computed<LaunchpadEntry[]>(() => {
    const out: LaunchpadEntry[] = []
    for (const entry of entries.value) {
      if (entry.type === 'item') {
        if (visibleIds.value.has(entry.id)) out.push(entry)
      } else {
        const vis = entry.itemIds.filter((id) => visibleIds.value.has(id))
        if (vis.length > 0) out.push({ ...entry, itemIds: vis })
      }
    }
    return out
  })

  // ==================== 拖拽状态 ====================
  const draggingKey = ref<string | null>(null)
  const dropTarget = ref<{ key: string; mode: DropMode } | null>(null)

  function onDragStart(entry: LaunchpadEntry) {
    draggingKey.value = keyOf(entry)
    dropTarget.value = null
  }

  function onDragEnd() {
    draggingKey.value = null
    dropTarget.value = null
  }

  /** 指针在磁贴上的位置 → 放置模式：两侧 30% 插入排序，中间 40% 合并成组 */
  function resolveDropMode(entry: LaunchpadEntry, event: DragEvent): DropMode {
    const el = event.currentTarget as HTMLElement
    const rect = el.getBoundingClientRect()
    const ratio = (event.clientX - rect.left) / rect.width
    const mergeAllowed =
      draggingKey.value?.startsWith('i:') === true && keyOf(entry) !== draggingKey.value
    if (mergeAllowed && ratio >= 0.3 && ratio <= 0.7) return 'merge'
    return ratio < 0.5 ? 'before' : 'after'
  }

  function onDragOver(entry: LaunchpadEntry, event: DragEvent) {
    if (!draggingKey.value || keyOf(entry) === draggingKey.value) {
      dropTarget.value = null
      return
    }
    dropTarget.value = { key: keyOf(entry), mode: resolveDropMode(entry, event) }
  }

  function onDragLeave(entry: LaunchpadEntry) {
    if (dropTarget.value?.key === keyOf(entry)) dropTarget.value = null
  }

  function onDrop(entry: LaunchpadEntry) {
    const dragKey = draggingKey.value
    const target = dropTarget.value
    onDragEnd()
    if (!dragKey || !target || target.key !== keyOf(entry)) return
    applyDrop(dragKey, target.key, target.mode)
  }

  /** 拖到网格空白处：移到末尾 */
  function onDropToEnd() {
    const dragKey = draggingKey.value
    onDragEnd()
    if (!dragKey) return
    const from = entries.value.findIndex((e) => keyOf(e) === dragKey)
    if (from < 0) return
    const [moved] = entries.value.splice(from, 1)
    entries.value.push(moved)
    persist()
  }

  function applyDrop(dragKey: string, targetKey: string, mode: DropMode) {
    const arr = entries.value
    const fromIdx = arr.findIndex((e) => keyOf(e) === dragKey)
    const targetIdx = arr.findIndex((e) => keyOf(e) === targetKey)
    if (fromIdx < 0 || targetIdx < 0 || fromIdx === targetIdx) return

    if (mode === 'merge') {
      const dragged = arr[fromIdx]
      const target = arr[targetIdx]
      if (dragged.type !== 'item') return
      if (target.type === 'item') {
        // item + item → 新分组（占据目标位置）
        const folder: LaunchpadFolderEntry = {
          type: 'folder',
          id: `g${Date.now()}`,
          name: defaultGroupName(),
          itemIds: [target.id, dragged.id],
        }
        arr.splice(targetIdx, 1, folder)
        const removeIdx = arr.findIndex((e) => keyOf(e) === dragKey)
        if (removeIdx >= 0) arr.splice(removeIdx, 1)
      } else {
        // item → 既有分组
        target.itemIds.push(dragged.id)
        arr.splice(fromIdx, 1)
      }
    } else {
      const [moved] = arr.splice(fromIdx, 1)
      let insertIdx = arr.findIndex((e) => keyOf(e) === targetKey)
      if (insertIdx < 0) return
      if (mode === 'after') insertIdx += 1
      arr.splice(insertIdx, 0, moved)
    }
    persist()
  }

  // ==================== 分组操作 ====================
  function folderById(folderId: string): LaunchpadFolderEntry | undefined {
    return entries.value.find(
      (e): e is LaunchpadFolderEntry => e.type === 'folder' && e.id === folderId
    )
  }

  function renameFolder(folderId: string, name: string) {
    const folder = folderById(folderId)
    if (!folder) return
    const trimmed = name.trim()
    if (trimmed) folder.name = trimmed
    persist()
  }

  /** 组内重排（浮层里拖动） */
  function reorderInFolder(folderId: string, fromId: number, toId: number, mode: 'before' | 'after') {
    const folder = folderById(folderId)
    if (!folder || fromId === toId) return
    const fromIdx = folder.itemIds.indexOf(fromId)
    if (fromIdx < 0) return
    folder.itemIds.splice(fromIdx, 1)
    let toIdx = folder.itemIds.indexOf(toId)
    if (toIdx < 0) {
      folder.itemIds.splice(fromIdx, 0, fromId)
      return
    }
    if (mode === 'after') toIdx += 1
    folder.itemIds.splice(toIdx, 0, fromId)
    persist()
  }

  /** 移出分组：成员回到根网格（分组之后）；组内不足 2 人自动解散 */
  function removeFromFolder(folderId: string, itemId: number) {
    const idx = entries.value.findIndex(
      (e) => e.type === 'folder' && e.id === folderId
    )
    if (idx < 0) return
    const folder = entries.value[idx] as LaunchpadFolderEntry
    folder.itemIds = folder.itemIds.filter((id) => id !== itemId)
    const insertAfter: LaunchpadEntry[] = [{ type: 'item', id: itemId }]
    if (folder.itemIds.length === 1) {
      insertAfter.unshift({ type: 'item', id: folder.itemIds[0] })
      entries.value.splice(idx, 1, ...insertAfter)
    } else {
      entries.value.splice(idx + 1, 0, ...insertAfter)
    }
    persist()
  }

  return {
    entries,
    visibleEntries,
    itemById,
    draggingKey,
    dropTarget,
    onDragStart,
    onDragEnd,
    onDragOver,
    onDragLeave,
    onDrop,
    onDropToEnd,
    folderById,
    renameFolder,
    reorderInFolder,
    removeFromFolder,
  }
}
