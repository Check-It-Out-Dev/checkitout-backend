#!/bin/bash
# =============================================================================
# Certificate Setup for Loki mTLS Authentication with Grafana Alloy
# Creates CA, server, and client certificates for secure access
# Updated for Grafana Alloy (replaces Promtail) - August 2025
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
CERT_DIR="/opt/instagram-platform/loki/certs"
CA_DAYS=3650  # 10 years
CERT_DAYS=365  # 1 year
COUNTRY="US"
STATE="YourState"
CITY="YourCity"
ORG="CheckItOut"
OU="Operations"
CA_CN="Loki CA"
SERVER_CN="loki.checkitout.app"

echo -e "${BLUE}Setting up certificates for Loki mTLS authentication...${NC}"

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo -e "${RED}This script must be run as root${NC}"
   exit 1
fi

# Create certificate directory structure
echo -e "${GREEN}Creating certificate directory structure...${NC}"
mkdir -p "${CERT_DIR}"/{ca,server,clients}
chmod 700 "${CERT_DIR}"

# =============================================================================
# Certificate Authority (CA)
# =============================================================================
echo -e "${GREEN}Creating Certificate Authority...${NC}"

# Generate CA private key
openssl genrsa -out "${CERT_DIR}/ca/ca-key.pem" 4096
chmod 400 "${CERT_DIR}/ca/ca-key.pem"

# Create CA certificate
cat > "${CERT_DIR}/ca/ca.conf" << EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_ca
prompt = no

[req_distinguished_name]
C = ${COUNTRY}
ST = ${STATE}
L = ${CITY}
O = ${ORG}
OU = ${OU}
CN = ${CA_CN}

[v3_ca]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical,CA:true
keyUsage = critical, digitalSignature, keyCertSign, cRLSign
EOF

openssl req -new -x509 -days ${CA_DAYS} \
    -key "${CERT_DIR}/ca/ca-key.pem" \
    -out "${CERT_DIR}/ca/ca-cert.pem" \
    -config "${CERT_DIR}/ca/ca.conf"

chmod 444 "${CERT_DIR}/ca/ca-cert.pem"

# =============================================================================
# Server Certificate (for nginx)
# =============================================================================
echo -e "${GREEN}Creating server certificate...${NC}"

# Generate server private key
openssl genrsa -out "${CERT_DIR}/server/server-key.pem" 2048
chmod 400 "${CERT_DIR}/server/server-key.pem"

# Create server certificate request
cat > "${CERT_DIR}/server/server.conf" << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = ${COUNTRY}
ST = ${STATE}
L = ${CITY}
O = ${ORG}
OU = ${OU}
CN = ${SERVER_CN}

[v3_req]
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = ${SERVER_CN}
DNS.2 = loki
DNS.3 = localhost
IP.1 = 127.0.0.1
EOF

openssl req -new \
    -key "${CERT_DIR}/server/server-key.pem" \
    -out "${CERT_DIR}/server/server-req.pem" \
    -config "${CERT_DIR}/server/server.conf"

# Sign server certificate with CA
openssl x509 -req -days ${CERT_DAYS} \
    -in "${CERT_DIR}/server/server-req.pem" \
    -CA "${CERT_DIR}/ca/ca-cert.pem" \
    -CAkey "${CERT_DIR}/ca/ca-key.pem" \
    -CAcreateserial \
    -out "${CERT_DIR}/server/server-cert.pem" \
    -extensions v3_req \
    -extfile "${CERT_DIR}/server/server.conf"

chmod 444 "${CERT_DIR}/server/server-cert.pem"

# Create server certificate chain
cat "${CERT_DIR}/server/server-cert.pem" "${CERT_DIR}/ca/ca-cert.pem" \
    > "${CERT_DIR}/server/server-chain.pem"
chmod 444 "${CERT_DIR}/server/server-chain.pem"

# =============================================================================
# Client Certificate Function
# =============================================================================
create_client_cert() {
    local CLIENT_NAME=$1
    local CLIENT_CN="${CLIENT_NAME}@${ORG}"
    
    echo -e "${BLUE}Creating client certificate for ${CLIENT_NAME}...${NC}"
    
    local CLIENT_DIR="${CERT_DIR}/clients/${CLIENT_NAME}"
    mkdir -p "${CLIENT_DIR}"
    
    # Generate client private key
    openssl genrsa -out "${CLIENT_DIR}/client-key.pem" 2048
    chmod 400 "${CLIENT_DIR}/client-key.pem"
    
    # Create client certificate request
    cat > "${CLIENT_DIR}/client.conf" << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = ${COUNTRY}
ST = ${STATE}
L = ${CITY}
O = ${ORG}
OU = ${OU}
CN = ${CLIENT_CN}

[v3_req]
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth
EOF
    
    openssl req -new \
        -key "${CLIENT_DIR}/client-key.pem" \
        -out "${CLIENT_DIR}/client-req.pem" \
        -config "${CLIENT_DIR}/client.conf"
    
    # Sign client certificate with CA
    openssl x509 -req -days ${CERT_DAYS} \
        -in "${CLIENT_DIR}/client-req.pem" \
        -CA "${CERT_DIR}/ca/ca-cert.pem" \
        -CAkey "${CERT_DIR}/ca/ca-key.pem" \
        -CAcreateserial \
        -out "${CLIENT_DIR}/client-cert.pem" \
        -extensions v3_req \
        -extfile "${CLIENT_DIR}/client.conf"
    
    chmod 444 "${CLIENT_DIR}/client-cert.pem"
    
    # Create PKCS12 bundle for easy import
    openssl pkcs12 -export \
        -out "${CLIENT_DIR}/${CLIENT_NAME}.p12" \
        -inkey "${CLIENT_DIR}/client-key.pem" \
        -in "${CLIENT_DIR}/client-cert.pem" \
        -certfile "${CERT_DIR}/ca/ca-cert.pem" \
        -passout pass:changeme
    
    chmod 400 "${CLIENT_DIR}/${CLIENT_NAME}.p12"
    
    # Create combined PEM file for curl/Grafana
    cat "${CLIENT_DIR}/client-cert.pem" "${CLIENT_DIR}/client-key.pem" \
        > "${CLIENT_DIR}/${CLIENT_NAME}-combined.pem"
    chmod 400 "${CLIENT_DIR}/${CLIENT_NAME}-combined.pem"
    
    echo -e "${GREEN}Client certificate created for ${CLIENT_NAME}${NC}"
    echo -e "  Certificate: ${CLIENT_DIR}/client-cert.pem"
    echo -e "  Private key: ${CLIENT_DIR}/client-key.pem"
    echo -e "  PKCS12 bundle: ${CLIENT_DIR}/${CLIENT_NAME}.p12"
    echo -e "  Combined PEM: ${CLIENT_DIR}/${CLIENT_NAME}-combined.pem"
}

# =============================================================================
# Create Client Certificates
# =============================================================================

# Grafana Cloud client
create_client_cert "grafana-cloud"

# Alloy client (log collector)
create_client_cert "alloy"

# Admin client
create_client_cert "admin"

# Monitoring client
create_client_cert "monitoring"

# =============================================================================
# Create CRL (Certificate Revocation List)
# =============================================================================
echo -e "${GREEN}Setting up Certificate Revocation List...${NC}"

# Create CRL config
cat > "${CERT_DIR}/ca/crl.conf" << EOF
[ca]
default_ca = CA_default

[CA_default]
database = ${CERT_DIR}/ca/index.txt
crlnumber = ${CERT_DIR}/ca/crlnumber
default_crl_days = 30
default_md = sha256
crl_extensions = crl_ext

[crl_ext]
authorityKeyIdentifier = keyid:always
EOF

# Initialize CRL database
touch "${CERT_DIR}/ca/index.txt"
echo "01" > "${CERT_DIR}/ca/crlnumber"

# Generate initial (empty) CRL
echo "Generating Certificate Revocation List..."
openssl ca -gencrl \
    -keyfile "${CERT_DIR}/ca/ca-key.pem" \
    -cert "${CERT_DIR}/ca/ca-cert.pem" \
    -out "${CERT_DIR}/ca/ca-crl.pem" \
    -config "${CERT_DIR}/ca/crl.conf" 2>/dev/null

if [ $? -eq 0 ]; then
    chmod 444 "${CERT_DIR}/ca/ca-crl.pem"
    echo -e "${GREEN}CRL generated successfully${NC}"
else
    echo -e "${YELLOW}Warning: CRL generation failed (optional component)${NC}"
    # Create empty CRL file so nginx doesn't fail
    touch "${CERT_DIR}/ca/ca-crl.pem"
    chmod 444 "${CERT_DIR}/ca/ca-crl.pem"
fi

# =============================================================================
# Set Permissions
# =============================================================================
echo -e "${GREEN}Setting secure permissions...${NC}"

# Create loki user if it doesn't exist
if ! id -u loki &>/dev/null; then
    useradd -r -s /bin/false -m -d /var/lib/loki loki
fi

# Set ownership - root owns the directory structure
chown -R root:root "${CERT_DIR}"

# Allow loki user to traverse and read necessary certificates
if id -u loki &>/dev/null; then
    setfacl -m u:loki:x "${CERT_DIR}"
    setfacl -m u:loki:x "${CERT_DIR}/ca"
    setfacl -m u:loki:r "${CERT_DIR}/ca/ca-cert.pem"
    setfacl -m u:loki:x "${CERT_DIR}/clients"
    setfacl -m u:loki:x "${CERT_DIR}/clients/alloy"
    setfacl -m u:loki:r "${CERT_DIR}/clients/alloy/alloy-combined.pem"
    echo -e "${GREEN}loki user permissions set${NC}"
else
    echo -e "${YELLOW}Warning: loki user not found - Docker will use UID 995:984${NC}"
fi

# Allow nginx to read server certificates
if id -u www-data &>/dev/null; then
    setfacl -m u:www-data:x "${CERT_DIR}"
    setfacl -m u:www-data:x "${CERT_DIR}/server"
    setfacl -m u:www-data:r "${CERT_DIR}/server/server-cert.pem"
    setfacl -m u:www-data:r "${CERT_DIR}/server/server-key.pem"
    setfacl -m u:www-data:r "${CERT_DIR}/server/server-chain.pem"
    setfacl -m u:www-data:x "${CERT_DIR}/ca"
    setfacl -m u:www-data:r "${CERT_DIR}/ca/ca-cert.pem"
    if [ -f "${CERT_DIR}/ca/ca-crl.pem" ]; then
        setfacl -m u:www-data:r "${CERT_DIR}/ca/ca-crl.pem"
    fi
    echo -e "${GREEN}nginx (www-data) permissions set${NC}"
else
    echo -e "${YELLOW}Warning: www-data user not found - nginx permissions not set${NC}"
fi

# =============================================================================
# Create Verification Script
# =============================================================================
cat > "${CERT_DIR}/verify-cert.sh" << 'SCRIPT_EOF'
#!/bin/bash
# Verify a client certificate against the CA

if [ $# -ne 1 ]; then
    echo "Usage: $0 <client-cert.pem>"
    exit 1
fi

CLIENT_CERT=$1
CA_CERT="/opt/instagram-platform/loki/certs/ca/ca-cert.pem"
CRL="/opt/instagram-platform/loki/certs/ca/ca-crl.pem"

# Verify certificate
openssl verify -CAfile "${CA_CERT}" -crl_check -CRLfile "${CRL}" "${CLIENT_CERT}"

# Show certificate details
echo ""
echo "Certificate details:"
openssl x509 -in "${CLIENT_CERT}" -noout -subject -issuer -dates
SCRIPT_EOF

chmod 755 "${CERT_DIR}/verify-cert.sh"

# =============================================================================
# Create Revocation Script
# =============================================================================
cat > "${CERT_DIR}/revoke-cert.sh" << 'SCRIPT_EOF'
#!/bin/bash
# Revoke a client certificate

if [ $# -ne 1 ]; then
    echo "Usage: $0 <client-cert.pem>"
    exit 1
fi

CLIENT_CERT=$1
CA_CERT="/opt/instagram-platform/loki/certs/ca/ca-cert.pem"
CA_KEY="/opt/instagram-platform/loki/certs/ca/ca-key.pem"
CRL_CONFIG="/opt/instagram-platform/loki/certs/ca/crl.conf"
CRL="/opt/instagram-platform/loki/certs/ca/ca-crl.pem"

# Revoke the certificate
openssl ca -revoke "${CLIENT_CERT}" \
    -keyfile "${CA_KEY}" \
    -cert "${CA_CERT}" \
    -config "${CRL_CONFIG}"

# Regenerate CRL
openssl ca -gencrl \
    -keyfile "${CA_KEY}" \
    -cert "${CA_CERT}" \
    -out "${CRL}" \
    -config "${CRL_CONFIG}"

echo "Certificate revoked and CRL updated"
echo "Remember to reload nginx: systemctl reload nginx"
SCRIPT_EOF

chmod 755 "${CERT_DIR}/revoke-cert.sh"

# =============================================================================
# Final Summary
# =============================================================================
echo ""
echo -e "${GREEN}========================================="
echo -e "✓ Certificate Setup Complete!"
echo -e "=========================================${NC}"
echo ""
echo "Certificates created:"
echo "  CA Certificate:     ${CERT_DIR}/ca/ca-cert.pem"
echo "  Server Certificate: ${CERT_DIR}/server/server-cert.pem"
echo ""
echo "Client certificates (CN format: name@CheckItOut):"
echo "  • alloy@CheckItOut         - ${CERT_DIR}/clients/alloy/"
echo "  • grafana-cloud@CheckItOut - ${CERT_DIR}/clients/grafana-cloud/"
echo "  • admin@CheckItOut         - ${CERT_DIR}/clients/admin/"
echo "  • monitoring@CheckItOut    - ${CERT_DIR}/clients/monitoring/"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Copy certificates for Docker:"
echo "   cp -r ${CERT_DIR}/* /opt/instagram-platform/loki/secrets/certificates/"
echo ""
echo "2. Setup nginx certificates:"
echo "   mkdir -p /etc/nginx/certs/loki/{ca,server}"
echo "   cp ${CERT_DIR}/ca/ca-cert.pem /etc/nginx/certs/loki/ca/ca.crt"
echo "   cp ${CERT_DIR}/server/server-cert.pem /etc/nginx/certs/loki/server/loki-server.crt"
echo "   cp ${CERT_DIR}/server/server-key.pem /etc/nginx/certs/loki/server/loki-server.key"
echo ""
echo "3. Start services:"
echo "   docker-compose up -d"
echo "   systemctl reload nginx"
echo ""
echo "Test with: curl --cert ${CERT_DIR}/clients/admin/admin-combined.pem https://loki.checkitout.app/ready"