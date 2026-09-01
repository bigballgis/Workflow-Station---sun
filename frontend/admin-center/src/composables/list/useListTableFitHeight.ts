import { nextTick, onBeforeUnmount, onMounted, ref, watch, type MaybeRefOrGetter, type Ref, toValue } from 'vue'

const PAGINATION_FALLBACK_PX = 48
const H_SCROLLBAR_PX = 12

/**
 * Size el-table to its rows, capped at leftover viewport under the filters.
 * The table card must not grow with the window, or pagination sits far below
 * the last row with a blank gap in between.
 */
export function useListTableFitHeight(
  cardRef: Ref<HTMLElement | null>,
  scrollRef: Ref<HTMLElement | null>,
  rowCount: MaybeRefOrGetter<number>,
) {
  const tableHeight = ref<number | undefined>()
  let observer: ResizeObserver | null = null
  let miss = 0

  function measure(): void {
    const card = cardRef.value
    const scroll = scrollRef.value
    if (!card || !scroll) return
    const available = leftoverForTable(card)
    if (available <= 0) return
    if (toValue(rowCount) <= 0) {
      tableHeight.value = available
      return
    }
    const natural = contentHeight(scroll)
    if (natural <= 0) {
      if (miss++ < 12) requestAnimationFrame(measure)
      return
    }
    miss = 0
    tableHeight.value = Math.min(natural, available)
  }

  function observe(el: HTMLElement | null): void {
    observer?.disconnect()
    if (!observer || !el) return
    observer.observe(el)
    const page = el.closest('.page-container')
    if (page instanceof HTMLElement) observer.observe(page)
  }

  onMounted(() => {
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(() => measure())
      observe(cardRef.value)
    }
    requestAnimationFrame(measure)
  })

  watch(cardRef, (el) => observe(el))
  watch([scrollRef, () => toValue(rowCount)], async () => {
    await nextTick()
    requestAnimationFrame(measure)
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
  })

  return { tableHeight }
}

export function leftoverForTable(card: HTMLElement): number {
  const page = card.closest('.page-container')
  if (!(page instanceof HTMLElement)) {
    return Math.max(0, card.clientHeight - paginationReserve(card))
  }
  let used = 0
  for (const child of page.children) {
    if (child === card || !(child instanceof HTMLElement)) continue
    if (!occupiesPageFlow(child)) continue
    used += outerHeight(child)
  }
  return Math.max(0, page.clientHeight - used - paginationReserve(card))
}

export function paginationReserve(card: HTMLElement): number {
  const pag = card.querySelector('.list-pagination')
  if (!(pag instanceof HTMLElement)) return PAGINATION_FALLBACK_PX
  return pag.offsetHeight + Number.parseFloat(getComputedStyle(pag).marginTop || '0')
}

export function contentHeight(scroll: HTMLElement): number {
  const header = scroll.querySelector('.el-table__header-wrapper')
  const wrap = scroll.querySelector('.el-table__body-wrapper')
  if (!(header instanceof HTMLElement) || !(wrap instanceof HTMLElement)) return 0
  const rows = wrap.querySelectorAll('tr.el-table__row')
  let bodyH = 0
  if (rows.length > 0) {
    rows.forEach((row) => {
      if (row instanceof HTMLElement) bodyH += row.offsetHeight
    })
  } else {
    const body = wrap.querySelector('.el-table__body')
    if (!(body instanceof HTMLElement)) return 0
    bodyH = body.offsetHeight
  }
  if (bodyH <= 0) return 0
  let height = header.offsetHeight + bodyH
  if (wrap.scrollWidth > wrap.clientWidth + 1) height += H_SCROLLBAR_PX
  return Math.ceil(height)
}

function occupiesPageFlow(el: HTMLElement): boolean {
  const style = getComputedStyle(el)
  if (style.display === 'none') return false
  if (style.position === 'absolute' || style.position === 'fixed') return false
  return el.offsetHeight > 0
}

function outerHeight(el: HTMLElement): number {
  const style = getComputedStyle(el)
  return el.offsetHeight
    + Number.parseFloat(style.marginTop || '0')
    + Number.parseFloat(style.marginBottom || '0')
}
