import type { FormField } from './formRendererTypes'

export const AUXILIARY_LAYOUT_TYPE_MAP: Record<string, FormField['type']> = {
  fcTitle: 'title',
  title: 'title',
  text: 'staticText',
  html: 'html',
  divider: 'divider',
  elDivider: 'divider',
  alert: 'alert',
  fcAlert: 'alert',
  tag: 'tag',
  elTag: 'tag',
  button: 'button',
  fcButton: 'button',
  space: 'space',
  fcSpace: 'space',
  image: 'image',
  fcImage: 'image',
}
