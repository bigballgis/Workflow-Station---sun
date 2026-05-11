<template>
  <div class="xml-tree-view">
    <template v-if="!parseError">
      <div class="xml-tree-view__toolbar">
        <el-button
          text
          size="small"
          @click="expandAll"
        >
          {{ t('ai.doc.expandAll') }}
        </el-button>
        <el-button
          text
          size="small"
          @click="collapseAll"
        >
          {{ t('ai.doc.collapseAll') }}
        </el-button>
      </div>
      <div class="xml-tree-view__tree">
        <XmlTreeNode
          v-for="node in nodes"
          :key="node.key"
          :node="node"
          :expanded-keys="expandedKeys"
          :depth="0"
          @toggle="toggleNode"
        />
      </div>
    </template>
    <template v-else>
      <div class="xml-tree-view__error">
        {{ t('ai.doc.parseError') }}
      </div>
      <pre class="xml-tree-view__fallback">{{ content }}</pre>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { markdownToXml } from '@/utils/markdownToXml'
import type { XmlNode } from '@/utils/markdownToXml'
import XmlTreeNode from './XmlTreeNode.vue'
import {
  computeDefaultExpandedKeys,
  computeAllExpandableKeys
} from './xmlTreeUtils'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  content: string
  defaultExpandLevel?: number
}>(), {
  defaultExpandLevel: 1
})

const nodes = ref<XmlNode[]>([])
const expandedKeys = ref<Set<string>>(new Set())
const parseError = ref(false)

function parseContent() {
  try {
    const result = markdownToXml(props.content)
    nodes.value = result
    parseError.value = false
    expandedKeys.value = computeDefaultExpandedKeys(result, props.defaultExpandLevel)
  } catch {
    nodes.value = []
    parseError.value = true
    expandedKeys.value = new Set()
  }
}

function toggleNode(key: string) {
  const newKeys = new Set(expandedKeys.value)
  if (newKeys.has(key)) {
    newKeys.delete(key)
  } else {
    newKeys.add(key)
  }
  expandedKeys.value = newKeys
}

function expandAll() {
  expandedKeys.value = computeAllExpandableKeys(nodes.value)
}

function collapseAll() {
  expandedKeys.value = computeDefaultExpandedKeys(nodes.value, props.defaultExpandLevel)
}

watch(() => props.content, () => {
  parseContent()
}, { immediate: true })

defineExpose({ expandAll, collapseAll })
</script>

<style lang="scss" scoped>
.xml-tree-view__toolbar {
  display: flex;
  gap: 4px;
  padding: 4px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 4px;
  position: sticky;
  top: 0;
  z-index: 1;
  background: #fff;
}

.xml-tree-view__tree {
  overflow-y: auto;
}

.xml-tree-view__error {
  color: #f56c6c;
  font-size: 13px;
  padding: 8px 0;
}

.xml-tree-view__fallback {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  margin: 0;
  font-family: inherit;
}
</style>
