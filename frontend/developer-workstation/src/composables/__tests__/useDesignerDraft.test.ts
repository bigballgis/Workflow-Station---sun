import { describe, expect, it, vi, beforeEach } from 'vitest'
import { reactive } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useDesignerDraft, combineDesignerDrafts, confirmDesignerLeave } from '../useDesignerDraft'

vi.mock('element-plus', () => ({ ElMessageBox: { confirm: vi.fn() } }))
const t = (key: string) => key

function setup() {
  const state = reactive({ name: 'Original', config: { roles: ['a'] } })
  const persist = vi.fn().mockResolvedValue(true)
  const draft = useDesignerDraft({
    snapshot: () => JSON.stringify(state), save: persist,
    restore: (snapshot) => Object.assign(state, JSON.parse(snapshot)),
  })
  draft.markSaved()
  return { state, persist, draft }
}

beforeEach(() => { vi.clearAllMocks() })

describe('designer draft navigation', () => {
  it('still validates an explicit Save on a pristine form without saving on ordinary navigation', async () => {
    const { persist, draft } = setup()
    expect(await draft.saveChanges()).toBe(true)
    expect(persist).not.toHaveBeenCalled()
    persist.mockResolvedValueOnce(false)
    expect(await draft.saveChanges(true)).toBe(false)
    expect(persist).toHaveBeenCalledTimes(1)
  })

  it('does not prompt for hydration, or an edit reverted to its original value', async () => {
    const { state, draft } = setup()
    state.name = 'Changed'
    expect(draft.hasUnsavedChanges()).toBe(true)
    state.name = 'Original'
    expect(await confirmDesignerLeave(draft, t)).toBe(true)
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('restores nested drafts on discard and never saves', async () => {
    const { state, persist, draft } = setup()
    state.config.roles.push('b')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    expect(await confirmDesignerLeave(draft, t)).toBe(true)
    expect(state.config.roles).toEqual(['a'])
    expect(persist).not.toHaveBeenCalled()
    expect(draft.isDirty.value).toBe(false)
  })

  it('keeps the draft when the confirmation is closed or saving fails', async () => {
    const { state, persist, draft } = setup()
    state.name = 'Changed'
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('close')
    expect(await confirmDesignerLeave(draft, t)).toBe(false)
    expect(persist).not.toHaveBeenCalled()
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
    persist.mockResolvedValueOnce(false)
    expect(await confirmDesignerLeave(draft, t)).toBe(false)
    expect(state.name).toBe('Changed')
    expect(draft.isDirty.value).toBe(true)
  })

  it('clears only the successfully saved section after a partial save', async () => {
    const action = setup(); const binding = setup()
    action.state.name = 'Action changed'; binding.state.name = 'Binding changed'
    binding.persist.mockResolvedValueOnce(false)
    const combined = combineDesignerDrafts([action.draft, binding.draft])
    expect(await combined.saveChanges()).toBe(false)
    expect(action.draft.isDirty.value).toBe(false)
    expect(binding.draft.isDirty.value).toBe(true)
    expect(await combined.saveChanges()).toBe(true)
    expect(action.persist).toHaveBeenCalledTimes(1)
    expect(binding.persist).toHaveBeenCalledTimes(2)
  })

  it('does not mark edits made during a save as persisted, or submit twice', async () => {
    const { state, persist, draft } = setup()
    let complete!: (success: boolean) => void
    persist.mockImplementationOnce(() => new Promise<boolean>(resolve => { complete = resolve }))
    state.name = 'Submitted'
    const save = draft.saveChanges()
    state.name = 'Newer edit'
    expect(await draft.saveChanges()).toBe(false)
    complete(true)
    expect(await save).toBe(false)
    expect(draft.isDirty.value).toBe(true)
    await draft.discardChanges()
    expect(state.name).toBe('Submitted')
    expect(persist).toHaveBeenCalledTimes(1)
  })

  it('blocks discard while a save is in flight, including after a callback updates its baseline', async () => {
    const { state, persist, draft } = setup()
    let complete!: (success: boolean) => void
    persist.mockImplementationOnce(() => new Promise<boolean>(resolve => { complete = resolve }))
    state.name = 'Submitted'
    const save = draft.saveChanges()
    draft.markSaved()
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    expect(await confirmDesignerLeave(combineDesignerDrafts([draft]), t)).toBe(false)
    expect(state.name).toBe('Submitted')
    complete(true)
    expect(await save).toBe(true)
  })
})
