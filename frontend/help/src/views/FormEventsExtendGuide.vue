<template>
  <GuideArticle
    test-id="form-events-extend-guide-page"
    page-title-key="formEventsExtend.pageTitle"
    intro-key="formEventsExtend.intro"
    crumb-key="formEventsExtend.crumb"
    flow-title-key="formEventsExtend.flowTitle"
    :flow-keys="flowKeys"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideRelated, type GuideSample, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'formEventsExtend.flow1',
  'formEventsExtend.flow2',
  'formEventsExtend.flow3',
  'formEventsExtend.flow4',
]

const related: GuideRelated[] = [
  { to: '/form-events#params', titleKey: 'guides.formEvents.title' },
  { to: '/form-events#lookup', titleKey: 'formEventsGuide.lookupTitle' },
  { to: '/form-events#required', titleKey: 'formEventsGuide.requiredTitle' },
  { to: '/form-events#visibility', titleKey: 'formEventsGuide.visTitle' },
  { to: '/form-events#form-level', titleKey: 'formEventsGuide.formLevelTitle' },
]

function ctl(anchor: string, samples: GuideSample[]): GuideSection {
  return {
    anchor,
    titleKey: `formEventsExtend.${anchor}Title`,
    bodyKey: `formEventsExtend.${anchor}Body`,
    sampleLayout: 'block',
    samples,
  }
}

const sections: GuideSection[] = [
  ctl('subTable', [
    { code: "if (!value || !value.length) { api.setFieldError(field, 'Add at least one line') } else { api.clearFieldError(field) }", hintKey: 'formEventsExtend.subTableChange' },
  ]),
  ctl('inlineSubForm', [
    { code: "api.clearFieldError('item_name')", hintKey: 'formEventsExtend.inlineClick' },
  ]),
  ctl('linkForm', [
    { code: "if (!value) { api.setFieldError(field, 'Open the linked form') } else { api.clearFieldError(field) }", hintKey: 'formEventsExtend.linkFormChange' },
  ]),
  ctl('lookup', [
    { code: "if (value) { api.setValue('cost_center', value) }", hintKey: 'formEventsExtend.lookupChange' },
    { code: "api.setLookupFilter(field, [{ fieldName: 'status', value: 'Active', matchType: 'eq' }])\napi.refresh(field)", hintKey: 'formEventsExtend.lookupFilter' },
  ]),
  ctl('owner', [
    { code: "if (value) { api.setValue('requester', value) }", hintKey: 'formEventsExtend.ownerChange' },
  ]),
  ctl('recordNote', [
    { code: "api.clearFieldError('notes')", hintKey: 'formEventsExtend.recordNoteClick' },
  ]),
  ctl('miAssignment', [
    { code: "api.clearFieldError('requester')", hintKey: 'formEventsExtend.miClick' },
  ]),
]
</script>
