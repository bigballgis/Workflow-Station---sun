import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useBusinessUnit } from '../modules/useBusinessUnit'

const mocks = vi.hoisted(() => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
  notifyConfirm: vi.fn(),
  orgStore: {
    businessUnitTree: [] as unknown[],
    fetchTree: vi.fn(),
    moveBusinessUnit: vi.fn(),
    deleteBusinessUnit: vi.fn()
  },
  organizationApi: {
    getById: vi.fn(),
    getMembers: vi.fn()
  },
  businessUnitApi: {
    getApprovers: vi.fn()
  }
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, params?: Record<string, string>) => (params ? `${key}:${JSON.stringify(params)}` : key) })
}))
vi.mock('@/utils/notify', () => ({
  notifySuccess: mocks.notifySuccess,
  notifyError: mocks.notifyError,
  notifyConfirm: mocks.notifyConfirm
}))
vi.mock('@/stores/organization', () => ({ useOrganizationStore: () => mocks.orgStore }))
vi.mock('@/api/organization', () => ({ organizationApi: mocks.organizationApi }))
vi.mock('@/api/businessUnit', () => ({ businessUnitApi: mocks.businessUnitApi }))

interface FakeNode {
  level: number
  data: { id: string; name: string; parentId?: string }
  parent?: FakeNode | null
  childNodes: FakeNode[]
}

/** 模拟 el-tree drop 之后的 Node 形状：dragging 已挪到 parent.childNodes[index] */
const node = (level: number, id: string, parentId?: string, parent: FakeNode | null = null): FakeNode => ({
  level,
  data: { id, name: id.toUpperCase(), parentId },
  parent,
  childNodes: []
})

describe('useBusinessUnit drag & drop', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.notifyConfirm.mockResolvedValue(undefined)
    mocks.orgStore.moveBusinessUnit.mockResolvedValue(undefined)
    mocks.orgStore.fetchTree.mockResolvedValue(undefined)
  })

  it('only nodes below the first level can be dragged', () => {
    const { allowDrag } = useBusinessUnit()
    expect(allowDrag(node(1, 'root') as never)).toBe(false)
    expect(allowDrag(node(2, 'child', 'root') as never)).toBe(true)
    expect(allowDrag(node(4, 'deep', 'x') as never)).toBe(true)
  })

  it('dropping inner is always allowed, but never before/after a first-level node', () => {
    const { allowDrop } = useBusinessUnit()
    const dragging = node(2, 'child', 'root')
    const root = node(1, 'root')
    const sibling = node(2, 'sib', 'root')
    expect(allowDrop(dragging as never, root as never, 'inner')).toBe(true)
    expect(allowDrop(dragging as never, root as never, 'prev')).toBe(false)
    expect(allowDrop(dragging as never, root as never, 'next')).toBe(false)
    expect(allowDrop(dragging as never, sibling as never, 'prev')).toBe(true)
    expect(allowDrop(dragging as never, sibling as never, 'next')).toBe(true)
  })

  it('inner drop re-parents under the drop node with the index among its children, after confirm', async () => {
    const { handleNodeDrop } = useBusinessUnit()
    const target = node(2, 'target', 'root')
    const existing = node(3, 'existing', 'target', target)
    const dragging = node(2, 'moving', 'root')
    const inserted = node(3, 'moving', 'root', target)
    target.childNodes = [existing, inserted]

    await handleNodeDrop(dragging as never, target as never, 'inner')

    expect(mocks.notifyConfirm).toHaveBeenCalledTimes(1)
    expect(mocks.notifyConfirm.mock.calls[0][0]).toContain('organization.moveConfirm')
    expect(mocks.notifyConfirm.mock.calls[0][0]).toContain('"target":"TARGET"')
    expect(mocks.orgStore.moveBusinessUnit).toHaveBeenCalledWith('moving', { newParentId: 'target', sortOrder: 1 })
    expect(mocks.notifySuccess).toHaveBeenCalled()
  })

  it('before/after drop uses the drop node parent and the new sibling index', async () => {
    const { handleNodeDrop } = useBusinessUnit()
    const parent = node(2, 'parent', 'root')
    const a = node(3, 'a', 'parent', parent)
    const inserted = node(3, 'moving', 'other', parent)
    const b = node(3, 'b', 'parent', parent)
    parent.childNodes = [a, inserted, b]
    const dragging = node(3, 'moving', 'other')

    await handleNodeDrop(dragging as never, b as never, 'before')

    expect(mocks.orgStore.moveBusinessUnit).toHaveBeenCalledWith('moving', { newParentId: 'parent', sortOrder: 1 })
  })

  it('reordering inside the same parent skips the confirm dialog', async () => {
    const { handleNodeDrop } = useBusinessUnit()
    const parent = node(2, 'parent', 'root')
    const a = node(3, 'a', 'parent', parent)
    const inserted = node(3, 'moving', 'parent', parent)
    parent.childNodes = [inserted, a]
    const dragging = node(3, 'moving', 'parent')

    await handleNodeDrop(dragging as never, a as never, 'before')

    expect(mocks.notifyConfirm).not.toHaveBeenCalled()
    expect(mocks.orgStore.moveBusinessUnit).toHaveBeenCalledWith('moving', { newParentId: 'parent', sortOrder: 0 })
  })

  it('cancelling the confirm reloads the tree instead of moving', async () => {
    mocks.notifyConfirm.mockRejectedValueOnce(new Error('cancel'))
    const { handleNodeDrop } = useBusinessUnit()
    const target = node(1, 'target')
    const inserted = node(2, 'moving', 'root', target)
    target.childNodes = [inserted]

    await handleNodeDrop(node(2, 'moving', 'root') as never, target as never, 'inner')

    expect(mocks.orgStore.moveBusinessUnit).not.toHaveBeenCalled()
    expect(mocks.orgStore.fetchTree).toHaveBeenCalledTimes(1)
  })

  it('a failed move reloads the tree to undo the local drop', async () => {
    mocks.orgStore.moveBusinessUnit.mockRejectedValueOnce(new Error('NAME_EXISTS'))
    const { handleNodeDrop } = useBusinessUnit()
    const target = node(1, 'target')
    const inserted = node(2, 'moving', 'root', target)
    target.childNodes = [inserted]

    await handleNodeDrop(node(2, 'moving', 'root') as never, target as never, 'inner')

    expect(mocks.orgStore.fetchTree).toHaveBeenCalledTimes(1)
    expect(mocks.notifySuccess).not.toHaveBeenCalled()
  })

  it('a drop with type none is ignored', async () => {
    const { handleNodeDrop } = useBusinessUnit()
    await handleNodeDrop(node(2, 'moving', 'root') as never, node(1, 'target') as never, 'none')
    expect(mocks.orgStore.moveBusinessUnit).not.toHaveBeenCalled()
    expect(mocks.orgStore.fetchTree).not.toHaveBeenCalled()
  })
})
