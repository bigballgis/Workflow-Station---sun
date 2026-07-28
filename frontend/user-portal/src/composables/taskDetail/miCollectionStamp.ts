import type { TaskDetailCtx } from './context'

/**
 * Record the BPMN's verdict on MI membership onto sub-table bindings.
 *
 * <p>{@code isMiDashboardSubTableBinding} can only guess from column names, so a Function Unit
 * copied from the MI meeting demo — its sub-table still carrying {@code assignee_user_id} long
 * after the multi-instance sub-process is gone — gets classified as an MI dashboard and then loses
 * every row that lacks the sub-table PK to the MI ghost-row filter. Task detail has already parsed
 * the diagram, so here it is a fact rather than a guess.
 *
 * <p>Only ever stamps {@code false}, and only when the diagram was actually parsed:
 * <ul>
 *   <li>no BPMN → unknown, leave the heuristic alone (a missing diagram must not un-MI a real MI FU);</li>
 *   <li>BPMN has an MI sub-process → the heuristic still decides <em>which</em> binding is the
 *       collection, so stamping {@code true} here would wrongly flip bindings it currently rejects.</li>
 * </ul>
 *
 * <p>Deliberately shared by every place that builds bindings (FU loader, previous forms, node form
 * map) — the same condition copy-pasted three times is exactly how the two paths drift apart.
 */
export function stampMiCollectionFromBpmn(
  ctx: TaskDetailCtx,
  bindings: Array<{ miCollection?: boolean | null }> | undefined | null,
): void {
  if (!Array.isArray(bindings) || bindings.length === 0) return
  if (!ctx.bpmn.bpmnXml.value) return
  if (ctx.miSubProcessScope.value) return
  for (const b of bindings) {
    b.miCollection = false
  }
}
