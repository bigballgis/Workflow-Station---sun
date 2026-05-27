import { isFormCreateRuleReadonly } from './formCreateRuleUtils'

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

function applyReadonlyToParserCtx(ctx: FormCreateParserCtx): void {
  if (!isFormCreateRuleReadonly(ctx.rule)) return

  ctx.rule.disabled = true
  const props = ctx.prop.props ?? (ctx.prop.props = {})
  props.disabled = true
  delete props.readonly
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
