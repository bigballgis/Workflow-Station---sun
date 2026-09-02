<template>
  <GuideArticle
    test-id="form-events-layout-guide-page"
    page-title-key="formEventsLayout.pageTitle"
    intro-key="formEventsLayout.intro"
    crumb-key="formEventsLayout.crumb"
    flow-title-key="formEventsLayout.flowTitle"
    :flow-keys="flowKeys"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideRelated, type GuideSample, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'formEventsLayout.flow1',
  'formEventsLayout.flow2',
  'formEventsLayout.flow3',
  'formEventsLayout.flow4',
]

const related: GuideRelated[] = [
  { to: '/form-events#params', titleKey: 'guides.formEvents.title' },
  { to: '/form-events#required', titleKey: 'formEventsGuide.requiredTitle' },
  { to: '/form-events#visibility', titleKey: 'formEventsGuide.visTitle' },
  { to: '/form-events#chrome', titleKey: 'formEventsGuide.chromeTitle' },
  { to: '/form-events#form-level', titleKey: 'formEventsGuide.formLevelTitle' },
]

function ctl(anchor: string, samples: GuideSample[]): GuideSection {
  return {
    anchor,
    titleKey: `formEventsLayout.${anchor}Title`,
    bodyKey: `formEventsLayout.${anchor}Body`,
    sampleLayout: 'block',
    samples,
  }
}

const sections: GuideSection[] = [
  ctl('fcRow', [
    { code: "api.clearFieldError('request_title')", hintKey: 'formEventsLayout.rowClick' },
  ]),
  ctl('col', [
    { code: "api.clearFieldError('requester')", hintKey: 'formEventsLayout.colClick' },
  ]),
  ctl('elCard', [
    { code: "api.display(false, 'cost_center')", hintKey: 'formEventsLayout.cardClick' },
  ]),
  ctl('elTabs', [
    { code: "if (value) { api.clearFieldError('notes') }", hintKey: 'formEventsLayout.tabsTabChange' },
  ]),
  ctl('elTabPane', [
    { code: "api.clearFieldError('notes')", hintKey: 'formEventsLayout.tabPaneClick' },
  ]),
  ctl('elCollapse', [
    { code: "api.clearFieldError('request_title')", hintKey: 'formEventsLayout.collapseClick' },
  ]),
  ctl('elCollapseItem', [
    { code: "api.clearFieldError('cost_center')", hintKey: 'formEventsLayout.collapseItemClick' },
  ]),
  ctl('fcTitle', [
    { code: "api.setValue('notes', 'Opened from Title')", hintKey: 'formEventsLayout.titleClick' },
  ]),
  ctl('html', [
    { code: "api.clearFieldError('request_title')", hintKey: 'formEventsLayout.htmlClick' },
  ]),
  ctl('elDivider', [
    { code: "api.clearFieldError('requester')", hintKey: 'formEventsLayout.dividerClick' },
  ]),
  ctl('elAlert', [
    { code: "api.hidden(true, 'cost_center')", hintKey: 'formEventsLayout.alertClick' },
  ]),
  ctl('space', [
    { code: "api.clearFieldError('notes')", hintKey: 'formEventsLayout.spaceClick' },
  ]),
  ctl('elButton', [
    { code: "api.setValue('requester', user && user.displayName)", hintKey: 'formEventsLayout.buttonClick' },
  ]),
  ctl('elTag', [
    { code: "api.clearFieldError('cost_center')", hintKey: 'formEventsLayout.tagClick' },
  ]),
  ctl('elImage', [
    { code: "api.clearFieldError('notes')", hintKey: 'formEventsLayout.imageClick' },
  ]),
]
</script>
