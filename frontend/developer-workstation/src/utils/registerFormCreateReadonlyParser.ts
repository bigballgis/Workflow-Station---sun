import {
  isFormCreateRuleExplicitlyEditable,
  isFormCreateRuleReadonly,
} from './formCreateRuleUtils'

/** form-create component types that honor props.disabled (built-in + custom). */
const READONLY_PARSER_TYPES = [
  'input',
  'textarea',
  'password',
  'select',
  'radio',
  'checkbox',
  'switch',
  'inputNumber',
  'datePicker',
  'timePicker',
  'upload',
  'rate',
  'slider',
  'cascader',
  'transfer',
  'tree',
  'treeSelect',
  'elTreeSelect',
  'elTransfer',
  'elCascader',
  'colorPicker',
  'editor',
  'signature',
  'lookup',
  'fcTitle',
  'html',
  'text',
  'group',
  'subForm',
  'tableForm',
  'fcTable',
  'fcRow',
  'fcEditor',
  'fcSignature',
] as const

type FormCreateParserCtx = {
  rule: Record<string, unknown>
  prop: { props?: Record<string, unknown> }
}

// form-create calls mergeProp during every rule normalization pass. These helpers MUST be
// idempotent: reassigning ctx.rule / ctx.rule.props (or writing an unchanged value onto a
// reactive rule) re-triggers form-create's watcher → re-normalize → mergeProp → infinite
// render loop that synchronously locks the main thread (Form Preview spinner never clears).
// So only write when the value actually changes, and mutate the existing props object in place.

function clearStaleReadonlyDisabled(ctx: FormCreateParserCtx): void {
  if (ctx.rule.disabled !== undefined) delete ctx.rule.disabled
  if (ctx.rule.readonly !== false) ctx.rule.readonly = false
  const ruleProps = (ctx.rule.props as Record<string, unknown> | undefined) ?? (ctx.rule.props = {})
  if ((ruleProps as Record<string, unknown>).disabled !== undefined) delete (ruleProps as Record<string, unknown>).disabled
  if ((ruleProps as Record<string, unknown>).readonly !== false) (ruleProps as Record<string, unknown>).readonly = false

  const props = ctx.prop.props ?? (ctx.prop.props = {})
  if (props.disabled !== undefined) delete props.disabled
  if (props.readonly !== false) props.readonly = false
}

function isPanelReadonlyExplicitlyOff(ctx: FormCreateParserCtx): boolean {
  return ctx.prop.props?.readonly === false
}

function applyReadonlyToParserCtx(ctx: FormCreateParserCtx): void {
  if (isFormCreateRuleExplicitlyEditable(ctx.rule) || isPanelReadonlyExplicitlyOff(ctx)) {
    clearStaleReadonlyDisabled(ctx)
    return
  }
  if (!isFormCreateRuleReadonly(ctx.rule)) return

  if (ctx.rule.disabled !== true) ctx.rule.disabled = true
  const props = ctx.prop.props ?? (ctx.prop.props = {})
  if (props.disabled !== true) props.disabled = true
  if (props.readonly !== undefined) delete props.readonly
}

const readonlyParserConfig = {
  merge: true as const,
  mergeProp(ctx: FormCreateParserCtx) {
    applyReadonlyToParserCtx(ctx)
  },
}

type FormCreateWithParser = {
  parser: (name: string, config: typeof readonlyParserConfig) => void
}

/** Map designer props.readonly → disabled for every form-create surface (incl. fc-designer preview). */
export function registerFormCreateReadonlyParser(formCreate: FormCreateWithParser): void {
  if (typeof formCreate?.parser !== 'function') return

  for (const type of READONLY_PARSER_TYPES) {
    try {
      formCreate.parser(type, readonlyParserConfig)
    } catch {
      // Skip aliases / custom components that cannot merge with built-in parsers.
    }
  }
}
