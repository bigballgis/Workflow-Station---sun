<template>
  <div class="markdown-renderer" v-html="sanitizedHtml"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{
  content: string
}>()

const sanitizedHtml = computed(() => {
  const rawHtml = marked.parse(props.content) as string
  return DOMPurify.sanitize(rawHtml)
})
</script>

<style lang="scss" scoped>
.markdown-renderer {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  word-break: break-word;

  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin: 16px 0 8px;
    font-weight: 600;
  }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(code) {
    background: #f5f7fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
  }

  :deep(pre) {
    background: #f5f7fa;
    padding: 12px;
    border-radius: 4px;
    overflow-x: auto;

    code {
      background: none;
      padding: 0;
    }
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;

    th, td {
      border: 1px solid #ebeef5;
      padding: 6px 12px;
      text-align: left;
    }

    th {
      background: #f5f7fa;
      font-weight: 600;
    }
  }

  :deep(ul), :deep(ol) {
    padding-left: 20px;
    margin: 8px 0;
  }

  :deep(blockquote) {
    border-left: 4px solid #dcdfe6;
    padding-left: 12px;
    margin: 8px 0;
    color: #606266;
  }
}
</style>
