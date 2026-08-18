import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref, nextTick } from 'vue'
import type { FunctionUnitResponse } from '@/api/functionUnit'

vi.mock('@/api/userPreference', () => ({
  userPreferenceApi: {
    get: vi.fn().mockResolvedValue(null),
    save: vi.fn().mockResolvedValue(undefined),
  },
}))

import { useLaunchpadLayout, keyOf } from '../useLaunchpadLayout'

function fu(id: number): FunctionUnitResponse {
  return { id, name: `FU ${id}`, status: 'DRAFT' } as FunctionUnitResponse
}

function setup(count: number, pageSize: number) {
  const list = ref(Array.from({ length: count }, (_, i) => fu(i + 1)))
  const size = ref(pageSize)
  const layout = useLaunchpadLayout({
    list,
    visibleList: list,
    pageSize: size,
    defaultGroupName: () => 'New Group',
  })
  return { layout, list, size }
}

/** Ids rendered on the current page, folders reported as `f:<id>` */
function pageKeys(layout: ReturnType<typeof useLaunchpadLayout>): string[] {
  return layout.pagedEntries.value.map(keyOf)
}

/** Drop an item onto another tile with the center ("merge into a folder") drop mode */
function mergeInto(
  layout: ReturnType<typeof useLaunchpadLayout>,
  dragId: number,
  targetKey: string
) {
  const entries = layout.entries.value
  const dragged = entries.find((e) => e.type === 'item' && e.id === dragId)!
  const target = entries.find((e) => keyOf(e) === targetKey)!
  layout.onDragStart(dragged)
  layout.dropTarget.value = { key: keyOf(target), mode: 'merge' }
  layout.onDrop(target)
}

describe('useLaunchpadLayout paging', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('counts a folder as a single tile, so a full page still shows pageSize tiles', async () => {
    const { layout } = setup(23, 20)
    await nextTick()
    expect(pageKeys(layout)).toHaveLength(20)

    // Fold three units into one folder: 23 units -> 21 tiles, page 1 still shows 20
    mergeInto(layout, 2, 'i:1')
    const folderKey = layout.entries.value.map(keyOf).find((k) => k.startsWith('f:'))!
    expect(folderKey).toBeDefined()
    mergeInto(layout, 3, folderKey)
    await nextTick()

    expect(pageKeys(layout)).toContain(folderKey)
    expect(layout.totalTiles.value).toBe(21)
    expect(pageKeys(layout)).toHaveLength(20)
  })

  it('page size drives how many tiles a page holds and how many pages there are', async () => {
    const { layout, size } = setup(60, 50)
    await nextTick()
    expect(pageKeys(layout)).toHaveLength(50)
    expect(layout.pageCount.value).toBe(2)

    // Switching to the large-icon view (20 per page)
    size.value = 20
    await nextTick()
    expect(pageKeys(layout)).toHaveLength(20)
    expect(layout.pageCount.value).toBe(3)
  })

  it('clamps the current page when the tile count shrinks', async () => {
    const { layout, list } = setup(60, 20)
    await nextTick()
    layout.page.value = 3
    list.value = list.value.slice(0, 21)
    await nextTick()
    expect(layout.page.value).toBe(2)
  })

  it('drops onto the next-page edge: the tile lands first on the next page', async () => {
    const { layout } = setup(40, 20)
    await nextTick()
    const first = layout.entries.value[0]

    layout.onDragStart(first)
    layout.onDropToPage(1)
    await nextTick()

    expect(layout.page.value).toBe(2)
    expect(pageKeys(layout)[0]).toBe(keyOf(first))
    expect(layout.draggingKey.value).toBeNull()
  })

  it('drops onto the previous-page edge: the tile lands last on the previous page', async () => {
    const { layout } = setup(40, 20)
    await nextTick()
    layout.page.value = 2
    const moved = layout.entries.value[25]

    layout.onDragStart(moved)
    layout.onDropToPage(-1)
    await nextTick()

    expect(layout.page.value).toBe(1)
    const keys = pageKeys(layout)
    expect(keys).toHaveLength(20)
    expect(keys[keys.length - 1]).toBe(keyOf(moved))
  })

  it('ignores an edge drop that would leave the page range', async () => {
    const { layout } = setup(40, 20)
    await nextTick()
    const first = layout.entries.value[0]

    layout.onDragStart(first)
    layout.onDropToPage(-1)
    await nextTick()

    expect(layout.page.value).toBe(1)
    expect(pageKeys(layout)[0]).toBe(keyOf(first))
  })

  it('drops on empty space: the tile moves to the end of the current page, not the last page', async () => {
    const { layout } = setup(40, 20)
    await nextTick()
    const first = layout.entries.value[0]

    layout.onDragStart(first)
    layout.onDropToEnd()
    await nextTick()

    expect(layout.page.value).toBe(1)
    const keys = pageKeys(layout)
    expect(keys[keys.length - 1]).toBe(keyOf(first))
  })

  it('hovering an edge flips the page only after the dwell delay, and only while dragging', async () => {
    vi.useFakeTimers()
    try {
      const { layout } = setup(40, 20)
      await nextTick()
      layout.onDragStart(layout.entries.value[0])

      layout.armPageFlip(1)
      vi.advanceTimersByTime(300)
      expect(layout.page.value).toBe(1)

      // Repeated dragover must not keep resetting the timer
      layout.armPageFlip(1)
      vi.advanceTimersByTime(400)
      expect(layout.page.value).toBe(2)

      // Dragging finished: an armed flip must not fire afterwards
      layout.armPageFlip(-1)
      layout.onDragEnd()
      vi.advanceTimersByTime(1000)
      expect(layout.page.value).toBe(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('does not merge tiles when layout is read-only', async () => {
    const list = ref(Array.from({ length: 4 }, (_, i) => fu(i + 1)))
    const size = ref(20)
    const canModifyLayout = ref(false)
    const layout = useLaunchpadLayout({
      list,
      visibleList: list,
      pageSize: size,
      defaultGroupName: () => 'New Group',
      canModifyLayout,
    })
    await nextTick()
    mergeInto(layout, 1, 'i:2')
    expect(layout.entries.value.every((e) => e.type === 'item')).toBe(true)
  })
})
