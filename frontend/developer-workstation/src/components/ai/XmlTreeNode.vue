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
        <pre class="xml-tree-node__text">{{ node.content }}</pre>
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
      <pre class="xml-tree-node__text">{{ node.content }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
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

.xml-tree-node__text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
  color: #606266;
  margin: 0;
  font-family: inherit;
}
</style>
