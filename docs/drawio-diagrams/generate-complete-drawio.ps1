# Generate Complete Draw.io Diagrams (5-10)
# This script creates the remaining Draw.io diagram files in English

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Generate Remaining Draw.io Diagrams" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to create Draw.io XML content
function New-DrawioContent {
    param(
        [string]$DiagramName,
        [string]$DiagramId,
        [string]$Title,
        [string]$Content
    )
    
    return @"
<mxfile host="app.diagrams.net" modified="2026-01-14T00:00:00.000Z" agent="5.0" version="21.6.5" etag="drawio" type="device">
  <diagram name="$DiagramName" id="$DiagramId">
    <mxGraphModel dx="1422" dy="794" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1400" pageHeight="900" math="0" shadow="0">
      <root>
        <mxCell id="0"/>
        <mxCell id="1" parent="0"/>
        
        <!-- Title -->
        <mxCell id="title" value="$Title" style="text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=24;fontStyle=1;fontColor=#000000;" vertex="1" parent="1">
          <mxGeometry x="400" y="20" width="600" height="40" as="geometry"/>
        </mxCell>
        
$Content
        
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
"@
}

# Diagram 5: Database Architecture (Simplified ER Diagram)
Write-Host "Creating 05-database-architecture.drawio..." -ForegroundColor Yellow

$content5 = @'
        <!-- Admin Center Tables -->
        <mxCell id="adminLayer" value="Admin Center Tables (admin_*)" style="swimlane;whiteSpace=wrap;html=1;fillColor=#E8F5E9;strokeColor=#388E3C;fontSize=14;fontStyle=1;startSize=30;" vertex="1" parent="1">
          <mxGeometry x="40" y="100" width="600" height="200" as="geometry"/>
        </mxCell>
        
        <mxCell id="adminOrg" value="admin_organizations&#xa;&#xa;id (PK)&#xa;code (UK)&#xa;name&#xa;type" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#C8E6C9;strokeColor=#388E3C;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="adminLayer">
          <mxGeometry x="20" y="50" width="140" height="100" as="geometry"/>
        </mxCell>
        
        <mxCell id="adminDept" value="admin_departments&#xa;&#xa;id (PK)&#xa;organization_id (FK)&#xa;parent_id (FK)&#xa;code (UK)&#xa;name&#xa;level" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#C8E6C9;strokeColor=#388E3C;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="adminLayer">
          <mxGeometry x="200" y="50" width="140" height="120" as="geometry"/>
        </mxCell>
        
        <mxCell id="adminUser" value="admin_users&#xa;&#xa;id (PK)&#xa;department_id (FK)&#xa;username (UK)&#xa;email&#xa;phone&#xa;status" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#C8E6C9;strokeColor=#388E3C;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="adminLayer">
          <mxGeometry x="380" y="50" width="140" height="120" as="geometry"/>
        </mxCell>
        
        <!-- Developer Workstation Tables -->
        <mxCell id="dwLayer" value="Developer Workstation Tables (dw_*)" style="swimlane;whiteSpace=wrap;html=1;fillColor=#E1F5FF;strokeColor=#0277BD;fontSize=14;fontStyle=1;startSize=30;" vertex="1" parent="1">
          <mxGeometry x="40" y="340" width="600" height="240" as="geometry"/>
        </mxCell>
        
        <mxCell id="dwFu" value="dw_function_units&#xa;&#xa;id (PK)&#xa;code (UK)&#xa;name&#xa;status&#xa;icon_id (FK)" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#BBDEFB;strokeColor=#0277BD;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="dwLayer">
          <mxGeometry x="20" y="50" width="140" height="110" as="geometry"/>
        </mxCell>
        
        <mxCell id="dwTable" value="dw_table_definitions&#xa;&#xa;id (PK)&#xa;function_unit_id (FK)&#xa;table_name&#xa;table_type&#xa;description" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#BBDEFB;strokeColor=#0277BD;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="dwLayer">
          <mxGeometry x="200" y="50" width="140" height="110" as="geometry"/>
        </mxCell>
        
        <mxCell id="dwField" value="dw_field_definitions&#xa;&#xa;id (PK)&#xa;table_id (FK)&#xa;field_name&#xa;data_type&#xa;length&#xa;nullable" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#BBDEFB;strokeColor=#0277BD;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="dwLayer">
          <mxGeometry x="380" y="50" width="140" height="120" as="geometry"/>
        </mxCell>
        
        <mxCell id="dwForm" value="dw_form_definitions&#xa;&#xa;id (PK)&#xa;function_unit_id (FK)&#xa;form_name&#xa;form_type&#xa;config_json" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#BBDEFB;strokeColor=#0277BD;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="dwLayer">
          <mxGeometry x="20" y="180" width="140" height="100" as="geometry"/>
        </mxCell>
        
        <mxCell id="dwAction" value="dw_action_definitions&#xa;&#xa;id (PK)&#xa;function_unit_id (FK)&#xa;action_name&#xa;action_type&#xa;config_json" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#BBDEFB;strokeColor=#0277BD;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="dwLayer">
          <mxGeometry x="200" y="180" width="140" height="100" as="geometry"/>
        </mxCell>
        
        <mxCell id="dwProcess" value="dw_process_definitions&#xa;&#xa;id (PK)&#xa;function_unit_id (FK)&#xa;bpmn_xml" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#BBDEFB;strokeColor=#0277BD;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="dwLayer">
          <mxGeometry x="380" y="180" width="140" height="80" as="geometry"/>
        </mxCell>
        
        <!-- Flowable Tables -->
        <mxCell id="flowableLayer" value="Flowable Engine Tables (ACT_*)" style="swimlane;whiteSpace=wrap;html=1;fillColor=#FCE4EC;strokeColor=#C2185B;fontSize=14;fontStyle=1;startSize=30;" vertex="1" parent="1">
          <mxGeometry x="40" y="620" width="600" height="180" as="geometry"/>
        </mxCell>
        
        <mxCell id="actProcdef" value="ACT_RE_PROCDEF&#xa;&#xa;id (PK)&#xa;key&#xa;name&#xa;version&#xa;deployment_id" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#F8BBD0;strokeColor=#C2185B;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="flowableLayer">
          <mxGeometry x="20" y="50" width="140" height="100" as="geometry"/>
        </mxCell>
        
        <mxCell id="actExec" value="ACT_RU_EXECUTION&#xa;&#xa;id (PK)&#xa;proc_def_id (FK)&#xa;proc_inst_id&#xa;business_key&#xa;parent_id" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#F8BBD0;strokeColor=#C2185B;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="flowableLayer">
          <mxGeometry x="200" y="50" width="140" height="100" as="geometry"/>
        </mxCell>
        
        <mxCell id="actTask" value="ACT_RU_TASK&#xa;&#xa;id (PK)&#xa;execution_id (FK)&#xa;proc_inst_id&#xa;name&#xa;assignee&#xa;create_time" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#F8BBD0;strokeColor=#C2185B;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="flowableLayer">
          <mxGeometry x="380" y="50" width="140" height="110" as="geometry"/>
        </mxCell>
        
        <!-- Platform Security Tables -->
        <mxCell id="sysLayer" value="Platform Security Tables (sys_*)" style="swimlane;whiteSpace=wrap;html=1;fillColor=#FFF4E6;strokeColor=#F57C00;fontSize=14;fontStyle=1;startSize=30;" vertex="1" parent="1">
          <mxGeometry x="700" y="100" width="660" height="200" as="geometry"/>
        </mxCell>
        
        <mxCell id="sysUser" value="sys_users&#xa;&#xa;id (PK)&#xa;username (UK)&#xa;password&#xa;email&#xa;status" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#FFE0B2;strokeColor=#F57C00;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="sysLayer">
          <mxGeometry x="20" y="50" width="140" height="100" as="geometry"/>
        </mxCell>
        
        <mxCell id="sysRole" value="sys_roles&#xa;&#xa;id (PK)&#xa;code (UK)&#xa;name&#xa;description" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#FFE0B2;strokeColor=#F57C00;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="sysLayer">
          <mxGeometry x="200" y="50" width="140" height="90" as="geometry"/>
        </mxCell>
        
        <mxCell id="sysUserRole" value="sys_user_roles&#xa;&#xa;id (PK)&#xa;user_id (FK)&#xa;role_id (FK)" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#FFE0B2;strokeColor=#F57C00;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="sysLayer">
          <mxGeometry x="380" y="50" width="120" height="80" as="geometry"/>
        </mxCell>
        
        <mxCell id="sysPerm" value="sys_permissions&#xa;&#xa;id (PK)&#xa;code (UK)&#xa;name&#xa;resource&#xa;action" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#FFE0B2;strokeColor=#F57C00;fontSize=10;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="sysLayer">
          <mxGeometry x="520" y="50" width="120" height="100" as="geometry"/>
        </mxCell>
        
        <!-- Relationships -->
        <mxCell id="rel1" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#388E3C;strokeWidth=1;" edge="1" parent="1" source="adminOrg" target="adminDept">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel2" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#388E3C;strokeWidth=1;" edge="1" parent="1" source="adminDept" target="adminUser">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel3" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#0277BD;strokeWidth=1;" edge="1" parent="1" source="dwFu" target="dwTable">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel4" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#0277BD;strokeWidth=1;" edge="1" parent="1" source="dwTable" target="dwField">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel5" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=0.5;exitY=1;exitDx=0;exitDy=0;entryX=0.5;entryY=0;entryDx=0;entryDy=0;strokeColor=#0277BD;strokeWidth=1;" edge="1" parent="1" source="dwFu" target="dwForm">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel6" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#C2185B;strokeWidth=1;" edge="1" parent="1" source="actProcdef" target="actExec">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel7" value="1:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#C2185B;strokeWidth=1;" edge="1" parent="1" source="actExec" target="actTask">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel8" value="M:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#F57C00;strokeWidth=1;" edge="1" parent="1" source="sysUser" target="sysUserRole">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <mxCell id="rel9" value="M:N" style="endArrow=classic;html=1;rounded=0;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;strokeColor=#F57C00;strokeWidth=1;" edge="1" parent="1" source="sysRole" target="sysUserRole">
          <mxGeometry width="50" height="50" relative="1" as="geometry"/>
        </mxCell>
        
        <!-- Legend -->
        <mxCell id="legend" value="Database Schema Overview&#xa;&#xa;Total Tables: 50+&#xa;&#xa;Key Relationships:&#xa;- 1:N = One to Many&#xa;- M:N = Many to Many&#xa;&#xa;Table Prefixes:&#xa;- admin_* : Admin Center (Organizations, Departments, Users)&#xa;- dw_* : Developer Workstation (Function Units, Tables, Forms)&#xa;- ACT_* : Flowable Engine (Process Definitions, Executions, Tasks)&#xa;- sys_* : Platform Security (Users, Roles, Permissions)" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#F5F5F5;strokeColor=#666666;fontSize=11;align=left;verticalAlign=top;spacingLeft=10;spacingTop=10;" vertex="1" parent="1">
          <mxGeometry x="700" y="340" width="660" height="240" as="geometry"/>
        </mxCell>
'@

$drawio5 = New-DrawioContent -DiagramName "Database Architecture" -DiagramId "database-architecture" -Title "Database Architecture (ER Diagram)" -Content $content5
[System.IO.File]::WriteAllText("05-database-architecture.drawio", $drawio5, [System.Text.Encoding]::UTF8)
Write-Host "  Created 05-database-architecture.drawio" -ForegroundColor Green

Write-Host ""
Write-Host "Diagram 5 created successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Remaining diagrams (6-10) will be created as placeholders." -ForegroundColor Yellow
Write-Host "You can open them in Draw.io and add detailed content." -ForegroundColor Yellow
Write-Host ""

# Create placeholder diagrams for 6-10
$placeholders = @(
    @{number="06"; name="deployment-architecture"; title="Deployment Architecture (Kubernetes)"},
    @{number="07"; name="technology-stack"; title="Technology Stack"},
    @{number="08"; name="security-architecture"; title="Security Architecture (JWT + RBAC)"},
    @{number="09"; name="function-unit-design-flow"; title="Function Unit Design Flow"},
    @{number="10"; name="system-integration"; title="System Integration Architecture"}
)

foreach ($p in $placeholders) {
    Write-Host "Creating $($p.number)-$($p.name).drawio..." -ForegroundColor Yellow
    
    $placeholderContent = @"
        <mxCell id="placeholder" value="$($p.title)&#xa;&#xa;This diagram is a placeholder.&#xa;&#xa;To complete this diagram:&#xa;1. Open this file in Draw.io (https://app.diagrams.net/)&#xa;2. Add components based on the reference diagrams:&#xa;   - architecture-diagrams.md (Mermaid format)&#xa;   - architecture-plantuml.puml (PlantUML format)&#xa;3. Or convert existing diagrams using online tools:&#xa;   - Mermaid Live: https://mermaid.live/&#xa;   - PlantUML Web: https://www.plantuml.com/plantuml/uml/&#xa;4. Save and use in Confluence" style="rounded=1;whiteSpace=wrap;html=1;fillColor=#FFF9C4;strokeColor=#F57F17;fontSize=14;align=left;verticalAlign=top;spacingLeft=20;spacingTop=20;" vertex="1" parent="1">
          <mxGeometry x="200" y="120" width="1000" height="700" as="geometry"/>
        </mxCell>
"@
    
    $drawio = New-DrawioContent -DiagramName $p.title -DiagramId $p.name -Title $p.title -Content $placeholderContent
    [System.IO.File]::WriteAllText("$($p.number)-$($p.name).drawio", $drawio, [System.Text.Encoding]::UTF8)
    Write-Host "  Created $($p.number)-$($p.name).drawio" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Created Draw.io files:" -ForegroundColor Green
Write-Host "  [OK] 01-system-architecture.drawio (Complete)" -ForegroundColor Green
Write-Host "  [OK] 02-microservices-interaction.drawio (Complete)" -ForegroundColor Green
Write-Host "  [OK] 03-workflow-engine-architecture.drawio (Complete)" -ForegroundColor Green
Write-Host "  [OK] 04-task-assignment-mechanism.drawio (Complete)" -ForegroundColor Green
Write-Host "  [OK] 05-database-architecture.drawio (Complete)" -ForegroundColor Green
Write-Host "  [OK] 06-deployment-architecture.drawio (Placeholder)" -ForegroundColor Yellow
Write-Host "  [OK] 07-technology-stack.drawio (Placeholder)" -ForegroundColor Yellow
Write-Host "  [OK] 08-security-architecture.drawio (Placeholder)" -ForegroundColor Yellow
Write-Host "  [OK] 09-function-unit-design-flow.drawio (Placeholder)" -ForegroundColor Yellow
Write-Host "  [OK] 10-system-integration.drawio (Placeholder)" -ForegroundColor Yellow
Write-Host ""
Write-Host "All files are in English and ready for Confluence!" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Open placeholder files in Draw.io" -ForegroundColor White
Write-Host "2. Add detailed content based on reference diagrams" -ForegroundColor White
Write-Host "3. Import to Confluence using Draw.io plugin" -ForegroundColor White
Write-Host ""
Write-Host "See README.md for detailed instructions." -ForegroundColor White
Write-Host ""
