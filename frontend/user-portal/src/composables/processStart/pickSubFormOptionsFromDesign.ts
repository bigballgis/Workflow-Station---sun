/**
 * Designer sub-form `options` (Form events: onChange / onCreated / …).
 * Must travel on New Request bindings the same way task-detail resolveSubFormDesign does.
 */
export function pickSubFormOptionsFromDesign(
  design: { options?: unknown } | null | undefined,
): Record<string, unknown> | undefined {
  const options = design?.options
  if (!options || typeof options !== 'object' || Array.isArray(options)) return undefined
  return options as Record<string, unknown>
}
