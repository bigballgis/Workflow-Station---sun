# Purchase Request - Generate Form Configurations with Multi-Tab Support
# This script generates config_json for all 6 forms

# Helper function to create form field
function New-Field {
    param(
        [string]$type,
        [string]$field,
        [string]$title,
        [hashtable]$props = @{},
        [array]$validate = @(),
        [array]$options = @()
    )
    $result = @{
        type = $type
        field = $field
        title = $title
        props = $props
    }
    if ($validate.Count -gt 0) { $result.validate = $validate }
    if ($options.Count -gt 0) { $result.options = $options }
    return $result
}

# Form 11: Purchase Request Main Form (MAIN) - Multi-tab layout
$mainFormRule = @(
    @{
        type = "el-tabs"
        props = @{ type = "border-card"; modelValue = "basic" }
        children = @(
            @{
                type = "el-tab-pane"
                props = @{ label = "Basic Info"; name = "basic" }
                children = @(
                    @{ type = "input"; field = "request_no"; title = "Request No"; props = @{ placeholder = "Auto generated"; disabled = $true } }
                    @{ type = "input"; field = "title"; title = "Title"; props = @{ placeholder = "Enter request title" }; validate = @(@{ required = $true; message = "Title is required"; trigger = "blur" }) }
                    @{ type = "input"; field = "applicant"; title = "Applicant"; props = @{ placeholder = "Applicant name"; disabled = $true } }
                    @{ type = "input"; field = "department"; title = "Department"; props = @{ placeholder = "Department"; disabled = $true } }
                    @{ type = "datePicker"; field = "apply_date"; title = "Apply Date"; props = @{ type = "date"; valueFormat = "YYYY-MM-DD"; placeholder = "Select date" }; validate = @(@{ required = $true; message = "Apply date is required"; trigger = "change" }) }
                    @{ type = "select"; field = "purchase_type"; title = "Purchase Type"; props = @{ placeholder = "Select type" }; options = @(@{value="OFFICE";label="Office Supplies"},@{value="IT";label="IT Equipment"},@{value="SERVICE";label="Service"},@{value="OTHER";label="Other"}) }
                    @{ type = "select"; field = "urgency"; title = "Urgency"; props = @{ placeholder = "Select urgency" }; options = @(@{value="LOW";label="Low"},@{value="NORMAL";label="Normal"},@{value="HIGH";label="High"},@{value="URGENT";label="Urgent"}) }
                )
            }
            @{
                type = "el-tab-pane"
                props = @{ label = "Amount Info"; name = "amount" }
                children = @(
                    @{ type = "inputNumber"; field = "total_amount"; title = "Total Amount"; props = @{ precision = 2; min = 0; placeholder = "Enter amount" }; validate = @(@{ required = $true; message = "Amount is required"; trigger = "blur" }) }
                    @{ type = "select"; field = "currency"; title = "Currency"; props = @{ placeholder = "Select currency" }; options = @(@{value="CNY";label="CNY"},@{value="USD";label="USD"},@{value="EUR";label="EUR"}) }
                    @{ type = "input"; field = "reason"; title = "Purchase Reason"; props = @{ type = "textarea"; rows = 4; placeholder = "Enter purchase reason" }; validate = @(@{ required = $true; message = "Reason is required"; trigger = "blur" }) }
                )
            }
            @{
                type = "el-tab-pane"
                props = @{ label = "Delivery Info"; name = "delivery" }
                children = @(
                    @{ type = "datePicker"; field = "expected_delivery_date"; title = "Expected Delivery"; props = @{ type = "date"; valueFormat = "YYYY-MM-DD"; placeholder = "Select date" } }
                    @{ type = "input"; field = "delivery_address"; title = "Delivery Address"; props = @{ placeholder = "Enter delivery address" } }
                    @{ type = "input"; field = "contact_person"; title = "Contact Person"; props = @{ placeholder = "Contact person name" } }
                    @{ type = "input"; field = "contact_phone"; title = "Contact Phone"; props = @{ placeholder = "Contact phone number" } }
                )
            }
            @{
                type = "el-tab-pane"
                props = @{ label = "Purchase Items"; name = "items" }
                children = @(
                    @{
                        type = "subForm"
                        field = "purchase_items"
                        title = "Purchase Items"
                        props = @{ maxLength = 50; minLength = 1 }
                        children = @(
                            @{ type = "input"; field = "item_name"; title = "Item Name"; props = @{ placeholder = "Item name" }; validate = @(@{ required = $true; message = "Item name is required"; trigger = "blur" }) }
                            @{ type = "input"; field = "specification"; title = "Specification"; props = @{ placeholder = "Specification" } }
                            @{ type = "input"; field = "unit"; title = "Unit"; props = @{ placeholder = "Unit" } }
                            @{ type = "inputNumber"; field = "quantity"; title = "Quantity"; props = @{ min = 1; precision = 0 }; validate = @(@{ required = $true; message = "Quantity is required"; trigger = "blur" }) }
                            @{ type = "inputNumber"; field = "unit_price"; title = "Unit Price"; props = @{ precision = 2; min = 0 } }
                            @{ type = "inputNumber"; field = "amount"; title = "Amount"; props = @{ precision = 2; disabled = $true } }
                            @{ type = "input"; field = "item_remarks"; title = "Remarks"; props = @{ placeholder = "Item remarks" } }
                        )
                    }
                )
            }
            @{
                type = "el-tab-pane"
                props = @{ label = "Attachments"; name = "attachments" }
                children = @(
                    @{ type = "upload"; field = "attachments"; title = "Attachments"; props = @{ action = "/api/upload"; multiple = $true; listType = "text" } }
                    @{ type = "input"; field = "remarks"; title = "Remarks"; props = @{ type = "textarea"; rows = 4; placeholder = "Additional remarks" } }
                )
            }
        )
    }
)

$mainFormConfig = @{
    rule = $mainFormRule
    options = @{
        submitBtn = $false
        resetBtn = $false
        form = @{ labelWidth = "120px"; labelPosition = "right" }
    }
}

# Form 12: Purchase Items Form (SUB)
$itemsFormConfig = @{
    rule = @(
        @{ type = "input"; field = "item_name"; title = "Item Name"; props = @{ placeholder = "Enter item name" }; validate = @(@{ required = $true; message = "Item name is required"; trigger = "blur" }) }
        @{ type = "input"; field = "specification"; title = "Specification"; props = @{ placeholder = "Enter specification" } }
        @{ type = "input"; field = "unit"; title = "Unit"; props = @{ placeholder = "e.g. pcs, box" } }
        @{ type = "inputNumber"; field = "quantity"; title = "Quantity"; props = @{ min = 1; precision = 0 }; validate = @(@{ required = $true; message = "Quantity is required"; trigger = "blur" }) }
        @{ type = "inputNumber"; field = "unit_price"; title = "Unit Price"; props = @{ min = 0; precision = 2 } }
        @{ type = "inputNumber"; field = "amount"; title = "Amount"; props = @{ precision = 2; disabled = $true } }
        @{ type = "input"; field = "item_remarks"; title = "Remarks"; props = @{ type = "textarea"; rows = 2 } }
    )
    options = @{ submitBtn = $false; resetBtn = $false; form = @{ labelWidth = "100px" } }
}

# Form 13: Approval Form (ACTION)
$approvalFormConfig = @{
    rule = @(
        @{
            type = "el-tabs"
            props = @{ type = "border-card"; modelValue = "request" }
            children = @(
                @{
                    type = "el-tab-pane"
                    props = @{ label = "Request Info"; name = "request" }
                    children = @(
                        @{ type = "input"; field = "request_no"; title = "Request No"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "title"; title = "Title"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "applicant"; title = "Applicant"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "department"; title = "Department"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "apply_date"; title = "Apply Date"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "purchase_type"; title = "Purchase Type"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "urgency"; title = "Urgency"; props = @{ disabled = $true } }
                        @{ type = "inputNumber"; field = "total_amount"; title = "Total Amount"; props = @{ disabled = $true; precision = 2 } }
                        @{ type = "input"; field = "reason"; title = "Reason"; props = @{ type = "textarea"; disabled = $true; rows = 3 } }
                    )
                }
                @{
                    type = "el-tab-pane"
                    props = @{ label = "Purchase Items"; name = "items" }
                    children = @(
                        @{
                            type = "subForm"
                            field = "purchase_items"
                            title = "Purchase Items"
                            props = @{ disabled = $true }
                            children = @(
                                @{ type = "input"; field = "item_name"; title = "Item"; props = @{ disabled = $true } }
                                @{ type = "input"; field = "specification"; title = "Spec"; props = @{ disabled = $true } }
                                @{ type = "inputNumber"; field = "quantity"; title = "Qty"; props = @{ disabled = $true } }
                                @{ type = "inputNumber"; field = "unit_price"; title = "Price"; props = @{ disabled = $true } }
                                @{ type = "inputNumber"; field = "amount"; title = "Amount"; props = @{ disabled = $true } }
                            )
                        }
                    )
                }
                @{
                    type = "el-tab-pane"
                    props = @{ label = "Approval"; name = "approval" }
                    children = @(
                        @{ type = "input"; field = "approve_comment"; title = "Comment"; props = @{ type = "textarea"; rows = 4; placeholder = "Enter approval comment" } }
                    )
                }
            )
        }
    )
    options = @{ submitBtn = $false; resetBtn = $false; form = @{ labelWidth = "120px" } }
}

# Form 14: Supplier Selection (POPUP)
$supplierFormConfig = @{
    rule = @(
        @{ type = "input"; field = "supplier_name"; title = "Supplier Name"; props = @{ placeholder = "Enter supplier name" }; validate = @(@{ required = $true; message = "Supplier name is required"; trigger = "blur" }) }
        @{ type = "input"; field = "supplier_code"; title = "Supplier Code"; props = @{ placeholder = "Supplier code" } }
        @{ type = "input"; field = "supplier_contact"; title = "Contact Person"; props = @{ placeholder = "Contact person" } }
        @{ type = "input"; field = "supplier_phone"; title = "Phone"; props = @{ placeholder = "Contact phone" } }
        @{ type = "input"; field = "supplier_address"; title = "Address"; props = @{ placeholder = "Supplier address" } }
    )
    options = @{ submitBtn = $true; resetBtn = $true; form = @{ labelWidth = "120px" } }
}

# Form 15: Budget Query (POPUP)
$budgetFormConfig = @{
    rule = @(
        @{ type = "input"; field = "budget_code"; title = "Budget Code"; props = @{ placeholder = "Enter budget code" }; validate = @(@{ required = $true; message = "Budget code is required"; trigger = "blur" }) }
        @{ type = "input"; field = "budget_name"; title = "Budget Name"; props = @{ disabled = $true } }
        @{ type = "inputNumber"; field = "budget_amount"; title = "Budget Amount"; props = @{ disabled = $true; precision = 2 } }
        @{ type = "inputNumber"; field = "used_amount"; title = "Used Amount"; props = @{ disabled = $true; precision = 2 } }
        @{ type = "inputNumber"; field = "available_amount"; title = "Available"; props = @{ disabled = $true; precision = 2 } }
    )
    options = @{ submitBtn = $true; resetBtn = $false; form = @{ labelWidth = "120px" } }
}

# Form 16: Countersign Form (ACTION)
$countersignFormConfig = @{
    rule = @(
        @{
            type = "el-tabs"
            props = @{ type = "border-card"; modelValue = "request" }
            children = @(
                @{
                    type = "el-tab-pane"
                    props = @{ label = "Request Info"; name = "request" }
                    children = @(
                        @{ type = "input"; field = "request_no"; title = "Request No"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "title"; title = "Title"; props = @{ disabled = $true } }
                        @{ type = "input"; field = "applicant"; title = "Applicant"; props = @{ disabled = $true } }
                        @{ type = "inputNumber"; field = "total_amount"; title = "Amount"; props = @{ disabled = $true; precision = 2 } }
                        @{ type = "input"; field = "reason"; title = "Reason"; props = @{ type = "textarea"; disabled = $true; rows = 3 } }
                    )
                }
                @{
                    type = "el-tab-pane"
                    props = @{ label = "Countersign"; name = "countersign" }
                    children = @(
                        @{ type = "input"; field = "signer_dept"; title = "Department"; props = @{ disabled = $true } }
                        @{ type = "select"; field = "sign_result"; title = "Result"; props = @{ placeholder = "Select result" }; options = @(@{value="AGREE";label="Agree"},@{value="DISAGREE";label="Disagree"},@{value="ABSTAIN";label="Abstain"}) }
                        @{ type = "input"; field = "sign_comment"; title = "Comment"; props = @{ type = "textarea"; rows = 4; placeholder = "Enter your comment" } }
                    )
                }
            )
        }
    )
    options = @{ submitBtn = $false; resetBtn = $false; form = @{ labelWidth = "120px" } }
}

# Convert to JSON with sufficient depth
$mainJson = $mainFormConfig | ConvertTo-Json -Depth 100 -Compress
$itemsJson = $itemsFormConfig | ConvertTo-Json -Depth 100 -Compress
$approvalJson = $approvalFormConfig | ConvertTo-Json -Depth 100 -Compress
$supplierJson = $supplierFormConfig | ConvertTo-Json -Depth 100 -Compress
$budgetJson = $budgetFormConfig | ConvertTo-Json -Depth 100 -Compress
$countersignJson = $countersignFormConfig | ConvertTo-Json -Depth 100 -Compress

# Escape single quotes for SQL
$mainJson = $mainJson -replace "'", "''"
$itemsJson = $itemsJson -replace "'", "''"
$approvalJson = $approvalJson -replace "'", "''"
$supplierJson = $supplierJson -replace "'", "''"
$budgetJson = $budgetJson -replace "'", "''"
$countersignJson = $countersignJson -replace "'", "''"

# Generate SQL
$sql = @"
-- Purchase Request - Form Configurations with Multi-Tab Support
-- Generated by generate-form-configs.ps1

-- Form 11: Purchase Request Main Form (MAIN)
UPDATE dw_form_definitions SET config_json = '$mainJson'::jsonb WHERE id = 11;

-- Form 12: Purchase Items Form (SUB)
UPDATE dw_form_definitions SET config_json = '$itemsJson'::jsonb WHERE id = 12;

-- Form 13: Approval Form (ACTION)
UPDATE dw_form_definitions SET config_json = '$approvalJson'::jsonb WHERE id = 13;

-- Form 14: Supplier Selection (POPUP)
UPDATE dw_form_definitions SET config_json = '$supplierJson'::jsonb WHERE id = 14;

-- Form 15: Budget Query (POPUP)
UPDATE dw_form_definitions SET config_json = '$budgetJson'::jsonb WHERE id = 15;

-- Form 16: Countersign Form (ACTION)
UPDATE dw_form_definitions SET config_json = '$countersignJson'::jsonb WHERE id = 16;
"@

[System.IO.File]::WriteAllText("04-10-form-configs.sql", $sql, [System.Text.Encoding]::UTF8)

Write-Host "SQL file generated: 04-10-form-configs.sql"
Write-Host ""
Write-Host "Form configurations:"
Write-Host "  - Form 11 (Main): Multi-tab with Basic/Amount/Delivery/Items/Attachments"
Write-Host "  - Form 12 (Items): Sub-table form for purchase items"
Write-Host "  - Form 13 (Approval): Read-only request info + approval comment"
Write-Host "  - Form 14 (Supplier): Popup form for supplier selection"
Write-Host "  - Form 15 (Budget): Popup form for budget query"
Write-Host "  - Form 16 (Countersign): Read-only request + countersign fields"
