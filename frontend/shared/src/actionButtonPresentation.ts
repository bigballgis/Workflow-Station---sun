/**
 * Cross-app Action button presentation contract.
 *
 * Action Design writes a concrete `#RRGGBB` whenever Action Type changes.
 * Empty values are still resolved semantically for historical rows that predate
 * that rule.
 *
 * Historical rows may contain an Element Plus button type name. Those values
 * remain readable, but new designer writes use hex colours only.
 */

export type ActionButtonType = 'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined

const HEX_PATTERN = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$/

const NAMED_BUTTON_TYPES: Record<string, Exclude<ActionButtonType, undefined>> = {
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  danger: 'danger',
  info: 'info',
}

const DEFAULT_ACTION_BUTTON_TYPES: Record<string, ActionButtonType> = {
  APPROVE: 'success',
  REJECT: 'danger',
  PROCESS_REJECT: 'danger',
  URGE: 'warning',
  ROLLBACK: 'warning',
  WITHDRAW: 'warning',
  DRAFT: 'info',
  TRANSFER: undefined,
  DELEGATE: undefined,
  PROCESS_SUBMIT: 'primary',
  SAVE: 'primary',
  API_CALL: 'info',
}

const BUTTON_TYPE_HEX: Record<Exclude<ActionButtonType, undefined>, string> = {
  primary: '#DB0011',
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  info: '#909399',
}

/** Only valid CSS hex colours are treated as explicit designer overrides. */
export function isCustomButtonColor(color?: string | null): boolean {
  return HEX_PATTERN.test(String(color || '').trim())
}

/** Resolve the Element Plus semantic type before applying an optional hex override. */
export function resolveActionButtonType(
  actionType?: string | null,
  buttonColor?: string | null,
): ActionButtonType {
  const normalizedColor = String(buttonColor || '').trim().toLowerCase()
  if (isCustomButtonColor(normalizedColor)) return 'primary'
  if (NAMED_BUTTON_TYPES[normalizedColor]) return NAMED_BUTTON_TYPES[normalizedColor]

  const normalizedActionType = String(actionType || '').trim().toUpperCase()
  return Object.prototype.hasOwnProperty.call(DEFAULT_ACTION_BUTTON_TYPES, normalizedActionType)
    ? DEFAULT_ACTION_BUTTON_TYPES[normalizedActionType]
    : 'primary'
}

/** Concrete value written into Color after the designer selects an Action Type. */
export function defaultActionButtonColor(actionType?: string | null): string {
  const type = resolveActionButtonType(actionType)
  return type ? BUTTON_TYPE_HEX[type] : BUTTON_TYPE_HEX.info
}

function toRgb(color: string): [number, number, number] {
  let hex = color.trim().slice(1)
  if (hex.length === 3) hex = hex.split('').map(channel => channel + channel).join('')
  return [
    parseInt(hex.slice(0, 2), 16),
    parseInt(hex.slice(2, 4), 16),
    parseInt(hex.slice(4, 6), 16),
  ]
}

function toHex(rgb: [number, number, number]): string {
  return '#' + rgb
    .map(value => Math.round(Math.min(255, Math.max(0, value))).toString(16).padStart(2, '0'))
    .join('')
}

function tint(color: string, ratio: number): string {
  const rgb = toRgb(color)
  return toHex(rgb.map(value => value + (255 - value) * ratio) as [number, number, number])
}

function shade(color: string, ratio: number): string {
  const rgb = toRgb(color)
  return toHex(rgb.map(value => value * (1 - ratio)) as [number, number, number])
}

function luminance(color: string): number {
  const [red, green, blue] = toRgb(color).map(value => {
    const channel = value / 255
    return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

/** Generate the CSS variables needed for exact custom-colour rendering. */
export function actionButtonStyle(color?: string | null): Record<string, string> | undefined {
  if (!isCustomButtonColor(color)) return undefined
  const base = String(color).trim()
  const text = luminance(base) > 0.6 ? '#303133' : '#ffffff'
  return {
    '--el-button-bg-color': base,
    '--el-button-border-color': base,
    '--el-button-text-color': text,
    '--el-button-hover-bg-color': tint(base, 0.3),
    '--el-button-hover-border-color': tint(base, 0.3),
    '--el-button-hover-text-color': text,
    '--el-button-active-bg-color': shade(base, 0.1),
    '--el-button-active-border-color': shade(base, 0.1),
    '--el-button-active-text-color': text,
    '--el-button-disabled-bg-color': tint(base, 0.5),
    '--el-button-disabled-border-color': tint(base, 0.5),
    '--el-button-disabled-text-color': text,
  }
}
