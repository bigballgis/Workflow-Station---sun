import type { BindingType } from '@/api/functionUnit'

/**
 * Composable providing label/type mapping functions for form designer.
 * Encapsulates i18n-aware label lookups for form types, node types, table types, and binding types.
 */
export function useFormLabels(t: (key: string) => string) {
  const formTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      PROCESS: t('form.processForm'),
      TASK: t('form.taskForm'),
      ACTION: t('form.actionForm')
    }
    return map[type] || type
  }

  const nodeTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      userTask: t('form.nodeTypeUserTask'),
      serviceTask: t('form.nodeTypeServiceTask'),
      startEvent: t('form.nodeTypeStartEvent'),
      endEvent: t('form.nodeTypeEndEvent')
    }
    return map[type] || type
  }

  const tableTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      MAIN: t('table.mainTable'),
      SUB: t('table.subTable'),
      ACTION: t('table.actionTable'),
      RELATION: t('table.relationTable')
    }
    return map[type] || type
  }

  const bindingTypeLabel = (type: BindingType): string => {
    const map: Record<BindingType, string> = {
      PRIMARY: t('form.bindingTypePrimary'),
      SUB: t('form.bindingTypeSub'),
      RELATED: t('form.bindingTypeRelated')
    }
    return map[type] || type
  }

  const bindingTypeTag = (type: BindingType): 'primary' | 'success' | 'warning' | 'info' => {
    const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info'> = {
      PRIMARY: 'primary',
      SUB: 'success',
      RELATED: 'warning'
    }
    return map[type] || 'info'
  }

  const getFormComponentType = (dataType: string): string => {
    const typeMap: Record<string, string> = {
      'VARCHAR': t('form.inputBox'),
      'TEXT': t('form.textArea'),
      'INTEGER': t('form.numberInput'),
      'BIGINT': t('form.numberInput'),
      'DECIMAL': t('form.numberInput'),
      'BOOLEAN': t('form.switch'),
      'DATE': t('form.datePicker'),
      'TIMESTAMP': t('form.dateTimePicker'),
      'FILE': t('form.fileUpload')
    }
    return typeMap[dataType] || t('form.inputBox')
  }

  return {
    formTypeLabel,
    nodeTypeLabel,
    tableTypeLabel,
    bindingTypeLabel,
    bindingTypeTag,
    getFormComponentType,
  }
}
