import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import type { ListColumnFilter, ListColumnMeta } from '@platform-shared/list/columnMeta'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

function textColumn(overrides: Partial<ListColumnMeta> = {}): ListColumnMeta {
  return {
    field: 'title',
    label: 'Title',
    kind: 'TEXT',
    filterable: true,
    sortable: true,
    groupable: false,
    operators: ['contains', 'eq', 'isNull', 'between'],
    ...overrides,
  }
}

let wrapper: VueWrapper | null = null

async function mountDialog(column: ListColumnMeta | null, filter: ListColumnFilter | null) {
  wrapper = mount(ListFilterDialog, {
    props: { visible: true, column, filter },
    global: { plugins: [ElementPlus] },
    attachTo: document.body,
  })
  // el-dialog renders its (appended-to-body) content asynchronously.
  await nextTick()
  await nextTick()
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

function bodyText(): string {
  return document.body.textContent ?? ''
}

function confirmButton(): HTMLButtonElement {
  const btn = [...document.querySelectorAll('button')].find((b) =>
    (b.textContent ?? '').includes('sharedList.confirm'),
  )
  if (!btn) throw new Error('confirm button not rendered')
  return btn
}

describe('shared ListFilterDialog', () => {
  it('seeds the draft from the current filter and applies it back unchanged', async () => {
    const w = await mountDialog(textColumn(), { operator: 'eq', value: 'hello' })
    const valueInput = document.querySelector<HTMLInputElement>('.list-filter-value input')
    expect(valueInput?.value).toBe('hello')

    confirmButton().click()
    await w.vm.$nextTick()
    expect(w.emitted('apply')).toEqual([[{ operator: 'eq', value: 'hello' }]])
  })

  it('valueless operators hide the value input and apply with an empty value', async () => {
    const w = await mountDialog(textColumn(), { operator: 'isNull', value: 'stale' })
    expect(document.querySelector('.list-filter-value')).toBeNull()
    expect(confirmButton().disabled).toBe(false)

    confirmButton().click()
    await w.vm.$nextTick()
    expect(w.emitted('apply')).toEqual([[{ operator: 'isNull', value: '' }]])
  })

  it('between requires both bounds before apply is enabled and emits value2', async () => {
    const w = await mountDialog(textColumn({ kind: 'NUMBER' }), {
      operator: 'between',
      value: '10',
      value2: '',
    })
    expect(confirmButton().disabled).toBe(true)

    const upper = document.querySelector<HTMLInputElement>('.list-filter-value2 input')
    if (!upper) throw new Error('range upper-bound input not rendered')
    upper.value = '90'
    upper.dispatchEvent(new Event('input'))
    await w.vm.$nextTick()

    expect(confirmButton().disabled).toBe(false)
    confirmButton().click()
    await w.vm.$nextTick()
    expect(w.emitted('apply')).toEqual([[{ operator: 'between', value: '10', value2: '90' }]])
  })

  it('columns with options render a closed value select instead of free text', async () => {
    await mountDialog(
      textColumn({
        kind: 'ENUM',
        operators: ['eq', 'ne'],
        options: [
          { value: 'OPEN', label: 'Open' },
          { value: 'DONE', label: 'Done' },
        ],
      }),
      null,
    )
    const select = document.querySelector('.list-filter-value')
    expect(select?.classList.contains('el-select')).toBe(true)
    expect(document.querySelector('.list-filter-value input[type="text"]')).toBeTruthy()
  })

  it('a stored operator no longer in the whitelist falls back to the first declared operator', async () => {
    await mountDialog(textColumn({ operators: ['contains', 'eq'] }), {
      operator: 'regexMatch',
      value: 'x',
    })
    expect(bodyText()).toContain('sharedList.opContains')
  })

  it('opening without an operator whitelist throws instead of rendering an empty dialog', async () => {
    await expect(mountDialog(textColumn({ operators: [] }), null)).rejects.toThrow(
      /operator whitelist/,
    )
  })
})
