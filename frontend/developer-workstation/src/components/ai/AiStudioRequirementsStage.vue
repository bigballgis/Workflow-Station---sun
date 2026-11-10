<template>
  <div class="req-stage">
    <div
      class="req-stage__bar"
      :class="{ 'is-pending': pending }"
    >
      <span class="req-stage__hint">{{ hint }}</span>
      <el-tooltip
        :content="disabledReason"
        :disabled="!disabledReason"
        placement="top"
      >
        <!-- 包一层：禁用的按钮不触发 tooltip -->
        <span>
          <el-button
            type="primary"
            :plain="!pending"
            :loading="syncing"
            :disabled="readonly || syncing || replying || !!disabledReason"
            data-testid="ai-studio-requirements-draft"
            @click="emit('draft')"
          >
            <el-icon v-if="!syncing"><MagicStick /></el-icon>
            {{ hasDocument ? t('ai.studio.workspace.requirements.update') : t('ai.studio.workspace.requirements.generate') }}
          </el-button>
        </span>
      </el-tooltip>
    </div>
    <FunctionUnitDocumentEditor
      ref="editorRef"
      class="req-stage__editor"
      :function-unit-id="functionUnitId"
      :function-unit-name="functionUnitName"
      type="REQUIREMENTS"
      :readonly="readonly"
      initial-mode="preview"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { MagicStick } from '@element-plus/icons-vue'
import FunctionUnitDocumentEditor from '@/components/function-unit/FunctionUnitDocumentEditor.vue'

/**
 * AI Studio 需求访谈阶段的中间区：Requirements 文档本身（与 Settings 同一个编辑器）+ "根据对话成稿"。
 * 成稿是后台作业（工作台发起、推送结束），这里只在它结束后重新载入文档。
 */
const props = defineProps<{
  functionUnitId: number
  functionUnitName?: string
  readonly: boolean
  /** 有文档更新作业在跑 */
  syncing: boolean
  /** 变化时（作业结束 / 卡片上恢复了版本）重新载入文档 */
  refreshKey: number
  /** 共享线程可用：成稿读的是后端线程 */
  available: boolean
  /** 访谈里有用户发言 */
  hasConversation: boolean
  /** 上次成稿之后又有新的发言 */
  pending: boolean
  /** Copilot 正在回答：这一轮还没落库，现在成稿会漏掉它 */
  replying: boolean
}>()

const emit = defineEmits<{ draft: [] }>()

const { t } = useI18n()
const editorRef = ref<InstanceType<typeof FunctionUnitDocumentEditor> | null>(null)
const hasDocument = computed(() => !!editorRef.value?.hasSaved)

const disabledReason = computed(() => {
  if (!props.available) return t('ai.studio.workspace.requirements.unavailable')
  if (!props.hasConversation) return t('ai.studio.workspace.requirements.needConversation')
  return ''
})

const hint = computed(() => {
  if (props.pending) return t('ai.studio.workspace.requirements.hintPending')
  return hasDocument.value
    ? t('ai.studio.workspace.requirements.hintUpToDate')
    : t('ai.studio.workspace.requirements.hintEmpty')
})

watch(() => props.refreshKey, () => {
  // 有未保存的手动修改时不重载：AI 的版本已经落库，用户保存时走编辑器自己的冲突处理
  if (!editorRef.value?.isDirty) void editorRef.value?.reload()
})

defineExpose({ isDirty: computed(() => !!editorRef.value?.isDirty) })
</script>

<style lang="scss" scoped>
.req-stage {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 16px 20px;
  box-sizing: border-box;
}

.req-stage__bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;

  &.is-pending {
    border-color: #f3d19e;
    background: #fdf6ec;
  }
}

.req-stage__hint {
  flex: 1;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.req-stage__editor {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.document-editor__body) {
    flex: 1;
    min-height: 0;
    height: auto;
  }
}
</style>
