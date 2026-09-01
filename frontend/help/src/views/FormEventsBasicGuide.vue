<template>
  <GuideArticle
    test-id="form-events-basic-guide-page"
    page-title-key="formEventsBasic.pageTitle"
    intro-key="formEventsBasic.intro"
    crumb-key="formEventsBasic.crumb"
    flow-title-key="formEventsBasic.flowTitle"
    :flow-keys="flowKeys"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideRelated, type GuideSample, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'formEventsBasic.flow1',
  'formEventsBasic.flow2',
  'formEventsBasic.flow3',
  'formEventsBasic.flow4',
]

const related: GuideRelated[] = [
  { to: '/form-events#params', titleKey: 'guides.formEvents.title' },
  { to: '/form-events#required', titleKey: 'formEventsGuide.requiredTitle' },
  { to: '/form-events#disabled', titleKey: 'formEventsGuide.disabledTitle' },
  { to: '/form-events#options', titleKey: 'formEventsGuide.optionsTitle' },
  { to: '/form-events#visibility', titleKey: 'formEventsGuide.visTitle' },
  { to: '/form-events#form-level', titleKey: 'formEventsGuide.formLevelTitle' },
]

function ctl(anchor: string, samples: GuideSample[]): GuideSection {
  return {
    anchor,
    titleKey: `formEventsBasic.${anchor}Title`,
    bodyKey: `formEventsBasic.${anchor}Body`,
    sampleLayout: 'block',
    samples,
  }
}

const sections: GuideSection[] = [
  ctl('input', [
    { code: "if (value) { api.setValue('requester', user && user.displayName) }", hintKey: 'formEventsBasic.inputChange' },
  ]),
  ctl('textarea', [
    { code: "if (value && value.length > 400) { api.setFieldError('notes', 'Keep notes under 400 characters') }", hintKey: 'formEventsBasic.textareaChange' },
  ]),
  ctl('password', [
    { code: "if (value && value.length < 8) { api.setFieldError(field, 'Use at least 8 characters') }", hintKey: 'formEventsBasic.passwordChange' },
  ]),
  ctl('inputNumber', [
    { code: "if (value < 1) { api.setValue('quantity', 1) }", hintKey: 'formEventsBasic.inputNumberChange' },
  ]),
  ctl('radio', [
    { code: "api.required(value === 'A', ['start_date', 'end_date'])", hintKey: 'formEventsBasic.radioChange' },
  ]),
  ctl('checkbox', [
    { code: "api.required(value && value.length > 0, ['notes'])", hintKey: 'formEventsBasic.checkboxChange' },
  ]),
  ctl('select', [
    { code: "var on = value === 'A'\napi.required(on, ['start_date', 'end_date'])", hintKey: 'formEventsBasic.selectChange' },
    { code: "api.setOptions('scenario', [{ label: 'A', value: 'A' }])", hintKey: 'formEventsBasic.selectOptions' },
  ]),
  ctl('switch', [
    { code: "api.hidden(!value, 'cost_center')", hintKey: 'formEventsBasic.switchChange' },
  ]),
  ctl('slider', [
    { code: "if (value > 80) { api.setFieldError(field, 'Keep the score at 80 or below') }", hintKey: 'formEventsBasic.sliderChange' },
  ]),
  ctl('rate', [
    { code: "if (value < 3) { api.required(true, ['notes']) }", hintKey: 'formEventsBasic.rateChange' },
  ]),
  ctl('datePicker', [
    { code: "if (value && formData.end_date && value > formData.end_date) { api.setFieldError('start_date', 'Start must be on or before Need-by') }", hintKey: 'formEventsBasic.dateChange' },
  ]),
  ctl('dateRange', [
    { code: "if (value && value[0] && value[1]) { api.setValue('start_date', value[0]); api.setValue('end_date', value[1]) }", hintKey: 'formEventsBasic.dateRangeChange' },
  ]),
  ctl('timePicker', [
    { code: "if (value) { api.clearFieldError(field) }", hintKey: 'formEventsBasic.timeChange' },
  ]),
  ctl('timeRange', [
    { code: "if (value && value[0] && value[1] && value[0] === value[1]) { api.setFieldError(field, 'End time must be after start') }", hintKey: 'formEventsBasic.timeRangeChange' },
  ]),
  ctl('cascader', [
    { code: "if (value) { api.clearFieldError(field) }", hintKey: 'formEventsBasic.cascaderChange' },
  ]),
  ctl('colorPicker', [
    { code: "if (value) { api.clearFieldError(field) }", hintKey: 'formEventsBasic.colorChange' },
  ]),
  ctl('upload', [
    { code: "api.setFieldError(field, 'Upload failed')", hintKey: 'formEventsBasic.uploadError' },
  ]),
  ctl('tree', [
    { code: "api.setValue('cost_center', value)", hintKey: 'formEventsBasic.treeNodeClick' },
  ]),
  ctl('elTreeSelect', [
    { code: "api.setValue('cost_center', value)", hintKey: 'formEventsBasic.treeSelectChange' },
  ]),
  ctl('transfer', [
    { code: "if (value && value.length) { api.clearFieldError(field) }", hintKey: 'formEventsBasic.transferChange' },
  ]),
  ctl('editor', [
    { code: "if (value) { api.clearFieldError('notes') }", hintKey: 'formEventsBasic.editorChange' },
  ]),
]
</script>
