export default {
  viewDesignGuide: {
    pageTitle: 'View Design',
    crumb: 'Developer Workstation · Function Units · View Design',
    intro:
      'Each view is a published list of one table. Access control decides who sees it in User Portal. Columns come from Table Design. Create a Main Table first.',
    flowTitle: 'Order of work',
    flow1: 'Open View Design. A Main Table must already exist',
    flow2: 'Select a view, or Create view / Generate default views',
    flow3: 'Add Table columns (and Lookup / Related columns if needed)',
    flow4: 'Set Name, Export, Detail form, Access control, then Sort / Filter',
    flow5: 'Save, then deploy the Function Unit. The view takes effect in User Portal only after the deployment, and only for people who match Access control',
    whatTitle: 'What View Design is',
    whatBody:
      'Function Unit tab View Design. Left: Views grouped by table. Default marks the default view for that table. This is not Form Design and not Table Design.',
    workspaceFigure: 'View Design. Left: views by table. Right: columns, Name, Access control (Business Units and Roles).',
    columnsTitle: 'Columns',
    columnsBody:
      'Table columns lists fields on this table. Lookup columns and Related columns add fields from linked tables. Add selected appends the ticks. Clear all removes every column from the grid.',
    catViews: 'Left list. Pick a view to edit. Empty until you create one or generate defaults.',
    catDefaults: 'Creates a starter view per table that has none.',
    catCreate: 'Adds a blank view on the current table.',
    catTableColumns: 'Fields on this table. Search filters the list. Primary key fields are marked.',
    catAddSelected: 'Appends ticked catalog fields to the view grid.',
    propsTitle: 'Name, toolbar, sort, filter',
    propsBody:
      'Name is the portal label. It must be unique within the Function Unit (case and surrounding spaces are ignored), because User Portal and BI [[/bi-data-view-assignment]] find the view by its name; after a rename, bind the view\'s existing BI dashboard assignments again. Portal toolbar Show Export button is off until you tick it. Detail form is for SUB tables; Main Table rows open the request detail page and use No detail page.',
    catName: 'Required label in the portal view list.',
    catExport: 'On: portal users see Export. Off is the default.',
    catDetail: 'SUB views: which form opens a row. Main Table: No detail page.',
    catSort: 'Sort by, then Then sort by. Click the arrow for ascending or descending.',
    catFilter: 'Filter by. Edit filters opens the condition dialog (Equals, Contains, …).',
    accessTitle: 'Access control',
    accessBody:
      'When Business Units and Roles are both empty, nobody sees this view after publishing. If you configure access, pick at least one Business Unit and at least one Role. A user must match both in the current Business Unit and Role. System Administrator is checked the same way as everyone else.',
    catAccess: 'Section on the right. Hint under the heading states the empty and paired rules.',
    catBu: 'Who may see the view, by business unit. Placeholder: Select BUs that can see this view.',
    catRoles: 'Who may see the view, by role inside the selected units. Options load from those units, not the global role list.',
    catRolesFirst: 'Roles stays disabled until a Business Unit is selected. Placeholder until then: Select a Business Unit first.',
    catInvolved:
      'Off: everyone who passed BU+Role sees all rows. On: only initiator, assignee, and MI participants, for every user including System Administrator.',
    failTitle: 'When it fails',
    failPair:
      'Save is blocked if only BU or only Role is set. Toast: Configure both BU and Role together, or leave both empty (nobody can see the view).',
    failEmpty: 'Both empty: Save still works and warns that nobody will see the view; in Portal nobody sees it, System Administrator included.',
    failAnd: 'BU and Role both set: the current workspace of the person must be one selected unit with one selected role. Roles held in different workspaces are not combined.',
    failMainTable: 'Need a Main Table in Table Design first. The list reads Create a Main Table in Table Design first.',
  },
}
