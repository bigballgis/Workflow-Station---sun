<template>
  <GuideArticle
    test-id="rt-structure-guide-page"
    page-title-key="rtStructureGuide.pageTitle"
    intro-key="rtStructureGuide.intro"
    crumb-key="rtStructureGuide.crumb"
    flow-title-key="rtStructureGuide.flowTitle"
    :flow-keys="flowKeys"
    :jump-links="jumpLinks"
    :related="related"
    :sections="sections"
  />
</template>

<script setup lang="ts">
import GuideArticle, {
  type GuideJump,
  type GuideRelated,
  type GuideSection,
} from '@/components/GuideArticle.vue'

const flowKeys = [
  'rtStructureGuide.flow1',
  'rtStructureGuide.flow2',
  'rtStructureGuide.flow3',
  'rtStructureGuide.flow4',
]

const jumpLinks: GuideJump[] = [
  { anchor: 'list', titleKey: 'rtStructureGuide.listTitle' },
  { anchor: 'form', titleKey: 'rtStructureGuide.formTitle' },
  { anchor: 'fields', titleKey: 'rtStructureGuide.fieldsTitle' },
  { anchor: 'lookup', titleKey: 'rtStructureGuide.lookupTitle' },
  { anchor: 'access', titleKey: 'rtStructureGuide.accessTitle' },
  { anchor: 'permissions', titleKey: 'rtStructureGuide.permTitle' },
  { anchor: 'read-only', titleKey: 'rtStructureGuide.readonlyTitle' },
  { anchor: 'fail', titleKey: 'rtStructureGuide.failTitle' },
]

const related: GuideRelated[] = [
  { to: '/table-data', titleKey: 'guides.tableData.title' },
  { to: '/computed-fields#relation', titleKey: 'guides.computedFields.title' },
  { to: '/table-design', titleKey: 'guides.tableDesign.title' },
  { to: '/form-ctl-lookup', titleKey: 'nav.formCtlLookup' },
]

const catalog = { sampleLayout: 'block' as const }

const sections: GuideSection[] = [
  {
    anchor: 'list',
    titleKey: 'rtStructureGuide.listTitle',
    bodyKey: 'rtStructureGuide.listBody',
    bodyKeys: ['rtStructureGuide.listLead'],
    figure: { src: 'guides/ac-rt-structure-list.png', captionKey: 'rtStructureGuide.listFigure' },
    ...catalog,
    samples: [
      { code: 'View ER Diagram', hintKey: 'rtStructureGuide.fErDiagram' },
      { code: 'Create Table', hintKey: 'rtStructureGuide.fCreate' },
      { code: 'Search... · Search · Export', hintKey: 'rtStructureGuide.fSearch' },
      { code: 'Display Name · Version', hintKey: 'rtStructureGuide.fNameVersion' },
      { code: 'Status', hintKey: 'rtStructureGuide.fStatus' },
      { code: 'Enable', hintKey: 'rtStructureGuide.fEnable' },
      { code: 'Portal', hintKey: 'rtStructureGuide.fPortal' },
      { code: 'Created At · Updated At', hintKey: 'rtStructureGuide.fDates' },
      { code: 'Edit / View', hintKey: 'rtStructureGuide.fEdit' },
      { code: 'Deploy', hintKey: 'rtStructureGuide.fDeploy' },
      { code: 'Version', hintKey: 'rtStructureGuide.fVersion' },
      { code: 'More', hintKey: 'rtStructureGuide.fMore' },
    ],
  },
  {
    anchor: 'form',
    titleKey: 'rtStructureGuide.formTitle',
    bodyKey: 'rtStructureGuide.formBody',
    bodyKeys: ['rtStructureGuide.formLead'],
    figure: { src: 'guides/ac-rt-structure-form-tl.png', captionKey: 'rtStructureGuide.formFigure' },
    ...catalog,
    samples: [
      { code: 'Display Name', hintKey: 'rtStructureGuide.fDisplayName' },
      { code: 'Table Name', hintKey: 'rtStructureGuide.fTableName' },
      { code: 'Description', hintKey: 'rtStructureGuide.fDescription' },
      { code: 'Function Unit', hintKey: 'rtStructureGuide.fFunctionUnit' },
    ],
  },
  {
    anchor: 'fields',
    titleKey: 'rtStructureGuide.fieldsTitle',
    bodyKey: 'rtStructureGuide.fieldsBody',
    bodyKeys: ['rtStructureGuide.fieldsLead'],
    figure: { src: 'guides/ac-rt-structure-fields.png', captionKey: 'rtStructureGuide.fieldsFigure' },
    ...catalog,
    samples: [
      { code: 'Display Name', hintKey: 'rtStructureGuide.fFieldDisplay' },
      { code: 'Field Name', hintKey: 'rtStructureGuide.fFieldName' },
      { code: 'Type', hintKey: 'rtStructureGuide.fType' },
      { code: 'Length', hintKey: 'rtStructureGuide.fLength' },
      { code: 'Nullable', hintKey: 'rtStructureGuide.fNullable' },
      { code: 'Primary Key', hintKey: 'rtStructureGuide.fPk' },
      { code: 'Foreign Key', hintKey: 'rtStructureGuide.fFk' },
      { code: 'Lookup', hintKey: 'rtStructureGuide.fLookup' },
      { code: 'Computed', hintKey: 'rtStructureGuide.fComputed' },
      { code: 'Default Value', hintKey: 'rtStructureGuide.fDefault' },
    ],
  },
  {
    anchor: 'lookup',
    titleKey: 'rtStructureGuide.lookupTitle',
    bodyKey: 'rtStructureGuide.lookupBody',
    bodyKeys: ['rtStructureGuide.lookupLead'],
    figure: { src: 'guides/ac-rt-structure-lookup.png', captionKey: 'rtStructureGuide.lookupFigure' },
    ...catalog,
    samples: [
      { code: 'Reference Table', hintKey: 'rtStructureGuide.fLkRef' },
      { code: 'Search Fields', hintKey: 'rtStructureGuide.fLkSearch' },
      { code: 'Display Fields', hintKey: 'rtStructureGuide.fLkDisplay' },
      { code: 'Label Field', hintKey: 'rtStructureGuide.fLkLabel' },
      { code: 'Allow multiple', hintKey: 'rtStructureGuide.fLkMultiple' },
      { code: 'Show backfill panel', hintKey: 'rtStructureGuide.fLkBackfill' },
      { code: 'Fixed Filters', hintKey: 'rtStructureGuide.fLkFilters' },
      { code: 'Derived / Cascade', hintKey: 'rtStructureGuide.fLkDerived' },
    ],
  },
  {
    anchor: 'access',
    titleKey: 'rtStructureGuide.accessTitle',
    bodyKey: 'rtStructureGuide.accessBody',
    bodyKeys: ['rtStructureGuide.accessLead'],
    figure: { src: 'guides/ac-rt-structure-access.png', captionKey: 'rtStructureGuide.accessFigure' },
    ...catalog,
    samples: [
      { code: 'Add Role', hintKey: 'rtStructureGuide.fAccAdd' },
      { code: 'Role · Type', hintKey: 'rtStructureGuide.fAccRole' },
      { code: 'Permission', hintKey: 'rtStructureGuide.fAccPermission' },
      { code: 'Created At · Created By', hintKey: 'rtStructureGuide.fAccCreated' },
      { code: 'Delete', hintKey: 'rtStructureGuide.fAccDelete' },
    ],
  },
  {
    anchor: 'permissions',
    titleKey: 'rtStructureGuide.permTitle',
    bodyKey: 'rtStructureGuide.permBody',
    figure: { src: 'guides/ac-rt-structure-tl-list.png', captionKey: 'rtStructureGuide.permFigure' },
    ...catalog,
    samples: [
      { code: 'System Administrator', hintKey: 'rtStructureGuide.permAdmin' },
      { code: 'Technical Lead', hintKey: 'rtStructureGuide.permLead' },
      { code: 'Auditor', hintKey: 'rtStructureGuide.permAuditor' },
    ],
  },
  {
    anchor: 'read-only',
    titleKey: 'rtStructureGuide.readonlyTitle',
    bodyKey: 'rtStructureGuide.readonlyBody',
    bodyKeys: ['rtStructureGuide.readonlyList', 'rtStructureGuide.readonlyShared'],
    figure: { src: 'guides/ac-rt-structure-readonly.png', captionKey: 'rtStructureGuide.readonlyFigure' },
  },
  {
    anchor: 'fail',
    titleKey: 'rtStructureGuide.failTitle',
    failKeys: [
      'rtStructureGuide.failDisplayName',
      'rtStructureGuide.failNameTaken',
      'rtStructureGuide.failFields',
      'rtStructureGuide.failLink',
      'rtStructureGuide.failTeam',
      'rtStructureGuide.failNotOnData',
      'rtStructureGuide.failLoad',
    ],
  },
]
</script>
