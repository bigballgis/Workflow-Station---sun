<template>
  <div class="xml-tree-node" :style="{ paddingLeft: depth * 16 + 'px' }">
    <div class="xml-tree-node__header" @click="handleClick">
      <span v-if="isParent" class="xml-tree-node__arrow" :class="{ 'is-expanded': isExpanded }">
        <el-icon><ArrowRight /></el-icon>
      </span>
      <span v-else class="xml-tree-node__leaf-icon" />
      <span v-if="isParent || node.tagName !== 'content'" class="xml-tree-node__title">{{ node.title }}</span>
    </div>
    <template v-if="isParent && isExpanded">
      <div v-if="node.content" class="xml-tree-node__content" :style="{ paddingLeft: (depth + 1) * 16 + 'px' }">
        <div class="xml-tree-node__rich-text" v-html="renderedContent"></div>
      </div>
      <XmlTreeNode
        v-for="child in node.children"
        :key="child.key"
        :node="child"
        :expanded-keys="expandedKeys"
        :depth="depth + 1"
        @toggle="$emit('toggle', $event)"
      />
    </template>
    <div v-else-if="!isParent && node.content" class="xml-tree-node__content" :style="{ paddingLeft: (depth + 1) * 16 + 'px' }">
      <div class="xml-tree-node__rich-text" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { XmlNode } from '@/utils/markdownToXml'

const props = defineProps<{
  node: XmlNode
  expandedKeys: Set<string>
  depth: number
}>()

const emit = defineEmits<{
  toggle: [key: string]
}>()

const isParent = computed(() => props.node.children.length > 0)
const isExpanded = computed(() => props.expandedKeys.has(props.node.key))

const renderedContent = computed(() => {
  if (!props.node.content) return ''
  const rawHtml = marked.parse(props.node.content) as string
  return DOMPurify.sanitize(rawHtml)
})

function handleClick() {
  if (isParent.value) {
    emit('toggle', props.node.key)
  }
}
</script>

<style lang="scss" scoped>
.xml-tree-node__header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 0;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.5;

  &:hover {
    background: #f5f7fa;
    border-radius: 3px;
  }
}

.xml-tree-node__arrow {
  display: inline-flex;
  align-items: center;
  width: 16px;
  transition: transform 0.2s;

  &.is-expanded {
    transform: rotate(90deg);
  }
}

.xml-tree-node__leaf-icon {
  display: inline-block;
  width: 16px;
}

.xml-tree-node__title {
  color: #303133;
  font-weight: 500;
}

.xml-tree-node__content {
  padding: 2px 0;
}

.xml-tree-node__rich-text {
  font-size: 12px;
  line-height: 1.6;
  color: #606266;

  :deep(p) {
    margin: 4px 0;
  }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 6px 0;
    font-size: 12px;

    th, td {
      border: 1px solid #dcdfe6;
      padding: 5px 10px;
      text-align: left;
    }

    th {
      background: #f5f7fa;
      font-weight: 600;
      color: #303133;
    }

    tr:hover td {
      background: #fafafa;
    }
  }

  :deep(ul), :deep(ol) {
    padding-left: 18px;
    margin: 4px 0;
  }

  :deep(code) {
    background: #f5f7fa;
    padding: 1px 4px;
    border-radius: 3px;
    font-size: 11px;
  }

  :deep(pre) {
    background: #f5f7fa;
    padding: 8px;
    border-radius: 4px;
    overflow-x: auto;
    margin: 4px 0;

    code {
      background: none;
      padding: 0;
    }
  }

  :deep(blockquote) {
    border-left: 3px solid #dcdfe6;
    padding-left: 10px;
    margin: 4px 0;
    color: #909399;
  }

  :deep(strong) {
    color: #303133;
  }
}
</style>
