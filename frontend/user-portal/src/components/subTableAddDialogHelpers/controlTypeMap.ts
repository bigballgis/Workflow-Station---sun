import type { ColumnType, DialogColumn } from './types'

export const CONTROL_TYPE_MAP: Record<NonNullable<ColumnType> | 'text', string> = {
  text: 'ElInput',
  // 画布里文本控件的原始类型名，与 text 同义（FieldRenderer 也是这样处理的）。
  input: 'ElInput',
  // linkForm 不由本 map 渲染：useSubTableRowDialog 在进弹窗前就把这类列过滤掉了，
  // SubTableField 有专门的渲染分支。这里给 ElInput 只是保持 Record 完备，
  // 免得万一漏过滤时 resolveControlComponent 返回 undefined 炸在模板里。
  linkForm: 'ElInput',
  textarea: 'ElInput',
  number: 'ElInputNumber',
  select: 'ElSelect',
  radio: 'ElRadioGroup',
  checkbox: 'ElCheckboxGroup',
  switch: 'ElSwitch',
  date: 'ElDatePicker',
  datetime: 'ElDatePicker',
  upload: 'ElUpload',
  user: 'ElInput',
  department: 'ElInput',
  password: 'ElInput',
  timerange: 'ElTimePicker',
  treeselect: 'ElTreeSelect',
  colorPicker: 'ElColorPicker',
  rate: 'ElRate',
  slider: 'ElSlider',
  tree: 'ElTree',
  editor: 'ElInput',
  signature: 'ElInput',
  transfer: 'ElTransfer',
  cascader: 'ElCascader',
  lookup: 'LookupField',
  owner: 'ElInput',
}

export function resolveControlComponent(col: DialogColumn): string {
  return CONTROL_TYPE_MAP[col.type ?? 'text']
}
