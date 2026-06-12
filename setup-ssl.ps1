#!/usr/bin/env pwsh
# =============================================================================
# SSL Certificate Setup Script for CheckItOut Local Development
# =============================================================================
# This script helps generate SSL certificates for local HTTPS development
# Required for Instagram OAuth callbacks to localhost

param(
    [Parameter(Position=0)]
    [ValidateSet("self-signed", "mkcert", "check")]
    [string]$Method = "check",
    
    [Parameter()]
    [string]$OutputPath = ".\src\main\resources\",
    
    [Parameter()]
    [string]$Password = "changeit"
)

$ErrorActionPreference = "Stop"

function Write-ColorOutput($message, $color = "Green") {
    Write-Host $message -ForegroundColor $color
}

function Test-JavaKeytool {
    try {
        $null = keytool -help 2>&1
        return $true
    } catch {
        return $false
    }
}

function Test-Mkcert {
    try {
        $null = mkcert -version 2>&1
        return $true
    } catch {
        return $false
    }
}

function Test-OpenSSL {
    try {
        $null = openssl version 2>&1
        return $true
    } catch {
        return $false
    }
}

function New-SelfSignedCertificate {
    param($OutputPath, $Password)
    
    Write-ColorOutput "`n📝 Generating self-signed certificate..." "Cyan"
    
    $keystorePath = Join-Path $OutputPath "keystore.p12"
    
    # Generate the certificate
    $keytoolCmd = @"
keytool -genkeypair `
    -alias tomcat `
    -keyalg RSA `
    -keysize 2048 `
    -storetype PKCS12 `
    -keystore "$keystorePath" `
    -validity 3650 `
    -storepass $Password `
    -dname "CN=localhost, OU=Development, O=CheckItOut, L=Warsaw, ST=Mazovia, C=PL"
"@
    
    # Remove existing keystore if it exists
    if (Test-Path $keystorePath) {
        Write-ColorOutput "⚠️  Removing existing keystore..." "Yellow"
        Remove-Item $keystorePath -Force
    }
    
    Write-ColorOutput "Executing: $keytoolCmd" "Gray"
    Invoke-Expression $keytoolCmd
    
    if (Test-Path $keystorePath) {
        Write-ColorOutput "✅ Certificate generated successfully at: $keystorePath" "Green"
        return $true
    } else {
        Write-ColorOutput "❌ Failed to generate certificate" "Red"
        return $false
    }
}

function New-MkcertCertificate {
    param($OutputPath, $Password)
    
    Write-ColorOutput "`n🔐 Generating certificate with mkcert..." "Cyan"
    
    # Install local CA if not already installed
    Write-ColorOutput "Installing local CA (may require admin privileges)..." "Yellow"
    mkcert -install
    
    # Generate certificate files
    $certName = "localhost+2"
    Push-Location $OutputPath
    try {
        Write-ColorOutput "Generating certificate for localhost, 127.0.0.1, ::1..." "Gray"
        mkcert localhost 127.0.0.1 ::1
        
        $pemFile = "$certName.pem"
        $keyFile = "$certName-key.pem"
        
        if (-not (Test-Path $pemFile) -or -not (Test-Path $keyFile)) {
            throw "Certificate files not generated"
        }
        
        # Convert to PKCS12 if OpenSSL is available
        if (Test-OpenSSL) {
            Write-ColorOutput "Converting to PKCS12 format..." "Gray"
            
            $opensslCmd = @"
openssl pkcs12 -export `
    -in "$pemFile" `
    -inkey "$keyFile" `
    -out keystore.p12 `
    -name tomcat `
    -password pass:$Password
"@
            Invoke-Expression $opensslCmd
            
            if (Test-Path "keystore.p12") {
                Write-ColorOutput "✅ PKCS12 keystore created successfully" "Green"
                
                # Clean up PEM files
                Remove-Item $pemFile, $keyFile -Force
                Write-ColorOutput "Cleaned up temporary PEM files" "Gray"
            }
        } else {
            Write-ColorOutput "⚠️  OpenSSL not found. PEM files generated but not converted to PKCS12" "Yellow"
            Write-ColorOutput "You'll need to convert them manually or install OpenSSL" "Yellow"
        }
        
        return $true
    } finally {
        Pop-Location
    }
}

function Show-NextSteps {
    Write-ColorOutput "`n📋 Next Steps:" "Cyan"
    Write-ColorOutput "1. The keystore.p12 file has been created in: $OutputPath" "White"
    Write-ColorOutput "2. Run the application with SSL profile:" "White"
    Write-ColorOutput "   mvn spring-boot:run -Dspring.profiles.active=dev,ssl" "Yellow"
    Write-ColorOutput "3. Access the application at:" "White"
    Write-ColorOutput "   https://localhost:8080" "Yellow"
    Write-ColorOutput "4. If you see a browser warning, click 'Advanced' and 'Proceed to localhost'" "White"
    Write-ColorOutput "`n⚠️  Important for OAuth:" "Cyan"
    Write-ColorOutput "   Update Meta Dashboard redirect URIs to use HTTPS:" "White"
    Write-ColorOutput "   - https://localhost:8080/api/auth/social/callback/instagram" "Yellow"
}

# Main script logic
Write-ColorOutput "🔒 CheckItOut SSL Certificate Setup" "Magenta"
Write-ColorOutput "===================================" "Magenta"

# Check for required tools
Write-ColorOutput "`nChecking prerequisites..." "Cyan"

$hasKeytool = Test-JavaKeytool
$hasMkcert = Test-Mkcert
$hasOpenSSL = Test-OpenSSL

Write-ColorOutput "Java keytool: $(if($hasKeytool){'✅ Found'}else{'❌ Not found'})" "$(if($hasKeytool){'Green'}else{'Red'})"
Write-ColorOutput "mkcert:       $(if($hasMkcert){'✅ Found'}else{'❌ Not found'})" "$(if($hasMkcert){'Green'}else{'Red'})"
Write-ColorOutput "OpenSSL:      $(if($hasOpenSSL){'✅ Found'}else{'⚠️  Not found (optional)'})" "$(if($hasOpenSSL){'Green'}else{'Yellow'})"

if (-not $hasKeytool -and $Method -eq "self-signed") {
    Write-ColorOutput "`n❌ Java keytool is required for self-signed certificates" "Red"
    Write-ColorOutput "Install Java JDK or use mkcert method instead" "Yellow"
    exit 1
}

if (-not $hasMkcert -and $Method -eq "mkcert") {
    Write-ColorOutput "`n❌ mkcert is not installed" "Red"
    Write-ColorOutput "Install with: choco install mkcert" "Yellow"
    Write-ColorOutput "Or download from: https://github.com/FiloSottile/mkcert" "Yellow"
    exit 1
}

# Create output directory if it doesn't exist
if (-not (Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
}

# Check if keystore already exists
$keystorePath = Join-Path $OutputPath "keystore.p12"
if (Test-Path $keystorePath) {
    Write-ColorOutput "`n⚠️  Keystore already exists at: $keystorePath" "Yellow"
    $response = Read-Host "Overwrite existing keystore? (y/n)"
    if ($response -ne 'y') {
        Write-ColorOutput "Keeping existing keystore. Exiting." "Gray"
        exit 0
    }
}

# Execute based on method
switch ($Method) {
    "check" {
        Write-ColorOutput "`nPlease specify a method:" "Cyan"
        Write-ColorOutput "  .\setup-ssl.ps1 self-signed  # Use Java keytool (simple)" "White"
        Write-ColorOutput "  .\setup-ssl.ps1 mkcert       # Use mkcert (recommended)" "White"
        
        if ($hasKeytool) {
            Write-ColorOutput "`n💡 Tip: You have Java keytool, so 'self-signed' method will work" "Green"
        }
        if ($hasMkcert) {
            Write-ColorOutput "💡 Tip: You have mkcert installed, recommended for better browser trust" "Green"
        }
    }
    
    "self-signed" {
        if (New-SelfSignedCertificate -OutputPath $OutputPath -Password $Password) {
            Show-NextSteps
        }
    }
    
    "mkcert" {
        if (New-MkcertCertificate -OutputPath $OutputPath -Password $Password) {
            Show-NextSteps
        }
    }
}

Write-ColorOutput "`n✨ Done!" "Magenta"