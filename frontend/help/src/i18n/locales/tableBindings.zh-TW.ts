export default {
  tableBindingsGuide: {
    pageTitle: '管理表綁定',
    crumb: '開發工作站 · 功能單元 · 表單設計 · 管理表綁定',
    intro:
      '把 MAIN、SUB、ACTION、RELATION 表掛到目前表單。採購申請截圖是 PRIMARY help_pr 加 SUB help_pr_line。網格控制項見 [[/form-ctl-sub-table]]。',
    flowTitle: '操作順序',
    flow1: '開啟表單設計，Edit 這張表單',
    flow2: '點 Manage Table Bindings',
    flow3: 'Add Binding：選類型、表；Sub Table 再選 Filter foreign key',
    flow4: 'Add。Close。儲存表單',
    flow5: '畫布上 Sub Table Binding 選這條 SUB — [[/form-ctl-sub-table]]',
    whatTitle: '管理表綁定是什麼',
    whatBody:
      '表單設計工具列 Manage Table Bindings。對話框標題是 Table Binding Management。把 [[/table-design]] 裡的表掛到這張表單。不是 Sub-Table 網格。Lookup 讀 Relation Table 綁定 — [[/form-ctl-lookup]]。流程/任務表單：一張 Primary（MAIN）加多張 Sub（SUB），RELATED 給 Lookup 用。',
    listTitle: '綁定清單',
    listBody:
      '點 Manage Table Bindings。沒有列時顯示 No table bindings。採購申請清單裡 Primary Table 顯示 Purchase Request（help_pr），Sub Table 顯示 Line items（help_pr_line）。Operations 裡 Edit 或刪除。Primary 不能刪。',
    listFigure:
      '採購申請。Table Binding Management：Purchase Request 為 Primary Table（help_pr），Line items 為 Sub Table（help_pr_line），Add Binding。',
    catAddBinding: '開啟 Add Binding。清單上的 Edit Table Binding 用同一個對話框。',
    catTableName: '清單裡給人看的名稱（表設計顯示名）。採購申請對應 help_pr 和 help_pr_line。',
    catBindingTypeCol: 'Primary Table、Sub Table、Action Table 或 Relation Table。',
    catModeCol: '這條綁定是 Editable 還是 Read Only。',
    catFkCol: 'Sub Table 上宣告的過濾外鍵。Primary Table 為空。',
    catLinkModeCol: 'Structural FK 或 MI Participant Row。不用連結的類型為空。',
    catSubModeCol: 'Full Mode (Form + List) 或 Form Only Mode。僅 Sub Table。',
    catOperations: 'Edit 與 Delete。Primary Table 綁定不能 Delete。',
    addTitle: 'Add Binding',
    addBody:
      'Add Binding（或某一列 Edit）。先選 Binding Type。Sub Table 要求這張表單已有 Primary Table。再選 Select Table、Binding Mode、Link Mode。Structural FK 要選 Filter foreign key。Add 寫入一列；Edit 用 Save。Close 回到畫布。再儲存表單。',
    addFigure:
      'Line items（help_pr_line）的 Edit Table Binding。Binding Type、Select Table、Binding Mode、Link Mode、Filter foreign key、Foreign-key fill source。',
    catBindingType: '必填。Primary Table、Sub Table、Action Table 或 Relation Table。空白：Please select a binding type。',
    catPrimaryTable: '這張表單的 MAIN 表。每張表單一條 Primary Table。表必須先在 [[/table-design]] 建好。',
    catSubTable: 'Sub-Table 控制項顯示的 SUB 表。要先有 Primary Table，並宣告外鍵。',
    catActionTable: 'ACTION 表。選表和模式與 Sub Table 相同。',
    catRelatedTable:
      '給 Lookup 用的 RELATION 表。功能單元 Relation 表或已部署的關係表。可以沒有 Primary Table。',
    catSubMode: '僅 Sub Table。Full Mode (Form + List) 或 Form Only Mode。空白用 Full Mode。',
    catSubModeFull: '產生該 SUB 的表單設計和清單檢視。',
    catSubModeFormOnly: '只產生表單設計，沒有清單檢視。',
    catSelectTable: '必填。占位 Select a table to bind。清單隨 Binding Type 變化。空白：Please select a table。',
    catBindingMode: '必填。Editable 或 Read Only。空白：Please select a binding mode。',
    catEditable: '這條綁定上可以改列。',
    catReadOnly: '這條綁定只能看，不能改。',
    catLinkMode: 'Sub Table。Structural FK 或 MI Participant Row。預設 Structural FK。',
    catStructuralFk: '依表設計外鍵填父鍵。一張表宣告了多個外鍵時，選 Filter foreign key。',
    catMiRow: '把列連到多實例節點。選 Participant Row Field（通常是 SUB 主鍵）。',
    catFilterFk:
      'Structural FK 的 Sub Table 必填。占位 Select foreign key field for primary table。同一張表在這張表單上的每條 Sub Table 綁定必須用不同外鍵。',
    catParticipant: 'MI Participant Row。識別參與人列的欄位。空白則該模式不可用。',
    fillTitle: 'Foreign-key fill source',
    fillBody:
      'Structural FK 的 Sub Table 上，每個已宣告外鍵有 Foreign-key fill source。選完 Select Table 後出現。採購申請 help_pr_line 通常只有指向 help_pr 的一個外鍵 — 保持 Auto。Named ancestor binding 用於這張表單上已有的巢狀父綁定，不是給 help_pr_line 再加一個外鍵。',
    catFillSources:
      '每個已宣告外鍵一列。標籤是 Field Name（Display Name）。未選種類等於 Auto (unique table only)。',
    catFillAuto: '預設。用該表唯一的祖先列。上下文裡同一張表出現兩列不同紀錄時失敗。',
    catFillParent: '從目前網格的 host / parent 列取值。',
    catFillPrimary: '從這張表單 Primary Table 列取值（採購申請是 help_pr）。',
    catFillAncestor: '從這張表單上另一條綁定取值。接著必選 Select ancestor binding。',
    catSelectAncestor:
      '種類為 Named ancestor binding 時顯示。占位 Select ancestor binding。空白不能儲存該來源。',
    sameTableTitle: '同一張表，兩條 Sub 綁定',
    sameTableBody:
      'Filter foreign key 不同時，同一張 SUB 表可以綁兩次。Primary Table 和 Relation Table 仍是一表一條。採購申請只有一條 help_pr_line。截圖不要給 help_pr_line 加第二個外鍵。兩條綁定用同一個過濾外鍵會被拒絕。',
    failTitle: '失敗時',
    failPrimaryFirst:
      'Add a Primary (MAIN) table binding first — sub-table bindings require a primary one. (Related bindings can be added independently.)',
    failPrimaryExists:
      'A primary binding already exists for this form. Each form can only have one primary table.',
    failBindingExists:
      'A conflicting table binding already exists. Sub-table bindings on the same table must use different declared foreign keys.',
    failNoFk: 'No structural FK fields on this table yet. 在表設計設定至少一個外鍵，或改選 MI Participant Row。',
    failFilterRequired: 'Select which declared foreign key this binding filters by.',
    failSelectTable: 'Please select a table.',
    failCannotDeletePrimary: 'Primary table binding cannot be deleted.',
    failNoMain: 'No MAIN table found in this function unit. Create one in Table Designer first.',
    failNoSub: 'No SUB table found in this function unit. Create one in Table Designer first.',
  },
}
