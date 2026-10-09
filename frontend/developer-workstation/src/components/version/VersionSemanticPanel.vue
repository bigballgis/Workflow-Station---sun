<template>
  <div class="compare-panel">
    <el-alert
      v-if="module.key === 'BASIC' && module.semantic?.scope === 'PARTIAL'"
      type="info"
      :closable="false"
      :title="t('version.compareV2.semantic.basicHistoricalScope')"
      class="compare-panel__notice"
    />
    <el-alert
      v-if="module.key === 'FORMS' && module.semantic?.scope === 'PARTIAL'"
      type="info"
      :closable="false"
      :title="t('version.compareV2.semantic.formHistoricalScope')"
      class="compare-panel__notice"
    />
    <el-alert
      v-if="module.key === 'DECISIONS' && module.semantic?.scope === 'PARTIAL'"
      type="info"
      :closable="false"
      :title="t('version.compareV2.semantic.decisionHistoricalScope')"
      class="compare-panel__notice"
    />
    <el-alert
      v-if="module.key === 'VIEWS' && module.semantic?.scope === 'PARTIAL'"
      type="info"
      :closable="false"
      :title="t('version.compareV2.semantic.viewHistoricalScope')"
      class="compare-panel__notice"
    />
    <el-alert
      v-if="module.semantic?.status === 'UNPARSEABLE'"
      type="warning"
      :closable="false"
      :title="t('version.compareV2.semantic.unparseable')"
      class="compare-panel__notice"
    />
    <el-alert
      v-if="module.key === 'AUTOMATION'"
      type="info"
      :closable="false"
      :title="t('version.compareV2.semantic.automationScope')"
      class="compare-panel__notice"
    />

    <template v-if="semanticReady">
      <el-alert
        v-if="module.semantic?.truncated"
        type="warning"
        :closable="false"
        :title="t('version.compareV2.semantic.truncated')"
        class="compare-panel__notice"
      />
      <el-empty
        v-if="semanticItems.length === 0"
        :description="t('version.compareV2.noChanges')"
      />
      <div
        v-else
        class="compare-panel__ledger"
      >
        <nav
          class="compare-panel__index"
          :aria-label="t('version.compareV2.semantic.objectList')"
        >
          <button
            v-for="item in semanticItems"
            :key="`${item.objectType}:${item.objectKey}`"
            type="button"
            class="compare-panel__object"
            :class="{ 'is-active': selectedKey === item.objectKey }"
            :aria-current="selectedKey === item.objectKey ? 'true' : undefined"
            @click="selectedKey = item.objectKey"
          >
            <span class="compare-panel__object-type">{{ objectTypeLabel(item.objectType) }}</span>
            <span class="compare-panel__object-label">{{ item.label }}</span>
            <el-tag
              size="small"
              :type="tagType(item.type)"
            >
              {{ changeLabel(item.type) }}
            </el-tag>
          </button>
        </nav>
        <article
          v-if="selectedItem"
          class="compare-panel__detail"
        >
          <div class="compare-panel__detail-head">
            <div>
              <span class="compare-panel__eyebrow">{{ objectTypeLabel(selectedItem.objectType) }}</span>
              <h3>{{ selectedItem.label }}</h3>
            </div>
            <el-tag :type="tagType(selectedItem.type)">
              {{ changeLabel(selectedItem.type) }}
            </el-tag>
          </div>
          <el-tag
            v-if="selectedItem.scope === 'LAYOUT_ONLY'"
            size="small"
            type="info"
          >
            {{ t('version.compareV2.semantic.layoutOnly') }}
          </el-tag>
          <p
            v-if="selectedItem.fields.length === 0"
            class="compare-panel__object-summary"
          >
            {{ t(`version.compareV2.semantic.wholeObject.${selectedItem.type}`) }}
          </p>
          <div
            v-else
            class="compare-panel__fields"
          >
            <div
              v-for="field in selectedItem.fields"
              :key="field.field"
              class="compare-panel__field"
            >
              <div class="compare-panel__field-name">
                {{ fieldLabel(field.field) }}
              </div>
              <p v-if="field.textContext" class="compare-panel__object-summary">
                {{ t('version.compareV2.semantic.documentExcerpt', {
                  oldStart: field.textContext.oldStart, newStart: field.textContext.newStart,
                  oldLength: field.textContext.oldLength, newLength: field.textContext.newLength
                }) }}
                <span v-if="field.textContext.omittedChanges">{{ t('version.compareV2.semantic.documentOmittedChanges') }}</span>
              </p>
              <div class="compare-panel__value-grid">
                <div>
                  <span>{{ t('version.compareV2.before') }}</span>
                  <code>{{ field.oldValue ?? '—' }}</code>
                </div>
                <div>
                  <span>{{ t('version.compareV2.after') }}</span>
                  <code>{{ field.newValue ?? '—' }}</code>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </template>

    <template v-else-if="module.status !== 'COMPARED'">
      <el-alert
        type="info"
        :closable="false"
        :title="statusLabel(module.status)"
      />
    </template>
    <template v-else>
      <el-alert
        v-if="module.truncated"
        type="warning"
        :closable="false"
        :title="t('version.compareV2.truncated')"
        class="compare-panel__notice"
      />
      <el-empty
        v-if="module.changes.length === 0"
        :description="t('version.compareV2.noChanges')"
      />
      <div
        v-else
        class="compare-panel__generic"
      >
        <div
          v-for="change in module.changes"
          :key="`${change.path}:${change.type}`"
          class="compare-panel__generic-item"
        >
          <div class="compare-panel__generic-heading">
            <el-tag
              size="small"
              :type="tagType(change.type)"
            >
              {{ changeLabel(change.type) }}
            </el-tag>
            <span>{{ change.path.split('/').slice(1).join(' › ') }}</span>
          </div>
          <div
            v-if="change.oldValue !== null || change.newValue !== null"
            class="compare-panel__value-grid"
          >
            <div v-if="change.oldValue !== null">
              <span>{{ t('version.compareV2.before') }}</span>
              <code>{{ change.oldValue }}</code>
            </div>
            <div v-if="change.newValue !== null">
              <span>{{ t('version.compareV2.after') }}</span>
              <code>{{ change.newValue }}</code>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { VersionChangeType, VersionCompareModule, VersionCompareStatus } from '@/types/versionCompare'

const props = defineProps<{ module: VersionCompareModule }>()
const { t, te } = useI18n()
const selectedKey = ref<string>()
const semanticReady = computed(() => props.module.semantic?.status === 'COMPARED')
const semanticItems = computed(() => props.module.semantic?.items ?? [])
const selectedItem = computed(() => semanticItems.value.find(item => item.objectKey === selectedKey.value))

watch(semanticItems, items => { selectedKey.value = items[0]?.objectKey }, { immediate: true })

function objectTypeLabel(type: string): string {
  const key = `version.compareV2.semantic.objectTypes.${type}`
  return te(key) ? t(key) : type.replaceAll('_', ' ')
}

function fieldLabel(path: string): string {
  return path.split('.').map(part => {
    if (part.endsWith('Fingerprint')) return t('version.compareV2.semantic.nestedConfiguration')
    // Array indices are diff coordinates, not vue-i18n message-path indices.
    const bracket = part.indexOf('[')
    const name = bracket < 0 ? part : part.slice(0, bracket)
    const index = bracket < 0 ? '' : part.slice(bracket)
    const key = `version.compareV2.semantic.fields.${name}`
    if (te(key)) return t(key) + index
    const designerKey = `properties.${name}`
    return (te(designerKey) ? t(designerKey) : name.replace(/([a-z])([A-Z])/g, '$1 $2')) + index
  }).join(' › ')
}

function tagType(type: VersionChangeType): 'success' | 'warning' | 'danger' {
  return type === 'ADDED' ? 'success' : type === 'REMOVED' ? 'danger' : 'warning'
}

function changeLabel(type: VersionChangeType): string {
  return t(`version.compareV2.change.${type}`)
}

function statusLabel(status: VersionCompareStatus): string {
  return t(`version.compareV2.status.${status}`)
}
</script>

<style scoped>
.compare-panel { min-width: 0; }
.compare-panel__notice { margin-bottom: 12px; }
.compare-panel__ledger { display: grid; grid-template-columns: 244px minmax(0, 1fr); align-items: start; min-height: 330px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.compare-panel__index { position: sticky; top: 0; max-height: calc(92vh - 280px); min-height: min(330px, calc(92vh - 280px)); overflow-y: auto; overscroll-behavior: contain; background: var(--el-fill-color-lighter); border-right: 1px solid var(--el-border-color); border-radius: 8px 0 0 8px; }
.compare-panel__object { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 3px 8px; width: 100%; padding: 12px 16px; border: 0; border-left: 3px solid transparent; border-bottom: 1px solid var(--el-border-color-lighter); background: transparent; color: var(--el-text-color-primary); text-align: left; cursor: pointer; }
.compare-panel__object:hover { background: var(--el-fill-color-light); }
.compare-panel__object:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.compare-panel__object.is-active { border-left-color: var(--el-color-primary); background: var(--el-bg-color); }
.compare-panel__object-type { grid-column: 1 / -1; color: var(--el-text-color-secondary); font-size: 11px; letter-spacing: .04em; text-transform: uppercase; }
.compare-panel__object-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.compare-panel__detail { min-width: 0; padding: 24px 28px; }
.compare-panel__detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 15px; border-bottom: 1px solid var(--el-border-color-lighter); }
.compare-panel__detail-head h3 { margin: 5px 0 0; font-size: 18px; line-height: 1.3; overflow-wrap: anywhere; }
.compare-panel__eyebrow { color: var(--el-text-color-secondary); font-size: 11px; letter-spacing: .04em; text-transform: uppercase; }
.compare-panel__object-summary { color: var(--el-text-color-secondary); }
.compare-panel__fields { display: grid; gap: 14px; margin-top: 18px; }
.compare-panel__field { min-width: 0; }
.compare-panel__field-name { margin-bottom: 6px; color: var(--el-text-color-regular); font-weight: 600; }
.compare-panel__value-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.compare-panel__value-grid > div { display: grid; gap: 5px; min-width: 0; }
.compare-panel__value-grid span { color: var(--el-text-color-secondary); font-size: 11px; }
.compare-panel__value-grid code { min-height: 32px; padding: 8px; border-radius: 4px; background: var(--el-fill-color-light); font-family: var(--el-font-family); white-space: pre-wrap; overflow-wrap: anywhere; }
.compare-panel__generic { display: grid; gap: 8px; }
.compare-panel__generic-item { padding: 16px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.compare-panel__generic-heading { display: flex; align-items: center; gap: 8px; margin-bottom: 9px; overflow-wrap: anywhere; }
@media (max-width: 900px) { .compare-panel__ledger { grid-template-columns: 190px minmax(0, 1fr); } .compare-panel__detail { padding: 16px; } }
@media (max-width: 700px) { .compare-panel__ledger { grid-template-columns: 1fr; } .compare-panel__index { position: static; min-height: 0; max-height: 190px; border-right: 0; border-bottom: 1px solid var(--el-border-color); } .compare-panel__value-grid { grid-template-columns: 1fr; } }
</style>
