import { h, type VNode } from 'vue'
import ReferenceNotice from '@/components/ReferenceNotice.vue'
import { hasReferences, type ReferenceNotice as Notice } from '@/types/referenceNotice'

/**
 * A delete / move confirmation that also lists where the business unit or role is used (§8.3, notice only):
 * the plain message when nothing references it.
 */
export function confirmMessageWithReferences(message: string, notice: Notice | null): string | VNode {
  if (!hasReferences(notice)) return message
  return h('div', [h('p', { style: 'margin: 0 0 12px' }, message), h(ReferenceNotice, { notice })])
}
