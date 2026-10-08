export default {
  tableBindingsGuide: {
    pageTitle: '管理表绑定',
    crumb: '开发工作站 · 功能单元 · 表单设计 · 管理表绑定',
    intro:
      '把 MAIN、SUB、ACTION、RELATION 表挂到当前表单。采购申请截图是 PRIMARY help_pr 加 SUB help_pr_line。网格控件见 [[/form-ctl-sub-table]]。',
    flowTitle: '操作顺序',
    flow1: '打开表单设计，Edit 这张表单',
    flow2: '点 Manage Table Bindings',
    flow3: 'Add Binding：选类型、表；Sub Table 再选 Filter foreign key',
    flow4: 'Add。Close。保存表单',
    flow5: '画布上 Sub Table Binding 选这条 SUB — [[/form-ctl-sub-table]]',
    whatTitle: '管理表绑定是什么',
    whatBody:
      '表单设计工具栏 Manage Table Bindings。对话框标题是 Table Binding Management。把 [[/table-design]] 里的表挂到这张表单。不是 Sub-Table 网格。Lookup 读 Relation Table 绑定 — [[/form-ctl-lookup]]。流程/任务表单：一张 Primary（MAIN）加多张 Sub（SUB），RELATED 给 Lookup 用。',
    listTitle: '绑定列表',
    listBody:
      '点 Manage Table Bindings。没有行时显示 No table bindings。采购申请列表里 Primary Table 显示 Purchase Request（help_pr），Sub Table 显示 Line items（help_pr_line）。Operations 里 Edit 或删除。Primary 不能删。',
    listFigure:
      '采购申请。Table Binding Management：Purchase Request 为 Primary Table（help_pr），Line items 为 Sub Table（help_pr_line），Add Binding。',
    catAddBinding: '打开 Add Binding。列表上的 Edit Table Binding 用同一个对话框。',
    catTableName: '列表里给人看的名称（表设计显示名）。采购申请对应 help_pr 和 help_pr_line。',
    catBindingTypeCol: 'Primary Table、Sub Table、Action Table 或 Relation Table。',
    catModeCol: '这条绑定是 Editable 还是 Read Only。',
    catFkCol: 'Sub Table 上声明的过滤外键。Primary Table 为空。',
    catLinkModeCol: 'Structural FK 或 MI Participant Row。不用链接的类型为空。',
    catSubModeCol: 'Full Mode (Form + List) 或 Form Only Mode。仅 Sub Table。',
    catOperations: 'Edit 和 Delete。Primary Table 绑定不能 Delete。',
    addTitle: 'Add Binding',
    addBody:
      'Add Binding（或某一行 Edit）。先选 Binding Type。Sub Table 要求这张表单已有 Primary Table。再选 Select Table、Binding Mode、Link Mode。Structural FK 要选 Filter foreign key。Add 写入一行；Edit 用 Save。Close 回到画布。再保存表单。',
    addFigure:
      'Line items（help_pr_line）的 Edit Table Binding。Binding Type、Select Table、Binding Mode、Link Mode、Filter foreign key、Foreign-key fill source。',
    catBindingType: '必填。Primary Table、Sub Table、Action Table 或 Relation Table。空白：Please select a binding type。',
    catPrimaryTable: '这张表单的 MAIN 表。每张表单一条 Primary Table。表必须先在 [[/table-design]] 建好。',
    catSubTable: 'Sub-Table 控件显示的 SUB 表。要先有 Primary Table，并声明外键。',
    catActionTable: 'ACTION 表。选表和模式与 Sub Table 相同。',
    catRelatedTable:
      '给 Lookup 用的 RELATION 表。功能单元 Relation 表或已部署的关系表。可以没有 Primary Table。',
    catSubMode: '仅 Sub Table。Full Mode (Form + List) 或 Form Only Mode。空白用 Full Mode。',
    catSubModeFull: '生成该 SUB 的表单设计和列表视图。',
    catSubModeFormOnly: '只生成表单设计，没有列表视图。',
    catSelectTable: '必填。占位 Select a table to bind。列表随 Binding Type 变化。空白：Please select a table。',
    catBindingMode: '必填。Editable 或 Read Only。空白：Please select a binding mode。',
    catEditable: '这条绑定上可以改行。',
    catReadOnly: '这条绑定只能看，不能改。',
    catLinkMode: 'Sub Table。Structural FK 或 MI Participant Row。默认 Structural FK。',
    catStructuralFk: '按表设计外键填父键。一张表声明了多个外键时，选 Filter foreign key。',
    catMiRow: '把行连到多实例节点。选 Participant Row Field（通常是 SUB 主键）。',
    catFilterFk:
      'Structural FK 的 Sub Table 必填。占位 Select foreign key field for primary table。同一张表在这张表单上的每条 Sub Table 绑定必须用不同外键。',
    catParticipant: 'MI Participant Row。标识参与人行的字段。空白则该模式不可用。',
    fillTitle: 'Foreign-key fill source',
    fillBody:
      'Structural FK 的 Sub Table 上，每个已声明外键有 Foreign-key fill source。选完 Select Table 后出现。采购申请 help_pr_line 通常只有指向 help_pr 的一个外键 — 保持 Auto。Named ancestor binding 用于这张表单上已有的嵌套父绑定，不是给 help_pr_line 再加一个外键。',
    catFillSources:
      '每个已声明外键一行。标签是 Field Name（Display Name）。未选种类等于 Auto (unique table only)。',
    catFillAuto: '默认。用该表唯一的祖先行。上下文里同一张表出现两行不同记录时失败。',
    catFillParent: '从当前网格的 host / parent 行取值。',
    catFillPrimary: '从这张表单 Primary Table 行取值（采购申请是 help_pr）。',
    catFillAncestor: '从这张表单上另一条绑定取值。接着必选 Select ancestor binding。',
    catSelectAncestor:
      '种类为 Named ancestor binding 时显示。占位 Select ancestor binding。空白不能保存该来源。',
    sameTableTitle: '同一张表，两条 Sub 绑定',
    sameTableBody:
      'Filter foreign key 不同时，同一张 SUB 表可以绑两次。Primary Table 和 Relation Table 仍是一表一条。采购申请只有一条 help_pr_line。截图不要给 help_pr_line 加第二个外键。两条绑定用同一个过滤外键会被拒绝。',
    failTitle: '失败时',
    failPrimaryFirst:
      'Add a Primary (MAIN) table binding first — sub-table bindings require a primary one. (Related bindings can be added independently.)',
    failPrimaryExists:
      'A primary binding already exists for this form. Each form can only have one primary table.',
    failBindingExists:
      'A conflicting table binding already exists. Sub-table bindings on the same table must use different declared foreign keys.',
    failNoFk: 'No structural FK fields on this table yet. 在表设计配置至少一个外键，或改选 MI Participant Row。',
    failFilterRequired: 'Select which declared foreign key this binding filters by.',
    failSelectTable: 'Please select a table.',
    failCannotDeletePrimary: 'Primary table binding cannot be deleted.',
    failNoMain: 'No MAIN table found in this function unit. Create one in Table Designer first.',
    failNoSub: 'No SUB table found in this function unit. Create one in Table Designer first.',
  },
}
