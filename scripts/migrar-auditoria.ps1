param([switch]$Compose)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
Push-Location $raiz
try {
    $sql = Join-Path $raiz 'docs/sql/auditoria_constraints.sql'
    if ($Compose) {
        Get-Content -Raw $sql | docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U restopilot_audit -d restopilot_audit
    } else {
        Get-Content -Raw $sql | docker exec -i restopilot-postgres-audit psql -X -v ON_ERROR_STOP=1 -U restopilot_audit -d restopilot_audit
    }
    if ($LASTEXITCODE -ne 0) { throw 'La migración falló; no continuar con la entrega.' }
} finally { Pop-Location }
