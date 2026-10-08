<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('version.compare')"
    width="min(1280px, 96vw)"
    top="4vh"
    class="version-compare-dialog"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="version-compare">
      <div class="version-compare__selectors">
        <label class="version-compare__selector">
          <span>{{ t('version.compareV2.baseVersion') }}</span>
          <el-select
            v-model="baseVersionId"
            @change="clearResult"
          >
            <el-option
              v-for="version in versions"
              :key="version.id"
              :label="version.versionNumber"
              :value="version.id"
            />
          </el-select>
        </label>
        <span class="version-compare__arrow">→</span>
        <label class="version-compare__selector">
          <span>{{ t('version.compareV2.targetVersion') }}</span>
          <el-select
            v-model="targetVersionId"
            @change="clearResult"
          >
            <el-option
              v-for="version in versions"
              :key="version.id"
              :label="version.versionNumber"
              :value="version.id"
            />
          </el-select>
        </label>
        <el-button
          class="version-compare__submit"
          data-read-only-allowed
          type="primary"
          :loading="comparing"
          :disabled="!canCompare"
          @click="compare"
        >
          {{ t('common.compare') }}
        </el-button>
      </div>

      <template v-if="result">
        <div class="version-compare__summary">
          <span>{{ t('version.compareV2.direction', {
            base: result.baseVersion.versionNumber,
            target: result.targetVersion.versionNumber
          }) }}</span>
          <el-tag type="success">
            {{ t('common.added') }} {{ result.displayTotals?.added ?? result.totals.added }}
          </el-tag>
          <el-tag type="warning">
            {{ t('common.modified') }} {{ result.displayTotals?.modified ?? result.totals.modified }}
          </el-tag>
          <el-tag type="danger">
            {{ t('common.deleted') }} {{ result.displayTotals?.removed ?? result.totals.removed }}
          </el-tag>
        </div>

        <el-tabs
          v-model="activeModule"
          tab-position="left"
          class="version-compare__modules"
        >
          <el-tab-pane
            v-for="module in result.modules"
            :key="module.key"
            :name="module.key"
          >
            <template #label>
              {{ moduleLabel(module.key) }}
              <span
                v-if="changeCount(module)"
                class="version-compare__count"
              >
                {{ changeCount(module) }}
              </span>
            </template>

            <VersionSemanticPanel :module="module" />
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty
        v-else-if="!comparing"
        :description="t('version.compareV2.selectPrompt')"
      />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { functionUnitApi, type Version } from '@/api/functionUnit'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import VersionSemanticPanel from '@/components/version/VersionSemanticPanel.vue'
import type {
  VersionCompareModule,
  VersionCompareModuleKey,
  VersionCompareResponse
} from '@/types/versionCompare'

const props = defineProps<{
  modelValue: boolean
  functionUnitId: number
  versions: Version[]
  initialVersionId?: number
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()
const baseVersionId = ref<number>()
const targetVersionId = ref<number>()
const activeModule = ref<VersionCompareModuleKey>('BASIC')
const result = ref<VersionCompareResponse | null>(null)
const comparing = ref(false)
let requestSerial = 0

const canCompare = computed(() =>
  baseVersionId.value !== undefined && targetVersionId.value !== undefined
  && baseVersionId.value !== targetVersionId.value && !comparing.value
)

watch(() => props.modelValue, (opened) => {
  if (opened) {
    const otherVersionId = props.versions.find(v => v.id !== props.initialVersionId)?.id
    const pair = [props.initialVersionId, otherVersionId].filter((id): id is number => id !== undefined)
    baseVersionId.value = pair.length === 2 ? Math.min(...pair) : pair[0]
    targetVersionId.value = pair.length === 2 ? Math.max(...pair) : undefined
    activeModule.value = 'BASIC'
  }
  clearResult()
})

function clearResult(): void {
  requestSerial++
  result.value = null
  comparing.value = false
}

async function compare(): Promise<void> {
  if (!canCompare.value || baseVersionId.value === undefined || targetVersionId.value === undefined) return
  const serial = ++requestSerial
  comparing.value = true
  try {
    const response = await functionUnitApi.compareVersionsV2(
      props.functionUnitId, baseVersionId.value, targetVersionId.value
    )
    if (serial === requestSerial) {
      result.value = response.data
      activeModule.value = response.data.modules.find(module => changeCount(module) > 0)?.key || 'BASIC'
    }
  } catch (error: unknown) {
    if (serial === requestSerial) {
      ElMessage.error(resolveUserFacingHttpMessage(error, t) || t('common.error'))
    }
  } finally {
    if (serial === requestSerial) comparing.value = false
  }
}

function moduleLabel(key: VersionCompareModuleKey): string {
  return t(`version.compareV2.modules.${key}`)
}

function changeCount(module: VersionCompareModule): number {
  const counts = module.semantic?.status === 'COMPARED' ? module.semantic.counts : module.counts
  return counts.added + counts.modified + counts.removed
}
</script>

<style scoped>
:global(.version-compare-dialog) {
  display: flex;
  flex-direction: column;
  max-height: 92vh;
  padding: 24px;
  margin-bottom: 0;
  overflow: hidden;
}
:global(.version-compare-dialog .el-dialog__header) {
  flex-shrink: 0;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:global(.version-compare-dialog .el-dialog__body) {
  min-height: 0;
  padding-top: 20px;
  padding-right: 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.version-compare { min-width: 0; }
.version-compare__selectors, .version-compare__summary {
  display: flex;
  align-items: center;
  gap: 16px;
}
.version-compare__selectors { align-items: flex-end; flex-wrap: wrap; padding-bottom: 20px; border-bottom: 1px solid var(--el-border-color); }
.version-compare__selector { display: grid; gap: 5px; min-width: 190px; flex: 1; }
.version-compare__selector span { color: var(--el-text-color-secondary); font-size: 12px; }
.version-compare__arrow { color: var(--el-text-color-placeholder); font-size: 20px; line-height: 32px; }
.version-compare__submit { flex: 0 0 auto; height: 32px; min-width: 104px; margin-left: 0; padding: 0 16px; }
.version-compare__summary { flex-wrap: wrap; padding: 20px 0 12px; }
.version-compare__summary > span { margin-right: auto; font-weight: 600; }
.version-compare__modules { display: flex; align-items: flex-start; margin-top: 16px; overflow: visible; }
.version-compare__modules :deep(.el-tabs__header.is-left) { position: sticky; top: 0; flex-shrink: 0; min-width: 180px; }
.version-compare__modules :deep(.el-tabs__content) { flex: 1; min-width: 0; overflow: visible; }
.version-compare__count { border-radius: 10px; padding: 1px 6px; background: var(--el-fill-color); }
</style>
