/**
 * The form designer's shared list of draggable components, and what can switch to what.
 *
 * `FcDesigner.addDragRule` appends to the same list that holds form-create's built-in
 * components, and every entry becomes a palette (and type-switch) item. Registering a
 * replacement for a built-in that way leaves both entries in place, so the component showed
 * up twice — once where the built-in sits and once at the end of the group.
 */
// @ts-ignore — the designer ships its source without type declarations
import designerDragRules from '@form-create/designer/src/config'

interface DragRuleLike {
  name: string
  menu?: string
  input?: boolean
}

const dragRules = designerDragRules as DragRuleLike[]

/**
 * Replaces a built-in component's drag rule in place (keeping its palette position), or adds
 * the rule when no built-in has that name. Must run before the designer mounts.
 */
export function overrideDragRule<T extends DragRuleLike>(rule: T): void {
  const index = dragRules.findIndex((existing) => existing.name === rule.name)
  if (index >= 0) {
    dragRules.splice(index, 1, rule)
  } else {
    dragRules.push(rule)
  }
}

/** Palette groups whose components hold a field value (as opposed to layout or containers). */
const FIELD_MENUS = new Set(['main', 'extend'])

/**
 * Every component that holds a field value, Basic and Extend alike, for the designer's
 * `switchType` config.
 *
 * Without that config the "Type" switcher offers only the selected component's own palette
 * group, so a Basic input could never become a Lookup, Owner or Advanced Upload. Containers
 * (sub-table, inline/link form) and notes are `input: false` and stay out: switching a field
 * into one of those would discard the field.
 */
export function fieldSwitchTypes(): string[] {
  return [
    ...new Set(
      dragRules
        .filter((rule) => rule.input === true && FIELD_MENUS.has(rule.menu ?? ''))
        .map((rule) => rule.name),
    ),
  ]
}
