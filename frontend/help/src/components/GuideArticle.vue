<template>
  <article class="help-guide" :data-testid="testId">
    <p v-if="crumbKey" class="help-crumb">{{ t(crumbKey) }}</p>
    <h1>{{ t(pageTitleKey) }}</h1>
    <p class="help-intro">{{ t(introKey) }}</p>
    <ol v-if="flowKeys?.length" class="help-flow" :aria-label="t(flowTitleKey)">
      <li v-for="(key, index) in flowKeys" :key="key">
        <span class="help-flow-n">{{ index + 1 }}</span>
        <span>{{ t(key) }}</span>
      </li>
    </ol>
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
    <nav v-if="related?.length" class="help-related" :aria-label="t(relatedTitleKey)">
      <h2>{{ t(relatedTitleKey) }}</h2>
      <ul>
        <li v-for="item in related" :key="item.to">
          <RouterLink :to="item.to">{{ t(item.titleKey) }}</RouterLink>
        </li>
      </ul>
    </nav>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

export interface GuideSample {
  code: string
  hintKey: string
}

export interface GuideFigure {
  src: string
  captionKey: string
}

export interface GuideRelated {
  to: string
  titleKey: string
}

export interface GuideSection {
  titleKey: string
  bodyKey: string
  anchor?: string
  figure?: GuideFigure
  samples?: GuideSample[]
}

withDefaults(
  defineProps<{
    testId: string
    pageTitleKey: string
    introKey: string
    crumbKey?: string
    flowTitleKey?: string
    flowKeys?: string[]
    relatedTitleKey?: string
    related?: GuideRelated[]
    sections: GuideSection[]
  }>(),
  {
    flowTitleKey: 'app.flowAria',
    relatedTitleKey: 'app.relatedTitle',
  },
)

const { t } = useI18n()

/** Bump when replacing `public/guides/*.png` so browsers do not keep the old file. */
const GUIDE_FIGURE_REV = '20260826'

function assetUrl(src: string): string {
  const base = import.meta.env.BASE_URL
  return `${base}${src.replace(/^\//, '')}?v=${GUIDE_FIGURE_REV}`
}
</script>
