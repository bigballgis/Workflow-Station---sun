<template>
  <div 
    class="icon-preview" 
    :class="[`icon-preview--${size}`, { 'icon-preview--clickable': clickable }]"
    @click="handleClick"
    :title="iconName"
  >
    <div v-if="iconContent && iconContent.trim()" class="icon-content" v-html="iconContent"></div>
    <div v-else class="icon-placeholder">
      <svg :width="placeholderSize" :height="placeholderSize" viewBox="0 0 24 24" fill="none">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" fill="currentColor"/>
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { iconApi } from '@/api/icon'

const props = withDefaults(defineProps<{
  iconId?: number | null
  iconUrl?: string
  svgContent?: string
  size?: 'small' | 'medium' | 'large'
  clickable?: boolean
  iconName?: string
}>(), {
  size: 'medium',
  clickable: false,
  iconName: ''
})

const emit = defineEmits<{
  click: []
}>()

const iconContent = ref<string>('')

const placeholderSize = computed(() => {
  const sizeMap = { small: 16, medium: 24, large: 32 }
  return sizeMap[props.size]
})

async function loadIcon() {
  if (props.svgContent && props.svgContent.trim()) {
    iconContent.value = sanitizeSvg(props.svgContent)
    return
  }
  
  if (props.iconId) {
    try {
      const res = await iconApi.getById(props.iconId)
      const svg = res.data?.svgContent || ''
      iconContent.value = svg.trim() ? sanitizeSvg(svg) : ''
    } catch {
      iconContent.value = ''
    }
  } else {
    iconContent.value = ''
  }
}

// Sanitize SVG content, remove elements that may cause display issues
function sanitizeSvg(svg: string): string {
  let result = svg
  // Remove <title> elements
  result = result.replace(/<title[^>]*>[\s\S]*?<\/title>/gi, '')
  // Remove <style> elements (prevent style leaking to global scope)
  result = result.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
  // Remove <defs> elements (contains <style> definitions, prevent style leaking)
  result = result.replace(/<defs[\s\S]*?<\/defs>/gi, '')
  // Replace class="cls-1" with inline style fill="#fff"
  result = result.replace(/class="cls-1"/gi, 'fill="#fff"')
  // Replace class="cls-2" with inline style fill="#db0011"
  result = result.replace(/class="cls-2"/gi, 'fill="#db0011"')
  // Remove all class attributes to prevent style conflicts
  result = result.replace(/\s+class="[^"]*"/gi, '')
  return result
}

function handleClick() {
  if (props.clickable) {
    emit('click')
  }
}

watch(() => [props.iconId, props.svgContent], loadIcon, { immediate: true })
</script>

<style lang="scss" scoped>
.icon-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background-color: #f5f7fa;
  overflow: hidden;
  
  &::before,
  &::after {
    display: none !important;
    content: none !important;
  }
  
  &--small {
    width: 32px;
    height: 32px;
  }
  
  &--medium {
    width: 48px;
    height: 48px;
  }
  
  &--large {
    width: 64px;
    height: 64px;
  }
  
  &--clickable {
    cursor: pointer;
    transition: all 0.2s;
    
    &:hover {
      background-color: #e6e8eb;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }
  }
}

.icon-content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  
  &::before,
  &::after {
    display: none !important;
    content: none !important;
  }
  
  :deep(svg) {
    width: 100%;
    height: 100%;
    
    title {
      display: none !important;
    }
  }
}

.icon-placeholder {
  color: #c0c4cc;
}
</style>
