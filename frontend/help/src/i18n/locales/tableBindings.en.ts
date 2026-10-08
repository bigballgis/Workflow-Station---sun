export default {
  tableBindingsGuide: {
    pageTitle: 'Manage Table Bindings',
    crumb: 'Developer Workstation · Function Units · Form Design · Manage Table Bindings',
    intro:
      'Attach MAIN, SUB, ACTION, and RELATION tables to this form. On Purchase Request the screenshot is PRIMARY help_pr plus SUB help_pr_line. The grid widget is [[/form-ctl-sub-table]].',
    flowTitle: 'Order of work',
    flow1: 'Open Form Design and Edit this form',
    flow2: 'Click Manage Table Bindings',
    flow3: 'Add Binding: type, table, then Filter foreign key for a Sub Table',
    flow4: 'Add. Close. Save the form',
    flow5: 'On the canvas, Sub Table Binding picks that SUB — [[/form-ctl-sub-table]]',
    whatTitle: 'What Manage Table Bindings is',
    whatBody:
      'Form Design toolbar Manage Table Bindings. The dialog title is Table Binding Management. It attaches tables from [[/table-design]] to this form. It is not the Sub-Table grid. Lookup reads Relation Table bindings — [[/form-ctl-lookup]]. Process and Task forms allow one Primary (MAIN) plus Sub (SUB) tables, and RELATED for lookup data.',
    listTitle: 'Binding list',
    listBody:
      'Click Manage Table Bindings. The list is empty until you add a row (No table bindings). Purchase Request shows Purchase Request as Primary Table (help_pr) and Line items as Sub Table (help_pr_line). Edit or delete from Operations. Primary cannot be deleted.',
    listFigure:
      'Purchase Request. Table Binding Management: Purchase Request Primary Table (help_pr), Line items Sub Table (help_pr_line), Add Binding.',
    catAddBinding: 'Opens Add Binding. On the list, Edit Table Binding uses the same dialog.',
    catTableName: 'Name people see in the list (Table Design display name). Purchase Request uses help_pr and help_pr_line.',
    catBindingTypeCol: 'Primary Table, Sub Table, Action Table, or Relation Table.',
    catModeCol: 'Editable or Read Only for that binding.',
    catFkCol: 'Declared filter foreign key on a Sub Table. Blank on Primary Table.',
    catLinkModeCol: 'Structural FK or MI Participant Row. Blank when the type does not use a link.',
    catSubModeCol: 'Full Mode (Form + List) or Form Only Mode. Sub Table only.',
    catOperations: 'Edit and Delete. Delete is blocked on the Primary Table binding.',
    addTitle: 'Add Binding',
    addBody:
      'Add Binding (or Edit on a row). Pick Binding Type first. Sub Table needs a Primary Table on this form. Select Table, Binding Mode, then Link Mode. On Structural FK pick Filter foreign key. Add writes the row; Edit uses Save. Close returns to the canvas. Save the form.',
    addFigure:
      'Edit Table Binding for Line items (help_pr_line). Binding Type, Select Table, Binding Mode, Link Mode, Filter foreign key, Foreign-key fill source.',
    catBindingType: 'Required. Primary Table, Sub Table, Action Table, or Relation Table. Blank: Please select a binding type.',
    catPrimaryTable: 'MAIN table for this form. One Primary Table per form. Must exist in [[/table-design]].',
    catSubTable: 'SUB table shown by a Sub-Table control. Needs a Primary Table first, plus a declared foreign key.',
    catActionTable: 'ACTION table. Same dialog as Sub Table for table and mode.',
    catRelatedTable:
      'RELATION table for Lookup. Function-unit Relation table or a deployed relation table. Can be added without a Primary Table.',
    catSubMode: 'Sub Table only. Full Mode (Form + List) or Form Only Mode. Blank uses Full Mode.',
    catSubModeFull: 'Creates form design and list view for that SUB.',
    catSubModeFormOnly: 'Creates form design only. No list view.',
    catSelectTable: 'Required. Placeholder Select a table to bind. The list matches Binding Type. Blank: Please select a table.',
    catBindingMode: 'Required. Editable or Read Only. Blank: Please select a binding mode.',
    catEditable: 'People can change rows on this binding.',
    catReadOnly: 'People can see rows and cannot change them from this binding.',
    catLinkMode: 'Sub Table. Structural FK or MI Participant Row. Structural FK is the default.',
    catStructuralFk: 'Fills parent keys from Table Design foreign keys. Pick Filter foreign key when the table declares more than one.',
    catMiRow: 'Links rows to a multi-instance element. Pick Participant Row Field (usually the SUB primary key).',
    catFilterFk:
      'Required on Structural FK Sub Table. Placeholder Select foreign key field for primary table. Each Sub Table binding of the same table on this form must use a different foreign key.',
    catParticipant: 'MI Participant Row. Field that identifies the participant row. Blank leaves the mode unusable.',
    fillTitle: 'Foreign-key fill source',
    fillBody:
      'On a Sub Table with Structural FK, each declared foreign key has Foreign-key fill source. Shown after Select Table. Purchase Request help_pr_line usually has one FK to help_pr — leave Auto. Named ancestor binding is for a nested parent already on this form, not a second FK on help_pr_line.',
    catFillSources:
      'One row per declared foreign key. Label is Field Name (Display Name). Blank kind is Auto (unique table only).',
    catFillAuto: 'Default. Uses the unique ancestor row of that table. Fails when two different rows of the same table are in context.',
    catFillParent: 'Takes the value from the host / parent row of this grid.',
    catFillPrimary: 'Takes the value from the Primary Table row on this form (help_pr on Purchase Request).',
    catFillAncestor: 'Takes the value from another binding on this form. Then Select ancestor binding is required.',
    catSelectAncestor:
      'Shown when kind is Named ancestor binding. Placeholder Select ancestor binding. Blank cannot save that source.',
    sameTableTitle: 'Same table, two Sub bindings',
    sameTableBody:
      'A form may bind the same SUB table twice when Filter foreign key differs. Primary Table and Relation Table stay one per table. Purchase Request has one help_pr_line binding. Do not add a second foreign key on help_pr_line for the screenshot. Two bindings that reuse the same filter are rejected.',
    failTitle: 'When it fails',
    failPrimaryFirst:
      'Add a Primary (MAIN) table binding first — sub-table bindings require a primary one. (Related bindings can be added independently.)',
    failPrimaryExists:
      'A primary binding already exists for this form. Each form can only have one primary table.',
    failBindingExists:
      'A conflicting table binding already exists. Sub-table bindings on the same table must use different declared foreign keys.',
    failNoFk: 'No structural FK fields on this table yet. Configure at least one FK field in Table Design, or choose MI Participant Row.',
    failFilterRequired: 'Select which declared foreign key this binding filters by.',
    failSelectTable: 'Please select a table.',
    failCannotDeletePrimary: 'Primary table binding cannot be deleted.',
    failNoMain: 'No MAIN table found in this function unit. Create one in Table Designer first.',
    failNoSub: 'No SUB table found in this function unit. Create one in Table Designer first.',
  },
}
