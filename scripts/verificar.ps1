param([string]$BaseUrl = 'http://localhost:18082')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\mvnw.cmd '-DforkCount=0' test
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas Java.' }
    & newman run postman/postman_collection.json --env-var "baseUrl=$BaseUrl" --bail --reporters cli
    if ($LASTEXITCODE -ne 0) { throw 'Falló la colección Postman.' }
} finally { Pop-Location }
