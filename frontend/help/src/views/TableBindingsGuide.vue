<template>
  <GuideArticle
    test-id="table-bindings-guide-page"
    page-title-key="tableBindingsGuide.pageTitle"
    intro-key="tableBindingsGuide.intro"
    crumb-key="tableBindingsGuide.crumb"
    flow-title-key="tableBindingsGuide.flowTitle"
    :flow-keys="flowKeys"
    :related="related"
    :jump-links="jumpLinks"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, { type GuideJump, type GuideRelated, type GuideSection } from '@/components/GuideArticle.vue'

const flowKeys = [
  'tableBindingsGuide.flow1',
  'tableBindingsGuide.flow2',
  'tableBindingsGuide.flow3',
  'tableBindingsGuide.flow4',
  'tableBindingsGuide.flow5',
]

const related: GuideRelated[] = [
  { to: '/table-design', titleKey: 'guides.tableDesign.title' },
  { to: '/form-ctl-sub-table', titleKey: 'nav.formCtlSubTable' },
  { to: '/form-ctl-lookup', titleKey: 'nav.formCtlLookup' },
]

const jumpLinks: GuideJump[] = [
  { anchor: 'list', titleKey: 'tableBindingsGuide.listTitle' },
  { anchor: 'add', titleKey: 'tableBindingsGuide.addTitle' },
  { anchor: 'fill', titleKey: 'tableBindingsGuide.fillTitle' },
  { anchor: 'same-table', titleKey: 'tableBindingsGuide.sameTableTitle' },
]

const sections: GuideSection[] = [
  {
    titleKey: 'tableBindingsGuide.whatTitle',
    bodyKey: 'tableBindingsGuide.whatBody',
  },
  {
    anchor: 'list',
    titleKey: 'tableBindingsGuide.listTitle',
    bodyKey: 'tableBindingsGuide.listBody',
    figure: {
      src: 'guides/dw-table-bindings-list.png',
      captionKey: 'tableBindingsGuide.listFigure',
    },
    sampleLayout: 'block',
    samples: [
      { code: 'Add Binding', hintKey: 'tableBindingsGuide.catAddBinding' },
      { code: 'Table Name', hintKey: 'tableBindingsGuide.catTableName' },
      { code: 'Binding Type', hintKey: 'tableBindingsGuide.catBindingTypeCol' },
      { code: 'Mode', hintKey: 'tableBindingsGuide.catModeCol' },
      { code: 'Foreign Key Field', hintKey: 'tableBindingsGuide.catFkCol' },
      { code: 'Link Mode', hintKey: 'tableBindingsGuide.catLinkModeCol' },
      { code: 'Sub Mode', hintKey: 'tableBindingsGuide.catSubModeCol' },
      { code: 'Operations', hintKey: 'tableBindingsGuide.catOperations' },
    ],
  },
  {
    anchor: 'add',
    titleKey: 'tableBindingsGuide.addTitle',
    bodyKey: 'tableBindingsGuide.addBody',
    figure: {
      src: 'guides/dw-table-bindings-add-dialog.png',
      captionKey: 'tableBindingsGuide.addFigure',
    },
    sampleLayout: 'block',
    samples: [
      { code: 'Binding Type', hintKey: 'tableBindingsGuide.catBindingType' },
      { code: 'Primary Table', hintKey: 'tableBindingsGuide.catPrimaryTable' },
      { code: 'Sub Table', hintKey: 'tableBindingsGuide.catSubTable' },
      { code: 'Action Table', hintKey: 'tableBindingsGuide.catActionTable' },
      { code: 'Relation Table', hintKey: 'tableBindingsGuide.catRelatedTable' },
      { code: 'Sub Mode', hintKey: 'tableBindingsGuide.catSubMode' },
      { code: 'Full Mode (Form + List)', hintKey: 'tableBindingsGuide.catSubModeFull' },
      { code: 'Form Only Mode', hintKey: 'tableBindingsGuide.catSubModeFormOnly' },
      { code: 'Select Table', hintKey: 'tableBindingsGuide.catSelectTable' },
      { code: 'Binding Mode', hintKey: 'tableBindingsGuide.catBindingMode' },
      { code: 'Editable', hintKey: 'tableBindingsGuide.catEditable' },
      { code: 'Read Only', hintKey: 'tableBindingsGuide.catReadOnly' },
      { code: 'Link Mode', hintKey: 'tableBindingsGuide.catLinkMode' },
      { code: 'Structural FK', hintKey: 'tableBindingsGuide.catStructuralFk' },
      { code: 'MI Participant Row', hintKey: 'tableBindingsGuide.catMiRow' },
      { code: 'Filter foreign key', hintKey: 'tableBindingsGuide.catFilterFk' },
      { code: 'Participant Row Field', hintKey: 'tableBindingsGuide.catParticipant' },
    ],
  },
  {
    anchor: 'fill',
    titleKey: 'tableBindingsGuide.fillTitle',
    bodyKey: 'tableBindingsGuide.fillBody',
    sampleLayout: 'block',
    samples: [
      { code: 'Foreign-key fill source', hintKey: 'tableBindingsGuide.catFillSources' },
      { code: 'Auto (unique table only)', hintKey: 'tableBindingsGuide.catFillAuto' },
      { code: 'Host / parent row', hintKey: 'tableBindingsGuide.catFillParent' },
      { code: 'Primary form', hintKey: 'tableBindingsGuide.catFillPrimary' },
      { code: 'Named ancestor binding', hintKey: 'tableBindingsGuide.catFillAncestor' },
      { code: 'Select ancestor binding', hintKey: 'tableBindingsGuide.catSelectAncestor' },
    ],
  },
  {
    anchor: 'same-table',
    titleKey: 'tableBindingsGuide.sameTableTitle',
    bodyKey: 'tableBindingsGuide.sameTableBody',
  },
  {
    titleKey: 'tableBindingsGuide.failTitle',
    failKeys: [
      'tableBindingsGuide.failPrimaryFirst',
      'tableBindingsGuide.failPrimaryExists',
      'tableBindingsGuide.failBindingExists',
      'tableBindingsGuide.failNoFk',
      'tableBindingsGuide.failFilterRequired',
      'tableBindingsGuide.failSelectTable',
      'tableBindingsGuide.failCannotDeletePrimary',
      'tableBindingsGuide.failNoMain',
      'tableBindingsGuide.failNoSub',
    ],
  },
]
</script>
