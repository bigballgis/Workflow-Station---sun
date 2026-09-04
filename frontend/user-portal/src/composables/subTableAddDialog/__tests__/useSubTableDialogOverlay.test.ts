import { describe, it, expect, vi, afterEach } from 'vitest'
import { nextTick, ref } from 'vue'

vi.mock('element-plus', () => {
  let n = 3000
  return {
    useZIndex: () => ({
      nextZIndex: () => {
        n += 1
        return n
      },
    }),
  }
})

const { useSubTableDialogOverlay, SUB_TABLE_DIALOG_POPPER_Z_VAR } = await import(
  '../useSubTableDialogOverlay'
)

function readPopperZ(): string {
  return document.documentElement.style.getPropertyValue(SUB_TABLE_DIALOG_POPPER_Z_VAR)
}

describe('useSubTableDialogOverlay', () => {
  afterEach(() => {
    document.documentElement.style.removeProperty(SUB_TABLE_DIALOG_POPPER_Z_VAR)
  })

  it('raises dialog above EP baseline and popper CSS var above the dialog', async () => {
    const visible = ref(false)
    const { dialogZIndex, backdropZIndex } = useSubTableDialogOverlay(visible)
    expect(dialogZIndex.value).toBe(2010)

    visible.value = true
    await nextTick()

    expect(dialogZIndex.value).toBeGreaterThan(2010)
    expect(backdropZIndex.value).toBe(dialogZIndex.value - 1)
    expect(Number(readPopperZ())).toBe(dialogZIndex.value + 50)

    visible.value = false
    await nextTick()
  })

  it('re-tickets on each open so a nested dialog stacks above its parent', async () => {
    const visible = ref(true)
    const first = useSubTableDialogOverlay(visible)
    await nextTick()
    const parentZ = first.dialogZIndex.value

    const nestedVisible = ref(true)
    const nested = useSubTableDialogOverlay(nestedVisible)
    await nextTick()
    expect(nested.dialogZIndex.value).toBeGreaterThan(parentZ)

    nestedVisible.value = false
    visible.value = false
    await nextTick()
  })

  it('clears the popper CSS var when the last overlay closes', async () => {
    const visible = ref(true)
    useSubTableDialogOverlay(visible)
    await nextTick()
    expect(readPopperZ()).not.toBe('')

    visible.value = false
    await nextTick()
    expect(readPopperZ()).toBe('')
  })

  it('restores the parent popper z when a nested overlay closes', async () => {
    const parentVisible = ref(true)
    const parent = useSubTableDialogOverlay(parentVisible)
    await nextTick()
    const parentPopperZ = parent.dialogZIndex.value + 50

    const nestedVisible = ref(true)
    const nested = useSubTableDialogOverlay(nestedVisible)
    await nextTick()
    expect(Number(readPopperZ())).toBe(nested.dialogZIndex.value + 50)

    nestedVisible.value = false
    await nextTick()
    expect(Number(readPopperZ())).toBe(parentPopperZ)

    parentVisible.value = false
    await nextTick()
  })
})
