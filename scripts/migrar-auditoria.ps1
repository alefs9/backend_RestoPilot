param([string]$Container = 'restopilot-postgres-audit')
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
Push-Location $raiz
try {
    $identidad = & docker exec $Container psql -X -At -U restopilot_audit -d restopilot_audit -c "SELECT current_database() || '|' || current_user"
    if ($LASTEXITCODE -ne 0 -or (($identidad -join '
').Trim() -ne 'restopilot_audit|restopilot_audit')) {
        throw 'No se pudo verificar la base y el usuario de auditoría.'
    }
    $sql = Join-Path $raiz 'docs/sql/auditoria_constraints.sql'
    Get-Content -Raw -Encoding UTF8 $sql |
        & docker exec -i $Container psql -X -v ON_ERROR_STOP=1 -U restopilot_audit -d restopilot_audit
    if ($LASTEXITCODE -ne 0) { throw 'La migración falló; no continuar.' }
} finally {
    Pop-Location
}