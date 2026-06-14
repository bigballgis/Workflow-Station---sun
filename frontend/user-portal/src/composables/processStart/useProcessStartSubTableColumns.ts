import type { Ref } from 'vue'
import { isFormCreateRuleReadonly } from '@/components/formRendererHelpers'
import { isFormCreateRuleRequired } from '@/utils/formCreateValidateRules'
import {
  buildLookupColumnProps,
  enrichLookupColumnPropsFromSubFormRule,
  mergeListViewFieldColumn,
  parseLookupConfig,
  resolveSubListViewColumnsForBinding,
  type DialogColumn,
} from '@/components/subTableAddDialogHelpers'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'

export function isSyntheticLookupField(fieldName?: string): boolean {
  return !fieldName || String(fieldName).startsWith('lookup:')
}

export function isAssigneeLikeLabel(label?: string): boolean {
  const normalized = String(label || '').trim().toLowerCase()
  return /assignee|处理人|負責人|经办人|經辦人/.test(normalized)
}

/**
 * 创建子表绑定的展示列推导器。逻辑与原 useProcessStartFormParsing 内联实现逐行一致；
 * lookup / relation view 配置通过参数传入。
 */
export function createSubTableColumnDeriver(deps: {
  lookupDbConfigs: Ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>
  relationViewConfigs: Ref<Record<string, { viewFields: any[]; allFields: any[] }>>
}) {
  const { lookupDbConfigs, relationViewConfigs } = deps

  // Derive display columns for a sub-table binding based on table type
  const deriveColumnsFromBinding = (
    binding: any,
    subForms?: Record<string, any>,
    formConfig?: Record<string, any>,
  ): DialogColumn[] => {
    const subFormRule =
      binding.subFormConfig?.rule ||
      subForms?.[binding.bindingId]?.rule ||
      subForms?.[String(binding.bindingId)]?.rule

    const subFormColumns: DialogColumn[] =
      subFormRule && Array.isArray(subFormRule) && subFormRule.length > 0
        ? subFormRule.map((r: any): DialogColumn => {
          const rProps = r.props || {}
          let type: string | undefined

          if (r.type === 'input') {
            if (rProps.type === 'textarea') type = 'textarea'
            else if (rProps.type === 'password') type = 'password'
            else type = 'text'
          } else if (r.type === 'inputNumber') {
            type = 'number'
          } else if (r.type === 'select') {
            type = 'select'
          } else if (r.type === 'radio') {
            type = 'radio'
          } else if (r.type === 'switch') {
            type = 'switch'
          } else if (r.type === 'datePicker') {
            type = rProps.type === 'datetime' ? 'datetime' : 'date'
          } else if (r.type === 'timePicker') {
            type = rProps.isRange === true ? 'timerange' : 'time'
          } else if (r.type === 'treeSelect' || r.type === 'elTreeSelect') {
            type = 'treeselect'
          } else if (r.type === 'tree') {
            type = 'tree'
          } else if (r.type === 'upload') {
            type = 'upload'
          } else if (r.type === 'userSelect' || r.type === 'user') {
            type = 'user'
          } else if (r.type === 'departmentSelect' || r.type === 'department') {
            type = 'department'
          } else if (r.type === 'colorPicker') {
            type = 'colorPicker'
          } else if (r.type === 'rate') {
            type = 'rate'
          } else if (r.type === 'slider') {
            type = 'slider'
          } else if (r.type === 'editor') {
            type = 'editor'
          } else if (r.type === 'signature') {
            type = 'signature'
          } else if (r.type === 'transfer') {
            type = 'transfer'
          } else if (r.type === 'cascader') {
            type = 'cascader'
          } else if (r.type === 'lookup') {
            type = 'lookup'
          } else {
            type = r.type as any
          }

          const rawOptions = r.options || rProps.options
          const options = rawOptions
            ? (type === 'cascader' ? rawOptions : rawOptions.map((o: any) => ({ label: o.label ?? o.value, value: o.value })))
            : undefined

          const passProps: Record<string, any> = {}
          const propKeys = [
            'action', 'accept', 'multiple', 'precision', 'min', 'max', 'rows', 'maxlength', 'fileNameTargetField',
            'isRange', 'valueFormat', 'startPlaceholder', 'endPlaceholder', 'treeData', 'checkStrictly',
            'showAlpha', 'allowHalf', 'step', 'cascaderProps', 'leftTitle', 'rightTitle',
            'boundSubTableBindingId',
          ]
          for (const key of propKeys) {
            if (rProps[key] !== undefined) passProps[key] = rProps[key]
          }
          if (rProps.data !== undefined) passProps.treeData = rProps.data
          if (rProps.nodeKey !== undefined) passProps.nodeKey = rProps.nodeKey
          if (rProps.showCheckbox !== undefined) passProps.showCheckbox = rProps.showCheckbox
          if (rProps.props !== undefined) passProps.labelProps = rProps.props
          if (type === 'cascader' && rProps.props && !passProps.cascaderProps) passProps.cascaderProps = rProps.props

          if (type === 'lookup') {
            const dbCfg = lookupDbConfigs.value[r.field]
            const lookupCfg = parseLookupConfig(rProps.lookupConfig)
            const relationView = lookupCfg.bindingId ? relationViewConfigs.value[lookupCfg.bindingId] : undefined
            Object.assign(
              passProps,
              buildLookupColumnProps(rProps.lookupConfig || '{}', {
                dbCfg,
                relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
              }),
            )
            if (typeof rProps.selectedDisplayField === 'string' && rProps.selectedDisplayField.trim() !== '') {
              passProps.selectedDisplayField = rProps.selectedDisplayField.trim()
              passProps._lookupSelectedDisplayField = rProps.selectedDisplayField.trim()
            }
          }

          if (options) passProps.options = options

          const required = isFormCreateRuleRequired(r as Record<string, unknown>)
          const readonly = isFormCreateRuleReadonly(r)

          return {
            field: r.field,
            label: r.title || r.field,
            type: type as DialogColumn['type'],
            required,
            ...(readonly ? { readonly } : {}),
            ...(options ? { options } : {}),
            ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
          }
        })
        : []

    const config = formConfig || {}
    const listColumns = resolveSubListViewColumnsForBinding(
      config,
      binding.bindingId,
      subFormColumns.map(col => col.field),
    )

    if (Array.isArray(listColumns) && listColumns.length > 0) {
      const ruleByField = new Map(
        (Array.isArray(subFormRule) ? subFormRule : []).map((ruleItem: any) => [ruleItem?.field, ruleItem]),
      )
      const subFormColumnByField = new Map(subFormColumns.map(col => [col.field, col]))
      const assigneeField = resolveAssigneeFieldForBinding(
        subFormColumns as Array<{ field?: string }>,
        binding.tableDisplayName || binding.tableName,
      )
      return enrichLookupColumnPropsFromSubFormRule(
        listColumns.map((column: any): DialogColumn => {
        if (column.columnType === 'linkForm') {
          return {
            field: column.fieldName || `linkForm:${column.componentId || binding.bindingId}`,
            label: column.columnLabel || column.displayName || column.linkText || 'Link Form',
            type: 'linkForm' as DialogColumn['type'],
            minWidth: column.minWidth || 120,
            props: {
              linkText: column.linkText || 'Details',
              componentId: column.componentId,
              boundSubTableBindingId: column.boundSubTableBindingId,
              boundSubTableName: column.boundSubTableName,
            },
          }
        }
        if (column.columnType === 'lookup') {
          const label = column.columnLabel || column.displayName || 'Lookup'
          const field =
            isSyntheticLookupField(column.fieldName) && isAssigneeLikeLabel(label) && assigneeField
              ? assigneeField
              : (column.fieldName || `lookup:${binding.bindingId}`)
          const listLookupCfg = parseLookupConfig(column.lookupConfig || '{}')
          const relationView = listLookupCfg.bindingId
            ? relationViewConfigs.value[listLookupCfg.bindingId]
            : undefined
          return {
            field,
            label,
            type: 'lookup',
            minWidth: 260,
            props: buildLookupColumnProps(column.lookupConfig || '{}', {
              relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
            }),
          }
        }

        const fieldRule = ruleByField.get(column.fieldName)
        const baseColumn = subFormColumnByField.get(column.fieldName)
        if (fieldRule?.type === 'lookup' || fieldRule?.props?.lookupConfig || baseColumn?.type === 'lookup') {
          const rawCfg = fieldRule?.props?.lookupConfig || baseColumn?.props?.lookupConfig || '{}'
          const mergedLookupCfg = parseLookupConfig(rawCfg)
          const dbCfg = lookupDbConfigs.value[column.fieldName]
          const relationView = mergedLookupCfg.bindingId
            ? relationViewConfigs.value[mergedLookupCfg.bindingId]
            : undefined
          return {
            ...(baseColumn || {}),
            field: column.fieldName,
            label: column.displayName || column.columnLabel || baseColumn?.label || fieldRule?.title || column.fieldName,
            type: 'lookup',
            minWidth: column.minWidth || baseColumn?.minWidth || 260,
            placeholder: fieldRule?.props?.placeholder || baseColumn?.placeholder,
            props: buildLookupColumnProps(rawCfg, {
              dbCfg,
              relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
            }),
          }
        }

        return mergeListViewFieldColumn(column, baseColumn, fieldRule)
      }),
        subFormRule,
      )
    }

    return enrichLookupColumnPropsFromSubFormRule(subFormColumns, subFormRule)
  }

  return { deriveColumnsFromBinding }
}
