import { computed, ref, h } from 'vue'
import { ElMessageBox } from 'element-plus'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'

export interface DirtyDesigner {
  hasUnsavedChanges: () => boolean | Promise<boolean>
  saveChanges: () => Promise<boolean>
  discardChanges: () => boolean | void | Promise<boolean | void>
}

/** One confirmation policy for shell navigation and designer-internal navigation. */
export async function confirmDesignerLeave(
  designer: DirtyDesigner | null,
  t: (key: string) => string,
): Promise<boolean> {
  if (!designer || !(await designer.hasUnsavedChanges())) return true
  let action: unknown
  try {
    await ElMessageBox.confirm(h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
      h('span', t('common.unsavedChanges')),
      h(DesignerHelpLink, { path: '/fu-documents#unsaved', ariaLabel: t('common.unsavedGuide') }),
    ]), t('functionUnit.documents.unsavedTitle'), {
      type: 'warning',
      confirmButtonText: t('common.save'),
      cancelButtonText: t('functionUnit.documents.discard'),
      distinguishCancelAndClose: true,
    })
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') throw reason
    action = reason
  }
  if (action === 'cancel') {
    return (await designer.discardChanges()) !== false
  }
  if (action !== undefined) return false
  return designer.saveChanges()
}

/** JSON-compatible editable state only; callers establish the baseline after hydration. */
export function useDesignerDraft(options: {
  snapshot: () => string
  enabled?: () => boolean
  save: () => Promise<boolean>
  restore: (snapshot: string) => void | Promise<void>
}) {
  const baseline = ref<string | null>(null)
  const saving = ref(false)
  const isDirty = computed(() => baseline.value !== null
    && (options.enabled?.() ?? true)
    && options.snapshot() !== baseline.value)

  let baselineRevision = 0
  function markSaved(snapshot = options.snapshot()) {
    baselineRevision += 1
    baseline.value = snapshot
  }

  async function saveChanges(force = false): Promise<boolean> {
    if (saving.value) return false
    if (!force && !isDirty.value) return true
    const submitted = options.snapshot()
    const revision = baselineRevision
    saving.value = true
    try {
      if (!(await options.save())) return false
      if (revision === baselineRevision) markSaved(submitted)
      return !isDirty.value
    } finally {
      saving.value = false
    }
  }

  async function discardChanges() {
    if (saving.value) return false
    if (baseline.value !== null) await options.restore(baseline.value)
  }

  return { isDirty, saving, markSaved, hasUnsavedChanges: () => saving.value || isDirty.value, saveChanges, discardChanges }
}

/** Independent persistence boundaries retain their own baselines after a partial save. */
export function combineDesignerDrafts(designers: DirtyDesigner[]): DirtyDesigner {
  return {
    hasUnsavedChanges: async () => {
      for (const designer of designers) if (await designer.hasUnsavedChanges()) return true
      return false
    },
    saveChanges: async () => {
      for (const designer of designers) {
        if (await designer.hasUnsavedChanges() && !(await designer.saveChanges())) return false
      }
      return true
    },
    discardChanges: async () => {
      for (const designer of designers) if (await designer.discardChanges() === false) return false
      return true
    },
  }
}

/** Forward nested editors without retaining a stale component instance after a switch. */
export function delegateDesignerDraft(getDesigner: () => DirtyDesigner | null | undefined): DirtyDesigner {
  return {
    hasUnsavedChanges: () => getDesigner()?.hasUnsavedChanges() ?? false,
    saveChanges: async () => { const designer = getDesigner(); return designer ? designer.saveChanges() : true },
    discardChanges: async () => getDesigner()?.discardChanges(),
  }
}
