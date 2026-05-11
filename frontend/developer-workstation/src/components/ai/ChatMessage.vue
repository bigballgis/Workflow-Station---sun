<template>
  <div
    class="chat-message"
    :class="[`chat-message--${message.role.toLowerCase()}`]"
  >
    <div class="chat-message__avatar">
      <el-avatar
        v-if="message.role === 'USER'"
        :size="32"
        icon="User"
      />
      <el-avatar
        v-else
        :size="32"
        class="chat-message__avatar--ai"
      >
        AI
      </el-avatar>
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
.chat-message {
  display: flex;
  gap: 10px;
  padding: 12px 16px;

  &--user {
    flex-direction: row-reverse;

    .chat-message__body {
      align-items: flex-end;
    }

    .chat-message__content {
      background: #ecf5ff;
      color: #303133;
    }
  }

  &--assistant {
    .chat-message__content {
      background: #f4f4f5;
      color: #303133;
    }
  }
}

.chat-message__avatar--ai {
  background: #409eff;
  color: #fff;
  font-size: 12px;
}

.chat-message__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 70%;
}

.chat-message__content {
  padding: 10px 14px;
  border-radius: 8px;
  line-height: 1.6;
  font-size: 14px;
  word-break: break-word;
}

.chat-message__text {
  white-space: pre-wrap;
}

.chat-message__cursor {
  animation: blink 1s step-end infinite;
  color: #409eff;
}

@keyframes blink {
  50% { opacity: 0; }
}

.chat-message__time {
  font-size: 12px;
  color: #909399;
}
</style>
