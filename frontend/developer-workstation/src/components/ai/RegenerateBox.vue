<template>
  <el-popover
    v-model:visible="open"
    :width="320"
    trigger="click"
    placement="bottom-end"
    popper-class="regenerate-box__popper"
    @show="onShow"
  >
    <template #reference>
      <el-button
        size="small"
        :text="props.text"
        :disabled="props.disabled"
        @click.stop
      >
        {{ t('ai.preview.regenerate') }}
      </el-button>
    </template>

    <div
      class="regenerate-box"
      @click.stop
    >
      <p class="regenerate-box__title">
        {{ t('ai.regenerate.title') }}
      </p>
      <el-input
        ref="inputRef"
        v-model="instruction"
        type="textarea"
        :rows="4"
        :maxlength="MAX_INSTRUCTION_LENGTH"
        show-word-limit
        resize="none"
        :placeholder="t('ai.regenerate.placeholder')"
        @keydown.stop
        @keydown.enter.meta.prevent="confirm"
        @keydown.enter.ctrl.prevent="confirm"
      />
      <p class="regenerate-box__hint">
        {{ instruction.trim() ? t('ai.regenerate.hintTargeted') : t('ai.regenerate.hintBlank') }}
      </p>
      <div class="regenerate-box__actions">
        <el-button
          size="small"
          @click="open = false"
        >
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          size="small"
          type="primary"
          @click="confirm"
        >
          {{ t('ai.preview.regenerate') }}
        </el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Regenerate 按钮 + 纠错输入框。
 *
 * placement 用 bottom-end 而不是 top-end：文档卡都靠近对话区顶部，向上弹会整个溢出 AI 面板、
 * 盖住页面工具栏。空间不够时 Element Plus 会自己翻转，所以底部的 Preview 按钮照样能用。
 *
 * 空输入 = 保持原来的"整篇重出"行为；非空 = 调用方把这段话作为最高优先级的定向修改指令发出去，
 * 并要求模型保留其余已经正确的内容（见 ChatDialog 的 buildTargetedRegeneratePrompt）。
 * 三处 Regenerate（需求文档卡 / 设计文档卡 / Generation Preview）共用本组件，行为保持一致。
 */
const props = defineProps<{
  /** 文字按钮（文档卡片头部用），默认是普通按钮（Preview 底部动作区用）。 */
  text?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  /** instruction 已 trim；空串表示用户没填，走原有的整篇重出。 */
  confirm: [instruction: string]
}>()

const { t } = useI18n()

/** 与后端 message 列宽和 prompt 预算对齐：够写清几条修改点，又不至于把上下文挤爆。 */
const MAX_INSTRUCTION_LENGTH = 1000

const open = ref(false)
const instruction = ref('')
const inputRef = ref<{ focus?: () => void } | null>(null)

function onShow() {
  nextTick(() => inputRef.value?.focus?.())
}

function confirm() {
  emit('confirm', instruction.value.trim())
  // 清空而不是留着：下一次点开是另一轮修改，留着上一轮的文字很容易被顺手再发一遍
  instruction.value = ''
  open.value = false
}
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.regenerate-box__title {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: ai.$ai-ink;
}

.regenerate-box__hint {
  margin: 6px 0 10px;
  font-size: 11px;
  line-height: 1.5;
  color: ai.$ai-graphite;
}

.regenerate-box__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
