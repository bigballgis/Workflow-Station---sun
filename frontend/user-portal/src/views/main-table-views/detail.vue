<template>
  <div class="view-detail-page">
    <div class="page-header">
      <el-button
        link
        type="primary"
        @click="goBack"
      >
        <el-icon><ArrowLeft /></el-icon>
        {{ t('common.back') }}
      </el-button>
      <h1>{{ viewName || t('mainTableView.detailTitle') }}</h1>
    </div>

    <div class="portal-card">
      <div
        v-if="loading"
        class="detail-loading"
      >
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('common.loading') }}</span>
      </div>

      <el-empty
        v-else-if="!detailFormId"
        :description="t('mainTableView.noDetailForm')"
      />

      <el-empty
        v-else-if="!rowFound"
        :description="t('mainTableView.rowNotFound')"
      />

      <!-- Read-only by design: this page shows what a record holds, it is not a
           place to change it. -->
      <FormRenderer
        v-else
        :fields="formFields"
        :model-value="rowValues"
        :readonly="true"
        :primary-read-only="true"
        :sub-table-bindings="subTableBindings"
        :linked-sub-table-bindings="subTableBindings"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import FormRenderer from '@/components/FormRenderer.vue'
import type { FormField } from '@/components/formRendererHelpers/formRendererTypes'
import type { SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'
import { buildViewDetailSubTableBindings, toViewDetailFields } from '@/composables/mainTableView/viewDetailForm'
import { mainTableViewApi } from '@/api/mainTableView'
import { processApi } from '@/api/process'
import { relationTableApi } from '@/api/relationTable'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const rowFound = ref(false)
const viewName = ref('')
const detailFormId = ref<number | null>(null)
const formFields = ref<FormField[]>([])
const subTableBindings = ref<SubTableBinding[]>([])
const rowValues = ref<Record<string, any>>({})
const lookupDbConfigs = ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>({})

const functionUnitCode = computed(() => String(route.params.functionUnitCode || ''))
const viewId = computed(() => Number(route.query.viewId) || null)
const rowKey = computed(() => String(route.query.rowKey || ''))

function goBack() {
  router.push(`/views/${functionUnitCode.value}`)
}

async function load() {
  loading.value = true
  rowFound.value = false
  formFields.value = []
  subTableBindings.value = []
  try {
    if (!viewId.value || !functionUnitCode.value) return

    const viewsRes = await mainTableViewApi.listViews(functionUnitCode.value)
    const view = (viewsRes.data || []).find(v => v.id === viewId.value)
    viewName.value = view?.viewName || ''
    detailFormId.value = view?.detailFormId ?? null
    if (!detailFormId.value) return

    // The row is fetched through the view's own data endpoint so that the view's
    // access rules and column projection apply here exactly as they do in the list.
    const dataRes: any = await mainTableViewApi.queryData(viewId.value, {
      page: 0,
      size: 50,
      search: rowKey.value,
    })
    const rows: any[] = dataRes.data?.rows || dataRes.data?.records || []
    const row = rows.find(r => matchesRowKey(r)) || null
    if (!row) return
    rowValues.value = row.values || {}

    const [contentRes, lookupConfigsRes]: [any, any] = await Promise.all([
      processApi.getFunctionUnitContent(functionUnitCode.value).catch(() => null),
      relationTableApi.getLookupConfigs(detailFormId.value).catch(() => null),
    ])
    const lookupConfigs: Record<string, any> = {}
    for (const lc of (lookupConfigsRes?.data || [])) {
      let sf: string[] = []
      try { sf = typeof lc.searchFields === 'string' ? JSON.parse(lc.searchFields || '[]') : (lc.searchFields || []) } catch { sf = [] }
      lookupConfigs[lc.componentId] = { tableId: lc.tableId, searchFields: sf, displayField: lc.displayField || '', viewFields: lc.viewFields || [] }
    }
    lookupDbConfigs.value = lookupConfigs

    const forms = contentRes?.data?.forms || contentRes?.forms || []
    const form = forms.find((f: any) => String(f.sourceId) === String(detailFormId.value))
    if (form?.data) {
      const config = typeof form.data === 'string' ? JSON.parse(form.data) : form.data
      const rules = Array.isArray(config?.rule) ? config.rule : []
      formFields.value = toViewDetailFields(rules, lookupDbConfigs.value)
      subTableBindings.value = buildViewDetailSubTableBindings(
        form.tableBindings,
        config && typeof config === 'object' ? config : {},
        rowValues.value,
      )
      rowFound.value = true
    }
  } catch {
    rowFound.value = false
  } finally {
    loading.value = false
  }
}

/**
 * 列表页跳过来时 `?rowKey=` 就是后端下发的 `rowKey`（见 useMainTableViewPage.resolveRowKey），
 * 故这里按同一个字段匹配 —— 两边必须用同一套 key，否则详情页找不到行。
 * 不再按 `['id', 'id_idw', 'row_id']` 猜列名。
 */
function matchesRowKey(row: any): boolean {
  if (row?.rowKey != null && String(row.rowKey) === rowKey.value) return true
  return row?.processInstanceId != null && String(row.processInstanceId) === rowKey.value
}

onMounted(load)
</script>

<style scoped lang="scss">
.view-detail-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }
}

.detail-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
}
</style>
