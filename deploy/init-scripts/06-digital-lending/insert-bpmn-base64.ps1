# Insert BPMN as Base64
$bpmnFile = "digital-lending-process.bpmn"
$bpmnContent = Get-Content $bpmnFile -Raw -Encoding UTF8
$bytes = [System.Text.Encoding]::UTF8.GetBytes($bpmnContent)
$base64 = [Convert]::ToBase64String($bytes)

Write-Host "BPMN file size: $($bpmnContent.Length) characters"
Write-Host "Base64 size: $($base64.Length) characters"

# Escape single quotes for SQL
$base64Escaped = $base64.Replace("'", "''")

# Create SQL
$sql = @"
DO `$`$
DECLARE
    v_function_unit_id BIGINT;
    v_process_id BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id 
    FROM public.dw_function_units 
    WHERE code = 'DIGITAL_LENDING';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING not found.';
    END IF;

    INSERT INTO public.dw_process_definitions (
        function_unit_id,
        bpmn_xml
    ) VALUES (
        v_function_unit_id,
        '$base64Escaped'
    ) RETURNING id INTO v_process_id;

    RAISE NOTICE 'Process inserted with ID: % (Base64 encoded)', v_process_id;
END `$`$;
"@

# Execute via Docker
$sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSUCCESS: BPMN process inserted as Base64" -ForegroundColor Green
} else {
    Write-Host "`nERROR: Failed to insert BPMN process" -ForegroundColor Red
    exit 1
}
