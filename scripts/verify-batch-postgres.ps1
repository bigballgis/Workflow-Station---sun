#Requires -Version 7.0
# Runs only against a new, disposable PostgreSQL container. No platform volumes or credentials are used.
$ErrorActionPreference = 'Stop'
$batchContainer = 'batch-pg-verification-' + [guid]::NewGuid().ToString('N')
$batchNames = @('POSTGRES_PASSWORD', 'BATCH_TEST_JDBC_URL', 'BATCH_TEST_DB_USER', 'BATCH_TEST_DB_PASSWORD')
$batchPrevious = @{}
foreach ($batchKey in $batchNames) { $batchPrevious[$batchKey] = [Environment]::GetEnvironmentVariable($batchKey) }
$batchCreated = $false
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $env:POSTGRES_PASSWORD = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    docker run --detach --name $batchContainer --label workflow.batch-verification=true --publish 127.0.0.1::5432 `
        --env POSTGRES_DB=batch_verification --env POSTGRES_USER=batch_verifier --env POSTGRES_PASSWORD `
        postgres@sha256:0366402213df5db03c47ff80bcc697e92c8be0c213d03c941df1fc42d1ba9560 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable PostgreSQL creation failed' }
    $batchCreated = $true
    $batchReady = $false
    for ($batchAttempt = 0; $batchAttempt -lt 30; $batchAttempt++) {
        docker exec $batchContainer pg_isready -U batch_verifier -d batch_verification *> $null
        if ($LASTEXITCODE -eq 0) { $batchReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$batchReady) { throw 'Disposable PostgreSQL did not become ready' }
    $batchPort = docker inspect --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' $batchContainer
    if ($LASTEXITCODE -ne 0 -or $batchPort -notmatch '^[0-9]{1,5}$') { throw 'Cannot resolve disposable PostgreSQL port' }
    $env:BATCH_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$batchPort/batch_verification"
    $env:BATCH_TEST_DB_USER = 'batch_verifier'
    $env:BATCH_TEST_DB_PASSWORD = $env:POSTGRES_PASSWORD
    mvn -pl backend/user-portal -am test '-Dtest=SubTableBatchPostgresTransactionIT,SubTableBatchRealTransactionTest' '-Dsurefire.failIfNoSpecifiedTests=false'
    if ($LASTEXITCODE -ne 0) { throw 'Batch PostgreSQL/JPA verification failed; inspect Surefire results' }
} finally {
    if ($batchCreated) {
        $batchLabel = docker inspect --format '{{index .Config.Labels "workflow.batch-verification"}}' $batchContainer
        if ($LASTEXITCODE -eq 0 -and $batchLabel -eq 'true' -and $batchContainer -match '^batch-pg-verification-[a-f0-9]{32}$') {
            docker rm --force --volumes $batchContainer | Out-Null
            if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove disposable container $batchContainer" }
        } else { Write-Warning 'Disposable container identity check failed; no cleanup attempted' }
    }
    foreach ($batchKey in $batchNames) { [Environment]::SetEnvironmentVariable($batchKey, $batchPrevious[$batchKey]) }
    Pop-Location
}
