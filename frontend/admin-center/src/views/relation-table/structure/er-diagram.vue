<template>
  <div class="page-container">
    <PageHeader :title="focusId ? t('erDiagram.titleFocus', { table: focusTableName }) : t('erDiagram.title')">
      <template #actions>
        <el-button @click="router.back()">
          {{ t('erDiagram.back') }}
        </el-button>
        <el-button
          v-if="focusId"
          @click="router.push('/relation-tables/structure/er-diagram')"
        >
          {{ t('erDiagram.viewAll') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card
      v-loading="loading"
      class="er-card"
    >
      <RelationDiagramEditor
        v-if="!loading"
        v-model="relations"
        :tables="diagramTables"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RelationDiagramEditor, { type RelationRow } from '@/components/relation-table/RelationDiagramEditor.vue'
import {
  relationTableStructureApi,
  type RelationTableResponse,
  type UpdateFieldDefinitionRequest,
} from '@/api/relationTable'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const focusId = computed<number | null>(() => {
  const raw = route.params.id
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isNaN(n) ? null : n
})

const loading = ref(false)
const saving = ref(false)
const tables = ref<RelationTableResponse[]>([])
const relations = ref<RelationRow[]>([])

const focusTableName = computed(() => {
  const tb = tables.value.find(t => t.id === focusId.value)
  return tb?.displayName || tb?.tableName || ''
})

// 只画已部署的表；focus 模式下只保留该表 + 一跳邻居。
const deployedTables = computed(() => tables.value.filter(t => t.status === 'DEPLOYED'))

const diagramTables = computed<RelationTableResponse[]>(() => {
  const deployed = deployedTables.value
  if (focusId.value == null) return deployed
  const keep = new Set<number>([focusId.value])
  for (const r of relations.value) {
    if (r.sourceTableId === focusId.value && r.targetTableId != null) keep.add(r.targetTableId)
    if (r.targetTableId === focusId.value && r.sourceTableId != null) keep.add(r.sourceTableId)
  }
  return deployed.filter(t => keep.has(t.id))
})

/** 从字段级 FK 元数据派生关系行（每个 isForeignKey 字段一条）。 */
function deriveRelations(allTables: RelationTableResponse[]): RelationRow[] {
  const out: RelationRow[] = []
  for (const tb of allTables) {
    for (const f of tb.fieldDefinitions || []) {
      if (!f.isForeignKey || f.refTableId == null) continue
      out.push({
        sourceTableId: tb.id,
        sourceFieldName: f.fieldName,
        targetTableId: f.refTableId,
        targetFieldName: (f.refPrimaryKeyFields && f.refPrimaryKeyFields[0]) || '',
        relationType: 'ONE_TO_MANY',
      })
    }
  }
  return out
}

const fetchTables = async () => {
  loading.value = true
  try {
    tables.value = await relationTableStructureApi.list()
    relations.value = deriveRelations(deployedTables.value)
  } catch {
    tables.value = []
    relations.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 保存：把关系行回写为字段级 FK 元数据。
 * Admin Center 的 FK 存在字段定义上，没有独立的 relations 接口；
 * 因此对每个「作为源表」或「原本带 FK」的表，重建其完整 fieldDefinitions 并 PUT。
 */
const handleSave = async () => {
  saving.value = true
  try {
    // sourceTableId -> Map<sourceFieldName, relation>
    const byTable = new Map<number, Map<string, RelationRow>>()
    for (const r of relations.value) {
      if (r.sourceTableId == null) continue
      if (!byTable.has(r.sourceTableId)) byTable.set(r.sourceTableId, new Map())
      byTable.get(r.sourceTableId)!.set(r.sourceFieldName, r)
    }

    // 需要更新的表：当前有 FK 关系的源表 ∪ 原本字段上带 FK 的表（用于清除被删掉的 FK）。
    const affected = new Set<number>(byTable.keys())
    for (const tb of deployedTables.value) {
      if ((tb.fieldDefinitions || []).some(f => f.isForeignKey)) affected.add(tb.id)
    }

    for (const tableId of affected) {
      const tb = tables.value.find(t => t.id === tableId)
      if (!tb) continue
      const fkMap = byTable.get(tableId) ?? new Map<string, RelationRow>()

      const fieldDefs: UpdateFieldDefinitionRequest[] = (tb.fieldDefinitions || []).map((f, i) => {
        const rel = fkMap.get(f.fieldName)
        const base: UpdateFieldDefinitionRequest = {
          id: f.id,
          fieldName: f.fieldName,
          dataType: f.dataType,
          length: f.length,
          nullable: f.nullable,
          isPrimaryKey: f.isPrimaryKey,
          defaultValue: f.defaultValue || undefined,
          displayName: f.displayName || undefined,
          sortOrder: f.sortOrder ?? i,
          pkGeneration: f.pkGeneration,
        }
        if (rel) {
          base.isForeignKey = true
          base.refTableId = rel.targetTableId ?? undefined
          base.refPrimaryKeyFields = rel.targetFieldName ? [rel.targetFieldName] : []
          base.fkDisplayMode = f.fkDisplayMode || 'readonly'
        } else {
          base.isForeignKey = false
          base.refTableId = undefined
          base.refPrimaryKeyFields = undefined
        }
        return base
      })

      await relationTableStructureApi.update(tableId, { fieldDefinitions: fieldDefs })
    }

    ElMessage.success(t('common.success'))
    await fetchTables()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || t('common.error'))
  } finally {
    saving.value = false
  }
}

onMounted(fetchTables)
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.er-card {
  min-height: 700px;
}
.er-card :deep(.el-card__body) {
  height: 700px;
}
</style>
