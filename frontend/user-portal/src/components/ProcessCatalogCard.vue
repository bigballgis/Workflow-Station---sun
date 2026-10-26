<template>
  <div class="process-card">
    <button
      type="button"
      class="favorite-button"
      :data-process-key="process.key"
      :class="{ 'is-favorite': process.isFavorite }"
      :aria-label="t(process.isFavorite ? 'process.removeFavorite' : 'process.addFavorite', { name: process.name })"
      :aria-pressed="!!process.isFavorite"
      :disabled="favoritePending"
      @click="$emit('toggle-favorite', process.key, $event)"
    >
      <el-icon><component :is="process.isFavorite ? StarFilled : Star" /></el-icon>
    </button>
    <RouterLink
      class="process-link"
      :to="`/processes/start/${process.key}`"
      :aria-label="process.name"
    >
      <div
        class="card-icon"
        aria-hidden="true"
      >
        <div
          v-if="process.icon"
          class="process-icon-svg"
          v-html="sanitizeIcon(process.icon)"
        />
        <el-icon
          v-else
          :size="56"
          :color="getProcessColor(process.category)"
        >
          <component :is="getProcessIcon(process.category)" />
        </el-icon>
      </div>
      <div class="card-content">
        <h3
          class="process-name"
          :title="process.name"
        >
          {{ process.name }}
        </h3>
        <p
          class="process-desc"
          :title="process.description"
        >
          {{ process.description }}
        </p>
        <span
          v-if="process.version"
          class="process-version"
        >v{{ process.version }}</span>
      </div>
    </RouterLink>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { Document, Calendar, Money, ShoppingCart, Location, Clock, Files, Star, StarFilled, Tickets } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import type { ProcessDefinition } from '@/api/process'

const { t } = useI18n()
defineProps<{ process: ProcessDefinition; favoritePending?: boolean }>()
defineEmits<{ (event: 'toggle-favorite', key: string, click: MouseEvent): void }>()

const SVG_PURIFY_CONFIG = {
  ALLOWED_TAGS: ['svg', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'g', 'defs', 'use'],
  ALLOWED_ATTR: ['viewBox', 'd', 'fill', 'stroke', 'stroke-width', 'cx', 'cy', 'r', 'x', 'y',
    'width', 'height', 'points', 'transform', 'class', 'xmlns', 'xlink:href'],
}

function sanitizeIcon(icon: string): string {
  return DOMPurify.sanitize(icon, SVG_PURIFY_CONFIG)
}

// 根据分类获取图标
const getProcessIcon = (category: string) => {
  const iconMap: Record<string, Component> = {
    '人事': Calendar,
    'HR': Calendar,
    '财务': Money,
    'Finance': Money,
    '采购': ShoppingCart,
    'Procurement': ShoppingCart,
    '出差': Location,
    'Travel': Location,
    '加班': Clock,
    'Overtime': Clock,
    '合同': Files,
    'Contract': Files,
    '业务流程': Tickets,
    'Business Process': Tickets,
  }
  return iconMap[category] || Document
}

// 根据分类获取颜色
const getProcessColor = (category: string) => {
  const colorMap: Record<string, string> = {
    '人事': 'var(--hsbc-red)',
    'HR': 'var(--hsbc-red)',
    '财务': 'var(--success-green)',
    'Finance': 'var(--success-green)',
    '采购': 'var(--warning-orange)',
    'Procurement': 'var(--warning-orange)',
    '出差': 'var(--info-blue)',
    'Travel': 'var(--info-blue)',
    '加班': '#722ed1',
    'Overtime': '#722ed1',
    '合同': '#13c2c2',
    'Contract': '#13c2c2',
    '业务流程': '#1890ff',
    'Business Process': '#1890ff',
  }
  return colorMap[category] || 'var(--hsbc-red)'
}

</script>

<style lang="scss" scoped>
.process-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: var(--ws-card-bg);
  border: 1px solid var(--ws-card-border);
  border-radius: var(--ws-radius-card);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  color: var(--ws-text);
  text-decoration: none;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  }

  &:has(.process-link:focus-visible) {
    outline: 2px solid var(--primary-color);
    outline-offset: 3px;
  }
}

.process-link {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  background: linear-gradient(135deg, #f8f9fa 0%, #eceef1 100%);
}

.process-icon-svg {
  width: 56px;
  height: 56px;
  :deep(svg) { width: 100%; height: 100%; }
}

.card-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  padding: 18px 20px;
  text-align: center;
}

.process-name, .process-desc {
  align-self: stretch;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow-wrap: anywhere;
}

.process-name {
  margin: 0 0 8px;
  min-height: 48px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}

.process-desc {
  margin: 0 0 14px;
  min-height: 36px;
  font-size: 12px;
  line-height: 18px;
  color: var(--ws-text-secondary);
}

.process-version {
  margin-top: auto;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--ws-canvas);
  color: var(--ws-text-secondary);
  font-size: 11px;
  line-height: 18px;
}

.favorite-button {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--ws-card-border);
  border-radius: 50%;
  z-index: 1;
  cursor: pointer;
  font-size: 18px;
  background: var(--ws-card-bg);
  color: var(--ws-text-secondary);

  &.is-favorite { color: #a36a00; }
  &:hover { background: #fbf0dd; }
  &:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 2px; }
  &:disabled { cursor: wait; opacity: 0.55; }
}

@media (prefers-reduced-motion: reduce) {
  .process-card { transition: none; }
  .process-card:hover { transform: none; }
}
</style>
