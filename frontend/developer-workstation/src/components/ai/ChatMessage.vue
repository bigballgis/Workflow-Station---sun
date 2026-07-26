<template>
  <div
    class="chat-message"
    :class="[`chat-message--${message.role.toLowerCase()}`]"
  >
    <div class="chat-message__avatar">
      <span
        v-if="message.role === 'USER'"
        class="chat-message__mark chat-message__mark--user"
      >
        <el-icon :size="14"><User /></el-icon>
      </span>
      <span
        v-else
        class="chat-message__mark chat-message__mark--ai"
      >AI</span>
    </div>
    <div class="chat-message__body">
      <div class="chat-message__content">
        <span
          class="chat-message__text"
          v-text="message.content"
        />
        <span
          v-if="isStreaming"
          class="chat-message__cursor"
        >▊</span>
      </div>
      <div class="chat-message__time">
        {{ formattedTime }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { User } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import type { AiMessage } from '@/types/aiGeneration'

const props = withDefaults(defineProps<{
  message: AiMessage
  isStreaming?: boolean
}>(), {
  isStreaming: false
})

const formattedTime = computed(() =>
  props.message.createdAt ? dayjs(props.message.createdAt).format('HH:mm:ss') : ''
)
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.chat-message {
  display: flex;
  gap: 10px;
  padding: 10px 16px;

  // 用户消息：右侧，墨水（graphite 底白字）
  &--user {
    flex-direction: row-reverse;

    .chat-message__body {
      align-items: flex-end;
    }

    .chat-message__content {
      background: ai.$ai-user-bubble;
      color: #fff;
      border: 1px solid ai.$ai-user-bubble;
      border-radius: 10px 3px 10px 10px;
    }
  }

  // AI 消息：左侧，图纸（白纸 + 发丝边）
  &--assistant {
    .chat-message__content {
      background: ai.$ai-paper;
      color: ai.$ai-body;
      border: 1px solid ai.$ai-hairline;
      border-radius: 3px 10px 10px 10px;
    }
  }
}

// 头像：统一小方标
.chat-message__mark {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--ai {
    font-family: ai.$ai-mono;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: ai.$ai-red;
    background: ai.$ai-paper;
    border: 1px solid ai.$ai-hairline;
  }

  &--user {
    color: #fff;
    background: ai.$ai-user-bubble;
  }
}

.chat-message__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-width: 72%;
}

.chat-message__content {
  padding: 9px 13px;
  line-height: 1.65;
  font-size: 13px;
  word-break: break-word;
}

.chat-message__text {
  white-space: pre-wrap;
}

.chat-message__cursor {
  animation: blink 1s step-end infinite;
  color: ai.$ai-red;
}

@keyframes blink {
  50% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .chat-message__cursor {
    animation: none;
  }
}

.chat-message__time {
  @include ai.ai-mono-num;
  font-size: 10px;
  color: ai.$ai-faint;
  padding: 0 2px;
}
</style>
