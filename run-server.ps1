param(
    [ValidateSet("MandateLens", "Merchant")]
    [string]$Service = "MandateLens"
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repositoryRoot

$envFile = Join-Path $repositoryRoot ".env"
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "The repository-root .env file was not found. Create it from .env.example first."
}

$envLines = Get-Content -LiteralPath $envFile
foreach ($line in $envLines) {
    $trimmedLine = $line.Trim()
    if (-not $trimmedLine -or $trimmedLine.StartsWith("#")) {
        continue
    }

    $separator = $trimmedLine.IndexOf("=")
    if ($separator -lt 1) {
        continue
    }

    $keyName = $trimmedLine.Substring(0, $separator).Trim()
    $value = $trimmedLine.Substring($separator + 1).Trim()
    if ($value.Length -ge 2 -and
        (($value.StartsWith('"') -and $value.EndsWith('"')) -or
         ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($keyName, $value, "Process")
}

foreach ($keyName in @("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD")) {
    if (-not [Environment]::GetEnvironmentVariable($keyName, "Process")) {
        throw "$keyName is missing or empty in the repository-root .env file."
    }
}

$mavenArguments = @("-pl", "src/server", "spring-boot:run")
switch ($Service) {
    "Merchant" {
        $env:SPRING_PROFILES_ACTIVE = "merchant-server,storesync-simulator"
        # The Maven profile selects MerchantApplication. Passing spring-boot.run.main-class
        # as a command-line property is overridden by the plugin configuration in src/server/pom.xml.
        $mavenArguments = @("-pl", "src/server", "-Pmerchant-app", "spring-boot:run")
    }
    default {
        $env:SPRING_PROFILES_ACTIVE = "mandatelens-server,agentguard-deterministic"
    }
}

& (Join-Path $repositoryRoot ".\mvnw.cmd") @mavenArguments
exit $LASTEXITCODE
