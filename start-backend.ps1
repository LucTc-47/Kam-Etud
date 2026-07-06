[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemainingArguments
)

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$StartLocal = Join-Path $ProjectRoot "start-local.ps1"

& $StartLocal -BackendOnly @RemainingArguments
exit $LASTEXITCODE
