# Dot-source in the PowerShell 7 window that will later execute the separately authorized recovery.
# No database/service access or account changes. The Runner generates its new user's password in memory.
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 is required' }
if ($MyInvocation.InvocationName -ne '.') { throw 'Dot-source this script: . .\tools\rag-e2e\restore-recovery-environment.ps1' }
$taskRecoverySecure = Read-Host 'Existing rag_e2e_writer password' -AsSecureString
$taskRecoveryPointer = [IntPtr]::Zero
try {
    if ($taskRecoverySecure.Length -eq 0) { throw 'Writer password was empty' }
    $taskRecoveryPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskRecoverySecure)
    $env:E2E_DB_USERNAME = 'rag_e2e_writer'
    $env:E2E_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskRecoveryPointer)
    Write-Host 'Recovery credentials restored in this process only. No source/admin credentials or grants are needed.'
} finally {
    if ($taskRecoveryPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskRecoveryPointer) }
    $taskRecoverySecure.Dispose()
    Remove-Variable taskRecoverySecure,taskRecoveryPointer -ErrorAction SilentlyContinue
}
