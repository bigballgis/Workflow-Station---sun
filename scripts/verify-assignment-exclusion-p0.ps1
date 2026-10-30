param([string]$PostgresImage = 'postgres:16.5-alpine')
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$containerName = 'assignment-p0-' + [guid]::NewGuid().ToString('N')
$previousJdbcUrl = $env:ASSIGNMENT_TEST_JDBC_URL
$startedAt = Get-Date
$containerStarted = $false
try {
    # Dedicated trust-authenticated synthetic database, bound only to this machine.
    docker run --detach --name $containerName --publish '127.0.0.1::5432' `
        --env 'POSTGRES_HOST_AUTH_METHOD=trust' --env 'POSTGRES_DB=assignment_p0' $PostgresImage
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start the isolated P0 database' }
    $containerStarted = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $containerName pg_isready -U postgres -d assignment_p0 *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Isolated P0 database did not become ready' }
    $binding = docker port $containerName 5432/tcp
    if ($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected P0 database port binding' }
    $env:ASSIGNMENT_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:' + $Matches[1] + '/assignment_p0'
    Push-Location $workspacePath
    try {
        mvn -pl backend/workflow-engine-core,backend/user-portal,backend/admin-center,backend/developer-workstation -am test `
            '-Dtest=Assignment*Test,TaskAssignmentSourceCaptureTest' '-Dsurefire.failIfNoSpecifiedTests=false'
        if ($LASTEXITCODE -ne 0) { throw 'Assignment P0/foundation tests failed' }
        $modules = @('platform-common','workflow-engine-core','user-portal','admin-center','developer-workstation')
        foreach ($module in $modules) {
            $testRoot = Join-Path $workspacePath "backend/$module/src/test/java"
            $testSources = @(Get-ChildItem -LiteralPath $testRoot -Recurse -File -Filter 'Assignment*Test.java')
            if ($module -eq 'workflow-engine-core') {
                $testSources += Get-Item -LiteralPath (Join-Path $testRoot 'com/workflow/listener/TaskAssignmentSourceCaptureTest.java')
            }
            if ($testSources.Count -eq 0) { throw "No assignment tests found in $module" }
            foreach ($testSource in $testSources) {
                $testClass = $testSource.BaseName
                $sourceText = Get-Content -LiteralPath $testSource.FullName -Raw
                if ($sourceText -notmatch '(?m)^package\s+([\w.]+)\s*;') { throw "Missing package in $testClass" }
                $prefix = $Matches[1]
                $reportPath = Join-Path $workspacePath "backend/$module/target/surefire-reports/TEST-$prefix.$testClass.xml"
                $report = Get-Item -LiteralPath $reportPath
                if ($report.LastWriteTime -lt $startedAt) { throw "Stale test report: $testClass" }
                [xml]$result = Get-Content -LiteralPath $reportPath -Raw
                $suite = $result.testsuite
                if ([int]$suite.tests -eq 0 -or [int]$suite.skipped -ne 0 -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0) {
                    throw "Missing, skipped or failed tests: $testClass"
                }
                Write-Output "$testClass : $($suite.tests) passed, zero skipped"
            }
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:ASSIGNMENT_TEST_JDBC_URL = $previousJdbcUrl
    if ($containerStarted) {
        # Only remove the exact container created by this invocation. No shared app volumes are touched.
        docker rm --force $containerName
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove owned P0 container $containerName" }
    }
}
