<template>
  <article class="help-guide" :data-testid="testId">
    <p v-if="crumbKey" class="help-crumb">{{ t(crumbKey) }}</p>
    <h1>{{ t(pageTitleKey) }}</h1>
    <p class="help-intro">{{ t(introKey) }}</p>
    <section v-for="section in sections" :id="section.anchor" :key="section.titleKey">
      <h2>{{ t(section.titleKey) }}</h2>
      <p>{{ t(section.bodyKey) }}</p>
      <figure v-if="section.figure" class="help-figure">
        <img
          :src="assetUrl(section.figure.src)"
          :alt="t(section.figure.captionKey)"
        />
        <figcaption>{{ t(section.figure.captionKey) }}</figcaption>
      </figure>
      <ul v-if="section.samples?.length">
        <li v-for="sample in section.samples" :key="sample.code">
          <code>{{ sample.code }}</code>
          <span>{{ t(sample.hintKey) }}</span>
        </li>
      </ul>
    </section>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

export interface GuideSample {
  code: string
  hintKey: string
}

export interface GuideFigure {
  src: string
  captionKey: string
}

export interface GuideSection {
  titleKey: string
  bodyKey: string
  anchor?: string
  figure?: GuideFigure
  samples?: GuideSample[]
}

defineProps<{
  testId: string
  pageTitleKey: string
  introKey: string
  crumbKey?: string
  sections: GuideSection[]
}>()

const { t } = useI18n()

function assetUrl(src: string): string {
  const base = import.meta.env.BASE_URL
  return `${base}${src.replace(/^\//, '')}`
}
</script>
