# 采购申请功能单元 - 生成字段SQL文件
# 使用 StringBuilder 和 UTF8 BOM 确保编码正确

$outputDir = $PSScriptRoot

# 使用 StringBuilder 构建SQL
$sb = New-Object System.Text.StringBuilder

# 主表字段
[void]$sb.AppendLine("-- Purchase Request - Main Table Fields (purchase_request)")
[void]$sb.AppendLine("")

# 定义字段数组
$mainFields = @(
    @{name='request_no'; type='VARCHAR'; len=50; nullable='false'; unique='true'; desc='Request Number'; order=1},
    @{name='title'; type='VARCHAR'; len=200; nullable='false'; desc='Request Title'; order=2},
    @{name='applicant'; type='VARCHAR'; len=50; nullable='false'; desc='Applicant'; order=3},
    @{name='department'; type='VARCHAR'; len=100; nullable='false'; desc='Department'; order=4},
    @{name='apply_date'; type='DATE'; nullable='false'; desc='Apply Date'; order=5},
    @{name='purchase_type'; type='VARCHAR'; len=50; nullable='false'; desc='Purchase Type'; order=6},
    @{name='urgency'; type='VARCHAR'; len=20; nullable='false'; desc='Urgency Level'; order=7},
    @{name='total_amount'; type='DECIMAL'; prec=18; scale=2; nullable='false'; desc='Total Amount'; order=8},
    @{name='currency'; type='VARCHAR'; len=10; default='CNY'; desc='Currency'; order=9},
    @{name='reason'; type='TEXT'; len=2000; nullable='false'; desc='Purchase Reason'; order=10},
    @{name='expected_delivery_date'; type='DATE'; desc='Expected Delivery Date'; order=11},
    @{name='delivery_address'; type='VARCHAR'; len=500; desc='Delivery Address'; order=12},
    @{name='contact_person'; type='VARCHAR'; len=50; desc='Contact Person'; order=13},
    @{name='contact_phone'; type='VARCHAR'; len=20; desc='Contact Phone'; order=14},
    @{name='attachments'; type='TEXT'; desc='Attachments'; order=15},
    @{name='remarks'; type='TEXT'; len=1000; desc='Remarks'; order=16},
    @{name='status'; type='VARCHAR'; len=20; default='DRAFT'; desc='Status'; order=17}
)

foreach ($f in $mainFields) {
    $cols = "table_id, field_name, data_type"
    $vals = "t.id, '$($f.name)', '$($f.type)'"
    
    if ($f.len) { $cols += ", length"; $vals += ", $($f.len)" }
    if ($f.prec) { $cols += ", precision_value"; $vals += ", $($f.prec)" }
    if ($f.scale) { $cols += ", scale"; $vals += ", $($f.scale)" }
    if ($f.nullable) { $cols += ", nullable"; $vals += ", $($f.nullable)" }
    if ($f.unique) { $cols += ", is_unique"; $vals += ", $($f.unique)" }
    if ($f.default) { $cols += ", default_value"; $vals += ", '$($f.default)'" }
    $cols += ", description, sort_order"
    $vals += ", '$($f.desc)', $($f.order)"
    
    [void]$sb.AppendLine("INSERT INTO dw_field_definitions ($cols)")
    [void]$sb.AppendLine("SELECT $vals")
    [void]$sb.AppendLine("FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id")
    [void]$sb.AppendLine("WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';")
    [void]$sb.AppendLine("")
}

[System.IO.File]::WriteAllText("$outputDir\04-03-fields-main.sql", $sb.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-03-fields-main.sql"

# 子表字段
$sb2 = New-Object System.Text.StringBuilder
[void]$sb2.AppendLine("-- Purchase Request - Sub/Relation/Action Table Fields")
[void]$sb2.AppendLine("")

# purchase_item 字段
$itemFields = @(
    @{name='item_name'; type='VARCHAR'; len=200; nullable='false'; desc='Item Name'; order=1},
    @{name='specification'; type='VARCHAR'; len=200; desc='Specification'; order=2},
    @{name='unit'; type='VARCHAR'; len=20; desc='Unit'; order=3},
    @{name='quantity'; type='INTEGER'; nullable='false'; desc='Quantity'; order=4},
    @{name='unit_price'; type='DECIMAL'; prec=18; scale=2; desc='Unit Price'; order=5},
    @{name='amount'; type='DECIMAL'; prec=18; scale=2; desc='Amount'; order=6},
    @{name='item_remarks'; type='TEXT'; len=500; desc='Remarks'; order=7}
)

[void]$sb2.AppendLine("-- ========== purchase_item ==========")
foreach ($f in $itemFields) {
    $cols = "table_id, field_name, data_type"
    $vals = "t.id, '$($f.name)', '$($f.type)'"
    if ($f.len) { $cols += ", length"; $vals += ", $($f.len)" }
    if ($f.prec) { $cols += ", precision_value"; $vals += ", $($f.prec)" }
    if ($f.scale) { $cols += ", scale"; $vals += ", $($f.scale)" }
    if ($f.nullable) { $cols += ", nullable"; $vals += ", $($f.nullable)" }
    $cols += ", description, sort_order"
    $vals += ", '$($f.desc)', $($f.order)"
    
    [void]$sb2.AppendLine("INSERT INTO dw_field_definitions ($cols)")
    [void]$sb2.AppendLine("SELECT $vals")
    [void]$sb2.AppendLine("FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id")
    [void]$sb2.AppendLine("WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';")
    [void]$sb2.AppendLine("")
}

# supplier_info 字段
$supplierFields = @(
    @{name='supplier_name'; type='VARCHAR'; len=200; nullable='false'; desc='Supplier Name'; order=1},
    @{name='supplier_code'; type='VARCHAR'; len=50; desc='Supplier Code'; order=2},
    @{name='supplier_contact'; type='VARCHAR'; len=50; desc='Contact Person'; order=3},
    @{name='supplier_phone'; type='VARCHAR'; len=20; desc='Contact Phone'; order=4},
    @{name='supplier_address'; type='VARCHAR'; len=500; desc='Address'; order=5}
)

[void]$sb2.AppendLine("-- ========== supplier_info ==========")
foreach ($f in $supplierFields) {
    $cols = "table_id, field_name, data_type"
    $vals = "t.id, '$($f.name)', '$($f.type)'"
    if ($f.len) { $cols += ", length"; $vals += ", $($f.len)" }
    if ($f.nullable) { $cols += ", nullable"; $vals += ", $($f.nullable)" }
    $cols += ", description, sort_order"
    $vals += ", '$($f.desc)', $($f.order)"
    
    [void]$sb2.AppendLine("INSERT INTO dw_field_definitions ($cols)")
    [void]$sb2.AppendLine("SELECT $vals")
    [void]$sb2.AppendLine("FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id")
    [void]$sb2.AppendLine("WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';")
    [void]$sb2.AppendLine("")
}

# budget_info 字段
$budgetFields = @(
    @{name='budget_code'; type='VARCHAR'; len=50; nullable='false'; desc='Budget Code'; order=1},
    @{name='budget_name'; type='VARCHAR'; len=200; desc='Budget Name'; order=2},
    @{name='budget_amount'; type='DECIMAL'; prec=18; scale=2; desc='Budget Amount'; order=3},
    @{name='used_amount'; type='DECIMAL'; prec=18; scale=2; desc='Used Amount'; order=4},
    @{name='available_amount'; type='DECIMAL'; prec=18; scale=2; desc='Available Amount'; order=5}
)

[void]$sb2.AppendLine("-- ========== budget_info ==========")
foreach ($f in $budgetFields) {
    $cols = "table_id, field_name, data_type"
    $vals = "t.id, '$($f.name)', '$($f.type)'"
    if ($f.len) { $cols += ", length"; $vals += ", $($f.len)" }
    if ($f.prec) { $cols += ", precision_value"; $vals += ", $($f.prec)" }
    if ($f.scale) { $cols += ", scale"; $vals += ", $($f.scale)" }
    if ($f.nullable) { $cols += ", nullable"; $vals += ", $($f.nullable)" }
    $cols += ", description, sort_order"
    $vals += ", '$($f.desc)', $($f.order)"
    
    [void]$sb2.AppendLine("INSERT INTO dw_field_definitions ($cols)")
    [void]$sb2.AppendLine("SELECT $vals")
    [void]$sb2.AppendLine("FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id")
    [void]$sb2.AppendLine("WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';")
    [void]$sb2.AppendLine("")
}

# purchase_approval 字段
$approvalFields = @(
    @{name='approver'; type='VARCHAR'; len=50; nullable='false'; desc='Approver'; order=1},
    @{name='approve_time'; type='TIMESTAMP'; nullable='false'; desc='Approve Time'; order=2},
    @{name='approve_result'; type='VARCHAR'; len=20; nullable='false'; desc='Approve Result'; order=3},
    @{name='approve_comment'; type='TEXT'; len=1000; desc='Approve Comment'; order=4},
    @{name='task_name'; type='VARCHAR'; len=100; desc='Task Name'; order=5}
)

[void]$sb2.AppendLine("-- ========== purchase_approval ==========")
foreach ($f in $approvalFields) {
    $cols = "table_id, field_name, data_type"
    $vals = "t.id, '$($f.name)', '$($f.type)'"
    if ($f.len) { $cols += ", length"; $vals += ", $($f.len)" }
    if ($f.nullable) { $cols += ", nullable"; $vals += ", $($f.nullable)" }
    $cols += ", description, sort_order"
    $vals += ", '$($f.desc)', $($f.order)"
    
    [void]$sb2.AppendLine("INSERT INTO dw_field_definitions ($cols)")
    [void]$sb2.AppendLine("SELECT $vals")
    [void]$sb2.AppendLine("FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id")
    [void]$sb2.AppendLine("WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';")
    [void]$sb2.AppendLine("")
}

# countersign_record 字段
$countersignFields = @(
    @{name='signer'; type='VARCHAR'; len=50; nullable='false'; desc='Signer'; order=1},
    @{name='signer_dept'; type='VARCHAR'; len=100; desc='Signer Department'; order=2},
    @{name='sign_time'; type='TIMESTAMP'; desc='Sign Time'; order=3},
    @{name='sign_result'; type='VARCHAR'; len=20; desc='Sign Result'; order=4},
    @{name='sign_comment'; type='TEXT'; len=1000; desc='Sign Comment'; order=5}
)

[void]$sb2.AppendLine("-- ========== countersign_record ==========")
foreach ($f in $countersignFields) {
    $cols = "table_id, field_name, data_type"
    $vals = "t.id, '$($f.name)', '$($f.type)'"
    if ($f.len) { $cols += ", length"; $vals += ", $($f.len)" }
    if ($f.nullable) { $cols += ", nullable"; $vals += ", $($f.nullable)" }
    $cols += ", description, sort_order"
    $vals += ", '$($f.desc)', $($f.order)"
    
    [void]$sb2.AppendLine("INSERT INTO dw_field_definitions ($cols)")
    [void]$sb2.AppendLine("SELECT $vals")
    [void]$sb2.AppendLine("FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id")
    [void]$sb2.AppendLine("WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';")
    [void]$sb2.AppendLine("")
}

[System.IO.File]::WriteAllText("$outputDir\04-04-fields-sub.sql", $sb2.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-04-fields-sub.sql"

Write-Host "Field SQL files generated successfully!"
