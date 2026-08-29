import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { buildViewsFormGroups } from '@/utils/formDesigner'

/**
 * `sceneCounts` is declared before `viewsFormGroups` but reads it. Vue computeds are lazy, so the
 * later `const` is initialised by the time anything evaluates the earlier one — but that is a
 * temporal-dead-zone hazard that only shows up at runtime, and no test mounts the sidebar. This
 * reproduces the exact declaration order to prove the read is safe, and that the DETAIL badge
 * equals the rows the groups actually render.
 */
describe('Views tab badge derived from the rendered groups', () => {
  it('reads a later-declared computed without hitting the temporal dead zone', () => {
    const forms = ref<any[]>([
      { id: 1, formName: 'Sub detail', formType: 'DETAIL', boundTableId: 2 },
      { id: 2, formName: 'Main detail', formType: 'DETAIL', boundTableId: 1 },
      { id: 3, formName: 'Step', formType: 'TASK', scene: 'TASK' },
      { id: 4, formName: 'Step (My Request)', formType: 'TASK', scene: 'REQUEST' },
    ])
    const tables = ref<any[]>([
      { id: 1, tableName: 'meeting', tableType: 'MAIN' },
      { id: 2, tableName: 'participants', tableType: 'SUB' },
    ])

    // Mirrors the component's declaration order exactly: counts first, groups after.
    const sceneCounts = computed(() => {
      const counts: Record<string, number> = { TASK: 0, REQUEST: 0, DETAIL: 0 }
      for (const f of forms.value) {
        const scene = f.formType === 'DETAIL' ? 'DETAIL' : (f.scene ?? 'TASK')
        if (scene !== 'DETAIL') counts[scene]++
      }
      counts.DETAIL = viewsFormGroups.value.reduce((t, g) => t + g.forms.length, 0)
      return counts
    })
    const viewsFormGroups = computed(() =>
      buildViewsFormGroups(forms.value, tables.value, []),
    )

    expect(() => sceneCounts.value).not.toThrow()
    // The MAIN-bound detail form is not rendered, so the badge must not count it.
    expect(sceneCounts.value.DETAIL).toBe(1)
    expect(sceneCounts.value.TASK).toBe(1)
    expect(sceneCounts.value.REQUEST).toBe(1)
  })

  it('keeps the badge equal to the rendered row count as data changes', () => {
    const forms = ref<any[]>([{ id: 1, formName: 'A', formType: 'DETAIL', boundTableId: 2 }])
    const tables = ref<any[]>([
      { id: 1, tableName: 'meeting', tableType: 'MAIN' },
      { id: 2, tableName: 'participants', tableType: 'SUB' },
    ])
    const groups = computed(() => buildViewsFormGroups(forms.value, tables.value, []))
    const badge = computed(() => groups.value.reduce((t, g) => t + g.forms.length, 0))

    expect(badge.value).toBe(1)
    forms.value = [...forms.value, { id: 2, formName: 'B', formType: 'DETAIL', boundTableId: 1 }]
    expect(badge.value).toBe(1) // MAIN-bound form still not rendered
    forms.value = [...forms.value, { id: 3, formName: 'C', formType: 'DETAIL', boundTableId: 2 }]
    expect(badge.value).toBe(2)
  })
})
