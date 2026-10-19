<template>
  <aside
    class="table-navigator"
    data-testid="relation-table-navigator"
  >
    <div class="navigator-heading">
      <div class="navigator-title">
        {{ t('tableDataNavigation.title') }}
      </div>
      <span class="navigator-total">{{ t('tableDataNavigation.tableCount', { count: tables.length }) }}</span>
    </div>
    <div class="navigator-search">
      <el-input
        v-model="keyword"
        clearable
        :placeholder="t('tableDataNavigation.search')"
        :aria-label="t('tableDataNavigation.search')"
        data-testid="relation-table-search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>
    <div
      v-loading="loading"
      class="navigator-body"
    >
      <div
        v-if="error"
        class="navigator-error"
      >
        <el-alert
          :title="error"
          type="error"
          :closable="false"
          show-icon
        />
        <el-button @click="emit('retry')">
          {{ t('tableDataNavigation.retry') }}
        </el-button>
      </div>
      <ul
        v-else
        class="group-list"
      >
        <li
          v-for="group in visibleGroups"
          :key="group.key"
          class="table-group"
        >
          <button
            class="group-toggle"
            :aria-expanded="group.expanded"
            :aria-controls="`table-group-${group.key}`"
            @click="toggleGroup(group.key)"
          >
            <el-icon
              class="group-chevron"
              :class="{ expanded: group.expanded }"
            >
              <ArrowRight />
            </el-icon>
            <span
              class="group-name"
              :title="group.label"
            >{{ group.label }}</span>
            <span
              class="group-count"
              :title="t('tableDataNavigation.tableCount', { count: group.tableCount })"
            >{{ group.tableCount }}</span>
          </button>
          <ul
            v-show="group.expanded"
            :id="`table-group-${group.key}`"
            class="table-list"
          >
            <li
              v-for="table in group.tables"
              :key="table.id"
            >
              <button
                class="table-item"
                :class="{ 'is-selected': selectedTableId === table.id && selectedGroupKey === group.key }"
                :aria-current="selectedTableId === table.id && selectedGroupKey === group.key ? 'true' : undefined"
                :data-table-id="table.id"
                :data-fu-code="group.code"
                @click="emit('select', table.id, group.code)"
              >
                <el-icon class="table-icon">
                  <Grid />
                </el-icon>
                <span class="table-names">
                  <span
                    class="table-name"
                    :title="table.displayName || table.tableName"
                  >{{ table.displayName || table.tableName }}</span>
                  <span
                    class="table-code"
                    :title="table.tableName"
                  >{{ table.tableName }}</span>
                </span>
                <el-icon
                  v-if="table.permissionLevel === 'READONLY'"
                  :title="t('tableDataNavigation.readonly')"
                >
                  <Lock />
                </el-icon>
              </button>
            </li>
          </ul>
        </li>
      </ul>
      <el-empty
        v-if="!loading && !error && visibleGroups.length === 0"
        :description="t(keyword ? 'tableDataNavigation.noMatches' : 'tableDataNavigation.noTables')"
        :image-size="48"
      />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Grid, Lock, Search } from '@element-plus/icons-vue'
import type { RelationTableResponse } from '@/api/relationTable'

interface TableGroup {
  key: string
  code: string | null | undefined
  label: string
  tables: RelationTableResponse[]
}
const props = defineProps<{
  tables: RelationTableResponse[]
  selectedTableId: number | null
  activeGroupKey: string | null | undefined
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ select: [tableId: number, functionUnitCode: string | null | undefined]; retry: [] }>()
const { t } = useI18n()
const keyword = ref('')
const expandedKeys = ref<string[]>([])
const groups = computed<TableGroup[]>(() => {
  const result = new Map<string, TableGroup>([
    ['all', { key: 'all', code: undefined, label: t('menu.allFunctionUnits'), tables: props.tables }],
  ])
  for (const table of props.tables) {
    const memberships = table.functionUnits.length > 0
      ? table.functionUnits.map(unit => ({ key: `fu:${unit.code}`, code: unit.code, label: unit.name || unit.code }))
      : [{ key: 'common', code: null, label: t('tableDataNavigation.common') }]
    for (const membership of memberships) {
      let group = result.get(membership.key)
      if (!group) {
        group = { ...membership, tables: [] }
        result.set(membership.key, group)
      }
      if (!group.tables.some(item => item.id === table.id)) group.tables.push(table)
    }
  }
  return [...result.values()]
})
const selectedGroupKey = computed(() => {
  const explicitKey = props.activeGroupKey === null ? 'common' : `fu:${props.activeGroupKey}`
  if (props.activeGroupKey !== undefined) return explicitKey
  return 'all'
})
const visibleGroups = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return groups.value.map(group => {
    const groupMatches = `${group.label} ${group.code ?? ''}`.toLowerCase().includes(search)
    const tables = groupMatches ? group.tables : group.tables.filter(table =>
      `${table.displayName} ${table.tableName}`.toLowerCase().includes(search))
    return { ...group, tables, tableCount: group.tables.length, expanded: search.length > 0 || expandedKeys.value.includes(group.key) }
  }).filter(group => group.tables.length > 0)
})
watch(selectedGroupKey, key => {
  if (key && !expandedKeys.value.includes(key)) expandedKeys.value.push(key)
}, { immediate: true })
function toggleGroup(key: string): void {
  if (keyword.value.trim()) return
  expandedKeys.value = expandedKeys.value.includes(key)
    ? expandedKeys.value.filter(item => item !== key) : [...expandedKeys.value, key]
}
</script>

<style scoped>
.table-navigator { width: 320px; min-width: 320px; max-width: 380px; flex-shrink: 0; display: flex; flex-direction: column; overflow: auto; resize: horizontal; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.navigator-heading { display: flex; align-items: center; gap: 12px; padding: 20px 20px 16px; }
.navigator-title { flex: 1; font-size: 16px; font-weight: 600; line-height: 1.5; }
.navigator-total { color: var(--el-text-color-secondary); font-size: 12px; }
.navigator-search { padding: 0 20px 20px; border-bottom: 1px solid var(--el-border-color-extra-light); }
.navigator-search :deep(.el-input__wrapper) { padding: 1px 10px; }
.navigator-body { flex: 1; min-height: 80px; overflow-y: auto; padding: 16px 12px; }
ul { list-style: none; margin: 0; padding: 0; }
.table-group + .table-group { margin-top: 14px; }
.group-toggle, .table-item { width: 100%; display: flex; align-items: center; gap: 8px; text-align: left; border: 0; background: transparent; color: var(--el-text-color-primary); cursor: pointer; border-radius: 5px; font: inherit; }
.group-toggle { min-height: 48px; padding: 12px 8px; gap: 10px; font-size: 14px; font-weight: 600; }
.group-toggle:hover, .table-item:hover { background: var(--el-fill-color-light); }
.group-toggle:focus-visible, .table-item:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.group-chevron { font-size: 11px; transition: transform .15s; }
.group-chevron.expanded { transform: rotate(90deg); }
.table-icon { color: var(--el-text-color-secondary); flex-shrink: 0; }
.group-name { flex: 1; min-width: 0; white-space: normal; overflow-wrap: anywhere; line-height: 1.6; }
.group-count { color: var(--el-text-color-secondary); background: var(--el-fill-color); border-radius: 10px; padding: 2px 7px; font-size: 11px; }
.table-list { margin: 4px 0 0 14px; padding-left: 10px; border-left: 1px solid var(--el-border-color-light); }
.table-item { padding: 12px; margin: 4px 0; font-size: 13px; }
.table-item.is-selected { background: var(--el-color-primary-light-9); color: var(--el-color-primary); box-shadow: inset 3px 0 var(--el-color-primary); }
.is-selected .table-icon { color: var(--el-color-primary); }
.table-names { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5px; }
.table-name, .table-code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table-name { font-weight: 500; }
.table-code { font-size: 11px; color: var(--el-text-color-secondary); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.navigator-error { padding: 4px; display: grid; gap: 12px; justify-items: start; }
@media (prefers-reduced-motion: reduce) { .group-chevron { transition: none; } }
</style>
