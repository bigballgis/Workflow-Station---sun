export default {
  rtStructureGuide: {
    pageTitle: 'Table Structure',
    crumb: 'Admin Center · Relation Tables',
    intro:
      'Table Structure defines Admin Center Relation Tables: their fields and keys, which Function Units they belong to, versions, and who can use them in User Portal. Records are kept on [[/table-data]]. A table linked to no Function Unit is Common.',
    flowTitle: 'Order of work',
    flow1: 'Open Admin Center → Relation Tables → Table Structure and click Create Table.',
    flow2: 'Type Display Name, pick Function Unit, then add fields under Field Definitions and click Create Table.',
    flow3: 'Click Deploy on the row. Once deployed and enabled, the table is listed on Table Data.',
    flow4: 'For User Portal, open More → Access, add the roles, then switch Portal on.',
    listTitle: 'The table list',
    listBody:
      'The Function Unit panel narrows the list: All Function Units, Common, or one Function Unit with its table count. A table linked to several Function Units is counted under each.',
    listLead: 'Field catalog — Table Structure list.',
    listFigure: 'Table Structure as a System Administrator, searched for WP9 RT: the Function Unit panel, Status, Enable, Portal and the row actions.',
    fErDiagram:
      'Opens the diagram of the tables you can see with Status DEPLOYED, and their foreign keys. Drag from a field to a primary key to add a foreign key, click a line to delete it, then Save. Save writes back only the tables whose foreign keys you changed, so it needs write access to those tables; if a change would touch a table you cannot edit, Save is hidden. A saved table becomes UPDATED: it leaves the diagram and User Portal until you Deploy it again.',
    fCreate: 'Opens Create Table Structure. Shown only to roles that may create tables (see Who can do what).',
    fSearch:
      'Matches Display Name. Press Enter or click Search. Export downloads the checked rows as CSV, or the current page when none is checked.',
    fNameVersion:
      'Display Name is the name shown on Table Data and in lookups. Version is the latest deployed or rolled-back version; v0 before the first Deploy.',
    fStatus:
      'INIT: never deployed. DEPLOYED: the fields match the deployed version; only DEPLOYED tables are offered in User Portal and drawn on the ER diagram. UPDATED: fields, Display Name or Description changed after Deploy, including by Save on the ER diagram. ROLLBACK: rolled back to an earlier version, saved as a new version. An UPDATED or ROLLBACK table leaves User Portal and the ER diagram until you Deploy again; Table Data still lists it (it lists DEPLOYED, UPDATED and ROLLBACK). DRAFT is offered in the filter; new tables start as INIT. Changing only Function Unit keeps DEPLOYED.',
    fEnable:
      'Off: the table is not listed on Table Data or in User Portal. Portal can be switched only while Enable is on.',
    fPortal: 'On: the table appears in User Portal Relation Tables for the roles granted in Access, while its Status is DEPLOYED and Enable is on. After a change (UPDATED) or a rollback (ROLLBACK) it disappears from User Portal until you Deploy again.',
    fDates: 'When the table was created and last saved.',
    fEdit: 'Opens the table form. View instead of Edit: you cannot change this table; the form opens read-only with the title View Table Structure.',
    fDeploy:
      'After Confirm Deploy, saves the current fields as the next version (Change Log Initial deployment, later Structure update deployment). Missing audit fields are added.',
    fVersion:
      'Version History: Version, Deployed By, Deployed At, Change Log. Rollback restores that version’s field list — names, types, lengths, DECIMAL precision and scale, Nullable, Primary Key, Default Value and display names — as a new version (Change Log Rollback to version N, Status ROLLBACK). Foreign keys, Lookup Settings, PK generation and formulas are cleared by a rollback: set them again afterwards.',
    fMore:
      'ER Diagram (this table and the tables it links to directly), Compare (two versions, field by field: Added, Removed, Modified, Unchanged), Rollback (opens Version History), Access, Delete (asks Confirm Delete; cannot be undone).',
    formTitle: 'Create or edit a table',
    formBody:
      'Create Table Structure and Edit Table Structure use the same form. Create Table or Save Changes saves it; Cancel and Back leave without saving. A new table is listed on Table Data only after Deploy.',
    formLead: 'Field catalog — table form.',
    formFigure: 'Create Table Structure as a Technical Lead: Function Unit is required, Common is greyed out, only a unit of the team is offered, and saving without one shows the red message.',
    fDisplayName: 'Required. Blank shows Display name is required.',
    fTableName:
      'Generated automatically from display name when the table is created; it cannot be typed and keeps its name on edit. Must be unique on the whole platform, Table Design tables included.',
    fDescription: 'Optional.',
    fFunctionUnit:
      'Which Function Units the table belongs to; pick one or more. Common means none: every Function Unit can use the table. Picking Common clears the others, and picking a Function Unit clears Common. A System Administrator may leave it blank, which saves as Common. For a Technical Lead the field is required (placeholder Select Function Unit(s) of your teams): pick at least one Function Unit of a team you lead.',
    fieldsTitle: 'Field Definitions',
    fieldsBody:
      'Add Field adds a row, above the audit fields when they are listed. created_at, created_by, updated_at and updated_by are added on Deploy when missing; on Edit they are listed last, filled by the platform, and cannot be changed or removed. The bin icon at the end of a row removes it.',
    fieldsLead: 'Field catalog — one row per field.',
    fieldsFigure: 'Field Definitions of a deployed table: item_code is the key (UUID), label is a plain field, and the four audit fields at the bottom are locked.',
    fFieldDisplay: 'Label on Table Data and in lookups. On a new row, typing it suggests Field Name.',
    fFieldName: 'Key the value is stored under. Required: a blank name stops the save with All fields must have a name.',
    fType: 'VARCHAR, INTEGER, BIGINT, DECIMAL, BOOLEAN, DATE, TIMESTAMP, TEXT or LOOKUP. A new row starts as VARCHAR.',
    fLength: 'VARCHAR length. A new row starts at 255.',
    fNullable: 'On (default): the value may be empty.',
    fPk:
      'On: this field is part of the key. Pick Strategy — Manual, UUID, Auto increment, Prefixed sequence, Date + daily sequence, Date + monthly sequence or Custom format — and its Generation settings. A new key starts as UUID. The strategies are described on [[/table-design#pk]].',
    fFk:
      'Tick it, then click the link button: Reference Table (another table with Status DEPLOYED) and Reference Primary Key Fields (filled from that table’s primary keys). The button stays orange until both are set.',
    fLookup: 'Only on LOOKUP fields. The search button opens Lookup Settings (next section).',
    fComputed:
      'Not offered on audit, key, foreign-key or LOOKUP fields. Turn the switch on, click the pencil button and write the Formula. Relation Tables have no MAIN/SUB reads: [[/computed-fields#relation]].',
    fDefault: 'Optional value for new records.',
    lookupTitle: 'Lookup Settings',
    lookupBody:
      'A LOOKUP field stores a pick from another deployed table. On Table Data, Add and Edit show a search box for it. The settings work like the form Lookup control on [[/form-ctl-lookup]].',
    lookupLead: 'Field catalog — Lookup Settings.',
    lookupFigure: 'A new LOOKUP field Supplier: Lookup Settings with Reference Table WP9 RT common, and label as Search Field, Display Field and Label Field.',
    fLkRef: 'Required. Another table with Status DEPLOYED that you can see.',
    fLkSearch: 'Columns the search box matches. The search button stays orange until Reference Table and at least one Search Field are set.',
    fLkDisplay: 'Columns shown in the search results.',
    fLkLabel: 'Column shown after a row is picked. Blank shows the stored key.',
    fLkMultiple: 'On: several rows can be picked; the keys are stored as a list. Off (default): one row.',
    fLkBackfill: 'On (default): Add and Edit show the picked row read-only under the field.',
    fLkFilters:
      'Optional. Field, match (Equals, Contains, Starts with, Ends with) and Value; Add filter adds a row. Rows are filtered before the search.',
    fLkDerived:
      'Optional. Driven by field picks another LOOKUP field of this table. Behavior: Auto-fill takes this value from the parent pick; Filter only narrows this list. Join Columns pairs Parent ref, Match and This lookup ref; Add join adds a pair.',
    accessTitle: 'Access in User Portal',
    accessBody:
      'More → Access opens Access Config: which roles can use the table in User Portal. The table also needs Enable and Portal on.',
    accessLead: 'Field catalog — Access Config.',
    accessFigure: 'More → Access → Add Role: Permission, then the BU Role tab (Business Unit, BU Role) or the System Role tab.',
    fAccAdd:
      'Opens Add Role Access. Permission: Read & Write or Readonly. BU Role tab: pick a Business Unit, then one or more of its roles. System Role tab: every role without access yet is pre-selected; untick the ones you do not want.',
    fAccRole: 'The granted role and its type. No access configured: no role can use the table in User Portal.',
    fAccPermission: 'Read & Write or Readonly. Change it in the row.',
    fAccCreated: 'Who granted it and when.',
    fAccDelete: 'Removes the grant.',
    permTitle: 'Who can do what',
    permBody: 'What the list shows and what you can change follow your Admin Center role. Each row decides its own buttons.',
    permFigure: 'The same list as a Technical Lead of WP9 Team A: the Team B table is not listed, the panel shows only the team’s units, and the shared and Common tables show View with no Deploy.',
    permAdmin:
      'Every table. Create, edit, deploy, roll back and delete, switch Enable and Portal, and change Access on any table. Can link a table to any Function Unit, or to Common.',
    permLead:
      'Sees Common tables and tables linked to at least one Function Unit of a team you lead; the Function Unit panel lists only your teams’ Function Units. A table is yours to change only when every Function Unit it is linked to belongs to a team you lead. On Create Table, Function Unit offers only your teams’ Function Units, Common is greyed out, and at least one must be picked. Reference Table (Foreign Key, Lookup) lists only tables you can see.',
    permAuditor:
      'Every table, read-only: no Create Table, and every row opens with View. An Auditor who also leads a team can change that team’s tables, as a Technical Lead does.',
    readonlyTitle: 'A table you cannot change',
    readonlyBody:
      'Common tables, tables shared with another team, and tables linked to a Function Unit without a team are read-only for a Technical Lead; every table is read-only for an Auditor.',
    readonlyList:
      'Edit becomes View, and the form is titled View Table Structure. The form and the Field Definitions grid are read-only: no Add Field, no bin icon, no Save Changes. Deploy, Rollback and Delete are hidden, and Enable and Portal cannot be switched. Version History has no Rollback. Access Config has no Add Role or Delete, and Permission cannot be changed.',
    readonlyShared:
      'On a table shared with another team, Function Unit also shows that team’s Function Unit by name. You can read the name; you cannot open or change that Function Unit here.',
    readonlyFigure: 'A table shared with WP9 Team B, opened by a Technical Lead of Team A: View Table Structure, everything read-only, both teams’ units named under Function Unit.',
    failTitle: 'When it fails',
    failDisplayName: 'Display name is required: type Display Name.',
    failNameTaken:
      'Table name "…" already exists platform-wide. Please choose another name.: change Display Name so another Table Name is generated.',
    failFields: 'Please add at least one field, or All fields must have a name: add a field, or fill every Field Name.',
    failLink:
      'Link the table to at least one function unit of a team you lead, in red under Function Unit: a Technical Lead tried to save without one of their teams’ Function Units. Pick one. A call that skips the form gets the server’s wording instead: Link the relation table to at least one function unit of a team you lead.',
    failTeam:
      'This belongs to team X (code), which you do not lead, or This is shared by every team (a Common relation table or the Public workspace); only a System Administrator can change it: the request touched a table you cannot change. The pages hide such actions, so this appears only on a page opened before your access changed, or on a direct API call. Reload the page, or ask a System Administrator.',
    failNotOnData: 'The table is not on Table Data: it is still INIT (click Deploy), or Enable is off.',
    failLoad: 'Failed to load table structures: reload the page.',
  },
}
